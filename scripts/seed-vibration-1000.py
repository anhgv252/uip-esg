#!/usr/bin/env python3
"""
SEED VIBRATION 1000 — Sprint 9 S9-DATA-SEED
Purpose: Seed 1000 vibration sensor readings for Welford algorithm E2E testing
Table  : environment.sensor_readings (raw_payload JSONB contains vibration value)
Sensor : VIBE-001 (structural vibration sensor)
Period : Last 30 days, evenly spaced (~43.2 minutes apart)
Data   : Normally distributed around mean=5.2 mm/s, stddev=0.8
Idempotent: Skips if VIBE-001 readings already exist in last 30 days

Usage:
  python3 scripts/seed-vibration-1000.py
  # or with custom DB config:
  DB_HOST=localhost DB_PORT=5432 DB_NAME=uip_smartcity DB_USER=uip DB_PASSWORD=uip python3 scripts/seed-vibration-1000.py
"""

import os
import sys
import json
from datetime import datetime, timedelta, timezone
from typing import List, Tuple

try:
    import psycopg2
    import numpy as np
except ImportError as e:
    print(f"Error: Missing dependencies. Install with: pip install psycopg2-binary numpy")
    sys.exit(1)

# ─── Configuration ───────────────────────────────────────────────────────────
DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = int(os.getenv("DB_PORT", "5432"))
DB_NAME = os.getenv("DB_NAME", "uip_smartcity")
DB_USER = os.getenv("DB_USER", "uip")
DB_PASSWORD = os.getenv("DB_PASSWORD", "uip")

SENSOR_ID = "VIBE-001"
TENANT_ID = "default"
NUM_READINGS = 1000
DAYS_BACK = 30

# Vibration data distribution (TCVN 9386:2012 — typical building vibration)
MEAN_VIBRATION = 5.2  # mm/s (baseline, non-anomalous)
STDDEV_VIBRATION = 0.8  # mm/s (normal variation)

# ─── Helper Functions ────────────────────────────────────────────────────────
def connect_db():
    """Connect to TimescaleDB."""
    try:
        conn = psycopg2.connect(
            host=DB_HOST,
            port=DB_PORT,
            dbname=DB_NAME,
            user=DB_USER,
            password=DB_PASSWORD
        )
        return conn
    except psycopg2.OperationalError as e:
        print(f"Error: Cannot connect to database: {e}")
        sys.exit(1)


def check_existing_readings(conn) -> int:
    """Check if VIBE-001 readings already exist in last 30 days."""
    with conn.cursor() as cur:
        cur.execute("""
            SELECT COUNT(*)
            FROM environment.sensor_readings
            WHERE sensor_id = %s
              AND timestamp > NOW() - INTERVAL '30 days'
        """, (SENSOR_ID,))
        count = cur.fetchone()[0]
    return count


def generate_vibration_data() -> List[Tuple[datetime, float]]:
    """
    Generate 1000 vibration readings over the last 30 days.
    Returns: List of (timestamp, vibration_value) tuples
    """
    now = datetime.now(timezone.utc)
    start_time = now - timedelta(days=DAYS_BACK)
    interval_minutes = (DAYS_BACK * 24 * 60) / NUM_READINGS  # ~43.2 minutes
    
    readings = []
    for i in range(NUM_READINGS):
        timestamp = start_time + timedelta(minutes=i * interval_minutes)
        # Normal distribution with occasional slightly higher values (realistic)
        vibration = np.random.normal(MEAN_VIBRATION, STDDEV_VIBRATION)
        vibration = max(0.1, vibration)  # Floor at 0.1 mm/s (no negative vibration)
        readings.append((timestamp, round(vibration, 2)))
    
    return readings


