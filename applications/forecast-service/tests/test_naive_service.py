"""Unit tests for naive forecast service."""
import numpy as np
import pytest

from services.naive_service import naive_forecast


@pytest.fixture
def hourly_data():
    np.random.seed(42)
    return np.random.normal(100, 10, 8760)  # 365 days


class TestNaiveForecast:
    def test_returns_naive_result(self, hourly_data):
        result = naive_forecast(hourly_data, horizon_hours=24, tenant_id="t1", building_id="b1")
        assert result.model == "NAIVE"
        assert result.is_fallback is True
        assert len(result.points) == 24

    def test_prediction_values_reasonable(self, hourly_data):
        result = naive_forecast(hourly_data, horizon_hours=24, tenant_id="t1", building_id="b1")
        for p in result.points:
            assert p.predicted_value > 0
            assert p.confidence_upper > p.predicted_value
            assert p.confidence_lower < p.predicted_value

    def test_short_series(self):
        series = np.array([10.0] * 48)  # 2 days only
        result = naive_forecast(series, horizon_hours=24, tenant_id="t1", building_id="b1")
        assert len(result.points) == 24

    def test_horizon_boundary(self):
        series = np.ones(168) * 50  # 7 days
        result = naive_forecast(series, horizon_hours=1, tenant_id="t1", building_id="b1")
        assert len(result.points) == 1
