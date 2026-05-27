import clickhouse_connect

from config import settings


def get_client():
    return clickhouse_connect.get_client(
        host=settings.clickhouse_host,
        port=settings.clickhouse_port,
        database=settings.clickhouse_db,
    )


def fetch_hourly_energy(tenant_id: str, building_id: str, days: int = 365):
    """Fetch hourly energy consumption from ClickHouse.

    ADR-032 Decision 5: parameterized queries — no string interpolation.
    """
    client = get_client()
    query = """
        SELECT
            toUnixTimestamp(toStartOfHour(recorded_at)) AS ts_hour,
            sum(value) AS total_kwh
        FROM esg_readings
        WHERE tenant_id = %(tenant_id)s
          AND building_id = %(building_id)s
          AND metric_type = 'ENERGY'
          AND recorded_at >= now() - INTERVAL %(days)s DAY
        GROUP BY ts_hour
        ORDER BY ts_hour ASC
    """
    return client.query_df(query, parameters={
        "tenant_id": tenant_id,
        "building_id": building_id,
        "days": str(days),
    })
