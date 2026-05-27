"""Unit tests for ARIMA service."""
import numpy as np
import pytest
from unittest.mock import patch, MagicMock

from services.arima_service import forecast_arima, fit_arima


@pytest.fixture
def hourly_data():
    """365 days of hourly energy data with daily seasonality."""
    np.random.seed(42)
    hours = 365 * 24
    base = 50.0
    daily_pattern = np.sin(np.linspace(0, 2 * np.pi, 24)) * 10
    noise = np.random.normal(0, 2, hours)
    series = np.array([base + daily_pattern[h % 24] + noise[h] for h in range(hours)])
    return series


def _make_mock_arima(n_periods_result=None):
    """Return a mock ARIMA model with predict() method."""
    mock_model = MagicMock()
    if n_periods_result is not None:
        predicted = np.full(n_periods_result, 55.0)
        ci = np.column_stack([predicted - 5, predicted + 5])
        mock_model.predict.return_value = (predicted, ci)
    return mock_model


class TestFitArima:
    @pytest.mark.slow
    def test_fit_returns_model(self, hourly_data):
        model = fit_arima(hourly_data)
        assert model is not None

    @pytest.mark.slow
    def test_fit_with_minimal_data(self):
        series = np.random.normal(100, 5, 720)  # 30 days
        model = fit_arima(series)
        assert model is not None

    def test_fit_calls_auto_arima(self):
        """Fast: verify fit_arima calls pm.auto_arima with correct params."""
        series = np.random.normal(100, 5, 720)
        mock_model = MagicMock()
        with patch("services.arima_service.pm.auto_arima", return_value=mock_model) as mock_aa:
            result = fit_arima(series)
        mock_aa.assert_called_once()
        call_kwargs = mock_aa.call_args[1]
        assert call_kwargs["seasonal"] is True
        assert call_kwargs["m"] == 24
        assert result is mock_model


class TestForecastArima:
    @pytest.mark.slow
    def test_returns_forecast_result(self, hourly_data):
        result = forecast_arima(hourly_data, horizon_hours=24, tenant_id="t1", building_id="b1")
        assert result.tenant_id == "t1"
        assert result.building_id == "b1"
        assert result.model == "ARIMA"
        assert not result.is_fallback
        assert len(result.points) == 24

    @pytest.mark.slow
    def test_forecast_points_have_predicted_values(self, hourly_data):
        result = forecast_arima(hourly_data, horizon_hours=48, tenant_id="t1", building_id="b1")
        for p in result.points:
            assert p.predicted_value > 0
            assert p.confidence_upper > p.confidence_lower

    def test_insufficient_data_returns_empty(self):
        series = np.random.normal(100, 5, 100)  # < 30 days
        result = forecast_arima(series, horizon_hours=24, tenant_id="t1", building_id="b1")
        assert len(result.points) == 0

    def test_mocked_forecast_returns_correct_points(self):
        """Fast: mock fit_arima to cover forecast_arima main path."""
        series = np.random.normal(100, 5, 720 + 24)  # 30 days + 1 day
        mock_model = _make_mock_arima(n_periods_result=24)
        with patch("services.arima_service.fit_arima", return_value=mock_model):
            result = forecast_arima(series, horizon_hours=24, tenant_id="t1", building_id="b1")
        assert result.model == "ARIMA"
        assert not result.is_fallback
        assert len(result.points) == 24
        for p in result.points:
            assert p.predicted_value == 55.0
            assert p.confidence_lower == 50.0
            assert p.confidence_upper == 60.0

    @pytest.mark.slow
    def test_horizon_days_variations(self, hourly_data):
        for horizon in [24, 168, 720]:  # 1 day, 1 week, 30 days
            result = forecast_arima(hourly_data, horizon_hours=horizon, tenant_id="t1", building_id="b1")
            assert len(result.points) == horizon
