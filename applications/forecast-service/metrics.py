"""Custom Prometheus metrics for forecast-service.

Technical Spec Section 11.1:
- forecast_arima_fit_seconds (Histogram)
- forecast_fallback_total (Counter)
- forecast_cache_hits_total (Counter)
- forecast_arima_mape_ratio (Gauge)
"""
from prometheus_client import Counter, Histogram, Gauge

ARIMA_FIT_DURATION = Histogram(
    "forecast_arima_fit_seconds",
    "ARIMA model fit latency",
    ["tenant_id", "building_id"],
    buckets=[1, 5, 15, 30, 60, 90, 120],
)

FORECAST_FALLBACK_TOTAL = Counter(
    "forecast_fallback_total",
    "Fallback trigger count",
    ["reason"],
)

FORECAST_CACHE_HIT = Counter(
    "forecast_cache_hits_total",
    "Python TTL cache hit count",
)

FORECAST_MAPE = Gauge(
    "forecast_arima_mape_ratio",
    "Latest MAPE per building",
    ["tenant_id", "building_id"],
)
