# SmartTracker — Smartphone Addiction Tracker

A production-grade Android app that tracks smartphone usage, predicts addiction
level using a Machine Learning model, and optionally syncs data to Firebase.

---

## Project Structure

```
smartphone_addiction_tracker/
├── android_app/                  ← Android Studio project (Kotlin, MVVM)
│   └── app/src/main/java/com/smarttracker/
│       ├── data/
│       │   ├── local/            ← Room DB (offline-first)
│       │   │   ├── AppDatabase.kt
│       │   │   ├── dao/UsageLogDao.kt
│       │   │   ├── dao/WebVisitDao.kt
│       │   │   ├── entity/UsageLog.kt
│       │   │   └── entity/WebVisit.kt
│       │   ├── remote/
│       │   │   ├── FirestoreService.kt    ← Firebase integration
│       │   │   └── MlApiService.kt        ← FastAPI integration
│       │   └── repository/UsageRepository.kt
│       ├── di/AppModule.kt               ← Hilt DI (inject URLs here)
│       ├── service/
│       │   ├── UsageTrackerService.kt    ← Foreground service
│       │   └── FirebaseSyncWorker.kt     ← WorkManager sync
│       └── ui/
│           ├── dashboard/                ← Main screen + charts
│           ├── browser/                  ← In-app WebView tracker
│           └── settings/                 ← Auth + cloud toggle
├── ml_backend/
│   └── train_model.py                   ← Random Forest training script
├── fastapi_backend/
│   ├── main.py                          ← POST /predict endpoint
│   └── requirements.txt
├── Dockerfile                           ← Docker deploy
├── firestore.rules                      ← Firestore security rules
└── README.md
```

---

## ⚡ Quick Start

### Step 1 — Firebase Setup

1. Go to https://console.firebase.google.com
2. Create a new project named **SmartTracker**
3. Enable **Authentication** → Sign-in methods → Email/Password (and Google)
4. Create **Firestore Database** → Start in production mode
5. Go to Project Settings → Your apps → Add Android app
   - Package name: `com.smarttracker`
   - Download `google-services.json`
   - Place it at: `android_app/app/google-services.json`  ← **REQUIRED**
6. Deploy Firestore security rules:
   ```bash
   npm install -g firebase-tools
   firebase login
   firebase init firestore
   firebase deploy --only firestore:rules
   ```

### Step 2 — Train the ML Model

```bash
cd smartphone_addiction_tracker
pip install scikit-learn numpy pandas joblib
python ml_backend/train_model.py
# Output: ml_backend/model/addiction_model.joblib
```

### Step 3 — Run the FastAPI Server

**Option A — Local (dev)**
```bash
pip install -r fastapi_backend/requirements.txt
uvicorn fastapi_backend.main:app --reload --port 8000
# API docs: http://localhost:8000/docs
```

**Option B — Docker**
```bash
docker build -t smarttracker-api .
docker run -p 8000:8000 smarttracker-api
```

**Option C — Deploy to Cloud Run (recommended)**
```bash
gcloud run deploy smarttracker-api \
  --source . \
  --port 8000 \
  --allow-unauthenticated \
  --region us-central1
# Copy the HTTPS URL shown — you'll need it in Step 4
```

**Option D — Railway / Render (easiest)**
1. Push this repo to GitHub
2. Connect to Railway (railway.app) or Render (render.com)
3. Set start command: `uvicorn fastapi_backend.main:app --host 0.0.0.0 --port 8000`
4. Copy the public HTTPS URL

### Step 4 — Connect Android to FastAPI

Open `android_app/app/src/main/java/com/smarttracker/di/AppModule.kt`:

```kotlin
// Line ~55 — replace with your deployed FastAPI URL:
.baseUrl("https://YOUR_FASTAPI_URL/")
```

### Step 5 — Build & Run Android App

