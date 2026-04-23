#!/usr/bin/env python3
"""
UIP Smart City — UAT Seed Data Script
======================================
Seeds UAT environment với demo data đủ để city authority trải nghiệm:
  - 50 buildings phân bổ trên các quận TPHCM
  - 100 sensors (AIR_QUALITY, TRAFFIC, WATER_QUALITY, NOISE)
  - 3 citizen accounts với meters và 6 tháng invoices + consumption history
  - Historical ESG metrics (6 tháng, 30 buildings)
  - Sample alert events

Script này IDEMPOTENT — chạy nhiều lần an toàn (ON CONFLICT DO NOTHING).

Prerequisites:
  pip3 install psycopg2-binary

Usage:
  # Từ infrastructure/ (sau khi stack up):
  make seed-uat

  # Trực tiếp:
  DB_HOST=localhost DB_PORT=5432 DB_NAME=uip_smartcity DB_USER=uip DB_PASSWORD=... \\
    python3 ../scripts/seed_uat_data.py
"""

from __future__ import annotations

import os
import sys
import random
import logging
from datetime import datetime, timezone, timedelta
from decimal import Decimal

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger("seed-uat")

# ── DB config from environment ────────────────────────────────────────────────
DB_CONFIG = {
    "host":     os.getenv("DB_HOST", "localhost"),
    "port":     int(os.getenv("DB_PORT", "5432")),
    "dbname":   os.getenv("DB_NAME", "uip_smartcity"),
    "user":     os.getenv("DB_USER", "uip"),
    "password": os.getenv("DB_PASSWORD", "changeme_db_password"),
}

try:
    import psycopg2
    import psycopg2.extras
except ImportError:
    log.error("psycopg2 not installed. Run: pip3 install psycopg2-binary")
    sys.exit(1)

# ── HCMC district data ────────────────────────────────────────────────────────
DISTRICTS = [
    {"code": "D1",  "name": "Quận 1",          "lat_c": 10.7769, "lon_c": 106.7009},
    {"code": "D2",  "name": "Quận 2",          "lat_c": 10.7879, "lon_c": 106.7512},
    {"code": "D3",  "name": "Quận 3",          "lat_c": 10.7794, "lon_c": 106.6882},
    {"code": "D4",  "name": "Quận 4",          "lat_c": 10.7589, "lon_c": 106.7036},
    {"code": "D5",  "name": "Quận 5",          "lat_c": 10.7549, "lon_c": 106.6639},
    {"code": "D6",  "name": "Quận 6",          "lat_c": 10.7464, "lon_c": 106.6353},
    {"code": "D7",  "name": "Quận 7",          "lat_c": 10.7347, "lon_c": 106.7218},
    {"code": "D8",  "name": "Quận 8",          "lat_c": 10.7267, "lon_c": 106.6672},
    {"code": "D10", "name": "Quận 10",         "lat_c": 10.7720, "lon_c": 106.6686},
    {"code": "D11", "name": "Quận 11",         "lat_c": 10.7629, "lon_c": 106.6525},
    {"code": "D12", "name": "Quận 12",         "lat_c": 10.8688, "lon_c": 106.6561},
    {"code": "TB",  "name": "Tân Bình",        "lat_c": 10.8011, "lon_c": 106.6526},
    {"code": "BT",  "name": "Bình Thạnh",      "lat_c": 10.8120, "lon_c": 106.7127},
    {"code": "GV",  "name": "Gò Vấp",          "lat_c": 10.8382, "lon_c": 106.6639},
    {"code": "PN",  "name": "Phú Nhuận",       "lat_c": 10.7975, "lon_c": 106.6819},
    {"code": "TD",  "name": "Thủ Đức",         "lat_c": 10.8580, "lon_c": 106.7619},
    {"code": "BC",  "name": "Bình Chánh",      "lat_c": 10.6923, "lon_c": 106.5734},
    {"code": "HM",  "name": "Hóc Môn",         "lat_c": 10.8913, "lon_c": 106.5946},
    {"code": "NB",  "name": "Nhà Bè",          "lat_c": 10.6896, "lon_c": 106.7244},
    {"code": "CC",  "name": "Củ Chi",          "lat_c": 11.0031, "lon_c": 106.4963},
]

