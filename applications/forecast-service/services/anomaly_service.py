"""Isolation Forest anomaly detection for energy data (S4-13).

Detects anomalies in energy consumption data using scikit-learn Isolation Forest.
Anomalies are flagged when actual values fall outside forecast confidence intervals
or are detected as outliers by the model.
"""
import logging

import numpy as np
from sklearn.ensemble import IsolationForest

logger = logging.getLogger(__name__)

DEFAULT_CONTAMINATION = 0.05


def detect_anomalies(
    series: np.ndarray,
    contamination: float = DEFAULT_CONTAMINATION,
) -> np.ndarray:
    """Run Isolation Forest on time series, return boolean mask of anomalies.

    Args:
        series: 1D array of values.
        contamination: Expected proportion of anomalies (0.01–0.5).

    Returns:
        Boolean array where True = anomaly.
    """
    if len(series) < 24:
        logger.warning("Too few data points for anomaly detection: %d", len(series))
        return np.zeros(len(series), dtype=bool)

    X = series.reshape(-1, 1)

    clf = IsolationForest(
        contamination=contamination,
        random_state=42,
        n_estimators=100,
    )
    predictions = clf.fit_predict(X)

    # sklearn: -1 = anomaly, 1 = normal
    return predictions == -1


def flag_anomalies_in_forecast(
    actual: np.ndarray,
    predicted: np.ndarray,
    lower: np.ndarray,
    upper: np.ndarray,
) -> list[bool]:
    """Flag anomalies where actual value falls outside 95% confidence interval.

    Combines CI-based detection with statistical bounds.
    """
    flags = []
    for i in range(len(actual)):
        val = actual[i]
        if val is None or np.isnan(val):
            flags.append(False)
            continue

        outside_ci = bool(val < lower[i] or val > upper[i])
        flags.append(outside_ci)

    return flags