1. Open `android_app/` in Android Studio
2. Sync Gradle
3. Run on a real device (UsageStatsManager doesn't work on emulator)
4. Grant **Usage Access** permission when prompted

---

## 🔌 Firebase Integration Map

This table shows exactly where Firebase connects to the Android code:

| What | Firestore Path | Android File | Method |
|------|---------------|--------------|--------|
| User settings (cloud toggle) | `users/{uid}/settings/preferences` | `FirestoreService.kt` | `saveSettings()` |
| App usage logs | `users/{uid}/usage_logs/{date_pkg}` | `FirestoreService.kt` | `pushUsageLogs()` |
| ML predictions | `users/{uid}/predictions/{date}` | `FirestoreService.kt` | `savePrediction()` |
| Web visit logs | `users/{uid}/usage_logs/web_{date}_{hash}` | `FirestoreService.kt` | `pushUsageLogs()` |

**Sync Flow:**
```
UsageTrackerService (every 60s)
    → UsageRepository.refreshUsageData()
        → Room DB (usage_logs, web_visits)   ← always stored locally

WorkManager FirebaseSyncWorker (every 15 min, only when online)
    → UsageRepository.syncToFirestore()
        → FirestoreService.pushUsageLogs()
            → Firestore: users/{uid}/usage_logs/
        → Room: markSynced(ids)
```

**Cloud Sync Toggle** (in SettingsFragment):
- ON  → WorkManager schedules `FirebaseSyncWorker` every 15 min
- OFF → `WorkManager.cancelUniqueWork("firebase_sync")`

---

## 🤖 ML Integration Map

```
DashboardFragment → btnGetPrediction clicked
    → DashboardViewModel.fetchPrediction()
        → UsageRepository.getPrediction()
            → Room: query today's usage logs
            → Build PredictRequest (6 features)
            → MlApiService.predict(request)       ← Retrofit → FastAPI
                → POST https://YOUR_URL/predict
                    → scaler.transform(features)
                    → model.predict()
                    → return { addiction_level, confidence }
            → FirestoreService.savePrediction()   ← save to Firestore
        → DashboardViewModel: update UI state
    → DashboardFragment: show color-coded badge
        Green  = Low
        Yellow = Medium
        Red    = High
```

---

## 📡 API Reference

### POST /predict

Request:
```json
{
  "daily_usage_hours": 6.5,
  "night_usage_hours": 1.2,
  "app_switching_frequency": 85,
  "social_media_percentage": 60.0,
  "total_sessions": 72,
  "avg_session_duration_mins": 5.4
}
```

Response:
```json
{
  "addiction_level": "High",
  "confidence": 0.87,
  "all_probabilities": { "Low": 0.04, "Medium": 0.09, "High": 0.87 },
  "insight": "High addiction risk. Try enabling grayscale mode..."
}
```

### GET /health
```json
{ "status": "healthy", "model_loaded": true }
```

### GET /model-info
Returns model type, classes, feature list, and accuracy metrics.

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Android App                                                 │
│                                                              │
│  UsageStatsManager ──→ UsageTrackerService (foreground)     │
│  WebView URLs      ──→ InAppBrowserFragment                  │
│        │                       │                             │
│        └─────────┬─────────────┘                            │
│                  ▼                                           │
│           UsageRepository                                    │
│                  │                                           │
│        ┌─────────┴─────────┐                                │
│        ▼                   ▼                                 │
│    Room DB             WorkManager                           │
│  (offline cache)    FirebaseSyncWorker                       │
│                            │                                 │
└────────────────────────────┼─────────────────────────────────┘
                             │ (when online + toggle ON)
                             ▼
                    ┌─────────────────┐
                    │    Firebase     │
                    │  Firestore DB   │
                    │  Auth (Email/   │
                    │   Google)       │
                    └─────────────────┘

               Android ──→ FastAPI ──→ ML Model
               (features)  /predict   (Random Forest)
                                          │
                                          ▼
                                  addiction_level
                                  Low/Medium/High
                                          │
                    Android ──→ FirestoreService.savePrediction()
                                          │
                                          ▼
                               Firestore predictions/{date}
```

---

## 🔒 Permissions Explained

| Permission | Why |
|-----------|-----|
| `PACKAGE_USAGE_STATS` | Read app usage via UsageStatsManager (user must grant manually in Settings) |
| `INTERNET` | Firebase sync + FastAPI ML calls |
| `POST_NOTIFICATIONS` | Usage alert notifications |
| `FOREGROUND_SERVICE` | Keep UsageTrackerService alive |

---

## 🔧 Tech Stack

| Layer | Technology |
|-------|-----------|
| Android | Kotlin, MVVM, Jetpack (ViewModel, LiveData, Navigation, WorkManager) |
| Local DB | Room (SQLite) |
| Cloud DB | Firebase Firestore |
| Auth | Firebase Authentication |
| Charts | MPAndroidChart |
| DI | Hilt |
| Networking | Retrofit + OkHttp |
| ML Model | Python, scikit-learn (Random Forest) |
| ML API | FastAPI + Uvicorn |
| Deploy | Docker, Cloud Run / Railway |

---

## 🗂 Firestore Data Structure

```
users/
└── {uid}/
    ├── settings/
    │   └── preferences       { cloudSyncEnabled, alertThresholdHours }
    ├── usage_logs/
    │   └── {date_pkgName}    { packageName, appName, usageDurationMs,
    │                           launchCount, lastForegroundMs, date }
    └── predictions/
        └── {date}            { addictionLevel, confidence, timestamp }
```
## License

© 2026 Devi Bangaram Satti. All rights reserved.

This project is shared for viewing purposes only. No part of this code may be copied, modified, or redistributed without permission.

---

## ⚠️ Common Issues

**"Model not found" on server start**
→ Run `python ml_backend/train_model.py` first.

**"No usage data" in dashboard**
→ Running on emulator? Use a real device.
→ Grant Usage Access in Settings → Apps → Special App Access.

**Firebase sync not working**
→ Confirm `google-services.json` is in `android_app/app/`
→ Check user is signed in (Settings screen)
→ Check cloud sync toggle is ON

**Android build fails**
→ Add `google-services.json` to `android_app/app/`
→ Add `jcenter()` to `settings.gradle` for MPAndroidChart

---

*Built with Kotlin, Python, Firebase, and FastAPI.*
