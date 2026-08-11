from fastapi import APIRouter, HTTPException, Header
from pydantic import BaseModel
from typing import List, Optional, Dict
from backend.database.db import get_db_connection
import json
import uuid

router = APIRouter()

# In-memory store: maps OTP request_id -> phone_number
# In production, this would be a proper session store (Redis/DB) with TTL.
_otp_sessions: Dict[str, str] = {}
# In-memory store: maps access_token -> phone_number
_token_sessions: Dict[str, str] = {}

class OtpRequestModel(BaseModel):
    phone_number: str

class OtpVerifyModel(BaseModel):
    request_id: str
    code: str

class MedicalProfileModel(BaseModel):
    blood_group: str = ""
    allergies: List[str] = []
    chronic_illnesses: List[str] = []
    current_medications: List[str] = []

class ProfileUpdateModel(BaseModel):
    full_name: str
    age: str = "0"
    medical_profile: Optional[MedicalProfileModel] = None

@router.post("/login")
def request_otp(request: OtpRequestModel):
    if not request.phone_number:
        raise HTTPException(status_code=400, detail="Phone number required")
    req_id = f"req-{uuid.uuid4()}"
    # Store the mapping so /verify can look up the phone number
    _otp_sessions[req_id] = request.phone_number
    return {"status": "success", "request_id": req_id}

@router.post("/verify")
def verify_otp(request: OtpVerifyModel):
    # Any 6-digit code is accepted (mock OTP).
    # Look up the phone number from the session store.
    phone_number = _otp_sessions.pop(request.request_id, None)

    token = f"token-{request.request_id}"

    # Determine if user is new: check if a profile exists for this phone number.
    is_new = True
    if phone_number:
        conn = get_db_connection()
        row = conn.execute(
            "SELECT id FROM users WHERE phone_number = ?", (phone_number,)
        ).fetchone()
        conn.close()
        is_new = row is None
        # Track the token so profile endpoints can resolve the caller
        _token_sessions[token] = phone_number
    # If we can't find the session (e.g., server restarted), default to new user.

    return {
        "status": "success",
        "accessToken": token,
        "isNewUser": is_new
    }


def _resolve_phone_from_token(authorization: Optional[str]) -> Optional[str]:
    """Extract and resolve phone number from Bearer token. Returns None if unresolvable."""
    if authorization and authorization.startswith("Bearer "):
        token = authorization[7:]
        return _token_sessions.get(token)
    return None


def _build_profile_response(row) -> dict:
    """Build the standard profile response dict from a DB row."""
    return {
        "status": "success",
        "profile": {
            "full_name": row["full_name"],
            "age": row["age"] or "0",
            "medical_profile": {
                "blood_group": row["blood_group"] or "",
                "allergies": row["allergies"].split(",") if row["allergies"] else [],
                "chronic_illnesses": row["chronic_conditions"].split(",") if row["chronic_conditions"] else [],
                "current_medications": row["current_medications"].split(",") if row["current_medications"] else []
            },
            "verification_level": {
                "phone_verified": bool(row["phone_verified"]),
                "national_id_verified": bool(row["national_id_verified"]),
                "face_match_verified": bool(row["face_match_verified"])
            }
        }
    }


@router.get("/profile")
def get_profile(authorization: Optional[str] = Header(None)):
    phone = _resolve_phone_from_token(authorization)
    conn = get_db_connection()

    if phone:
        row = conn.execute("SELECT * FROM users WHERE phone_number = ?", (phone,)).fetchone()
    else:
        # Fallback: return the most recently created user (single-user dev mode)
        row = conn.execute("SELECT * FROM users ORDER BY id DESC LIMIT 1").fetchone()

    conn.close()

    if not row:
        # Return a blank default profile so the app doesn't crash
        return {
            "status": "success",
            "profile": {
                "full_name": "Unknown Victim",
                "age": "0",
                "medical_profile": {
                    "blood_group": "",
                    "allergies": [],
                    "chronic_illnesses": [],
                    "current_medications": []
                },
                "verification_level": {
                    "phone_verified": True,
                    "national_id_verified": False,
                    "face_match_verified": False
                }
            }
        }

    return _build_profile_response(row)


@router.post("/profile")
def update_profile(profile_data: ProfileUpdateModel, authorization: Optional[str] = Header(None)):
    phone = _resolve_phone_from_token(authorization)
    conn = get_db_connection()

    allergies_str = ",".join(profile_data.medical_profile.allergies) if profile_data.medical_profile else ""
    conditions_str = ",".join(profile_data.medical_profile.chronic_illnesses) if profile_data.medical_profile else ""
    meds_str = ",".join(profile_data.medical_profile.current_medications) if profile_data.medical_profile else ""
    blood_group = profile_data.medical_profile.blood_group if profile_data.medical_profile else ""

    if phone:
        # Token-aware upsert: find or create this user's profile by phone number
        row = conn.execute("SELECT * FROM users WHERE phone_number = ?", (phone,)).fetchone()
        if row:
            conn.execute("""
                UPDATE users SET
                    full_name = ?,
                    age = ?,
                    blood_group = ?,
                    allergies = ?,
                    chronic_conditions = ?,
                    current_medications = ?
                WHERE phone_number = ?
            """, (profile_data.full_name, profile_data.age, blood_group,
                  allergies_str, conditions_str, meds_str, phone))
        else:
            conn.execute("""
                INSERT INTO users (phone_number, full_name, age, blood_group, allergies, chronic_conditions, current_medications)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """, (phone, profile_data.full_name, profile_data.age, blood_group,
                  allergies_str, conditions_str, meds_str))
        conn.commit()
        updated_row = conn.execute("SELECT * FROM users WHERE phone_number = ?", (phone,)).fetchone()
    else:
        # Fallback: single-user dev mode — update the first user or insert one
        row = conn.execute("SELECT * FROM users ORDER BY id LIMIT 1").fetchone()
        if row:
            conn.execute("""
                UPDATE users SET
                    full_name = ?,
                    age = ?,
                    blood_group = ?,
                    allergies = ?,
                    chronic_conditions = ?,
                    current_medications = ?
                WHERE id = ?
            """, (profile_data.full_name, profile_data.age, blood_group,
                  allergies_str, conditions_str, meds_str, row["id"]))
        else:
            conn.execute("""
                INSERT INTO users (phone_number, full_name, age, blood_group, allergies, chronic_conditions, current_medications)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """, ("+254700000000", profile_data.full_name, profile_data.age, blood_group,
                  allergies_str, conditions_str, meds_str))
        conn.commit()
        updated_row = conn.execute("SELECT * FROM users ORDER BY id LIMIT 1").fetchone()

    conn.close()
    return _build_profile_response(updated_row)
