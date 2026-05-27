"""LSTM-equivalent forecast service using MLP with lag features.

POC evaluation for Sprint 4 Day-8 gate (S4-13b).
Uses sklearn MLPRegressor with a sliding-window lag feature matrix,
which captures sequential temporal dependencies similar to a shallow LSTM.

Go/No-Go criterion: LSTM MAPE must be < ARIMA MAPE - 0.02 (>2% improvement).
"""
import logging
from dataclasses import dataclass
from datetime import datetime, timezone, timedelta
from typing import Optional

import numpy as np
from sklearn.neural_network import MLPRegressor
from sklearn.preprocessing import StandardScaler

from config import settings
from models.forecast_result import ForecastPoint, ForecastResult
from services.backtest_service import mape_safe, quick_mape_estimate

logger = logging.getLogger(__name__)

# Sliding window size — mirrors 24h daily cycle
LAG_WINDOW = 24
LSTM_IMPROVEMENT_THRESHOLD = 0.02  # GO requires >2% MAPE improvement over ARIMA


@dataclass
class LSTMEvaluationResult:
    lstm_mape: float
    arima_mape: float
    go: bool
    reason: str
    n_train: int
    n_test: int


def _build_lag_features(series: np.ndarray, window: int) -> tuple[np.ndarray, np.ndarray]:
    """Build (X, y) lag feature matrix from a univariate time series.

    X shape: (n_samples, window)
    y shape: (n_samples,)
    """
    X, y = [], []
    for i in range(window, len(series)):
        X.append(series[i - window:i])
        y.append(series[i])
    return np.array(X), np.array(y)


def train_lstm_model(series: np.ndarray) -> tuple[MLPRegressor, StandardScaler, StandardScaler]:
    """Train MLPRegressor (2-layer, 64-32 hidden) on lag features.

    Returns (model, scaler_X, scaler_y) — all three needed for prediction.
    """
    X, y = _build_lag_features(series, LAG_WINDOW)

    scaler_X = StandardScaler()
    scaler_y = StandardScaler()

    X_scaled = scaler_X.fit_transform(X)
    y_scaled = scaler_y.fit_transform(y.reshape(-1, 1)).ravel()

    model = MLPRegressor(
        hidden_layer_sizes=(64, 32),
        activation="relu",
        max_iter=300,
        random_state=42,
        early_stopping=True,
        validation_fraction=0.1,
        n_iter_no_change=15,
        verbose=False,
    )
    model.fit(X_scaled, y_scaled)
    logger.info("LSTM model trained: %d samples, converged=%s", len(X), model.n_iter_ < model.max_iter)
    return model, scaler_X, scaler_y


def forecast_lstm(
    series: np.ndarray,
    horizon_hours: int,
    tenant_id: str,
    building_id: str,
) -> ForecastResult:
    """Autoregressive multi-step forecast using trained MLPRegressor.

    Rolls forward one step at a time using predicted values as input for the
    next step. Confidence interval derived from ±1 std of training residuals.
    """
    if len(series) < settings.forecast_min_data_days * 24:
        logger.warning("Insufficient data for LSTM: %d points", len(series))
        return ForecastResult(
            tenant_id=tenant_id,
            building_id=building_id,
            model="LSTM",
            is_fallback=False,
            mape=None,
            points=[],
            generated_at=datetime.now(timezone.utc),
        )

    model, scaler_X, scaler_y = train_lstm_model(series)

    # Estimate residual std on training set for confidence intervals
    X_all, y_all = _build_lag_features(series, LAG_WINDOW)
    X_all_scaled = scaler_X.transform(X_all)
    y_pred_scaled = model.predict(X_all_scaled)
    y_pred = scaler_y.inverse_transform(y_pred_scaled.reshape(-1, 1)).ravel()
    residual_std = float(np.std(y_all - y_pred))

    # Autoregressive rollout
    window = list(series[-LAG_WINDOW:])
    predictions = []

    for _ in range(horizon_hours):
        x = np.array(window[-LAG_WINDOW:]).reshape(1, -1)
        x_scaled = scaler_X.transform(x)
        y_scaled = model.predict(x_scaled)
        y_val = float(scaler_y.inverse_transform(y_scaled.reshape(-1, 1)).ravel()[0])
        predictions.append(y_val)
        window.append(y_val)

    now = datetime.now(timezone.utc).replace(minute=0, second=0, microsecond=0)
    points = []
    for h, pred in enumerate(predictions):
        ts = now + timedelta(hours=h + 1)
        points.append(ForecastPoint(
            timestamp=ts,
            predicted_value=pred,
            confidence_upper=pred + 1.96 * residual_std,
            confidence_lower=pred - 1.96 * residual_std,
        ))

    # Quick MAPE estimate on last 20% of data
    test_size = max(int(len(series) * 0.2), 24)
    train_s = series[:-test_size]
    test_s = series[-test_size:]

    train_model, sx, sy = train_lstm_model(train_s)
    test_window = list(train_s[-LAG_WINDOW:])
    test_preds = []
    for _ in range(len(test_s)):
        xv = np.array(test_window[-LAG_WINDOW:]).reshape(1, -1)
        yp = sy.inverse_transform(train_model.predict(sx.transform(xv)).reshape(-1, 1)).ravel()[0]
        test_preds.append(float(yp))
        test_window.append(float(yp))

    mape = mape_safe(test_s, np.array(test_preds))

    logger.info("LSTM forecast complete: horizon=%dh, MAPE=%.3f", horizon_hours, mape)

    return ForecastResult(
        tenant_id=tenant_id,
        building_id=building_id,
        model="LSTM",
        is_fallback=False,
        mape=mape,
        points=points,
        generated_at=datetime.now(timezone.utc),
    )


