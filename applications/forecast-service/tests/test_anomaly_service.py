"""Unit tests for anomaly detection service (S4-13)."""
import numpy as np
import pytest

from services.anomaly_service import detect_anomalies, flag_anomalies_in_forecast


@pytest.fixture
def normal_series():
    np.random.seed(42)
    return np.random.normal(100, 5, 1000)


@pytest.fixture
def series_with_outliers():
    np.random.seed(42)
    series = np.random.normal(100, 5, 1000)
    # Inject clear outliers
    series[100] = 500
    series[500] = -200
    series[800] = 600
    return series


class TestDetectAnomalies:
    def test_returns_boolean_array(self, normal_series):
        result = detect_anomalies(normal_series)
        assert result.dtype == bool
        assert len(result) == len(normal_series)

    def test_finds_injected_outliers(self, series_with_outliers):
        result = detect_anomalies(series_with_outliers, contamination=0.05)
        anomaly_indices = np.where(result)[0]
        # Should detect at least some of the injected outliers
        assert len(anomaly_indices) > 0

    def test_normal_data_few_anomalies(self, normal_series):
        result = detect_anomalies(normal_series, contamination=0.05)
        anomaly_count = np.sum(result)
        # ~5% contamination rate
        assert anomaly_count < len(normal_series) * 0.15

    def test_too_few_data_points(self):
        series = np.array([1.0, 2.0, 3.0])
        result = detect_anomalies(series)
        assert len(result) == 3
        assert not result.any()  # No anomaly detection for <24 points

    def test_contamination_parameter(self, normal_series):
        result_low = detect_anomalies(normal_series, contamination=0.01)
        result_high = detect_anomalies(normal_series, contamination=0.15)
        assert np.sum(result_high) >= np.sum(result_low)


class TestFlagAnomaliesInForecast:
    def test_all_within_ci(self):
        actual = np.array([100.0, 200.0, 300.0])
        predicted = np.array([102.0, 198.0, 295.0])
        lower = np.array([90.0, 180.0, 270.0])
        upper = np.array([110.0, 220.0, 330.0])
        flags = flag_anomalies_in_forecast(actual, predicted, lower, upper)
        assert flags == [False, False, False]

    def test_outside_ci_flagged(self):
        actual = np.array([100.0, 250.0, 300.0])  # 250 > 220 upper
        predicted = np.array([102.0, 198.0, 295.0])
        lower = np.array([90.0, 180.0, 270.0])
        upper = np.array([110.0, 220.0, 330.0])
        flags = flag_anomalies_in_forecast(actual, predicted, lower, upper)
        assert flags[1] is True
        assert flags[0] is False

    def test_nan_actual_not_flagged(self):
        actual = np.array([np.nan, 200.0])
        predicted = np.array([100.0, 200.0])
        lower = np.array([90.0, 180.0])
        upper = np.array([110.0, 220.0])
        flags = flag_anomalies_in_forecast(actual, predicted, lower, upper)
        assert flags[0] is False
