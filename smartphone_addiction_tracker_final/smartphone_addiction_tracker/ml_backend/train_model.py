"""
train_model.py
══════════════
Trains a Random Forest classifier to predict smartphone addiction level.

Output Features (sent from Android app):
  - daily_usage_hours       : total screen time in hours
  - night_usage_hours       : usage between 10PM–6AM
  - app_switching_frequency : number of app switches
  - social_media_percentage : % of time on social apps (0–100)
  - total_sessions          : total app opens
  - avg_session_duration_mins : average session length in minutes

Target (3-class):
  0 = Low   (< 2h/day, healthy patterns)
  1 = Medium (2–5h/day, moderate concern)
  2 = High  (> 5h/day, strong addiction markers)

Usage:
  python train_model.py

Output:
  model/addiction_model.joblib    ← loaded by FastAPI server
  model/label_encoder.joblib
  model/scaler.joblib
  model/metrics.json
"""

import json
import os
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.preprocessing import LabelEncoder, StandardScaler
from sklearn.metrics import (
    classification_report, confusion_matrix, accuracy_score
)
import joblib

# ── Reproducibility ──────────────────────────────────────────────────────────
SEED = 42
np.random.seed(SEED)
os.makedirs("model", exist_ok=True)

FEATURE_COLS = [
    "daily_usage_hours",
    "night_usage_hours",
    "app_switching_frequency",
    "social_media_percentage",
    "total_sessions",
    "avg_session_duration_mins",
]

LABEL_COL = "addiction_level"
LABELS    = ["Low", "Medium", "High"]


# ════════════════════════════════════════════════════════════════════════════
#  1. Generate Synthetic Training Data
#     Replace this section with real CSV data if available.
#     e.g.: df = pd.read_csv("real_usage_data.csv")
# ════════════════════════════════════════════════════════════════════════════

def generate_training_data(n_samples: int = 5000) -> pd.DataFrame:
    """
    Generates realistic synthetic smartphone usage data.

    LOW users  (class 0): short sessions, low night use, low social
    MEDIUM     (class 1): moderate screen time, some night use
    HIGH users (class 2): long sessions, heavy night use, social-heavy
    """
    records = []

    for _ in range(n_samples):
        label = np.random.choice(LABELS, p=[0.4, 0.35, 0.25])

        if label == "Low":
            daily        = np.random.uniform(0.5, 2.5)
            night        = np.random.uniform(0, 0.5)
            switches     = int(np.random.uniform(10, 40))
            social_pct   = np.random.uniform(5, 30)
            sessions     = int(np.random.uniform(5, 25))
            avg_session  = np.random.uniform(2, 8)

        elif label == "Medium":
            daily        = np.random.uniform(2.5, 5.0)
            night        = np.random.uniform(0.3, 1.5)
            switches     = int(np.random.uniform(30, 80))
            social_pct   = np.random.uniform(25, 55)
            sessions     = int(np.random.uniform(20, 60))
            avg_session  = np.random.uniform(5, 15)

        else:  # High
            daily        = np.random.uniform(5.0, 12.0)
            night        = np.random.uniform(1.0, 4.0)
            switches     = int(np.random.uniform(70, 200))
            social_pct   = np.random.uniform(50, 90)
            sessions     = int(np.random.uniform(50, 150))
            avg_session  = np.random.uniform(10, 30)

        # Add Gaussian noise to avoid perfect separation
        daily       += np.random.normal(0, 0.2)
        social_pct  = np.clip(social_pct + np.random.normal(0, 3), 0, 100)

        records.append({
            "daily_usage_hours":        round(max(0, daily), 2),
            "night_usage_hours":        round(max(0, night), 2),
            "app_switching_frequency":  max(0, switches),
            "social_media_percentage":  round(social_pct, 2),
            "total_sessions":           max(1, sessions),
            "avg_session_duration_mins": round(max(0.5, avg_session), 2),
            "addiction_level":          label,
        })

    return pd.DataFrame(records)


# ════════════════════════════════════════════════════════════════════════════
#  2. Preprocessing
# ════════════════════════════════════════════════════════════════════════════

