from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    clickhouse_host: str = "uip-clickhouse"
    clickhouse_port: int = 8123
    clickhouse_db: str = "analytics"
    forecast_cache_ttl_minutes: int = 15
    forecast_mape_threshold: float = 0.15
    forecast_min_data_days: int = 30
    forecast_data_days: int = 32
    log_level: str = "INFO"

    model_config = {"case_sensitive": False}


settings = Settings()
