#!/usr/bin/env python3
"""
Flask API service for LightGBM lead scoring.
Serves predictions via HTTP so the Java backend can call it.

Usage:
    python app.py
    # Or with a custom port:
    FLASK_PORT=5001 python app.py
"""
import os
from pathlib import Path

import lightgbm as lgb
import pandas as pd
from flask import Flask, request, jsonify

from features import FEATURE_NAMES, extract_features_from_lead

app = Flask(__name__)

MODEL_DIR = Path("models")
MODEL_PATH = MODEL_DIR / "lead_score_model.txt"

model = None


def load_model():
    global model
    if MODEL_PATH.exists():
        model = lgb.Booster(model_file=str(MODEL_PATH))
        print(f"Model loaded from {MODEL_PATH} ({model.num_feature()} features)")
    else:
        model = None
        print(f"Warning: No model found at {MODEL_PATH}")


@app.route("/health", methods=["GET"])
def health():
    return jsonify({
        "status": "ok",
        "model_loaded": model is not None,
        "expected_features": FEATURE_NAMES,
        "feature_count": len(FEATURE_NAMES),
    })


@app.route("/predict", methods=["POST"])
def predict():
    if model is None:
        return jsonify({"error": "Model not loaded. Train the model first."}), 503

    expected = model.num_feature()
    actual = len(FEATURE_NAMES)
    if expected != actual:
        return jsonify({
            "error": f"Feature mismatch: model expects {expected} features but code provides {actual}. Please retrain the model."
        }), 503

    data = request.get_json()
    if not data or "leads" not in data:
        return jsonify({"error": "Request body must contain 'leads' array"}), 400

    leads = data["leads"]
    if not leads:
        return jsonify({"predictions": []})

    rows = [extract_features_from_lead(lead) for lead in leads]
    X = pd.DataFrame(rows, columns=FEATURE_NAMES)
    probs = model.predict(X)

    predictions = []
    for lead, prob in zip(leads, probs):
        predictions.append({
            "leadId": lead.get("leadId") or lead.get("lead_id"),
            "probability": round(float(prob), 6),
        })

    return jsonify({"predictions": predictions})


@app.route("/reload", methods=["POST"])
def reload_model():
    """Reload the model from disk (call after retraining)."""
    load_model()
    return jsonify({
        "status": "ok",
        "model_loaded": model is not None,
    })


if __name__ == "__main__":
    load_model()
    port = int(os.environ.get("FLASK_PORT", 5001))
    print(f"Starting ML scoring service on port {port}")
    app.run(host="0.0.0.0", port=port, debug=False)
