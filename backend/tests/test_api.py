from datetime import datetime
from types import SimpleNamespace
from bson import ObjectId
from fastapi.testclient import TestClient

from backend.app import main


class FakeCollection:
    def __init__(self): self.items = []
    def find_one(self, query):
        for item in self.items:
            if all(item.get(key) == value for key, value in query.items() if not isinstance(value, dict)):
                return item
        return None
    def insert_one(self, item):
        item = dict(item); item["_id"] = ObjectId(); self.items.append(item); return SimpleNamespace(inserted_id=item["_id"])
    def update_one(self, query, update):
        item = self.find_one(query)
        if item and "$push" in update:
            for key, value in update["$push"].items(): item.setdefault(key, []).append(value)
    def find(self, query):
        result = [item for item in self.items if all(item.get(key) == value for key, value in query.items())]
        return FakeCursor(result)
    def delete_one(self, query):
        item = self.find_one(query)
        if item: self.items.remove(item)
        return SimpleNamespace(deleted_count=1 if item else 0)


class FakeCursor(list):
    def sort(self, *_args): return self


class FakeDatabase:
    def __init__(self): self.users = FakeCollection(); self.trips = FakeCollection(); self.expenses = FakeCollection()


def setup_client():
    fake = FakeDatabase()
    main.get_database = lambda: fake
    return TestClient(main.app)


def test_user_trip_join_expense_and_budget():
    client = setup_client()
    user = client.post("/users", json={"name": "Kishore", "phone": "9876543210"}).json()
    created = client.post("/trips", json={"tripName": "Chennai Trip", "budgetPerPerson": 2500, "userId": user["userId"]})
    assert created.status_code == 200
    trip = created.json()
    assert trip["totalBudget"] == 2500
    joined = client.post("/users", json={"name": "Arun", "phone": "9876543211"}).json()
    joined_trip = client.post("/trips/join", json={"tripId": trip["tripId"], "userId": joined["userId"]}).json()
    assert joined_trip["memberCount"] == 2
    expense = client.post(f"/trips/{trip['tripId']}/expenses", json={"name": "Food", "amount": 800, "paidByUserId": user["userId"], "paidByName": "Kishore"})
    assert expense.status_code == 200
    current = client.get(f"/trips/{trip['tripId']}").json()
    assert current["totalSpent"] == 800
    assert current["remaining"] == 4200
    assert len(client.get(f"/trips/{trip['tripId']}/expenses").json()) == 1


def test_invalid_trip_and_amount():
    client = setup_client()
    assert client.post("/trips/join", json={"tripId": "MISSING", "userId": str(ObjectId())}).status_code == 404
    user = client.post("/users", json={"name": "A", "phone": "123"}).json()
    trip = client.post("/trips", json={"tripName": "Test", "budgetPerPerson": 100, "userId": user["userId"]}).json()
    assert client.post(f"/trips/{trip['tripId']}/expenses", json={"name": "Bad", "amount": -1, "paidByUserId": user["userId"], "paidByName": "A"}).status_code == 422
