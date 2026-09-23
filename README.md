# Trip Expense Manager

A small native Android app for a group of travelers to share one trip budget and expenses. The Android client is Kotlin + Jetpack Compose and talks only to the FastAPI backend. MongoDB Atlas stores users, trips, and expenses.

## Features

- Passwordless local user setup with name and phone
- Create or join trips with a six-character code
- Shared expenses with payer and timestamp
- Automatic budget, spent, remaining, and member totals
- Refresh-on-dashboard-entry and manual refresh
- Material 3 mobile UI

## Project layout

- `app/`: Android Studio project
- `backend/`: FastAPI service and tests

## MongoDB and backend

1. Create a MongoDB Atlas free cluster and allow the development machine IP.
2. Copy `backend/.env.example` to `backend/.env` and fill in `MONGODB_URI`.
3. Create a virtual environment and install dependencies:

```powershell
cd backend
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

The API is available at `http://localhost:8000`. Run tests with `pytest`.

## Android Studio

Open this folder in Android Studio and let it sync Gradle. Run the `app` configuration on an emulator. The emulator reaches the local backend through `http://10.0.2.2:8000/`, configured in `app/src/main/java/com/example/tripexpensemanager/MainActivity.kt`.

For a physical phone on the same Wi-Fi network, change `API_BASE_URL` to the computer's LAN address, for example `http://192.168.1.100:8000/`, and run Uvicorn with `--host 0.0.0.0`.

## Typical flow

1. Enter a name and phone number once.
2. Create a trip with a name and per-person budget, or enter a friend's trip code.
3. Share the generated trip code.
4. Add expenses with the `+` button. Other phones see them after refresh or reopening the dashboard.

This is intentionally a small personal project. It does not include passwords, OTP, email login, payments, chat, notifications, or WebSockets.