# ── 50 buildings distributed across districts ────────────────────────────────
BUILDINGS = [
    # Quận 1
    ("Vinhomes Central Park",       "720A Điện Biên Phủ, Quận 1",               "D1"),
    ("Saigon One Tower",            "12 Hồ Tùng Mậu, Quận 1",                  "D1"),
    ("Bitexco Financial Tower",     "2 Hải Triều, Quận 1",                      "D1"),
    ("The Landmark 81",             "720A Điện Biên Phủ, Quận Bình Thạnh",     "BT"),
    ("Saigon Pearl Tower",          "92 Nguyễn Hữu Cảnh, Quận Bình Thạnh",    "BT"),
    # Quận 2 / Thủ Đức
    ("Masteri Thảo Điền",           "159 Xa Lộ Hà Nội, Quận 2",               "D2"),
    ("Gateway Thảo Điền",           "90 Nguyễn Văn Hưởng, Quận 2",            "D2"),
    ("Empire City Thủ Thiêm",       "Khu đô thị Thủ Thiêm, Quận 2",           "D2"),
    ("The Metropole Thu Thiem",     "Lô 3-6 Khu đô thị Thủ Thiêm",            "D2"),
    ("Vinhomes Grand Park T1",      "Quận 9, TP. Thủ Đức",                     "TD"),
    ("Vinhomes Grand Park T2",      "Quận 9, TP. Thủ Đức",                     "TD"),
    ("Masteri Centre Point",        "Quận 9, TP. Thủ Đức",                     "TD"),
    # Quận 7
    ("Sunrise City North",          "27 Nguyễn Hữu Thọ, Quận 7",              "D7"),
    ("The Pegasus Residences",      "Đường Huỳnh Tấn Phát, Quận 7",           "D7"),
    ("Midtown Phú Mỹ Hưng M7",     "Phú Mỹ Hưng, Quận 7",                    "D7"),
    ("The Peak Midtown",            "Phú Mỹ Hưng, Quận 7",                    "D7"),
    ("Phu My Hung Plaza",           "Nguyễn Lương Bằng, Quận 7",              "D7"),
    # Tân Bình
    ("Giga Mall Tân Bình",          "240A Kha Vạn Cân, Tân Bình",             "TB"),
    ("CT Plaza Tân Sơn Nhất",       "60A Trường Sơn, Tân Bình",               "TB"),
    ("Sunrise City View",           "162 Nguyễn Lương Bằng, Tân Bình",        "TB"),
    # Bình Thạnh
    ("Saigon Pearl Sapphire",       "92 Nguyễn Hữu Cảnh, Bình Thạnh",        "BT"),
    ("The Manor 2",                 "91 Nguyễn Hữu Cảnh, Bình Thạnh",        "BT"),
    ("Millennium Masteri",          "132 Bến Vân Đồn, Quận 4",               "D4"),
    # Gò Vấp
    ("Green Valley Gò Vấp",         "39 Phạm Văn Chiêu, Gò Vấp",             "GV"),
    ("Sky 9 Gò Vấp",               "24/25 Lê Đức Thọ, Gò Vấp",              "GV"),
    # Quận 3
    ("A&B Tower",                   "76 Lê Lai, Quận 3",                       "D3"),
    ("Flemington",                  "182 Lê Đại Hành, Quận 11",               "D11"),
    ("Thuận Kiều Plaza",            "190 Hồng Bàng, Quận 5",                  "D5"),
    # Quận 10
    ("Carillon 7 Tân Phú",          "Quận Tân Phú",                            "D10"),
    ("New Space Bình Dương",        "8A Thủ Đức",                              "TD"),
    # Phú Nhuận
    ("The Park Residence",          "56 Phan Văn Trị, Phú Nhuận",             "PN"),
    ("Saigon Skydeck Tower",        "Điện Biên Phủ, Phú Nhuận",               "PN"),
    # Quận 4
    ("The Gold View",               "346 Bến Vân Đồn, Quận 4",               "D4"),
    ("Sunny Plaza",                 "730 Nguyễn Văn Linh, Quận 7",            "D7"),
    # Hóc Môn / Bình Chánh
    ("Centuria Industrial Park",    "KCN Tân Quy, Củ Chi",                    "CC"),
    ("Saigon West Hills",           "Bình Chánh",                              "BC"),
    # Quận 8
    ("Moonlight Park View",         "Đường Số 7, Quận 8",                     "D8"),
    ("Botanica Premier",            "Hồng Hà, Tân Bình",                      "TB"),
    # Quận 12
    ("Jamona Golden Silk",          "Bến Đình – Bến Lức, Bình Chánh",         "BC"),
    ("Khu phức hợp Tân Tạo",        "Quận 12",                                "D12"),
    # Public buildings for ESG tracking
    ("UBND Quận 1",                 "86 Lê Thánh Tôn, Quận 1",               "D1"),
    ("Bệnh viện Chợ Rẫy",           "201B Nguyễn Chí Thanh, Quận 5",          "D5"),
    ("Bệnh viện Từ Dũ",             "284 Cống Quỳnh, Quận 1",                 "D1"),
    ("Sở TN&MT TPHCM",              "59 Lý Tự Trọng, Quận 1",                "D1"),
    ("Trường ĐH Bách Khoa",         "268 Lý Thường Kiệt, Quận 10",           "D10"),
    ("Sân bay Tân Sơn Nhất",        "Trường Sơn, Tân Bình",                   "TB"),
    ("Cảng Sài Gòn",                "Nguyễn Tất Thành, Quận 4",              "D4"),
    ("Khu CNC Quận 9",              "Lô T2-23, TP. Thủ Đức",                  "TD"),
    ("Trung tâm Hành chính TPHCM",  "86 Lê Thánh Tôn, Quận 1",               "D1"),
    ("Chợ Bến Thành",               "Phạm Ngũ Lão, Quận 1",                  "D1"),
]

