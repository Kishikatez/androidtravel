from datetime import datetime
from pydantic import BaseModel, Field


class UserCreate(BaseModel):
    name: str = Field(min_length=1, max_length=80)
    phone: str = Field(min_length=3, max_length=30)


class UserResponse(BaseModel):
    userId: str
    name: str
    phone: str


class TripCreate(BaseModel):
    tripName: str = Field(min_length=1, max_length=100)
    budgetPerPerson: float = Field(gt=0)
    peopleCount: int = Field(default=1, ge=1, le=1000)
    userId: str = Field(min_length=1)

class TripSettingsUpdate(BaseModel):
    peopleCount: int = Field(ge=1, le=1000)

class TripJoin(BaseModel):
    tripId: str = Field(min_length=1, max_length=20)
    userId: str = Field(min_length=1)


class ExpenseCreate(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    amount: float = Field(gt=0)
    description: str = Field(default="", max_length=500)
    paidByUserId: str = Field(min_length=1)
    paidByName: str = Field(min_length=1, max_length=80)
    timestamp: datetime | None = None
    clientExpenseId: str | None = Field(default=None, max_length=100)
