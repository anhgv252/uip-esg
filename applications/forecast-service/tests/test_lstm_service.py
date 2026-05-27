"""Unit tests for LSTM forecast service (S4-13b).

Fast tests only — no real ARIMA fitting.
All external dependencies (ClickHouse, ARIMA) are mocked.
"""
import numpy as np
import pytest
from unittest.mock import patch

from services.lstm_service import (
    _build_lag_features,
    train_lstm_model,
    forecast_lstm,
    evaluate_lstm_vs_arima,
    LSTM_IMPROVEMENT_THRESHOLD,
    LAG_WINDOW,
    LSTMEvaluationResult,
)


@pytest.fixture
def hourly_series():
    """30+ days of synthetic hourly energy data with daily seasonality."""
    np.random.seed(42)
    hours = 40 * 24  # 40 days — above forecast_min_data_days=30
    base = 50.0
    daily = np.sin(np.linspace(0, 2 * np.pi, 24)) * 10
    noise = np.random.normal(0, 2, hours)
    return np.array([base + daily[h % 24] + noise[h] for h in range(hours)])


@pytest.fixture
def short_series():
    """Series shorter than minimum data requirement."""
    return np.random.normal(50, 5, 20 * 24)  # 20 days < forecast_min_data_days=30


# ---------------------------------------------------------------------------
# _build_lag_features
# ---------------------------------------------------------------------------
class TestBuildLagFeatures:
    def test_output_shapes(self, hourly_series):
        X, y = _build_lag_features(hourly_series, LAG_WINDOW)
        expected_rows = len(hourly_series) - LAG_WINDOW
        assert X.shape == (expected_rows, LAG_WINDOW)
        assert y.shape == (expected_rows,)

    def test_lag_content(self):
        series = np.arange(10, dtype=float)
        X, y = _build_lag_features(series, window=3)
        # First row: [0,1,2] → y=3
        np.testing.assert_array_equal(X[0], [0.0, 1.0, 2.0])
        assert y[0] == 3.0

    def test_empty_result_when_series_too_short(self):
        series = np.arange(5, dtype=float)
        X, y = _build_lag_features(series, window=10)
        assert len(X) == 0
        assert len(y) == 0


# ---------------------------------------------------------------------------
# train_lstm_model
# ---------------------------------------------------------------------------
class TestTrainLstmModel:
    def test_returns_fitted_model_and_scalers(self, hourly_series):
        model, scaler_X, scaler_y = train_lstm_model(hourly_series)
        assert model is not None
        assert hasattr(model, "predict")
        assert hasattr(scaler_X, "transform")
        assert hasattr(scaler_y, "inverse_transform")

    def test_model_can_predict(self, hourly_series):
        model, scaler_X, scaler_y = train_lstm_model(hourly_series)
        x = hourly_series[-LAG_WINDOW:].reshape(1, -1)
        x_scaled = scaler_X.transform(x)
        y_scaled = model.predict(x_scaled)
        y_val = scaler_y.inverse_transform(y_scaled.reshape(-1, 1)).ravel()[0]
        assert np.isfinite(y_val)


# ---------------------------------------------------------------------------
# forecast_lstm
# ---------------------------------------------------------------------------
class TestForecastLstm:
    def test_insufficient_data_returns_empty_points(self, short_series):
        result = forecast_lstm(short_series, horizon_hours=24, tenant_id="t1", building_id="b1")
        assert result.model == "LSTM"
        assert result.points == []
        assert result.mape is None

    def test_forecast_returns_correct_horizon(self, hourly_series):
        horizon = 48
        result = forecast_lstm(hourly_series, horizon_hours=horizon, tenant_id="t1", building_id="b1")
        assert len(result.points) == horizon

    def test_forecast_model_name(self, hourly_series):
        result = forecast_lstm(hourly_series, horizon_hours=24, tenant_id="t1", building_id="b1")
        assert result.model == "LSTM"

    def test_forecast_confidence_intervals_ordered(self, hourly_series):
        result = forecast_lstm(hourly_series, horizon_hours=24, tenant_id="t1", building_id="b1")
        for p in result.points:
            assert p.confidence_lower <= p.predicted_value <= p.confidence_upper

    def test_forecast_mape_is_finite(self, hourly_series):
        result = forecast_lstm(hourly_series, horizon_hours=24, tenant_id="t1", building_id="b1")
        assert result.mape is not None
        assert np.isfinite(result.mape)

    def test_forecast_tenant_building_preserved(self, hourly_series):
        result = forecast_lstm(hourly_series, horizon_hours=12, tenant_id="tenant_abc", building_id="bld_xyz")
        assert result.tenant_id == "tenant_abc"
        assert result.building_id == "bld_xyz"