# ── 100 sensors by type and district ─────────────────────────────────────────
def _gen_sensors() -> list[dict]:
    sensors = []
    random.seed(42)

    # 40 AIR_QUALITY sensors
    air_districts = ["D1","D2","D3","D4","D5","D6","D7","D8","D10","D11",
                     "D12","TB","BT","GV","PN","TD","BC","HM","NB","CC"]
    for i, dist_code in enumerate(air_districts * 2, start=1):
        d = next(x for x in DISTRICTS if x["code"] == dist_code)
        sensors.append({
            "sensor_id":   f"UAT-ENV-{i:03d}",
            "sensor_name": f"{d['name']} AQI Station #{i}",
            "sensor_type": "AIR_QUALITY",
            "district_code": dist_code,
            "latitude":  d["lat_c"] + random.uniform(-0.02, 0.02),
            "longitude": d["lon_c"] + random.uniform(-0.02, 0.02),
        })

    # 30 TRAFFIC sensors
    traffic_districts = ["D1","D2","D3","D7","TB","BT","GV","TD","D10","D12"]
    for i, dist_code in enumerate(traffic_districts * 3, start=1):
        d = next(x for x in DISTRICTS if x["code"] == dist_code)
        sensors.append({
            "sensor_id":   f"UAT-TRF-{i:03d}",
            "sensor_name": f"{d['name']} Traffic Sensor #{i}",
            "sensor_type": "TRAFFIC",
            "district_code": dist_code,
            "latitude":  d["lat_c"] + random.uniform(-0.015, 0.015),
            "longitude": d["lon_c"] + random.uniform(-0.015, 0.015),
        })

    # 20 WATER_QUALITY sensors
    water_districts = ["D1","D4","D7","D8","NB","BC","TD","D2","D6","D12"]
    for i, dist_code in enumerate(water_districts * 2, start=1):
        d = next(x for x in DISTRICTS if x["code"] == dist_code)
        sensors.append({
            "sensor_id":   f"UAT-WQ-{i:03d}",
            "sensor_name": f"{d['name']} Water Quality #{i}",
            "sensor_type": "WATER_QUALITY",
            "district_code": dist_code,
            "latitude":  d["lat_c"] + random.uniform(-0.025, 0.025),
            "longitude": d["lon_c"] + random.uniform(-0.025, 0.025),
        })

    # 10 NOISE sensors
    noise_districts = ["D1","D3","D7","TB","BT","GV","TD","PN","D10","D12"]
    for i, dist_code in enumerate(noise_districts, start=1):
        d = next(x for x in DISTRICTS if x["code"] == dist_code)
        sensors.append({
            "sensor_id":   f"UAT-NOISE-{i:03d}",
            "sensor_name": f"{d['name']} Noise Monitor #{i}",
            "sensor_type": "NOISE",
            "district_code": dist_code,
            "latitude":  d["lat_c"] + random.uniform(-0.01, 0.01),
            "longitude": d["lon_c"] + random.uniform(-0.01, 0.01),
        })

    return sensors


