"""Unit tests for backtest service."""
import numpy as np
import pytest
from unittest.mock import patch, MagicMock

from services.backtest_service import mape_safe, wape, quick_mape_estimate, walk_forward_backtest


class TestMapeSafe:
    def test_perfect_prediction(self):
        actual = np.array([100.0, 200.0, 300.0])
        predicted = np.array([100.0, 200.0, 300.0])
        assert mape_safe(actual, predicted) == 0.0

    def test_10_percent_error(self):
        actual = np.array([100.0, 100.0, 100.0])
        predicted = np.array([110.0, 110.0, 110.0])
        mape = mape_safe(actual, predicted)
        assert abs(mape - 0.1) < 0.01

    def test_near_zero_values_use_epsilon(self):
        actual = np.array([0.1, 0.1, 0.1])
        predicted = np.array([1.0, 1.0, 1.0])
        mape = mape_safe(actual, predicted)
        assert mape > 0  # Uses epsilon=1.0 default

    def test_mape_threshold_boundary_14_9(self):
        """14.9% MAPE → should NOT trigger fallback (< 15% threshold)."""
        actual = np.full(100, 100.0)
        predicted = actual * 1.149
        mape = mape_safe(actual, predicted)
        assert mape < 0.15

    def test_mape_threshold_boundary_15_1(self):
        """15.1% MAPE → should trigger fallback (> 15% threshold)."""
        actual = np.full(100, 100.0)
        predicted = actual * 1.151
        mape = mape_safe(actual, predicted)
        assert mape > 0.15


class TestWape:
    def test_perfect_prediction(self):
        actual = np.array([100.0, 200.0])
        predicted = np.array([100.0, 200.0])
        assert wape(actual, predicted) == 0.0

    def test_zero_actual(self):
        actual = np.zeros(10)
        predicted = np.ones(10)
        assert wape(actual, predicted) == 0.0


class TestQuickMapeEstimate:
    def test_returns_float(self):
        series = np.random.normal(100, 5, 1000)
        mape = quick_mape_estimate(series)
        assert isinstance(mape, float)
        assert mape >= 0

    def test_short_series_returns_inf(self):
        series = np.random.normal(100, 5, 10)
        mape = quick_mape_estimate(series)
        assert mape == float("inf")


class TestWalkForwardBacktest:
    def test_mocked_backtest_returns_result(self):
        """Fast: mock pm.auto_arima to cover walk_forward_backtest code paths."""
        series = np.random.normal(100, 5, 500)

        mock_model = MagicMock()
        mock_model.predict.return_value = np.array([102.0])

        with patch("pmdarima.auto_arima", return_value=mock_model):
            result = walk_forward_backtest(series, horizon=24, train_ratio=0.8)

        assert result.mape >= 0
        assert result.n_points > 0
        assert len(result.actuals) == len(result.predictions)

    def test_backtest_step_exception_is_handled(self):
        """If auto_arima raises, step is skipped gracefully."""
        series = np.random.normal(100, 5, 500)

        with patch("pmdarima.auto_arima", side_effect=Exception("ARIMA fail")):
            result = walk_forward_backtest(series, horizon=24, train_ratio=0.8)

        assert result.mape == float("inf")
        assert result.n_points == 0
