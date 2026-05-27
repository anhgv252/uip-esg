from datetime import datetime
from typing import Optional

from pydantic import BaseModel


class ForecastPointSchema(BaseModel):
    timestamp: datetime
    actual_value: Optional[float] = None
    predicted_value: float
    confidence_upper: float
    confidence_lower: float
    is_anomaly: bool = False


class ForecastResponse(BaseModel):
    tenant_id: str
    building_id: str
    model: str = "ARIMA"
    is_fallback: bool = False
    mape: Optional[float] = None
    points: list[ForecastPointSchema]
    generated_at: datetime


class AnomalyResponse(BaseModel):
    anomalies: list[int]
    total_points: int
    anomaly_count: int