def seed_buildings(cur) -> list[str]:
    """Insert 50 buildings, return list of (id, name) for downstream seeding."""
    log.info("Seeding %d buildings...", len(BUILDINGS))
    inserted = 0
    building_ids = []
    for name, address, district in BUILDINGS:
        cur.execute("""
            INSERT INTO citizens.buildings (name, address, district)
            VALUES (%s, %s, %s)
            ON CONFLICT DO NOTHING
            RETURNING id
        """, (name, address, district))
        row = cur.fetchone()
        if row:
            building_ids.append(row[0])
            inserted += 1
        else:
            cur.execute(
                "SELECT id FROM citizens.buildings WHERE name = %s", (name,)
            )
            r = cur.fetchone()
            if r:
                building_ids.append(r[0])
    log.info("  %d buildings inserted, %d already existed", inserted, len(BUILDINGS) - inserted)
    return building_ids


def seed_sensors(cur) -> None:
    sensors = _gen_sensors()
    log.info("Seeding %d UAT sensors...", len(sensors))
    inserted = 0
    for s in sensors:
        cur.execute("""
            INSERT INTO environment.sensors
                (sensor_id, sensor_name, sensor_type, district_code, latitude, longitude)
            VALUES (%(sensor_id)s, %(sensor_name)s, %(sensor_type)s,
                    %(district_code)s, %(latitude)s, %(longitude)s)
            ON CONFLICT (sensor_id) DO NOTHING
        """, s)
        if cur.rowcount:
            inserted += 1
    log.info("  %d sensors inserted, %d already existed", inserted, len(sensors) - inserted)


def seed_citizen_app_users(cur) -> None:
    """Ensure 3 UAT citizen accounts exist in auth (citizen1/2/3 should already be there)."""
    # BCrypt $2a$12$ of "citizen_Dev#2026!"
    pw_hash = "$2a$12$PNchUMuWmIKTRq/gi33.Zu744k2c/D.S1DvGXFin/qO/j1QuM6sg2"
    users = [
        ("citizen1", "citizen1@uip.city", pw_hash, "ROLE_CITIZEN"),
        ("citizen2", "citizen2@uip.city", pw_hash, "ROLE_CITIZEN"),
        ("citizen3", "citizen3@uip.city", pw_hash, "ROLE_CITIZEN"),
    ]
    for username, email, pw, role in users:
        cur.execute("""
            INSERT INTO public.app_users (username, email, password_hash, role)
            VALUES (%s, %s, %s, %s)
            ON CONFLICT (username) DO NOTHING
        """, (username, email, pw, role))

    citizen_data = [
        ("citizen1", "citizen1@uip.city", "0901234567", "Nguyễn Văn An",     "001079012345"),
        ("citizen2", "citizen2@uip.city", "0912345678", "Trần Thị Bích",     "001079023456"),
        ("citizen3", "citizen3@uip.city", "0923456789", "Lê Minh Cường",     "001079034567"),
    ]
    for username, email, phone, full_name, cccd in citizen_data:
        cur.execute("""
            INSERT INTO citizens.citizen_accounts (username, email, phone, full_name, cccd, role)
            VALUES (%s, %s, %s, %s, %s, 'ROLE_CITIZEN')
            ON CONFLICT (username) DO NOTHING
        """, (username, email, phone, full_name, cccd))

    log.info("  citizen1/2/3 auth + profiles ensured")


