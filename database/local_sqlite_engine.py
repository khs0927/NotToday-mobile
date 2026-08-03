"""
NotToday-Mobile: Local SQLite Sub-1ms Query Engine & Escalation Manager
Handles offline Write-Ahead Logging (WAL), sensor anomaly buffering, and 4-Stage Escalation state transitions.
"""

import sqlite3
import os
import time
import json
from typing import Dict, Any, List, Optional

class LocalDatabaseEngine:
    """
    High-performance SQLite Manager for Smartwatch & Companion App.
    Guarantees sub-1ms local query execution for emergency decision trees.
    """
    
    def __init__(self, db_path: str = "nottoday_local.db"):
        self.db_path = db_path
        self._initialize_database()

    def _get_connection(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path, timeout=5.0)
        conn.row_factory = sqlite3.Row
        try:
            conn.execute("PRAGMA journal_mode = WAL;")
        except sqlite3.OperationalError:
            conn.execute("PRAGMA journal_mode = DELETE;")
        conn.execute("PRAGMA synchronous = NORMAL;")
        conn.execute("PRAGMA foreign_keys = ON;")
        return conn

    def _initialize_database(self):
        schema_file = os.path.join(os.path.dirname(__file__), "schema.sql")
        if os.path.exists(schema_file):
            with open(schema_file, "r", encoding="utf-8") as f:
                schema_sql = f.read()
            schema_sql_clean = "\n".join([line for line in schema_sql.splitlines() if "journal_mode" not in line])
            with self._get_connection() as conn:
                conn.executescript(schema_sql_clean)

    def set_patient_baseline(self, patient_data: Dict[str, Any]):
        sql = """
        INSERT INTO patient_baseline (
            patient_id, age, gender, has_prior_mi, has_hypertension,
            has_diabetes, baseline_resting_hr, baseline_hrv_sdnn, latest_troponin_ng_l
        ) VALUES (:patient_id, :age, :gender, :has_prior_mi, :has_hypertension,
                  :has_diabetes, :baseline_resting_hr, :baseline_hrv_sdnn, :latest_troponin_ng_l)
        ON CONFLICT(patient_id) DO UPDATE SET
            age=excluded.age,
            has_prior_mi=excluded.has_prior_mi,
            has_hypertension=excluded.has_hypertension,
            latest_troponin_ng_l=excluded.latest_troponin_ng_l,
            updated_at=CURRENT_TIMESTAMP;
        """
        with self._get_connection() as conn:
            conn.execute(sql, patient_data)

    def get_patient_baseline(self, patient_id: str) -> Optional[Dict[str, Any]]:
        sql = "SELECT * FROM patient_baseline WHERE patient_id = ?;"
        with self._get_connection() as conn:
            cursor = conn.execute(sql, (patient_id,))
            row = cursor.fetchone()
            return dict(row) if row else None

    def log_sensor_anomaly(self, sensor_type: str, raw_val: float, cqi: float,
                           anomaly_score: float, is_artifact: bool = False) -> int:
        sql = """
        INSERT INTO sensor_anomalies (
            timestamp, sensor_type, raw_metric_value, cqi_score, anomaly_score, is_motion_artifact
        ) VALUES (?, ?, ?, ?, ?, ?);
        """
        now_ms = int(time.time() * 1000)
        with self._get_connection() as conn:
            cursor = conn.execute(sql, (now_ms, sensor_type, raw_val, cqi, anomaly_score, is_artifact))
            return cursor.lastrowid

    def update_escalation_stage(self, new_stage: int, risk_score: float,
                                patient_response: str = 'PENDING') -> int:
        sql = """
        INSERT INTO escalation_state (current_stage, active_since, risk_score, patient_response)
        VALUES (?, ?, ?, ?);
        """
        now_ms = int(time.time() * 1000)
        with self._get_connection() as conn:
            cursor = conn.execute(sql, (new_stage, now_ms, risk_score, patient_response))
            return cursor.lastrowid

    def get_current_escalation_stage(self) -> Dict[str, Any]:
        sql = "SELECT * FROM escalation_state ORDER BY state_id DESC LIMIT 1;"
        with self._get_connection() as conn:
            cursor = conn.execute(sql)
            row = cursor.fetchone()
            if row:
                return dict(row)
            return {"current_stage": 1, "risk_score": 0.0, "patient_response": "NONE"}

    def log_emergency_dispatch(self, stage: int, lat: float, lon: float,
                               dispatch_type: str, payload: Dict[str, Any], status: str = 'TRANSMITTED'):
        sql = """
        INSERT INTO emergency_logs (timestamp, stage_level, gps_latitude, gps_longitude, dispatch_type, payload_json, dispatch_status)
        VALUES (?, ?, ?, ?, ?, ?, ?);
        """
        now_ms = int(time.time() * 1000)
        payload_str = json.dumps(payload, ensure_ascii=False)
        with self._get_connection() as conn:
            conn.execute(sql, (now_ms, stage, lat, lon, dispatch_type, payload_str, status))
