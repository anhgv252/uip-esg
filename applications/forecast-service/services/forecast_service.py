import logging
from datetime import datetime, timezone

import numpy as np

from config import settings
from data.clickhouse_client import fetch_hourly_energy
from data.preprocessor import preprocess
from models.cache import TTLCache
from models.forecast_result import ForecastPoint, ForecastResult
from metrics import ARIMA_FIT_DURATION, FORECAST_FALLBACK_TOTAL, FORECAST_CACHE_HIT, FORECAST_MAPE
from services.arima_service import forecast_arima
from services.naive_service import naive_forecast

logger = logging.getLogger(__name__)

cache = TTLCache(ttl_seconds=settings.forecast_cache_ttl_minutes * 60)


def forecast_energy(tenant_id: str, building_id: str, horizon_days: int) -> ForecastResult:
    """Main forecast orchestrator: try ARIMA, fallback to naive if MAPE > threshold."""
    cache_key = f"{tenant_id}:{building_id}:{horizon_days}"

    cached = cache.get(cache_key)
    if cached is not None:
        logger.info("Cache hit: %s", cache_key)
        FORECAST_CACHE_HIT.inc()
        return cached

    # Fetch data from ClickHouse
    df = fetch_hourly_energy(tenant_id, building_id, days=settings.forecast_data_days)
    if df.empty:
        logger.warning("No data returned from ClickHouse for %s/%s", tenant_id, building_id)
        FORECAST_FALLBACK_TOTAL.labels(reason="insufficient_data").inc()
        return ForecastResult(
            tenant_id=tenant_id, building_id=building_id,
            model="NONE", is_fallback=True, mape=None,
            points=[], generated_at=datetime.now(timezone.utc),
        )

    series, is_stationary = preprocess(df)
    if len(series) < settings.forecast_min_data_days * 24:
        logger.warning("Insufficient data after preprocessing: %d points", len(series))
        FORECAST_FALLBACK_TOTAL.labels(reason="insufficient_data").inc()
        return ForecastResult(
            tenant_id=tenant_id, building_id=building_id,
            model="NONE", is_fallback=True, mape=None,
            points=[], generated_at=datetime.now(timezone.utc),
        )

    # Try ARIMA with timing
    horizon_hours = horizon_days * 24
    with ARIMA_FIT_DURATION.labels(tenant_id=tenant_id, building_id=building_id).time():
        result = forecast_arima(series, horizon_hours, tenant_id, building_id)

    # Check MAPE via backtest
    if result.points:
        from services.backtest_service import quick_mape_estimate
        mape = quick_mape_estimate(series)
        result.mape = mape

        FORECAST_MAPE.labels(tenant_id=tenant_id, building_id=building_id).set(mape)

        if mape > settings.forecast_mape_threshold:
            logger.warning("ARIMA MAPE=%.1f%% > threshold %.0f%% — naive fallback",
                           mape * 100, settings.forecast_mape_threshold * 100)
            FORECAST_FALLBACK_TOTAL.labels(reason="mape_threshold").inc()
            result = naive_forecast(series, horizon_hours, tenant_id, building_id)
            result.is_fallback = True
            result.mape = mape

    cache.set(cache_key, result)
    return result
