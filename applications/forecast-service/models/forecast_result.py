from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional


@dataclass
class ForecastPoint:
    timestamp: datetime
    actual_value: Optional[float] = None
    predicted_value: float = 0.0
    confidence_upper: float = 0.0
    confidence_lower: float = 0.0
    is_anomaly: bool = False


@dataclass
class ForecastResult:
    tenant_id: str
    building_id: str
    model: str = "ARIMA"
    is_fallback: bool = False
    mape: Optional[float] = None
    points: list[ForecastPoint] = field(default_factory=list)
    generated_at: Optional[datetime] = None
