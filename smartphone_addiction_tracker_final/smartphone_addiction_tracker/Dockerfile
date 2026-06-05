# ══════════════════════════════════════════════════════
#  SmartTracker FastAPI ML Backend — Dockerfile
#
#  Build:  docker build -t smarttracker-api .
#  Run:    docker run -p 8000:8000 smarttracker-api
#
#  Deploy to Cloud Run:
#    gcloud run deploy smarttracker-api \
#      --source . --port 8000 --allow-unauthenticated
# ══════════════════════════════════════════════════════

FROM python:3.11-slim

WORKDIR /app

# Install dependencies first (cached layer)
COPY fastapi_backend/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy ML training script and run it to bake model into image
COPY ml_backend/ ./ml_backend/
RUN python ml_backend/train_model.py

# Copy API server
COPY fastapi_backend/ ./fastapi_backend/

EXPOSE 8000

CMD ["uvicorn", "fastapi_backend.main:app", "--host", "0.0.0.0", "--port", "8000"]
