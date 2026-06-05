#!/bin/bash
# SmartTracker Web Dashboard — Start Script
echo "╔══════════════════════════════════════════╗"
echo "║   SmartTracker Dashboard Server          ║"
echo "╚══════════════════════════════════════════╝"

# Check model exists
if [ ! -f "../fastapi_backend/model/addiction_model.joblib" ]; then
    echo "⚠ Model not found. Training now..."
    cd .. && python ml_backend/train_model.py && cd web_dashboard
fi

echo "Starting server on http://localhost:8080 ..."
python server.py