def seed_invoices_6months(cur) -> None:
    """Extend invoice history to 6 months and add consumption records.

    Idempotency strategy:
    - Invoices: skip if (meter_id, billing_month, billing_year) already exists
      (no UNIQUE constraint on invoices table, so we check explicitly)
    - Consumption records: delete existing rows for the meter before re-inserting
      (time-series data — truncate-and-reload is safe for demo env)
    """
    log.info("Seeding 6-month invoice + consumption history for citizen1/2/3...")

    now = datetime.now(tz=timezone.utc)
    random.seed(99)

    # Get citizen IDs
    cur.execute("""
        SELECT ca.id, ca.username, m.id AS meter_id, m.meter_type
        FROM citizens.citizen_accounts ca
        JOIN citizens.meters m ON ca.id = m.citizen_id
        WHERE ca.username IN ('citizen1','citizen2','citizen3')
        ORDER BY ca.username, m.meter_type
    """)
    rows = cur.fetchall()
    if not rows:
        log.warning("  No meters found for citizen1/2/3 — skipping invoice seed")
        return

    invoice_count = 0
    consumption_count = 0

    for citizen_id, username, meter_id, meter_type in rows:
        for months_back in range(1, 7):  # months 1..6
            month_start = (now.replace(day=1) - timedelta(days=30 * months_back))
            billing_month = month_start.month
            billing_year  = month_start.year

            # Idempotency check — invoices table has no UNIQUE constraint
            cur.execute("""
                SELECT COUNT(*) FROM citizens.invoices
                WHERE meter_id = %s AND billing_month = %s AND billing_year = %s
            """, (meter_id, billing_month, billing_year))
            if cur.fetchone()[0] > 0:
                continue

            if meter_type == "ELECTRICITY":
                units = round(random.uniform(180, 350), 2)
                unit_price = Decimal("3500")
            else:  # WATER
                units = round(random.uniform(18, 55), 2)
                unit_price = Decimal("8500")

            amount = round(float(units) * float(unit_price), 0)
            status = "PAID" if months_back > 1 else "UNPAID"
            issued_at = month_start.replace(day=15)
            due_at    = issued_at + timedelta(days=30)
            paid_at   = due_at - timedelta(days=random.randint(1, 10)) if status == "PAID" else None

            cur.execute("""
                INSERT INTO citizens.invoices
                    (citizen_id, meter_id, billing_month, billing_year, meter_type,
                     units_consumed, unit_price, amount, status, issued_at, due_at, paid_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """, (citizen_id, meter_id, billing_month, billing_year, meter_type,
                  units, unit_price, amount, status, issued_at, due_at, paid_at))
            invoice_count += 1

        # Consumption records: delete existing then re-insert (safe for demo)
        cur.execute("""
            DELETE FROM citizens.consumption_records
            WHERE meter_id = %s
              AND recorded_at >= NOW() - INTERVAL '181 days'
        """, (meter_id,))
        deleted = cur.rowcount

        if meter_type == "ELECTRICITY":
            baseline = 1500.0
        else:
            baseline = 600.0

        reading = baseline
        for day_back in range(180, -1, -1):
            ts = now - timedelta(days=day_back)
            daily_use = random.uniform(5, 18) if meter_type == "ELECTRICITY" else random.uniform(0.8, 3.5)
            reading += daily_use
            cur.execute("""
                INSERT INTO citizens.consumption_records
                    (meter_id, recorded_at, reading_value, units_used)
                VALUES (%s, %s, %s, %s)
            """, (meter_id, ts, round(reading, 2), round(daily_use, 2)))
            consumption_count += 1

        if deleted:
            log.debug("  Replaced %d existing consumption records for meter %s", deleted, meter_id)

    log.info("  %d invoices inserted, %d consumption records seeded",
             invoice_count, consumption_count)