def insert_readings(conn, readings: List[Tuple[datetime, float]]) -> int:
    """
    Insert vibration readings into environment.sensor_readings.
    Uses raw_payload JSONB to store vibration value.
    Returns: Number of rows inserted
    """
    insert_sql = """
        INSERT INTO environment.sensor_readings
            (sensor_id, timestamp, tenant_id, raw_payload)
        VALUES
            (%s, %s, %s, %s)
        ON CONFLICT DO NOTHING
    """
    
    batch = []
    for timestamp, vibration in readings:
        raw_payload = json.dumps({
            "sensorType": "STRUCTURAL_VIBRATION",
            "value": vibration,
            "unit": "mm/s",
            "quality": "GOOD"
        })
        batch.append((SENSOR_ID, timestamp, TENANT_ID, raw_payload))
    
    with conn.cursor() as cur:
        inserted = 0
        for i, row in enumerate(batch, 1):
            cur.execute(insert_sql, row)
            inserted += cur.rowcount
            
            # Progress indicator every 100 rows
            if i % 100 == 0:
                conn.commit()
                print(f"  Progress: {i}/{NUM_READINGS} rows inserted...")
        
        conn.commit()
    
    return inserted


def update_sensor_last_seen(conn):
    """Update sensors table to mark VIBE-001 as recently active."""
    with conn.cursor() as cur:
        # Register sensor if it doesn't exist
        cur.execute("""
            INSERT INTO environment.sensors
                (sensor_id, sensor_name, sensor_type, district_code, is_active, last_seen_at)
            VALUES
                (%s, 'Demo Vibration Sensor 001', 'STRUCTURAL_VIBRATION', 'D1', TRUE, NOW())
            ON CONFLICT (sensor_id) DO UPDATE
                SET last_seen_at = NOW(),
                    is_active = TRUE
        """, (SENSOR_ID,))
        conn.commit()


# ─── Main Execution ──────────────────────────────────────────────────────────
def main():
    print("=" * 80)
    print("SEED VIBRATION 1000 — Sprint 9 S9-DATA-SEED")
    print("=" * 80)
    print(f"Database: {DB_USER}@{DB_HOST}:{DB_PORT}/{DB_NAME}")
    print(f"Sensor  : {SENSOR_ID}")
    print(f"Count   : {NUM_READINGS} readings over last {DAYS_BACK} days")
    print(f"Data    : Normal(μ={MEAN_VIBRATION}, σ={STDDEV_VIBRATION}) mm/s")
    print()
    
    # Connect to database
    conn = connect_db()
    print("✓ Connected to TimescaleDB")
    
    # Check for existing data
    existing_count = check_existing_readings(conn)
    if existing_count >= NUM_READINGS:
        print(f"⚠ Skipped: {existing_count} readings already exist for {SENSOR_ID} in last 30 days")
        print("  (Idempotent: safe to re-run, but data is already present)")
        conn.close()
        sys.exit(0)
    
    # Generate data
    print(f"Generating {NUM_READINGS} vibration readings...")
    readings = generate_vibration_data()
    print(f"✓ Generated {len(readings)} readings")
    print(f"  Min: {min(v for _, v in readings):.2f} mm/s")
    print(f"  Max: {max(v for _, v in readings):.2f} mm/s")
    print(f"  Mean: {sum(v for _, v in readings) / len(readings):.2f} mm/s")
    print()
    
    # Insert data
    print("Inserting readings into environment.sensor_readings...")
    inserted = insert_readings(conn, readings)
    print(f"✓ Inserted {inserted} new readings")
    
    # Update sensor status
    update_sensor_last_seen(conn)
    print(f"✓ Updated sensor {SENSOR_ID} last_seen_at")
    
    # Summary
    print()
    print("=" * 80)
    print("✓ SUCCESS: Vibration seed data ready for Welford E2E testing")
    print("=" * 80)
    print()
    print("Verification SQL:")
    print(f"  SELECT COUNT(*) FROM environment.sensor_readings WHERE sensor_id = '{SENSOR_ID}';")
    print(f"  SELECT * FROM environment.sensors WHERE sensor_id = '{SENSOR_ID}';")
    print()
    
    conn.close()


if __name__ == "__main__":
    main()
