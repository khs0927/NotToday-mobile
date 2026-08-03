PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS patient_baseline (
    patient_id TEXT PRIMARY KEY,
    age INTEGER NOT NULL,
    gender TEXT CHECK(gender IN ('MALE', 'FEMALE', 'OTHER')),
    has_prior_mi BOOLEAN DEFAULT 0,
    has_hypertension BOOLEAN DEFAULT 0,
    has_diabetes BOOLEAN DEFAULT 0,
    baseline_resting_hr REAL DEFAULT 70.0,
    baseline_hrv_sdnn REAL DEFAULT 45.0,
    latest_troponin_ng_l REAL DEFAULT 0.0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sensor_anomalies (
    anomaly_id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp INTEGER NOT NULL,
    sensor_type TEXT NOT NULL CHECK(sensor_type IN ('ECG_1LEAD', 'ECG_12LEAD', 'PPG_HR', 'PPG_HRV', 'IMU_FALL', 'SPO2', 'TEMP')),
    raw_metric_value REAL NOT NULL,
    cqi_score REAL NOT NULL CHECK(cqi_score BETWEEN 0.0 AND 1.0),
    anomaly_score REAL NOT NULL CHECK(anomaly_score BETWEEN 0.0 AND 1.0),
    is_motion_artifact BOOLEAN DEFAULT 0,
    lead_off_detected BOOLEAN DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_sensor_anomalies_ts ON sensor_anomalies(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_sensor_anomalies_type ON sensor_anomalies(sensor_type, timestamp DESC);

CREATE TABLE IF NOT EXISTS escalation_state (
    state_id INTEGER PRIMARY KEY AUTOINCREMENT,
    current_stage INTEGER NOT NULL CHECK(current_stage BETWEEN 1 AND 4),
    active_since INTEGER NOT NULL,
    risk_score REAL NOT NULL CHECK(risk_score BETWEEN 0.0 AND 1.0),
    patient_response TEXT CHECK(patient_response IN ('PENDING', 'CONFIRMED_CHEST_PAIN', 'CANCELLED_BY_USER', 'UNRESPONSIVE_TIMEOUT')),
    last_prompt_timestamp INTEGER,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS emergency_logs (
    dispatch_id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp INTEGER NOT NULL,
    stage_level INTEGER NOT NULL CHECK(stage_level IN (3, 4)),
    gps_latitude REAL NOT NULL,
    gps_longitude REAL NOT NULL,
    dispatch_type TEXT NOT NULL CHECK(dispatch_type IN ('SMS_GPS_EMERGENCY', 'CALL_119_PSTN_WEBHOOK', 'FAMILY_ALERT_PUSH')),
    payload_json TEXT NOT NULL,
    dispatch_status TEXT CHECK(dispatch_status IN ('QUEUED', 'TRANSMITTED', 'FAILED_OFFLINE_RETRY')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_emergency_logs_status ON emergency_logs(dispatch_status);
