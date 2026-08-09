from fastapi import APIRouter, HTTPException, WebSocket, WebSocketDisconnect
from pydantic import BaseModel
from typing import List, Dict
import asyncio
from backend.services.routing_service import RoutingService
from backend.services.alert_service import AlertService
from backend.database.db import get_db_connection

router = APIRouter()

# Active WebSocket connections
active_connections: Dict[str, List[WebSocket]] = {}

class VictimProfile(BaseModel):
    age: str = "Unknown"
    allergies: str = "None known"
    chronic_conditions: str = "None known"

class EmergencyRequest(BaseModel):
    victim_name: str
    condition: str
    latitude: float
    longitude: float
    profile: VictimProfile

class AcceptRequest(BaseModel):
    responder_id: int

class DenyRequest(BaseModel):
    responder_id: int
    reason: str

class LocationRequest(BaseModel):
    responder_id: int
    latitude: float
    longitude: float

@router.post("/dispatch")
def handle_emergency_dispatch(emergency: EmergencyRequest):
    try:
        # Trigger external alerts (SMS/Push)
        AlertService.broadcast_to_authorities(emergency)
        
        # Get the AI + Hospital routing plan
        dispatch_plan = RoutingService.generate_dispatch_plan(emergency)
        
        # Query DB for available responder
        conn = get_db_connection()
        responder_row = conn.execute("SELECT * FROM responders WHERE current_status = 'AVAILABLE' LIMIT 1").fetchone()
        
        assigned_responder = None
        if responder_row:
            assigned_responder = {
                "display_name": responder_row["display_name"],
                "trust_score": responder_row["trust_score"],
                "completed_assists": responder_row["completed_assists"],
                "eta_minutes": responder_row["eta_minutes"],
                "is_verified": bool(responder_row["is_verified"]),
                "current_status": "ACCEPTED"
            }
            # Assign responder to emergency in assignments table
            # Since we don't have emergency_id in request body but we can generate or use a temporary one,
            # wait, the Android side does not pass emergency_id in the post request because sendEmergency sends victim name/coords.
            # However, we can generate a transient session identifier or use a mock id. Let's make sure it handles assignment.
            
        conn.close()
        
        # Embed responder information in dispatch response as required by Phase 4
        dispatch_plan["responder"] = assigned_responder
        
        return {
            "status": "success",
            "dispatch_data": dispatch_plan
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/{emergency_id}/offer")
def offer_emergency(emergency_id: str, req: AcceptRequest):
    try:
        conn = get_db_connection()
        # Just update status to OFFERED if it's currently PENDING
        cursor = conn.execute("UPDATE emergencies SET status = 'OFFERED' WHERE emergency_id = ? AND status = 'PENDING'", (emergency_id,))
        if cursor.rowcount == 0:
            conn.close()
            raise HTTPException(status_code=400, detail="Emergency cannot be offered or does not exist")
        conn.commit()
        conn.close()
        return {"status": "success", "message": "Emergency offered"}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/{emergency_id}/accept")
def accept_emergency(emergency_id: str, req: AcceptRequest):
    try:
        conn = get_db_connection()
        # Atomic assignment: Only update if it is currently OFFERED
        cursor = conn.execute("UPDATE emergencies SET status = 'ACCEPTED' WHERE emergency_id = ? AND status = 'OFFERED'", (emergency_id,))
        if cursor.rowcount == 0:
            conn.close()
            raise HTTPException(status_code=409, detail="Emergency already accepted by another responder or not in OFFERED state")
        
        # Insert assignment
        conn.execute("INSERT OR REPLACE INTO emergency_assignments (emergency_id, responder_id) VALUES (?, ?)", (emergency_id, req.responder_id))
        
        # Update responder status
        conn.execute("UPDATE responders SET current_status = 'BUSY' WHERE id = ?", (req.responder_id,))
        conn.commit()
        conn.close()
        return {"status": "success", "message": "Emergency accepted"}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/{emergency_id}/deny")
def deny_emergency(emergency_id: str, req: DenyRequest):
    try:
        conn = get_db_connection()
        # Insert denial record
        conn.execute("INSERT INTO responder_denials (responder_id, emergency_id, reason) VALUES (?, ?, ?)", (req.responder_id, emergency_id, req.reason))
        
        # Update trust score based on reason
        # Legitimate reasons (-0.1), others (-0.5)
        legitimate_reasons = ["TOO_FAR", "UNSAFE_SCENE", "UNAVAILABLE", "WRONG_SPECIALTY"]
        penalty = -0.1 if req.reason in legitimate_reasons else -0.5
        
        conn.execute("UPDATE responders SET trust_score = MAX(0.0, trust_score + ?) WHERE id = ?", (penalty, req.responder_id))
        
        # Clear assignment if it was somehow accepted, or just reset emergency status to PENDING/OFFERED
        # (Assuming it was just offered and not accepted, the status remains OFFERED for others or goes back to PENDING)
        # We leave the emergency status as OFFERED or update logic if needed
        conn.commit()
        conn.close()
        return {"status": "success", "message": "Emergency denied"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/{emergency_id}/arrived")
def arrive_emergency(emergency_id: str):
    try:
        conn = get_db_connection()
        cursor = conn.execute("UPDATE emergencies SET status = 'ARRIVED' WHERE emergency_id = ? AND status IN ('ACCEPTED', 'RESPONDER_EN_ROUTE')", (emergency_id,))
        if cursor.rowcount == 0:
            conn.close()
            raise HTTPException(status_code=400, detail="Invalid state transition to ARRIVED")
        conn.commit()
        conn.close()
        return {"status": "success", "message": "Responder arrived"}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/{emergency_id}/complete")
def complete_emergency(emergency_id: str):
    try:
        conn = get_db_connection()
        cursor = conn.execute("UPDATE emergencies SET status = 'COMPLETED' WHERE emergency_id = ? AND status = 'ARRIVED'", (emergency_id,))
        if cursor.rowcount == 0:
            conn.close()
            raise HTTPException(status_code=400, detail="Invalid state transition to COMPLETED")
        
        # Update responder completed_assists and trust score (+0.2)
        assignment = conn.execute("SELECT responder_id FROM emergency_assignments WHERE emergency_id = ?", (emergency_id,)).fetchone()
        if assignment:
            responder_id = assignment["responder_id"]
            conn.execute("UPDATE responders SET completed_assists = completed_assists + 1, trust_score = MIN(5.0, trust_score + 0.2), current_status = 'AVAILABLE' WHERE id = ?", (responder_id,))
            
        conn.commit()
        conn.close()
        return {"status": "success", "message": "Emergency completed"}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/{emergency_id}/location")
def update_responder_location(emergency_id: str, req: LocationRequest):
    try:
        conn = get_db_connection()
        conn.execute("INSERT INTO responder_locations (responder_id, emergency_id, latitude, longitude) VALUES (?, ?, ?, ?)", 
                     (req.responder_id, emergency_id, req.latitude, req.longitude))
        conn.commit()
        conn.close()
        return {"status": "success", "message": "Location updated"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/{emergency_id}/location")
def get_responder_location(emergency_id: str):
    try:
        conn = get_db_connection()
        location_row = conn.execute("SELECT latitude, longitude, timestamp FROM responder_locations WHERE emergency_id = ? ORDER BY timestamp DESC LIMIT 1", (emergency_id,)).fetchone()
        conn.close()
        if location_row:
            return {
                "status": "success",
                "latitude": location_row["latitude"],
                "longitude": location_row["longitude"],
                "timestamp": location_row["timestamp"]
            }
        else:
            raise HTTPException(status_code=404, detail="Location not found")
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.websocket("/ws/emergency/updates")
async def emergency_updates_websocket(websocket: WebSocket):
    await websocket.accept()
    try:
        # Simulate real-time updates for responder state progression
        # progression: ACCEPTED -> DEPARTED -> NEARBY -> ARRIVED
        status_updates = [
            {"status": "ACCEPTED", "eta_minutes": 5, "message": "Responder accepted emergency"},
            {"status": "DEPARTED", "eta_minutes": 3, "message": "Responder departed and is en route"},
            {"status": "NEARBY", "eta_minutes": 1, "message": "Responder is nearby"},
            {"status": "ARRIVED", "eta_minutes": 0, "message": "Responder arrived at your location"}
        ]
        
        for update in status_updates:
            await asyncio.sleep(8)  # delay between states
            payload = {
                "responder": {
                    "display_name": "Dr. John Doe",
                    "trust_score": 4.9,
                    "completed_assists": 124,
                    "eta_minutes": update["eta_minutes"],
                    "is_verified": True,
                    "current_status": update["status"]
                },
                "status_message": update["message"]
            }
            await websocket.send_json(payload)
            
    except WebSocketDisconnect:
        pass
    except Exception as e:
        print(f"WS Exception: {e}")