def preprocess(df: pd.DataFrame):
    le      = LabelEncoder()
    scaler  = StandardScaler()

    X = df[FEATURE_COLS].values
    y = le.fit_transform(df[LABEL_COL].values)  # Low=0, Medium=1, High=2
    X_scaled = scaler.fit_transform(X)

    return X_scaled, y, le, scaler


# ════════════════════════════════════════════════════════════════════════════
#  3. Train & Evaluate
# ════════════════════════════════════════════════════════════════════════════

def train_and_evaluate(X, y, le):
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=SEED, stratify=y
    )

    # ── Random Forest (primary) ──────────────────────────────────────────────
    rf = RandomForestClassifier(
        n_estimators=200,
        max_depth=10,
        min_samples_split=5,
        class_weight="balanced",
        random_state=SEED,
        n_jobs=-1,
    )
    rf.fit(X_train, y_train)

    # ── Logistic Regression (baseline) ──────────────────────────────────────
    lr = LogisticRegression(
        max_iter=1000, class_weight="balanced", random_state=SEED
    )
    lr.fit(X_train, y_train)

    # ── Evaluation ───────────────────────────────────────────────────────────
    rf_preds = rf.predict(X_test)
    lr_preds = lr.predict(X_test)

    rf_acc = accuracy_score(y_test, rf_preds)
    lr_acc = accuracy_score(y_test, lr_preds)

    print("=" * 60)
    print(f"Random Forest Accuracy : {rf_acc:.4f}")
    print(f"Logistic Regression    : {lr_acc:.4f}")
    print("\nRandom Forest Classification Report:")
    print(classification_report(y_test, rf_preds, target_names=le.classes_))
    print("Confusion Matrix:")
    print(confusion_matrix(y_test, rf_preds))

    # Cross-validation
    cv_scores = cross_val_score(rf, X, y, cv=5, scoring="accuracy")
    print(f"\n5-Fold CV Accuracy: {cv_scores.mean():.4f} ± {cv_scores.std():.4f}")

    # Feature importance
    print("\nFeature Importances (Random Forest):")
    importances = rf.feature_importances_
    for feat, imp in sorted(zip(FEATURE_COLS, importances),
                            key=lambda x: x[1], reverse=True):
        print(f"  {feat:35s} {imp:.4f}")

    # Choose best model
    best_model = rf if rf_acc >= lr_acc else lr
    print(f"\n✅ Using: {'Random Forest' if best_model is rf else 'Logistic Regression'}")

    metrics = {
        "random_forest_accuracy": rf_acc,
        "logistic_regression_accuracy": lr_acc,
        "cv_mean": float(cv_scores.mean()),
        "cv_std": float(cv_scores.std()),
        "feature_importances": dict(zip(FEATURE_COLS, importances.tolist())),
        "classes": le.classes_.tolist(),
    }

    return best_model, metrics


# ════════════════════════════════════════════════════════════════════════════
#  4. Save Artifacts
# ════════════════════════════════════════════════════════════════════════════

def save_artifacts(model, le, scaler, metrics):
    joblib.dump(model,  "model/addiction_model.joblib")
    joblib.dump(le,     "model/label_encoder.joblib")
    joblib.dump(scaler, "model/scaler.joblib")

    with open("model/metrics.json", "w") as f:
        json.dump(metrics, f, indent=2)

    print("\n✅ Saved to model/")
    print("   addiction_model.joblib")
    print("   label_encoder.joblib")
    print("   scaler.joblib")
    print("   metrics.json")


# ════════════════════════════════════════════════════════════════════════════
#  5. Main
# ════════════════════════════════════════════════════════════════════════════

if __name__ == "__main__":
    print("Generating training data ...")
    df = generate_training_data(n_samples=5000)
    print(f"Dataset shape: {df.shape}")
    print(df[LABEL_COL].value_counts())
    print()

    print("Preprocessing ...")
    X, y, le, scaler = preprocess(df)

    print("Training models ...")
    model, metrics = train_and_evaluate(X, y, le)

    print("\nSaving artifacts ...")
    save_artifacts(model, le, scaler, metrics)

    print("\n🎉 Training complete!")
