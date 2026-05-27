from fastapi import APIRouter, Depends, Query

from api.dependencies import get_tenant_id
from api.schemas import ForecastResponse, ForecastPointSchema, AnomalyResponse
from services.forecast_service import forecast_energy
from services.anomaly_service import detect_anomalies, flag_anomalies_in_forecast
from services.lstm_service import forecast_lstm, evaluate_lstm_vs_arima

router = APIRouter()


@router.get("/energy", response_model=ForecastResponse)
async def forecast_energy_endpoint(
    building_id: str = Query(..., description="Building ID"),
    horizon_days: int = Query(30, ge=1, le=90),
    include_backtest: bool = Query(False),
    tenant_id: str = Depends(get_tenant_id),
):
    """Energy forecast: ARIMA with naive fallback if MAPE > threshold."""
    result = forecast_energy(tenant_id, building_id, horizon_days)

    return ForecastResponse(
        tenant_id=result.tenant_id,
        building_id=result.building_id,
        model=result.model,
        is_fallback=result.is_fallback,
        mape=result.mape,
        points=[
            ForecastPointSchema(
                timestamp=p.timestamp,
                actual_value=p.actual_value,
                predicted_value=p.predicted_value,
                confidence_upper=p.confidence_upper,
                confidence_lower=p.confidence_lower,
                is_anomaly=p.is_anomaly,
            )
            for p in result.points
        ],
        generated_at=result.generated_at,
    )


@router.get("/energy/lstm", response_model=ForecastResponse)
async def forecast_energy_lstm_endpoint(
    building_id: str = Query(..., description="Building ID"),
    horizon_days: int = Query(30, ge=1, le=90),
    tenant_id: str = Depends(get_tenant_id),
):
    """Energy forecast using LSTM (experimental — S4-13b Day-8 gate).

    Returns LSTM-based multi-step energy forecast with confidence intervals.
    Model status depends on Day-8 gate evaluation result.
    """
    from data.clickhouse_client import fetch_hourly_energy
    from data.preprocessor import preprocess

    df = fetch_hourly_energy(tenant_id, building_id, days=365)
    if df.empty:
        from datetime import datetime, timezone
        from models.forecast_result import ForecastResult
        result = ForecastResult(
            tenant_id=tenant_id, building_id=building_id,
            model="LSTM", is_fallback=False, mape=None,
            points=[], generated_at=datetime.now(timezone.utc),
        )
    else:
        series, _ = preprocess(df)
        result = forecast_lstm(series, horizon_days * 24, tenant_id, building_id)

    return ForecastResponse(
        tenant_id=result.tenant_id,
        building_id=result.building_id,
        model=result.model,
        is_fallback=result.is_fallback,
        mape=result.mape,
        points=[
            ForecastPointSchema(
                timestamp=p.timestamp,
                actual_value=p.actual_value,
                predicted_value=p.predicted_value,
                confidence_upper=p.confidence_upper,
                confidence_lower=p.confidence_lower,
                is_anomaly=p.is_anomaly,
            )
            for p in result.points
        ],
        generated_at=result.generated_at,
    )


@router.get("/energy/lstm/evaluate")
async def evaluate_lstm_endpoint(
    building_id: str = Query(..., description="Building ID"),
    tenant_id: str = Depends(get_tenant_id),
):
    """S4-13b Day-8 gate: Compare LSTM vs ARIMA MAPE on held-out 20% test set.

    Returns go/no-go decision:
    - go=true  → LSTM MAPE improves >2% over ARIMA (integrate)
    - go=false → LSTM does not beat ARIMA threshold (abort, keep ARIMA)
    """
    from data.clickhouse_client import fetch_hourly_energy
    from data.preprocessor import preprocess

    df = fetch_hourly_energy(tenant_id, building_id, days=365)
    if df.empty:
        return {
            "go": False,
            "reason": "no_data",
            "lstm_mape": None,
            "arima_mape": None,
            "n_train": 0,
            "n_test": 0,
        }

    series, _ = preprocess(df)
    eval_result = evaluate_lstm_vs_arima(series)

    return {
        "go": eval_result.go,
        "reason": eval_result.reason,
        "lstm_mape": round(eval_result.lstm_mape, 6) if eval_result.lstm_mape != float("inf") else None,
        "arima_mape": round(eval_result.arima_mape, 6) if eval_result.arima_mape != float("inf") else None,
        "n_train": eval_result.n_train,
        "n_test": eval_result.n_test,
    }


@router.get("/anomaly")
async def detect_anomalies_endpoint(
    building_id: str = Query(..., description="Building ID"),
    contamination: float = Query(0.05, ge=0.01, le=0.5),
    tenant_id: str = Depends(get_tenant_id),
):
    """Run Isolation Forest anomaly detection on historical energy data."""
    from data.clickhouse_client import fetch_hourly_energy
    from data.preprocessor import preprocess
    import numpy as np

    df = fetch_hourly_energy(tenant_id, building_id, days=90)
    if df.empty:
        return {"anomalies": [], "total_points": 0, "anomaly_count": 0}

    series, _ = preprocess(df)
    anomaly_mask = detect_anomalies(series, contamination=contamination)

    anomaly_indices = np.where(anomaly_mask)[0]
    return {
        "anomalies": [int(i) for i in anomaly_indices],
        "total_points": len(series),
        "anomaly_count": len(anomaly_indices),
    }


@router.get("/models")
async def list_models():
    """List available forecast models and their status."""
    return {
        "models": [
            {"name": "ARIMA", "status": "active", "description": "Auto ARIMA with seasonal=True, m=24"},
            {"name": "NAIVE", "status": "active", "description": "7-day rolling average fallback"},
            {"name": "LSTM", "status": "no-go", "description": "MLP-based sequential forecast — S4-13b Day-8 gate: NO-GO (LSTM MAPE 18.65% > ARIMA MAPE 13.48%). Endpoint retained for evidence; not integrated into main forecast flow."},
        ]
    }