def evaluate_lstm_vs_arima(series: np.ndarray) -> LSTMEvaluationResult:
    """Side-by-side MAPE comparison on held-out 20% test set.

    Returns go/no-go decision for S4-13b Day-8 gate:
    - GO  : LSTM MAPE < ARIMA MAPE - LSTM_IMPROVEMENT_THRESHOLD
    - NO-GO: otherwise
    """
    from services.backtest_service import quick_mape_estimate

    if len(series) < settings.forecast_min_data_days * 24 + LAG_WINDOW:
        return LSTMEvaluationResult(
            lstm_mape=float("inf"),
            arima_mape=float("inf"),
            go=False,
            reason="insufficient_data",
            n_train=0,
            n_test=0,
        )

    test_size = max(int(len(series) * 0.2), 24)
    train_s = series[:-test_size]
    test_s = series[-test_size:]

    # ARIMA MAPE (using quick estimate on same split)
    arima_mape = quick_mape_estimate(series, test_ratio=0.2)

    # LSTM MAPE
    try:
        train_model, sx, sy = train_lstm_model(train_s)
        test_window = list(train_s[-LAG_WINDOW:])
        test_preds = []
        for _ in range(len(test_s)):
            xv = np.array(test_window[-LAG_WINDOW:]).reshape(1, -1)
            yp = sy.inverse_transform(train_model.predict(sx.transform(xv)).reshape(-1, 1)).ravel()[0]
            test_preds.append(float(yp))
            test_window.append(float(yp))
        lstm_mape = mape_safe(test_s, np.array(test_preds))
    except Exception as exc:
        logger.error("LSTM evaluation failed: %s", exc)
        return LSTMEvaluationResult(
            lstm_mape=float("inf"),
            arima_mape=arima_mape,
            go=False,
            reason=f"evaluation_error: {exc}",
            n_train=len(train_s),
            n_test=len(test_s),
        )

    improvement = arima_mape - lstm_mape
    go = improvement > LSTM_IMPROVEMENT_THRESHOLD

    reason = (
        f"LSTM MAPE={lstm_mape:.4f} < ARIMA MAPE={arima_mape:.4f} "
        f"(improvement={improvement:+.4f} > threshold {LSTM_IMPROVEMENT_THRESHOLD})"
        if go
        else
        f"LSTM MAPE={lstm_mape:.4f} NOT < ARIMA MAPE={arima_mape:.4f} - {LSTM_IMPROVEMENT_THRESHOLD} "
        f"(improvement={improvement:+.4f}, threshold={LSTM_IMPROVEMENT_THRESHOLD})"
    )

    logger.info("LSTM gate eval: %s → %s", reason, "GO" if go else "NO-GO")

    return LSTMEvaluationResult(
        lstm_mape=lstm_mape,
        arima_mape=arima_mape,
        go=go,
        reason=reason,
        n_train=len(train_s),
        n_test=len(test_s),
    )
