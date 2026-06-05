"""
main.py — FastAPI ML Prediction Server
═══════════════════════════════════════
Serves the trained addiction model via a REST API.

Endpoints:
  GET  /health       → service health check
  GET  /model-info   → model metadata and feature list
  POST /predict      → predict addiction level from usage features

INTEGRATION WITH ANDROID APP:
  AppModule.kt sets baseUrl to this server's URL.
  MlApiService.kt calls POST /predict with PredictRequest JSON.
  Response (addiction_level + confidence) is shown in DashboardFragment.

INTEGRATION WITH FIREBASE (optional):
  After prediction, the Android app calls:
    FirestoreService.savePrediction(date, level, confidence)
  This writes to: users/{uid}/predictions/{date}

Setup:
  pip install -r requirements.txt
  python ml_backend/train_model.py      # train first
  uvicorn ml_backend.main:app --reload  # dev server
  uvicorn ml_backend.main:app --host 0.0.0.0 --port 8000  # production

Deploy:
  Docker → Cloud Run / Railway / Render (see README for steps)
"""

import json
import os
from pathlib import Path
from typing import Optional

import joblib
import numpy as np
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field, validator

# ── App Setup ────────────────────────────────────────────────────────────────
app = FastAPI(
    title="SmartTracker ML API",
    description="Predicts smartphone addiction level (Low/Medium/High) from usage features.",
    version="1.0.0",
)

# Allow requests from Android app (and any origin for dev)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)

# ── Model Loading ─────────────────────────────────────────────────────────────
MODEL_DIR = Path(__file__).parent / "model"

_model         = None
_label_encoder = None
_scaler        = None
_metrics       = None


def load_model():
    """Load trained artifacts from disk. Called on startup."""
    global _model, _label_encoder, _scaler, _metrics

    model_path = MODEL_DIR / "addiction_model.joblib"
    if not model_path.exists():
        raise FileNotFoundError(
            f"Model not found at {model_path}. "
            "Run: python ml_backend/train_model.py"
        )

    _model         = joblib.load(MODEL_DIR / "addiction_model.joblib")
    _label_encoder = joblib.load(MODEL_DIR / "label_encoder.joblib")
    _scaler        = joblib.load(MODEL_DIR / "scaler.joblib")

    metrics_path = MODEL_DIR / "metrics.json"
    if metrics_path.exists():
        with open(metrics_path) as f:
            _metrics = json.load(f)

    print("✅ Model loaded successfully")
    print(f"   Classes: {_label_encoder.classes_}")


@app.on_event("startup")
def startup_event():
    load_model()


# ── Schemas ───────────────────────────────────────────────────────────────────

class PredictRequest(BaseModel):
    """
    Features sent from the Android app.
    Matches MlApiService.PredictRequest in the Android code.
    """
    daily_usage_hours: float = Field(
        ..., ge=0, le=24,
        description="Total screen time today in hours"
    )
    night_usage_hours: float = Field(
        ..., ge=0, le=12,
        description="Usage between 10PM and 6AM in hours"
    )
    app_switching_frequency: int = Field(
        ..., ge=0, le=1000,
        description="Number of times user switched apps"
    )
    social_media_percentage: float = Field(
        ..., ge=0, le=100,
        description="Percentage of total screen time spent on social media"
    )
    total_sessions: int = Field(
        ..., ge=0, le=1000,
        description="Total app launch count"
    )
    avg_session_duration_mins: float = Field(
        ..., ge=0, le=300,
        description="Average app session length in minutes"
    )

    @validator("night_usage_hours")
    def night_not_exceed_daily(cls, v, values):
        if "daily_usage_hours" in values and v > values["daily_usage_hours"]:
            raise ValueError("night_usage_hours cannot exceed daily_usage_hours")
        return v

    class Config:
        schema_extra = {
            "example": {
                "daily_usage_hours": 6.5,
                "night_usage_hours": 1.2,
                "app_switching_frequency": 85,
                "social_media_percentage": 60.0,
                "total_sessions": 72,
                "avg_session_duration_mins": 5.4
            }
        }


class PredictResponse(BaseModel):
    """
    Response consumed by MlApiService.PredictResponse in the Android app.
    Shown in DashboardFragment with color-coded badge.
    """
    addiction_level: str   # "Low" | "Medium" | "High"
    confidence: float      # 0.0 – 1.0  (probability of predicted class)
    all_probabilities: dict  # {"Low": 0.1, "Medium": 0.3, "High": 0.6}
    insight: str           # Human-readable suggestion


# ── Insights ──────────────────────────────────────────────────────────────────

INSIGHTS = {
    "Low": (
        "Great habits! Your screen time is healthy. "
        "Keep monitoring to maintain this balance."
    ),
    "Medium": (
        "Moderate usage detected. Consider setting app timers "
        "and taking regular breaks from your phone."
    ),
    "High": (
        "High addiction risk. Try enabling grayscale mode, "
        "turning off non-essential notifications, and keeping your "
        "phone outside the bedroom at night."
    ),
}


# ── Routes ────────────────────────────────────────────────────────────────────

@app.get("/health")
def health():
    """Used by Android app and deployment health checks."""
    return {
        "status": "healthy",
        "model_loaded": _model is not None
    }


@app.get("/model-info")
def model_info():
    """Returns model metadata and accuracy metrics."""
    if _model is None:
        raise HTTPException(503, "Model not loaded")

    return {
        "model_type": type(_model).__name__,
        "classes": _label_encoder.classes_.tolist() if _label_encoder else [],
        "features": [
            "daily_usage_hours",
            "night_usage_hours",
            "app_switching_frequency",
            "social_media_percentage",
            "total_sessions",
            "avg_session_duration_mins",
        ],
        "metrics": _metrics,
    }


@app.post("/predict", response_model=PredictResponse)
def predict(request: PredictRequest):
    """
    ┌──────────────────────────────────────────────────────────────┐
    │  PRIMARY ENDPOINT — called by Android MlApiService          │
    │                                                               │
    │  Input:  PredictRequest (6 usage features)                   │
    │  Output: PredictResponse (level + confidence + insight)      │
    └──────────────────────────────────────────────────────────────┘
    """
    if _model is None:
        raise HTTPException(503, "Model not loaded. Start the server properly.")

    # Build feature vector in training order
    features = np.array([[
        request.daily_usage_hours,
        request.night_usage_hours,
        request.app_switching_frequency,
        request.social_media_percentage,
        request.total_sessions,
        request.avg_session_duration_mins,
    ]])

    # Scale using training scaler
    features_scaled = _scaler.transform(features)

    # Predict class and probabilities
    pred_idx    = _model.predict(features_scaled)[0]
    proba       = _model.predict_proba(features_scaled)[0]
    level       = _label_encoder.inverse_transform([pred_idx])[0]
    confidence  = float(proba[pred_idx])

    all_proba = {
        cls: round(float(prob), 4)
        for cls, prob in zip(_label_encoder.classes_, proba)
    }

    return PredictResponse(
        addiction_level=level,
        confidence=round(confidence, 4),
        all_probabilities=all_proba,
        insight=INSIGHTS[level],
    )
