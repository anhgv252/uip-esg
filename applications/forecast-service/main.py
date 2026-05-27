from fastapi import FastAPI
from prometheus_fastapi_instrumentator import Instrumentator

from api.router import router
from config.logging_config import setup_logging
from config import settings

setup_logging(settings.log_level)

app = FastAPI(
    title="UIP Forecast Service",
    version="0.1.0",
    description="ARIMA/LSTM energy forecasting service for UIP Smart City",
)

app.include_router(router, prefix="/api/v1/forecast")

Instrumentator().instrument(app).expose(app)


@app.get("/api/v1/forecast/health")
async def health():
    return {"status": "UP"}