def seed_esg_metrics(cur, building_ids: list) -> None:
    """Insert 6 months of ESG metrics for 30 buildings.

    Idempotency: delete existing UAT ESG rows for the target buildings before
    re-inserting. esg.clean_metrics uses BIGSERIAL PK — no UNIQUE constraint on
    (source_id, timestamp), so explicit truncate-and-reload is used.
    """
    target_count = min(30, len(building_ids))
    log.info("Seeding 6-month ESG metrics for %d buildings...", target_count)

    now = datetime.now(tz=timezone.utc)
    count = 0
    random.seed(7)

    target_buildings = building_ids[:target_count]
    for bldg_id in target_buildings:
        cur.execute("SELECT name, district FROM citizens.buildings WHERE id = %s", (bldg_id,))
        row = cur.fetchone()
        if not row:
            continue
        bldg_name, district = row
        bldg_code = f"BLDG-UAT-{str(bldg_id)[:8]}"

        # Idempotency: remove existing UAT ESG data for this building before re-seeding
        cur.execute("""
            DELETE FROM esg.clean_metrics
            WHERE building_id = %s AND source_id LIKE 'ESG-%%'
        """, (bldg_code,))

        for day_back in range(180, -1, -1):
            ts = now - timedelta(days=day_back)

            # ENERGY kWh — variation by day of week
            base_energy = 5000 + random.uniform(-500, 500)
            if ts.weekday() >= 5:  # weekend
                base_energy *= 0.7
            cur.execute("""
                INSERT INTO esg.clean_metrics
                    (source_id, metric_type, timestamp, value, unit, building_id, district_code)
                VALUES (%s, 'ENERGY', %s, %s, 'kWh', %s, %s)
            """, (f"ESG-ENERGY-{bldg_code}", ts, round(base_energy, 1), bldg_code, district))
            count += 1

            # WATER m3
            base_water = 120 + random.uniform(-20, 20)
            cur.execute("""
                INSERT INTO esg.clean_metrics
                    (source_id, metric_type, timestamp, value, unit, building_id, district_code)
                VALUES (%s, 'WATER', %s, %s, 'm3', %s, %s)
            """, (f"ESG-WATER-{bldg_code}", ts, round(base_water, 2), bldg_code, district))
            count += 1

            # CARBON tCO2e (0.45 kg CO2/kWh = 0.00045 t/kWh)
            carbon = base_energy * 0.00045
            cur.execute("""
                INSERT INTO esg.clean_metrics
                    (source_id, metric_type, timestamp, value, unit, building_id, district_code)
                VALUES (%s, 'CARBON', %s, %s, 'tCO2e', %s, %s)
            """, (f"ESG-CARBON-{bldg_code}", ts, round(carbon, 4), bldg_code, district))
            count += 1

    log.info("  %d ESG metric rows inserted (181 days × 3 types × %d buildings)",
             count, target_count)


