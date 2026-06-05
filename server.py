"""
server.py — SmartTracker Web Dashboard Backend
Run from INSIDE the web_dashboard/ folder:
  python server.py

Then browser opens automatically at http://127.0.0.1:8080
"""

import asyncio
import json
import os
import random
import sqlite3
import threading
import time
import webbrowser
from datetime import datetime, timedelta
from pathlib import Path
from typing import Optional

import joblib
import numpy as np
import uvicorn
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse
from fastapi.staticfiles import StaticFiles

# ── Paths ─────────────────────────────────────────────────────────────────────
BASE_DIR   = Path(__file__).parent
MODEL_DIR  = BASE_DIR.parent / "fastapi_backend" / "model"
DB_PATH    = BASE_DIR / "usage_data.db"
HTML_PATH  = BASE_DIR / "dashboard.html"

# ── Load ML Model ─────────────────────────────────────────────────────────────
print("Loading ML model...")
model   = joblib.load(MODEL_DIR / "addiction_model.joblib")
encoder = joblib.load(MODEL_DIR / "label_encoder.joblib")
scaler  = joblib.load(MODEL_DIR / "scaler.joblib")
print(f"✅ Model loaded: {type(model).__name__}")

SOCIAL_APPS = {"Instagram","Facebook","TikTok","Snapchat","Twitter/X","YouTube","Reddit","WhatsApp"}
INSIGHT_MAP = {
    "Low":    "Great digital habits! Your usage patterns are healthy.",
    "Medium": "Moderate risk — consider setting daily screen time limits.",
    "High":   "High addiction risk. Try grayscale mode and phone-free hours."
}

# ── App ───────────────────────────────────────────────────────────────────────
app = FastAPI(title="SmartTracker Dashboard API")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"], allow_methods=["*"], allow_headers=["*"]
)

# ════════════════════════════════════════════════════════════════════════════
#  DATABASE
# ════════════════════════════════════════════════════════════════════════════
def get_db():
    conn = sqlite3.connect(str(DB_PATH))
    conn.row_factory = sqlite3.Row
    return conn

APP_PROFILES = [
    ("Instagram","com.instagram.android",55,True,20),
    ("YouTube","com.youtube.android",80,True,30),
    ("WhatsApp","com.whatsapp",40,True,15),
    ("TikTok","com.zhiliaoapp.musically",65,True,25),
    ("Twitter/X","com.twitter.android",30,True,15),
    ("Chrome","com.android.chrome",35,False,12),
    ("Gmail","com.google.android.gm",18,False,8),
    ("Maps","com.google.android.maps",12,False,10),
    ("Spotify","com.spotify.music",45,False,20),
    ("Netflix","com.netflix.mediaclient",60,False,40),
    ("Snapchat","com.snapchat.android",28,True,15),
    ("Reddit","com.reddit.frontpage",22,True,12),
    ("Camera","com.android.camera",8,False,5),
    ("Calculator","com.android.calculator2",3,False,2),
    ("Clock","com.android.deskclock",5,False,3),
]
WEB_SITES = [
    ("google.com","Google Search"),("youtube.com","YouTube"),
    ("reddit.com","Reddit"),("twitter.com","Twitter / X"),
    ("instagram.com","Instagram"),("github.com","GitHub"),
    ("stackoverflow.com","Stack Overflow"),("amazon.com","Amazon"),
]

def init_db():
    conn = get_db()
    c = conn.cursor()
    c.executescript("""
        CREATE TABLE IF NOT EXISTS usage_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            date TEXT NOT NULL, package_name TEXT NOT NULL,
            app_name TEXT NOT NULL, usage_ms INTEGER NOT NULL,
            launch_count INTEGER NOT NULL, is_social INTEGER DEFAULT 0,
            UNIQUE(date, package_name)
        );
        CREATE TABLE IF NOT EXISTS daily_summary (
            date TEXT PRIMARY KEY, total_usage_ms INTEGER,
            night_usage_ms INTEGER, app_switch_count INTEGER,
            social_usage_ms INTEGER, total_sessions INTEGER,
            avg_session_mins REAL, addiction_level TEXT,
            confidence REAL, updated_at TEXT
        );
        CREATE TABLE IF NOT EXISTS web_visits (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            date TEXT NOT NULL, url TEXT NOT NULL,
            title TEXT, time_ms INTEGER, visited_at TEXT
        );
    """)
    row = c.execute("SELECT COUNT(*) FROM usage_logs").fetchone()[0]
    if row == 0:
        seed_database(c)
    conn.commit()
    conn.close()
    print(f"✅ Database ready: {DB_PATH}")

