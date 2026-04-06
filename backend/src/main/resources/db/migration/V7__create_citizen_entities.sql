-- V7: Create Citizen Account Module Entities
-- Citizen profiles, households, and buildings

-- Drop legacy citizen tables from POC (different schema: user_id FK instead of username string)
DROP TABLE IF EXISTS citizens.invoices CASCADE;
DROP TABLE IF EXISTS citizens.meters CASCADE;
DROP TABLE IF EXISTS citizens.households CASCADE;
DROP TABLE IF EXISTS citizens.citizen_accounts CASCADE;

CREATE TABLE IF NOT EXISTS citizens.citizen_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(20),
    full_name VARCHAR(255) NOT NULL,
    cccd VARCHAR(20),
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_CITIZEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_citizen_accounts_username ON citizens.citizen_accounts(username);
CREATE INDEX IF NOT EXISTS idx_citizen_accounts_email ON citizens.citizen_accounts(email);

CREATE TABLE IF NOT EXISTS citizens.buildings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    address TEXT,
    district VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_buildings_district ON citizens.buildings(district);

CREATE TABLE IF NOT EXISTS citizens.households (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id UUID NOT NULL REFERENCES citizens.citizen_accounts(id) ON DELETE CASCADE,
    building_id UUID REFERENCES citizens.buildings(id),
    floor VARCHAR(10),
    unit_number VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_households_citizen_id ON citizens.households(citizen_id);
CREATE INDEX IF NOT EXISTS idx_households_building_id ON citizens.households(building_id);

-- Seed buildings for household setup
INSERT INTO citizens.buildings (name, address, district)
VALUES
    ('Chung cư Vinhomes Central Park', '720A Điện Biên Phủ, Quận 1', 'Quận 1'),
    ('Chung cư The Manor', '91 Nguyễn Hữu Cảnh, Quận 1', 'Quận 1'),
    ('Landmark 81', '720A Điện Biên Phủ, Quận 1', 'Quận 1'),
    ('Saigon Pearl', '92 Nguyễn Hữu Cảnh, Quận 1', 'Quận 1'),
    ('Masteri Thảo Điền', '159 Xa Lộ Hà Nội, Quận 2', 'Quận 2');

-- Seed test citizen accounts (NOTE: passwords are NOT stored in citizen_accounts)
-- These usernames should exist in auth.app_users table (from auth module)
INSERT INTO citizens.citizen_accounts (username, email, phone, full_name, cccd, role)
VALUES
    ('citizen1', 'citizen1@example.com', '0912345678', 'Nguyễn Văn A', '123456789012', 'ROLE_CITIZEN'),
    ('citizen2', 'citizen2@example.com', '0987654321', 'Trần Thị B', '987654321123', 'ROLE_CITIZEN'),
    ('citizen3', 'citizen3@example.com', '0901112222', 'Phạm Minh C', '111222333444', 'ROLE_CITIZEN');

-- Link test citizens to buildings
INSERT INTO citizens.households (citizen_id, building_id, floor, unit_number)
SELECT ca.id, b.id, '15', '1501'
FROM citizens.citizen_accounts ca, citizens.buildings b
WHERE ca.username = 'citizen1' AND b.name = 'Chung cư Vinhomes Central Park';

INSERT INTO citizens.households (citizen_id, building_id, floor, unit_number)
SELECT ca.id, b.id, '22', '2205'
FROM citizens.citizen_accounts ca, citizens.buildings b
WHERE ca.username = 'citizen2' AND b.name = 'Landmark 81';

INSERT INTO citizens.households (citizen_id, building_id, floor, unit_number)
SELECT ca.id, b.id, '18', '1803'
FROM citizens.citizen_accounts ca, citizens.buildings b
WHERE ca.username = 'citizen3' AND b.name = 'Masteri Thảo Điền';
