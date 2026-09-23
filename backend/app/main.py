import os
import secrets
import string
from datetime import datetime, timezone
from typing import Any

from bson import ObjectId
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from .database import get_database, utc_now
from .models import ExpenseCreate, TripCreate, TripJoin, UserCreate, UserResponse

app = FastAPI(title="Trip Expense Manager API")
origins = [item.strip() for item in os.getenv("ALLOWED_ORIGINS", "*").split(",")]
app.add_middleware(CORSMiddleware, allow_origins=origins, allow_credentials=True, allow_methods=["*"], allow_headers=["*"])


def database():
    try:
        return get_database()
    except Exception as error:
        raise HTTPException(status_code=503, detail="Database is unavailable") from error


def trip_code() -> str:
    alphabet = string.ascii_uppercase + string.digits
    return "".join(secrets.choice(alphabet) for _ in range(6))


def user_document(document: dict[str, Any]) -> UserResponse:
    return UserResponse(userId=str(document["_id"]), name=document["name"], phone=document["phone"])


def trip_response(trip: dict[str, Any], expenses: list[dict[str, Any]]) -> dict[str, Any]:
    spent = sum(float(item["amount"]) for item in expenses)
    member_count = len(trip.get("members", []))
    total_budget = float(trip["budgetPerPerson"]) * member_count
    return {
        "tripId": trip["tripId"], "tripName": trip["tripName"], "createdBy": str(trip["createdBy"]),
        "budgetPerPerson": trip["budgetPerPerson"], "memberCount": member_count,
        "totalBudget": total_budget, "totalSpent": spent, "remaining": total_budget - spent,
    }


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/users", response_model=UserResponse)
def create_user(payload: UserCreate):
    collection = database().users
    existing = collection.find_one({"phone": payload.phone})
    if existing:
        return user_document(existing)
    result = collection.insert_one({"name": payload.name.strip(), "phone": payload.phone.strip()})
    return user_document(collection.find_one({"_id": result.inserted_id}))


@app.post("/trips")
def create_trip(payload: TripCreate):
    db = database()
    if not db.users.find_one({"_id": ObjectId(payload.userId)}):
        raise HTTPException(status_code=404, detail="User not found")
    code = trip_code()
    while db.trips.find_one({"tripId": code}):
        code = trip_code()
    trip = {"tripId": code, "tripName": payload.tripName.strip(), "createdBy": ObjectId(payload.userId),
            "budgetPerPerson": payload.budgetPerPerson, "members": [ObjectId(payload.userId)], "createdAt": utc_now()}
    db.trips.insert_one(trip)
    return trip_response(trip, [])


@app.post("/trips/join")
def join_trip(payload: TripJoin):
    db = database()
    trip = db.trips.find_one({"tripId": payload.tripId.strip().upper()})
    if not trip:
        raise HTTPException(status_code=404, detail="Trip not found")
    user_id = ObjectId(payload.userId)
    if user_id not in trip.get("members", []):
        db.trips.update_one({"_id": trip["_id"]}, {"$push": {"members": user_id}})
        trip["members"].append(user_id)
    return trip_response(trip, list(db.expenses.find({"tripId": trip["tripId"]})))


@app.get("/trips/{trip_id}")
def get_trip(trip_id: str):
    db = database()
    trip = db.trips.find_one({"tripId": trip_id.upper()})
    if not trip:
        raise HTTPException(status_code=404, detail="Trip not found")
    return trip_response(trip, list(db.expenses.find({"tripId": trip["tripId"]})))


@app.get("/trips/{trip_id}/members")
def get_members(trip_id: str):
    db = database()
    trip = db.trips.find_one({"tripId": trip_id.upper()})
    if not trip:
        raise HTTPException(status_code=404, detail="Trip not found")
    members = list(db.users.find({"_id": {"$in": trip.get("members", [])}}))
    return [{"userId": str(member["_id"]), "name": member["name"], "isAdmin": member["_id"] == trip["createdBy"]} for member in members]


@app.get("/trips/{trip_id}/expenses")
def get_expenses(trip_id: str):
    db = database()
    if not db.trips.find_one({"tripId": trip_id.upper()}):
        raise HTTPException(status_code=404, detail="Trip not found")
    return [{"expenseId": str(item["_id"]), "name": item["name"], "amount": item["amount"],
             "description": item.get("description", ""), "paidByName": item["paidByName"],
             "timestamp": item["timestamp"]} for item in db.expenses.find({"tripId": trip_id.upper()}).sort("timestamp", -1)]


@app.post("/trips/{trip_id}/expenses")
def add_expense(trip_id: str, payload: ExpenseCreate):
    db = database()
    if not db.trips.find_one({"tripId": trip_id.upper()}):
        raise HTTPException(status_code=404, detail="Trip not found")
    document = {"tripId": trip_id.upper(), "name": payload.name.strip(), "amount": payload.amount,
                "description": payload.description.strip(), "paidBy": ObjectId(payload.paidByUserId),
                "paidByName": payload.paidByName.strip(), "timestamp": payload.timestamp or utc_now()}
    result = db.expenses.insert_one(document)
    document["expenseId"] = str(result.inserted_id)
    document.pop("_id", None)
    return document


@app.delete("/trips/{trip_id}/expenses/{expense_id}")
def delete_expense(trip_id: str, expense_id: str):
    result = database().expenses.delete_one({"_id": ObjectId(expense_id), "tripId": trip_id.upper()})
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Expense not found")
    return {"deleted": True}
