-- Table to store user profiles
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    phone_number TEXT UNIQUE NOT NULL,
    full_name TEXT NOT NULL,
    age TEXT,
    blood_group TEXT,
    allergies TEXT,
    chronic_conditions TEXT,
    current_medications TEXT,
    emergency_contact_phone TEXT,
    mesh_device_mac_address TEXT UNIQUE,
    phone_verified BOOLEAN DEFAULT 1,
    national_id_verified BOOLEAN DEFAULT 0,
    face_match_verified BOOLEAN DEFAULT 0
);

-- Table to cache known hospitals so we don't have to scrape EVERYTHING every time
CREATE TABLE IF NOT EXISTS hospitals (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    facility_name TEXT NOT NULL,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    known_inventory TEXT, -- e.g., "anti-venom, epinephrine, x-ray"
    is_specialized BOOLEAN DEFAULT 0,
    last_scraped_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Table to store emergencies
CREATE TABLE IF NOT EXISTS emergencies (
    emergency_id TEXT PRIMARY KEY,
    user_id TEXT,
    trigger_type TEXT,
    emergency_type TEXT,
    latitude REAL,
    longitude REAL,
    timestamp INTEGER,
    status TEXT
);

-- Table to store responders
CREATE TABLE IF NOT EXISTS responders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    display_name TEXT NOT NULL,
    trust_score REAL,
    completed_assists INTEGER,
    eta_minutes INTEGER,
    is_verified BOOLEAN DEFAULT 0,
    current_status TEXT
);

-- Table to store emergency assignments
CREATE TABLE IF NOT EXISTS emergency_assignments (
    emergency_id TEXT PRIMARY KEY,
    responder_id INTEGER,
    FOREIGN KEY(emergency_id) REFERENCES emergencies(emergency_id),
    FOREIGN KEY(responder_id) REFERENCES responders(id)
);

-- Insert some default mock data based on our project scenario
INSERT INTO hospitals (facility_name, latitude, longitude, known_inventory, is_specialized) 
VALUES ('JKUAT Specialized Dispensary', -1.1023, 37.0199, 'anti-venom, epinephrine, asthma inhalers', 1)
ON CONFLICT DO NOTHING;

-- Insert a mock responder to be assigned automatically
INSERT INTO responders (display_name, trust_score, completed_assists, eta_minutes, is_verified, current_status)
VALUES ('Dr. John Doe', 4.9, 124, 5, 1, 'AVAILABLE')
ON CONFLICT DO NOTHING;

-- Table to store responder denials for reputation tracking
CREATE TABLE IF NOT EXISTS responder_denials (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    responder_id INTEGER,
    emergency_id TEXT,
    reason TEXT,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(responder_id) REFERENCES responders(id),
    FOREIGN KEY(emergency_id) REFERENCES emergencies(emergency_id)
);

-- Table to store live responder locations
CREATE TABLE IF NOT EXISTS responder_locations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    responder_id INTEGER,
    emergency_id TEXT,
    latitude REAL,
    longitude REAL,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(responder_id) REFERENCES responders(id),
    FOREIGN KEY(emergency_id) REFERENCES emergencies(emergency_id)
);