import os
from datetime import datetime, timezone
from typing import Any

from dotenv import load_dotenv
from pymongo import MongoClient

load_dotenv()

_client: MongoClient | None = None
_database: Any = None


def get_database() -> Any:
    global _client, _database
    if _database is None:
        uri = os.getenv("MONGODB_URI")
        if not uri:
            raise RuntimeError("MONGODB_URI is not configured")
        _client = MongoClient(uri, serverSelectionTimeoutMS=5000)
        _database = _client[os.getenv("DATABASE_NAME", "trip_expense_manager")]
    return _database


def utc_now() -> datetime:
    return datetime.now(timezone.utc)
