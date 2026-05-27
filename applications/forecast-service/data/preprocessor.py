import numpy as np
import pandas as pd
from statsmodels.tsa.stattools import adfuller


def preprocess(df: pd.DataFrame, freq: str = "h") -> tuple[np.ndarray, bool]:
    """Preprocess raw energy data: fill gaps, remove outliers, check stationarity.

    Returns (clean_series, is_stationary).
    """
    if df.empty:
        return np.array([]), False

    series = df.set_index(df.columns[0])[df.columns[1]]

    # Ensure datetime index
    if not isinstance(series.index, pd.DatetimeIndex):
        series.index = pd.to_datetime(series.index, unit="s")

    # 1. Fill missing hours — linear interpolation
    full_index = pd.date_range(series.index.min(), series.index.max(), freq=freq)
    series = series.reindex(full_index)
    series = series.interpolate(method="linear")

    # 2. Remove outliers — IQR method (3x IQR threshold)
    Q1, Q3 = series.quantile(0.25), series.quantile(0.75)
    IQR = Q3 - Q1
    mask = (series >= Q1 - 3 * IQR) & (series <= Q3 + 3 * IQR)
    series[~mask] = np.nan
    series = series.interpolate(method="linear")

    # 3. Stationarity test (ADF)
    try:
        adf_result = adfuller(series.dropna())
        is_stationary = adf_result[1] < 0.05
    except Exception:
        is_stationary = False

    return series.values, is_stationary