def seed_database(c):
    today = datetime.now().date()
    for day_offset in range(29, -1, -1):
        date = (today - timedelta(days=day_offset)).isoformat()
        trend_factor = 1.0 + (29 - day_offset) / 29 * 0.8
        weekend = (today - timedelta(days=day_offset)).weekday() >= 5
        total_ms = social_ms = sessions = 0
        for (name, pkg, avg_mins, is_social, vol) in APP_PROFILES:
            base = avg_mins * trend_factor * (1.3 if weekend else 1.0)
            actual = max(0, base + random.gauss(0, vol * 0.3))
            usage_ms = int(actual * 60_000)
            launches = max(1, int(actual / 4) + random.randint(-3, 5))
            c.execute("""INSERT OR IGNORE INTO usage_logs
                (date,package_name,app_name,usage_ms,launch_count,is_social)
                VALUES (?,?,?,?,?,?)""",
                (date, pkg, name, usage_ms, launches, int(is_social)))
            total_ms += usage_ms
            if is_social: social_ms += usage_ms
            sessions += launches
        night_ms = int(total_ms * min(random.uniform(0.15,0.35)*trend_factor, 0.5))
        switches = int(sessions * random.uniform(2.5, 4.0))
        avg_session = (total_ms/1000/60/sessions) if sessions > 0 else 0
        features = np.array([[total_ms/3_600_000, night_ms/3_600_000, switches,
                               (social_ms/total_ms*100) if total_ms>0 else 0,
                               sessions, avg_session]])
        scaled = scaler.transform(features)
        pred_idx = model.predict(scaled)[0]
        level = encoder.inverse_transform([pred_idx])[0]
        conf = float(model.predict_proba(scaled)[0][pred_idx])
        c.execute("""INSERT OR REPLACE INTO daily_summary
            (date,total_usage_ms,night_usage_ms,app_switch_count,
             social_usage_ms,total_sessions,avg_session_mins,
             addiction_level,confidence,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?)""",
            (date,total_ms,night_ms,switches,social_ms,sessions,
             round(avg_session,2),level,round(conf,4),datetime.now().isoformat()))
        for _ in range(random.randint(3,8)):
            site,title = random.choice(WEB_SITES)
            c.execute("""INSERT INTO web_visits (date,url,title,time_ms,visited_at)
                VALUES (?,?,?,?,?)""",
                (date,f"https://{site}",title,random.randint(30_000,600_000),
                 datetime.now().isoformat()))
    print("✅ Database seeded with 30 days of data")

# ════════════════════════════════════════════════════════════════════════════
#  LIVE PREDICTION
# ════════════════════════════════════════════════════════════════════════════
def run_live_prediction(date=None):
    if date is None:
        date = datetime.now().date().isoformat()
    conn = get_db()
    c = conn.cursor()
    row = c.execute("""SELECT total_usage_ms,night_usage_ms,app_switch_count,
        social_usage_ms,total_sessions,avg_session_mins
        FROM daily_summary WHERE date=?""",(date,)).fetchone()
    if not row:
        conn.close()
        return {"error": "No data for this date"}
    total_ms  = row["total_usage_ms"] or 0
    night_ms  = row["night_usage_ms"] or 0
    switches  = row["app_switch_count"] or 0
    social_ms = row["social_usage_ms"] or 0
    sessions  = row["total_sessions"] or 1
    avg_sess  = row["avg_session_mins"] or 0
    social_pct = (social_ms/total_ms*100) if total_ms>0 else 0
    features = np.array([[total_ms/3_600_000, night_ms/3_600_000, switches,
                           social_pct, sessions, avg_sess]])
    scaled = scaler.transform(features)
    pred_idx = model.predict(scaled)[0]
    proba = model.predict_proba(scaled)[0]
    level = encoder.inverse_transform([pred_idx])[0]
    conf = float(proba[pred_idx])
    all_proba = {cls: round(float(p),4) for cls,p in zip(encoder.classes_, proba)}
    c.execute("""UPDATE daily_summary SET addiction_level=?,confidence=?,updated_at=?
        WHERE date=?""",(level,round(conf,4),datetime.now().isoformat(),date))
    conn.commit()
    conn.close()
    return {
        "date": date, "addiction_level": level,
        "confidence": round(conf,4), "all_probabilities": all_proba,
        "insight": INSIGHT_MAP[level],
        "features": {
            "daily_usage_hours": round(total_ms/3_600_000,2),
            "night_usage_hours": round(night_ms/3_600_000,2),
            "app_switching_frequency": switches,
            "social_media_percentage": round(social_pct,1),
            "total_sessions": sessions,
            "avg_session_duration_mins": round(avg_sess,1)
        },
        "updated_at": datetime.now().isoformat()
    }

