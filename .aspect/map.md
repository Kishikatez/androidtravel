# Map

_Symbol index with signatures and conventions. Use to find types, functions, and coding patterns._

## Symbol Index

_Functions, classes, and exports with call relationships._

### `backend/app/database.py`

| Symbol | Kind | Signature | Used In (files) |
|--------|------|-----------|----------------|
| `get_database` | function | `def get_database()` | `app/main.py` |
| `utc_now` | function | `def utc_now()` | `app/main.py` |

### `backend/app/main.py`

| Symbol | Kind | Signature | Used In (files) |
|--------|------|-----------|----------------|
| `database` | function | `def database()` | — |
| `trip_code` | function | `def trip_code()` | — |
| `user_document` | function | `def user_document(document)` | — |
| `trip_response` | function | `def trip_response(trip, expenses)` | — |
| `health` | function | `def health()` | — |
| `create_user` | function | `def create_user(payload)` | — |
| `create_trip` | function | `def create_trip(payload)` | — |
| `join_trip` | function | `def join_trip(payload)` | — |
| `get_trip` | function | `def get_trip(trip_id)` | — |
| `get_members` | function | `def get_members(trip_id)` | — |
| `get_expenses` | function | `def get_expenses(trip_id)` | — |
| `add_expense` | function | `def add_expense(trip_id, payload)` | — |

_+1 more symbols_

### `backend/app/models.py`

| Symbol | Kind | Signature | Used In (files) |
|--------|------|-----------|----------------|
| `UserCreate` | class | `class UserCreate(BaseModel)` | `app/main.py` |
| `UserResponse` | class | `class UserResponse(BaseModel)` | `app/main.py` |
| `TripCreate` | class | `class TripCreate(BaseModel)` | `app/main.py` |
| `TripJoin` | class | `class TripJoin(BaseModel)` | `app/main.py` |
| `ExpenseCreate` | class | `class ExpenseCreate(BaseModel)` | `app/main.py` |

---

## Conventions

_Naming patterns and styles. Follow these for consistency._

### File Naming

| Pattern | Example | Count |
|---------|---------|-------|
| snake_case | `__init__.py` | 2 |

**Use:** snake_case for new files.

### Function Naming

- `get_*` → `get_database` (4 occurrences)
- `create_*` → `create_user` (2 occurrences)
- `delete_*` → `delete_expense` (1 occurrences)


_Generated: 2026-09-23T05:19:09.478Z_
