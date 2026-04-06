-- V8: Create Citizen Utilities - Meters, Invoices, Consumption
-- Meter management, invoice billing, and consumption history

CREATE TABLE IF NOT EXISTS citizens.meters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id UUID NOT NULL REFERENCES citizens.citizen_accounts(id) ON DELETE CASCADE,
    meter_code VARCHAR(50) UNIQUE NOT NULL,
    meter_type VARCHAR(20) NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_meters_citizen_id ON citizens.meters(citizen_id);
CREATE INDEX IF NOT EXISTS idx_meters_meter_code ON citizens.meters(meter_code);

CREATE TABLE IF NOT EXISTS citizens.invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id UUID NOT NULL REFERENCES citizens.citizen_accounts(id) ON DELETE CASCADE,
    meter_id UUID REFERENCES citizens.meters(id),
    billing_month INTEGER NOT NULL,
    billing_year INTEGER NOT NULL,
    meter_type VARCHAR(20) NOT NULL,
    units_consumed DECIMAL(10,2),
    unit_price DECIMAL(10,4),
    amount DECIMAL(10,2),
    status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    issued_at TIMESTAMPTZ NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_invoices_citizen_id ON citizens.invoices(citizen_id);
CREATE INDEX IF NOT EXISTS idx_invoices_status ON citizens.invoices(status);
CREATE INDEX IF NOT EXISTS idx_invoices_billing_month_year ON citizens.invoices(billing_year, billing_month);

CREATE TABLE IF NOT EXISTS citizens.consumption_records (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    meter_id UUID NOT NULL REFERENCES citizens.meters(id) ON DELETE CASCADE,
    recorded_at TIMESTAMPTZ NOT NULL,
    reading_value DECIMAL(10,2) NOT NULL,
    units_used DECIMAL(10,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, recorded_at)
);

CREATE INDEX IF NOT EXISTS idx_consumption_meter_id ON citizens.consumption_records(meter_id);
CREATE INDEX IF NOT EXISTS idx_consumption_recorded_at ON citizens.consumption_records(recorded_at DESC);

-- Create hypertable for time-series consumption data (TimescaleDB)
SELECT create_hypertable('citizens.consumption_records', 'recorded_at', if_not_exists => TRUE);

-- Seed meters for test citizens
INSERT INTO citizens.meters (citizen_id, meter_code, meter_type, registered_at)
SELECT ca.id, 'ELE-' || ca.username || '-001', 'ELECTRICITY', NOW()
FROM citizens.citizen_accounts ca
WHERE ca.username IN ('citizen1', 'citizen2', 'citizen3');

INSERT INTO citizens.meters (citizen_id, meter_code, meter_type, registered_at)
SELECT ca.id, 'WTR-' || ca.username || '-001', 'WATER', NOW()
FROM citizens.citizen_accounts ca
WHERE ca.username IN ('citizen1', 'citizen2', 'citizen3');

-- Seed invoices for the past 3 months
INSERT INTO citizens.invoices 
    (citizen_id, meter_id, billing_month, billing_year, meter_type, units_consumed, unit_price, amount, status, issued_at, due_at)
SELECT 
    ca.id,
    m.id,
    (EXTRACT(MONTH FROM CURRENT_DATE)::INT - n.n),
    EXTRACT(YEAR FROM CURRENT_DATE)::INT,
    m.meter_type,
    CASE WHEN m.meter_type = 'ELECTRICITY' THEN 250.50 + RANDOM() * 100 ELSE 35.25 + RANDOM() * 15 END,
    CASE WHEN m.meter_type = 'ELECTRICITY' THEN 3500 ELSE 8500 END,
    CASE WHEN m.meter_type = 'ELECTRICITY' THEN (250.50 + RANDOM() * 100) * 3500 ELSE (35.25 + RANDOM() * 15) * 8500 END,
    CASE WHEN (EXTRACT(MONTH FROM CURRENT_DATE)::INT - n.n) = EXTRACT(MONTH FROM CURRENT_DATE)::INT THEN 'UNPAID' ELSE 'PAID' END,
    DATE_TRUNC('month', CURRENT_DATE - n.n * INTERVAL '1 month') + INTERVAL '15 days',
    DATE_TRUNC('month', CURRENT_DATE - n.n * INTERVAL '1 month') + INTERVAL '45 days'
FROM citizens.citizen_accounts ca
JOIN citizens.meters m ON ca.id = m.citizen_id
CROSS JOIN (SELECT 0 as n UNION SELECT 1 UNION SELECT 2) n
WHERE ca.username IN ('citizen1', 'citizen2', 'citizen3');

-- Seed consumption records for the past month (TimescaleDB time-series)
INSERT INTO citizens.consumption_records (meter_id, recorded_at, reading_value, units_used)
SELECT 
    m.id,
    NOW() - (t.n || ' days')::INTERVAL,
    (RANDOM() * 1000 + 1000),
    RANDOM() * 15
FROM citizens.meters m
CROSS JOIN (SELECT generate_series(0, 29) as n) t
WHERE m.meter_type = 'ELECTRICITY'
ORDER BY m.id, t.n;

INSERT INTO citizens.consumption_records (meter_id, recorded_at, reading_value, units_used)
SELECT 
    m.id,
    NOW() - (t.n || ' days')::INTERVAL,
    (RANDOM() * 500 + 500),
    RANDOM() * 3
FROM citizens.meters m
CROSS JOIN (SELECT generate_series(0, 29) as n) t
WHERE m.meter_type = 'WATER'
ORDER BY m.id, t.n;