# ════════════════════════════════════════════════════════════════════════════
#  ROUTES
# ════════════════════════════════════════════════════════════════════════════
@app.get("/")
def index():
    return FileResponse(str(HTML_PATH))

@app.get("/api/health")
def health():
    return {"status":"ok","model":type(model).__name__,"db":str(DB_PATH)}

@app.get("/api/predict/live")
def predict_live(date: Optional[str] = None):
    return run_live_prediction(date)

@app.get("/api/today")
def today_summary():
    today = datetime.now().date().isoformat()
    conn = get_db()
    row = conn.execute("SELECT * FROM daily_summary WHERE date=?",(today,)).fetchone()
    conn.close()
    if not row: return JSONResponse({"error":"No data"},status_code=404)
    return dict(row)

@app.get("/api/apps/today")
def apps_today():
    today = datetime.now().date().isoformat()
    conn = get_db()
    rows = conn.execute("""SELECT app_name,usage_ms,launch_count,is_social
        FROM usage_logs WHERE date=? ORDER BY usage_ms DESC LIMIT 12""",(today,)).fetchall()
    conn.close()
    return [dict(r) for r in rows]

@app.get("/api/trend/30days")
def trend_30():
    conn = get_db()
    rows = conn.execute("""SELECT date,total_usage_ms,social_usage_ms,night_usage_ms,
        addiction_level,confidence,total_sessions
        FROM daily_summary ORDER BY date DESC LIMIT 30""").fetchall()
    conn.close()
    return [dict(r) for r in reversed(rows)]

@app.get("/api/model/info")
def model_info():
    metrics_path = MODEL_DIR / "metrics.json"
    metrics = json.loads(metrics_path.read_text()) if metrics_path.exists() else {}
    return {
        "model_type": type(model).__name__,
        "classes": encoder.classes_.tolist(),
        "accuracy": metrics.get("random_forest_accuracy"),
        "cv_mean": metrics.get("cv_mean"),
        "feature_importances": metrics.get("feature_importances",{})
    }

@app.post("/api/simulate/update")
def simulate_update():
    today = datetime.now().date().isoformat()
    conn = get_db()
    c = conn.cursor()
    apps = c.execute("SELECT id,usage_ms FROM usage_logs WHERE date=? ORDER BY RANDOM() LIMIT 4",(today,)).fetchall()
    for app_row in apps:
        extra_ms = random.randint(300_000, 1_200_000)
        c.execute("UPDATE usage_logs SET usage_ms=usage_ms+? WHERE id=?",(extra_ms,app_row["id"]))
    totals = c.execute("""SELECT SUM(usage_ms) AS total,
        SUM(CASE WHEN is_social=1 THEN usage_ms ELSE 0 END) AS social,
        SUM(launch_count) AS sessions FROM usage_logs WHERE date=?""",(today,)).fetchone()
    total_ms = totals["total"] or 0
    social_ms = totals["social"] or 0
    sessions = totals["sessions"] or 1
    avg_sess = (total_ms/1000/60/sessions) if sessions>0 else 0
    night_ms = int(total_ms*random.uniform(0.15,0.30))
    switches = int(sessions*random.uniform(2.8,3.5))
    c.execute("""UPDATE daily_summary SET total_usage_ms=?,night_usage_ms=?,
        app_switch_count=?,social_usage_ms=?,total_sessions=?,avg_session_mins=?
        WHERE date=?""",(total_ms,night_ms,switches,social_ms,sessions,round(avg_sess,2),today))
    conn.commit()
    conn.close()
    return run_live_prediction(today)

@app.websocket("/ws/live")
async def websocket_live(ws: WebSocket):
    await ws.accept()
    print("WebSocket client connected")
    try:
        while True:
            data = run_live_prediction()
            await ws.send_json(data)
            await asyncio.sleep(30)
    except WebSocketDisconnect:
        print("WebSocket client disconnected")

# ════════════════════════════════════════════════════════════════════════════
#  STARTUP + AUTO-OPEN BROWSER
# ════════════════════════════════════════════════════════════════════════════
def open_browser():
    """Wait 2 seconds then open browser — gives server time to start."""
    time.sleep(2)
    url = "http://127.0.0.1:8080"
    print(f"\n🌐 Opening browser at {url} ...")
    webbrowser.open(url)

if __name__ == "__main__":
    init_db()
    print("\n" + "="*50)
    print("  SmartTracker Dashboard")
    print("  http://127.0.0.1:8080")
    print("="*50)
    print("  If browser doesn't open, manually go to:")
    print("  http://127.0.0.1:8080")
    print("="*50 + "\n")

    # Open browser in background thread
    threading.Thread(target=open_browser, daemon=True).start()

    uvicorn.run(app, host="127.0.0.1", port=8080, log_level="warning")
