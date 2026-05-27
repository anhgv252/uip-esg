import logging
from datetime import datetime, timezone, timedelta
from typing import Optional

import numpy as np
import pmdarima as pm

from config import settings
from models.forecast_result import ForecastPoint, ForecastResult

logger = logging.getLogger(__name__)


def fit_arima(series: np.ndarray) -> pm.ARIMA:
    """Fit ARIMA model using auto_arima with seasonal=True, m=24."""
    return pm.auto_arima(
        series,
        start_p=1, start_q=1,
        max_p=3, max_q=3,
        d=None,
        seasonal=False,
        stepwise=True,
        information_criterion="aic",
        trace=False,
        error_action="ignore",
        suppress_warnings=True,
        n_jobs=1,
    )


def forecast_arima(
    series: np.ndarray,
    horizon_hours: int,
    tenant_id: str,
    building_id: str,
) -> ForecastResult:
    """Run ARIMA forecast with 95% confidence intervals."""
    if len(series) < settings.forecast_min_data_days * 24:
        logger.warning("Insufficient data: %d points (< %d days)",
                       len(series), settings.forecast_min_data_days)
        return ForecastResult(
            tenant_id=tenant_id,
            building_id=building_id,
            model="ARIMA",
            is_fallback=False,
            mape=None,
            points=[],
            generated_at=datetime.now(timezone.utc),
        )

    logger.info("Fitting ARIMA: %d data points, horizon=%d hours", len(series), horizon_hours)

    model = fit_arima(series)
    predicted, ci = model.predict(n_periods=horizon_hours, return_conf_int=True, alpha=0.05)

    now = datetime.now(timezone.utc).replace(minute=0, second=0, microsecond=0)
    points = []
    for h in range(horizon_hours):
        ts = now + timedelta(hours=h + 1)
        pred = float(predicted[h])
        lower = float(ci[h, 0])
        upper = float(ci[h, 1])
        points.append(ForecastPoint(
            timestamp=ts,
            actual_value=None,
            predicted_value=pred,
            confidence_upper=upper,
            confidence_lower=lower,
            is_anomaly=False,
        ))

    return ForecastResult(
        tenant_id=tenant_id,
        building_id=building_id,
        model="ARIMA",
        is_fallback=False,
        mape=None,
        points=points,
        generated_at=datetime.now(timezone.utc),
    )