# ---------------------------------------------------------------------------
# evaluate_lstm_vs_arima
# ---------------------------------------------------------------------------
class TestEvaluateLstmVsArima:
    def test_insufficient_data_returns_no_go(self, short_series):
        result = evaluate_lstm_vs_arima(short_series)
        assert isinstance(result, LSTMEvaluationResult)
        assert result.go is False
        assert result.reason == "insufficient_data"

    def test_returns_evaluation_result_type(self, hourly_series):
        result = evaluate_lstm_vs_arima(hourly_series)
        assert isinstance(result, LSTMEvaluationResult)

    def test_mape_values_are_finite(self, hourly_series):
        result = evaluate_lstm_vs_arima(hourly_series)
        assert np.isfinite(result.lstm_mape)
        assert np.isfinite(result.arima_mape)

    def test_n_train_n_test_positive(self, hourly_series):
        result = evaluate_lstm_vs_arima(hourly_series)
        assert result.n_train > 0
        assert result.n_test > 0

    def test_go_true_when_lstm_beats_arima(self, hourly_series):
        """Inject mock so LSTM MAPE is clearly better than ARIMA."""
        with patch("services.lstm_service.train_lstm_model") as mock_train, \
             patch("services.lstm_service.mape_safe") as mock_mape, \
             patch("services.lstm_service.quick_mape_estimate") as mock_arima_mape:

            # ARIMA MAPE = 0.15, LSTM MAPE = 0.10 → improvement = 0.05 > 0.02 → GO
            mock_arima_mape.return_value = 0.15

            from unittest.mock import MagicMock
            from sklearn.preprocessing import StandardScaler
            from sklearn.neural_network import MLPRegressor

            mock_model = MagicMock()
            mock_sx = StandardScaler().fit(np.random.rand(100, LAG_WINDOW))
            mock_sy = StandardScaler().fit(np.random.rand(100, 1))
            mock_sy.mean_ = np.array([0.0])
            mock_sy.scale_ = np.array([1.0])

            mock_model.predict.return_value = np.full(24, 50.0)
            mock_train.return_value = (mock_model, mock_sx, mock_sy)
            mock_mape.return_value = 0.10

            result = evaluate_lstm_vs_arima(hourly_series)
            assert result.go is True

    def test_no_go_when_lstm_does_not_improve(self, hourly_series):
        """LSTM MAPE same as ARIMA → NO-GO."""
        with patch("services.lstm_service.quick_mape_estimate") as mock_arima_mape, \
             patch("services.lstm_service.mape_safe") as mock_mape, \
             patch("services.lstm_service.train_lstm_model") as mock_train:

            mock_arima_mape.return_value = 0.12
            mock_mape.return_value = 0.12  # no improvement

            from unittest.mock import MagicMock
            from sklearn.preprocessing import StandardScaler

            mock_model = MagicMock()
            mock_sx = StandardScaler().fit(np.random.rand(100, LAG_WINDOW))
            mock_sy = StandardScaler().fit(np.random.rand(100, 1))
            mock_sy.mean_ = np.array([0.0])
            mock_sy.scale_ = np.array([1.0])
            mock_model.predict.return_value = np.full(24, 50.0)
            mock_train.return_value = (mock_model, mock_sx, mock_sy)

            result = evaluate_lstm_vs_arima(hourly_series)
            assert result.go is False

    def test_go_false_on_evaluation_error(self, hourly_series):
        """If training raises, result is NO-GO with error reason."""
        with patch("services.lstm_service.train_lstm_model", side_effect=RuntimeError("training_failed")), \
             patch("services.lstm_service.quick_mape_estimate", return_value=0.10):
            result = evaluate_lstm_vs_arima(hourly_series)
            assert result.go is False
            assert "evaluation_error" in result.reason

    def test_improvement_threshold_constant(self):
        assert LSTM_IMPROVEMENT_THRESHOLD == 0.02
