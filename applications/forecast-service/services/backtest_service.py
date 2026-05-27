import logging
from dataclasses import dataclass

import numpy as np

from config import settings

logger = logging.getLogger(__name__)


@dataclass
class BacktestResult:
    mape: float
    n_points: int
    actuals: list[float]
    predictions: list[float]


def mape_safe(actual: np.ndarray, predicted: np.ndarray, epsilon: float = 1.0) -> float:
    """MAPE with epsilon-smoothing to avoid division by zero."""
    actual_safe = np.where(np.abs(actual) < epsilon, epsilon, actual)
    return float(np.mean(np.abs((actual - predicted) / actual_safe)))


def wape(actual: np.ndarray, predicted: np.ndarray) -> float:
    """Weighted Absolute Percentage Error — robust with zeros."""
    denom = np.sum(np.abs(actual))
    if denom == 0:
        return 0.0
    return float(np.sum(np.abs(actual - predicted)) / denom)


def quick_mape_estimate(series: np.ndarray, test_ratio: float = 0.2, m: int = 24) -> float:
    """Quick MAPE estimate using last 20% of data as test set.

    Uses naive seasonal (last-m period pattern) as the baseline forecast,
    which is the standard reference for seasonal time series.
    """
    test_size = max(int(len(series) * test_ratio), m)
    train = series[:-test_size]
    test = series[-test_size:]

    if len(train) < m:
        return float("inf")

    # Naive seasonal: repeat the last m-period pattern from training data
    last_season = train[-m:]
    repetitions = (len(test) // m) + 1
    predicted = np.tile(last_season, repetitions)[:len(test)]

    return mape_safe(test, predicted)


def walk_forward_backtest(
    series: np.ndarray,
    horizon: int = 24,
    train_ratio: float = 0.8,
) -> BacktestResult:
    """Full walk-forward backtest with auto_arima.

    WARNING: Slow — only use for model evaluation, not real-time requests.
    """
    import pmdarima as pm

    train_size = int(len(series) * train_ratio)
    actuals = []
    predictions = []

    step = max(horizon, 24)  # Step by 1 day to keep runtime reasonable

    for i in range(train_size, len(series) - horizon, step):
        train_window = series[max(0, i - train_size):i]

        try:
            model = pm.auto_arima(
                train_window,
                seasonal=True, m=24,
                stepwise=True,
                suppress_warnings=True,
                error_action="ignore",
            )
            fc = model.predict(n_periods=horizon)
            actuals.append(float(series[i]))
            predictions.append(float(fc[0]))
        except Exception as e:
            logger.debug("Backtest step %d failed: %s", i, e)
            continue

    if not actuals:
        return BacktestResult(mape=float("inf"), n_points=0, actuals=[], predictions=[])

    mape = mape_safe(np.array(actuals), np.array(predictions))
    return BacktestResult(
        mape=mape,
        n_points=len(actuals),
        actuals=actuals,
        predictions=predictions,
    )
