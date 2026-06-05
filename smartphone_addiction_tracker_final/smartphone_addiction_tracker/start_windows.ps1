# ================================================================
#  SmartTracker — Windows PowerShell Start Script
#  Run this from inside the smartphone_addiction_tracker/ folder
#  Right-click → "Run with PowerShell" OR paste into terminal
# ================================================================

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   SmartTracker Dashboard Setup         " -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Install dependencies
Write-Host "[1/3] Installing Python dependencies..." -ForegroundColor Yellow
pip install fastapi "uvicorn[standard]" scikit-learn numpy joblib

Write-Host ""

# Step 2: Train the ML model
Write-Host "[2/3] Training ML model..." -ForegroundColor Yellow
python ml_backend/train_model.py

Write-Host ""

# Step 3: Start the web dashboard server
Write-Host "[3/3] Starting web dashboard server..." -ForegroundColor Green
Write-Host ""
Write-Host "  Open your browser at: http://localhost:8080" -ForegroundColor Cyan
Write-Host "  Press Ctrl+C to stop the server" -ForegroundColor Gray
Write-Host ""

Set-Location web_dashboard
python server.py
