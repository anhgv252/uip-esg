import logging
from datetime import datetime, timezone, timedelta

import numpy as np

from models.forecast_result import ForecastPoint, ForecastResult

logger = logging.getLogger(__name__)


def naive_forecast(
    series: np.ndarray,
    horizon_hours: int,
    tenant_id: str,
    building_id: str,
) -> ForecastResult:
    """Naive forecast: 7-day rolling average projected forward."""
    # Use last 7 days (168 hours) for rolling average
    window = min(168, len(series))
    recent = series[-window:]
    avg = float(np.mean(recent))

    # Simple seasonal pattern: repeat last 24h pattern scaled to average
    last_24h = series[-24:] if len(series) >= 24 else np.array([avg])
    pattern = last_24h / np.mean(last_24h) if np.mean(last_24h) > 0 else np.ones(24)

    now = datetime.now(timezone.utc).replace(minute=0, second=0, microsecond=0)
    points = []
    for h in range(horizon_hours):
        hour_of_day = h % 24
        pred = avg * pattern[hour_of_day]
        points.append(ForecastPoint(
            timestamp=now + timedelta(hours=h + 1),
            actual_value=None,
            predicted_value=pred,
            confidence_upper=pred * 1.20,
            confidence_lower=pred * 0.80,
            is_anomaly=False,
        ))

    return ForecastResult(
        tenant_id=tenant_id,
        building_id=building_id,
        model="NAIVE",
        is_fallback=True,
        mape=None,
        points=points,
        generated_at=datetime.now(timezone.utc),
    )