def seed_alert_events(cur) -> None:
    """Insert sample alert events for the past 7 days to populate Alert dashboard.

    Idempotency: delete UAT demo alert events (sensor_id starts with UAT-) before
    re-inserting. alert_events uses UUID PK — no UNIQUE constraint to rely on.
    """
    log.info("Seeding sample alert events (last 7 days)...")

    # Remove previously seeded UAT demo alerts to avoid duplicates on re-run
    cur.execute("""
        DELETE FROM alerts.alert_events
        WHERE sensor_id LIKE 'UAT-%'
          AND detected_at >= NOW() - INTERVAL '8 days'
    """)
    deleted = cur.rowcount
    if deleted:
        log.debug("  Removed %d stale UAT demo alert events", deleted)

    now = datetime.now(tz=timezone.utc)
    random.seed(13)

    sensor_ids = [f"UAT-ENV-{i:03d}" for i in range(1, 21)]
    severities = ["WARNING", "CRITICAL", "EMERGENCY"]
    statuses   = ["OPEN", "ACKNOWLEDGED", "RESOLVED"]

    cur.execute("SELECT id FROM alerts.alert_rules WHERE rule_name = 'AQI WARNING' LIMIT 1")
    row = cur.fetchone()
    rule_id = row[0] if row else None

    count = 0
    for i in range(40):
        hours_back = random.randint(0, 7 * 24)
        detected_at = now - timedelta(hours=hours_back)
        sensor_id = random.choice(sensor_ids)
        severity  = random.choice(severities)
        status    = "RESOLVED" if hours_back > 48 else random.choice(statuses)
        aqi_val   = round(random.uniform(151, 350), 1)

        cur.execute("""
            INSERT INTO alerts.alert_events
                (rule_id, sensor_id, module, measure_type, value, threshold,
                 severity, status, detected_at)
            VALUES (%s, %s, 'ENVIRONMENT', 'aqi', %s, 150.0, %s, %s, %s)
        """, (rule_id, sensor_id, aqi_val, severity, status, detected_at))
        count += 1

    log.info("  %d alert events inserted", count)


def print_summary(cur) -> None:
    queries = [
        ("Citizens buildings",        "SELECT COUNT(*) FROM citizens.buildings"),
        ("Environment sensors",       "SELECT COUNT(*) FROM environment.sensors"),
        ("Citizen accounts",          "SELECT COUNT(*) FROM citizens.citizen_accounts"),
        ("Invoices",                  "SELECT COUNT(*) FROM citizens.invoices"),
        ("Consumption records",       "SELECT COUNT(*) FROM citizens.consumption_records"),
        ("ESG metrics (last 6mo)",    "SELECT COUNT(*) FROM esg.clean_metrics WHERE timestamp >= NOW() - INTERVAL '6 months'"),
        ("Alert events (last 7d)",    "SELECT COUNT(*) FROM alerts.alert_events WHERE detected_at >= NOW() - INTERVAL '7 days'"),
    ]
    log.info("─" * 55)
    log.info("UAT DATA SUMMARY")
    log.info("─" * 55)
    for label, sql in queries:
        cur.execute(sql)
        count = cur.fetchone()[0]
        log.info("  %-35s %6d", label, count)
    log.info("─" * 55)


def main() -> None:
    log.info("Connecting to %s@%s:%s/%s ...",
             DB_CONFIG["user"], DB_CONFIG["host"], DB_CONFIG["port"], DB_CONFIG["dbname"])

    try:
        conn = psycopg2.connect(**DB_CONFIG)
    except psycopg2.OperationalError as e:
        log.error("Cannot connect to database: %s", e)
        log.error("Make sure the UAT stack is running: make uat-up")
        sys.exit(1)

    try:
        with conn:
            with conn.cursor() as cur:
                log.info("=== Starting UAT seed ===")

                seed_citizen_app_users(cur)
                building_ids = seed_buildings(cur)
                seed_sensors(cur)
                seed_invoices_6months(cur)
                seed_esg_metrics(cur, building_ids)
                seed_alert_events(cur)

                print_summary(cur)

        log.info("=== UAT seed completed successfully ===")
        log.info("")
        log.info("Demo credentials:")
        log.info("  Admin:    admin     / admin_Dev#2026!")
        log.info("  Operator: operator  / operator_Dev#2026!")
        log.info("  Citizen:  citizen1  / citizen_Dev#2026!")

    except Exception as e:
        log.exception("Seed failed: %s", e)
        conn.rollback()
        sys.exit(1)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
