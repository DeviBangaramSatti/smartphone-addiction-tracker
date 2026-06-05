"""
test_api.py — FastAPI endpoint tests
Run: python fastapi_backend/test_api.py

Tests:
  1. GET  /health      → status check
  2. GET  /model-info  → metadata
  3. POST /predict     → Low / Medium / High cases
  4. POST /predict     → validation errors
"""

import json
import sys
import urllib.request
import urllib.error

BASE_URL = "http://localhost:8000"

PASS = "✅"
FAIL = "❌"

def get(path):
    resp = urllib.request.urlopen(f"{BASE_URL}{path}")
    return json.loads(resp.read())

def post(path, data: dict):
    body = json.dumps(data).encode()
    req  = urllib.request.Request(
        f"{BASE_URL}{path}",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST"
    )
    resp = urllib.request.urlopen(req)
    return json.loads(resp.read())

def post_expect_error(path, data: dict) -> int:
    body = json.dumps(data).encode()
    req  = urllib.request.Request(
        f"{BASE_URL}{path}",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST"
    )
    try:
        urllib.request.urlopen(req)
        return 200
    except urllib.error.HTTPError as e:
        return e.code

failures = 0

def check(label, condition, got=None):
    global failures
    status = PASS if condition else FAIL
    if not condition:
        failures += 1
        print(f"{status} {label}  →  got: {got}")
    else:
        print(f"{status} {label}")

print("=" * 55)
print("  SmartTracker FastAPI — Endpoint Tests")
print("=" * 55)

# ── 1. Health ─────────────────────────────────────────────────
print("\n[1] GET /health")
r = get("/health")
check("status == healthy",     r.get("status") == "healthy",     r.get("status"))
check("model_loaded == True",  r.get("model_loaded") == True,    r.get("model_loaded"))

# ── 2. Model info ──────────────────────────────────────────────
print("\n[2] GET /model-info")
r = get("/model-info")
check("has model_type",   "model_type"  in r)
check("has classes",      "classes"     in r)
check("3 classes",        len(r.get("classes", [])) == 3)
check("has features",     "features"    in r)

# ── 3. Predict — Low user ─────────────────────────────────────
print("\n[3] POST /predict — Low-risk user")
r = post("/predict", {
    "daily_usage_hours": 1.2,
    "night_usage_hours": 0.1,
    "app_switching_frequency": 12,
    "social_media_percentage": 10.0,
    "total_sessions": 8,
    "avg_session_duration_mins": 2.5
})
check("addiction_level == Low",  r.get("addiction_level") == "Low",  r.get("addiction_level"))
check("confidence > 0.5",        r.get("confidence", 0) > 0.5,       r.get("confidence"))
check("has insight",             len(r.get("insight", "")) > 0)

# ── 4. Predict — Medium user ──────────────────────────────────
print("\n[4] POST /predict — Medium-risk user")
r = post("/predict", {
    "daily_usage_hours": 3.5,
    "night_usage_hours": 0.7,
    "app_switching_frequency": 50,
    "social_media_percentage": 38.0,
    "total_sessions": 38,
    "avg_session_duration_mins": 7.0
})
check("addiction_level == Medium", r.get("addiction_level") == "Medium", r.get("addiction_level"))
check("has all_probabilities",     "all_probabilities" in r)
check("probabilities sum ≈ 1",
      abs(sum(r["all_probabilities"].values()) - 1.0) < 0.01,
      sum(r.get("all_probabilities", {}).values()))

# ── 5. Predict — High user ────────────────────────────────────
print("\n[5] POST /predict — High-risk user")
r = post("/predict", {
    "daily_usage_hours": 8.5,
    "night_usage_hours": 2.5,
    "app_switching_frequency": 130,
    "social_media_percentage": 72.0,
    "total_sessions": 95,
    "avg_session_duration_mins": 16.0
})
check("addiction_level == High", r.get("addiction_level") == "High", r.get("addiction_level"))
check("confidence >= 0.8",       r.get("confidence", 0) >= 0.8,     r.get("confidence"))

# ── 6. Validation — bad values ────────────────────────────────
print("\n[6] POST /predict — validation errors")
code = post_expect_error("/predict", {
    "daily_usage_hours": 99,   # > 24 — invalid
    "night_usage_hours": 0,
    "app_switching_frequency": 10,
    "social_media_percentage": 50,
    "total_sessions": 20,
    "avg_session_duration_mins": 5
})
check("daily_usage_hours > 24 → 422", code == 422, code)

code = post_expect_error("/predict", {
    "daily_usage_hours": 5,
    "night_usage_hours": 8,    # > daily_usage_hours — invalid
    "app_switching_frequency": 10,
    "social_media_percentage": 50,
    "total_sessions": 20,
    "avg_session_duration_mins": 5
})
check("night > daily → 422", code == 422, code)

# ── Summary ───────────────────────────────────────────────────
print("\n" + "=" * 55)
if failures == 0:
    print(f"{PASS} All tests passed!")
else:
    print(f"{FAIL} {failures} test(s) failed.")
    sys.exit(1)
