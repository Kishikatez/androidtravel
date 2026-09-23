# FastAPI backend

Set `MONGODB_URI` and optionally `DATABASE_NAME` in `.env`. Start with:

```powershell
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

The API exposes `/users`, `/trips`, `/trips/join`, trip details, members, expenses, and `/health`. Request validation returns normal FastAPI 4xx responses for invalid input. MongoDB credentials stay on the backend.
