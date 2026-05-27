"""Unit tests for forecast service orchestrator (forecast_service.py)."""
import numpy as np
import pandas as pd
import pytest
from datetime import datetime, timezone
from unittest.mock import patch, MagicMock

import services.forecast_service as _fs_module

from services.forecast_service import forecast_energy
from models.forecast_result import ForecastResult, ForecastPoint


@pytest.fixture(autouse=True)
def clear_forecast_cache():
    """Clear forecast cache before every test to prevent state pollution."""
    _fs_module.cache.clear()
    yield
    _fs_module.cache.clear()


def _make_forecast_result(tenant_id="t1", building_id="b1", model="ARIMA", n_points=24):
    now = datetime.now(timezone.utc)
    points = [
        ForecastPoint(
            timestamp=now,
            predicted_value=55.0,
            confidence_upper=60.0,
            confidence_lower=50.0,
        )
        for _ in range(n_points)
    ]
    return ForecastResult(
        tenant_id=tenant_id,
        building_id=building_id,
        model=model,
        is_fallback=False,
        mape=None,
        points=points,
        generated_at=now,
    )


class TestForecastEnergy:
    def test_empty_dataframe_returns_no_forecast(self):
        """When ClickHouse returns empty df, return NONE fallback."""
        with patch("services.forecast_service.fetch_hourly_energy", return_value=pd.DataFrame()):
            result = forecast_energy("tenant1", "building1", horizon_days=1)
        assert result.is_fallback is True
        assert result.points == []
        assert result.model == "NONE"
        assert result.tenant_id == "tenant1"
        assert result.building_id == "building1"

    def test_short_series_after_preprocess_returns_no_forecast(self):
        """When series after preprocess is too short, return NONE fallback."""
        df = pd.DataFrame({"ts_hour": range(100), "total_kwh": [50.0] * 100})
        short_series = np.random.normal(50, 5, 100)  # < 30 * 24 = 720

        with patch("services.forecast_service.fetch_hourly_energy", return_value=df), \
             patch("services.forecast_service.preprocess", return_value=(short_series, True)):
            result = forecast_energy("tenant1", "building1", horizon_days=1)

        assert result.is_fallback is True
        assert result.model == "NONE"

    def test_arima_ok_mape_within_threshold(self):
        """ARIMA succeeds + MAPE below 15% → keep ARIMA result."""
        df = pd.DataFrame({"ts_hour": range(1000), "total_kwh": [50.0] * 1000})
        long_series = np.random.normal(100, 5, 800)  # > 30 * 24

        arima_result = _make_forecast_result(n_points=24)

        with patch("services.forecast_service.fetch_hourly_energy", return_value=df), \
             patch("services.forecast_service.preprocess", return_value=(long_series, True)), \
             patch("services.forecast_service.forecast_arima", return_value=arima_result), \
             patch("services.backtest_service.quick_mape_estimate", return_value=0.10):
            result = forecast_energy("tenant1", "building1", horizon_days=1)

        assert result.model == "ARIMA"
        assert result.is_fallback is False
        assert result.mape == pytest.approx(0.10)

    def test_arima_high_mape_falls_back_to_naive(self):
        """ARIMA MAPE > 15% threshold → fallback to naive."""
        df = pd.DataFrame({"ts_hour": range(1000), "total_kwh": [50.0] * 1000})
        long_series = np.random.normal(100, 5, 800)

        arima_result = _make_forecast_result(n_points=24)
        naive_result = _make_forecast_result(model="NAIVE", n_points=24)

        with patch("services.forecast_service.fetch_hourly_energy", return_value=df), \
             patch("services.forecast_service.preprocess", return_value=(long_series, True)), \
             patch("services.forecast_service.forecast_arima", return_value=arima_result), \
             patch("services.backtest_service.quick_mape_estimate", return_value=0.20), \
             patch("services.forecast_service.naive_forecast", return_value=naive_result):
            result = forecast_energy("tenant1", "building1", horizon_days=1)

        assert result.is_fallback is True
        assert result.mape == pytest.approx(0.20)

    def test_arima_no_points_skips_mape_check(self):
        """If ARIMA returns 0 points, mape check is skipped."""
        df = pd.DataFrame({"ts_hour": range(1000), "total_kwh": [50.0] * 1000})
        long_series = np.random.normal(100, 5, 800)

        empty_result = _make_forecast_result(n_points=0)

        with patch("services.forecast_service.fetch_hourly_energy", return_value=df), \
             patch("services.forecast_service.preprocess", return_value=(long_series, True)), \
             patch("services.forecast_service.forecast_arima", return_value=empty_result):
            result = forecast_energy("tenant1", "building1", horizon_days=1)

        assert result.mape is None

    def test_cache_hit_returns_cached_result(self):
        """Second call with same params returns cached result (cache hit)."""
        df = pd.DataFrame({"ts_hour": range(1000), "total_kwh": [50.0] * 1000})
        long_series = np.random.normal(100, 5, 800)
        arima_result = _make_forecast_result(n_points=24)

        # Use a unique tenant/building to avoid collisions with other tests
        tenant = "cache-test-tenant"
        building = "cache-test-building"

        with patch("services.forecast_service.fetch_hourly_energy", return_value=df) as mock_fetch, \
             patch("services.forecast_service.preprocess", return_value=(long_series, True)), \
             patch("services.forecast_service.forecast_arima", return_value=arima_result), \
             patch("services.backtest_service.quick_mape_estimate", return_value=0.05):
            result1 = forecast_energy(tenant, building, horizon_days=1)
            result2 = forecast_energy(tenant, building, horizon_days=1)

        # fetch_hourly_energy should be called only once (second call hits cache)
        assert mock_fetch.call_count == 1
        assert result1 is result2
