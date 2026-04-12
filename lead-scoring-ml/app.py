#!/usr/bin/env python3
"""
Flask API service for LightGBM lead scoring.
Serves predictions via HTTP so the Java backend can call it.

Usage:
    python app.py
    # Or with a .env file (MONGODB_URI, FLASK_PORT, etc.):
    python app.py

Optional: Monthly auto-training on closed leads from previous month.
Runs automatically on the 1st of every month at 02:00.
To disable: set DISABLE_AUTO_TRAIN=true in .env or environment.
"""
import os
from pathlib import Path

# Load .env from the same directory as this script (or cwd) so MONGODB_URI etc. are set
try:
    from dotenv import load_dotenv
    _env_path = Path(__file__).resolve().parent / ".env"
    load_dotenv(_env_path)
    if not os.environ.get("MONGODB_URI") and (_env_path.parent.parent / ".env").exists():
        load_dotenv(_env_path.parent.parent / ".env")
except ImportError:
    pass

import lightgbm as lgb
import pandas as pd
from flask import Flask, request, jsonify
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
import subprocess
import sys
import requests
from datetime import datetime, timedelta

from features import FEATURE_NAMES, extract_features_from_lead

app = Flask(__name__)

MODEL_DIR = Path("models")
MODEL_PATH = MODEL_DIR / "lead_score_model.txt"

model = None

scheduler = BackgroundScheduler()


def get_last_month_range():
    """Return (start_date, end_date) for last calendar month (YYYY-MM-DD format)."""
    today = datetime.now()
    first_day_this_month = today.replace(day=1)
    last_month_end = first_day_this_month - timedelta(days=1)
    last_month_start = last_month_end.replace(day=1)
    
    start_date = last_month_start.strftime("%Y-%m-%d")
    end_date = last_month_end.strftime("%Y-%m-%d")
    return start_date, end_date


def run_auto_training():
    """Run monthly training job on last month's closed leads."""
    print(f"[Auto-Train] Starting monthly training job...")
    
    mongodb_uri = os.environ.get("MONGODB_URI")
    if not mongodb_uri:
        print("[Auto-Train] Error: MONGODB_URI not set, skipping training.", file=sys.stderr)
        return
    
    flask_port = os.environ.get("FLASK_PORT", "5001")
    reload_url = f"http://localhost:{flask_port}/reload"
    
    start_date, end_date = get_last_month_range()
    print(f"[Auto-Train] Training on leads closed from {start_date} to {end_date}")
    
    # Run training as subprocess to avoid blocking the Flask app
    train_script = Path(__file__).parent / "train.py"
    cmd = [
        sys.executable,
        str(train_script),
        "--start-date", start_date,
        "--end-date", end_date,
        "--reload-url", reload_url,
        "--min-leads", "50"
    ]
    
    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=600  # 10 minutes max
        )
        
        if result.returncode == 0:
            if "Skipping training" in result.stdout:
                print(f"[Auto-Train] Not enough leads in date range, skipped training.")
            else:
                # Print only key lines from training output
                lines = result.stdout.strip().split('\n')
                for line in lines:
                    if 'Loaded' in line or 'AUC' in line or 'saved' in line.lower() or 'Model saved' in line:
                        print(f"[Auto-Train] {line}")
                
                # Reload model in this process
                load_model()
                print(f"[Auto-Train] Model reloaded in running service.")
        else:
            print(f"[Auto-Train] Training failed with code {result.returncode}", file=sys.stderr)
            print(f"[Auto-Train] stderr: {result.stderr}", file=sys.stderr)
    except subprocess.TimeoutExpired:
        print("[Auto-Train] Training timed out after 10 minutes.", file=sys.stderr)
    except Exception as e:
        print(f"[Auto-Train] Error during training: {e}", file=sys.stderr)


def setup_scheduler():
    """Configure and start the monthly auto-training scheduler.
    
    Runs on the 1st of every month at 02:00, training on last month's closed leads.
    """
    # Always enable auto-training (monthly feedback loop)
    # To disable: set DISABLE_AUTO_TRAIN=true in environment
    
    scheduler_running = os.environ.get("DISABLE_AUTO_TRAIN", "false").lower() == "true"
    
    if scheduler_running:
        print("[Scheduler] Auto-training is disabled (set DISABLE_AUTO_TRAIN=false to enable)")
        return
    
    # Run on 1st of every month at 02:00
    trigger = CronTrigger(day=1, hour=2, minute=0)
    scheduler.add_job(
        run_auto_training,
        trigger=trigger,
        id="monthly_training",
        name="Monthly lead scoring model retraining",
        replace_existing=True
    )
    
    scheduler.start()
    print("[Scheduler] Monthly auto-training enabled. Next run: 1st of next month at 02:00")


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

    rows = []
    canonical_fields_per_lead = []
    
    for lead in leads:
        # Extract all 16 features
        all_features = extract_features_from_lead(lead)
        
        # Get canonical fields for this lead (optional) - if not provided, use all
        canonical_fields = lead.get("canonicalFields")
        if canonical_fields is None:
            # Use all features if not specified
            canonical_fields = FEATURE_NAMES
        else:
            # Ensure it's a list
            if not isinstance(canonical_fields, list):
                canonical_fields = list(canonical_fields)
        
        canonical_fields_per_lead.append(canonical_fields)
        rows.append(all_features)
    
    # Create DataFrame with all features
    X_all = pd.DataFrame(rows, columns=FEATURE_NAMES)
    
    # For prediction, use all features (the model was trained on all 16)
    # But we store which features were "selected" for reference/audit
    probs = model.predict(X_all)

    predictions = []
    for i, (lead, prob) in enumerate(zip(leads, probs)):
        predictions.append({
            "leadId": lead.get("leadId") or lead.get("lead_id"),
            "probability": round(float(prob), 6),
            "featuresUsed": canonical_fields_per_lead[i],  # Echo back which features were used
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


@app.route("/train", methods=["POST"])
def trigger_training():
    """Manually trigger monthly training (for testing).
    
    Body (optional): {"start_date": "2025-01-01", "end_date": "2025-01-31"}
    If not provided, uses last month's range.
    """
    import threading
    
    # Handle both JSON and non-JSON requests
    data = {}
    if request.content_type == "application/json":
        data = request.get_json(silent=True) or {}
    
    if data.get("start_date") and data.get("end_date"):
        # Override date range for testing
        original_func = run_auto_training
        
        def custom_training():
            mongodb_uri = os.environ.get("MONGODB_URI")
            if not mongodb_uri:
                print("[Auto-Train] Error: MONGODB_URI not set", file=sys.stderr)
                return
            flask_port = os.environ.get("FLASK_PORT", "5001")
            reload_url = f"http://localhost:{flask_port}/reload"
            start_date = data["start_date"]
            end_date = data["end_date"]
            print(f"[Auto-Train] Training on leads closed from {start_date} to {end_date}")
            train_script = Path(__file__).parent / "train.py"
            cmd = [
                sys.executable,
                str(train_script),
                "--start-date", start_date,
                "--end-date", end_date,
                "--reload-url", reload_url,
                "--min-leads", "1"
            ]
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
            if result.returncode == 0:
                # Print only key lines from training output
                lines = result.stdout.strip().split('\n')
                for line in lines:
                    if 'Loaded' in line or 'AUC' in line or 'saved' in line.lower() or 'Model saved' in line:
                        print(f"[Auto-Train] {line}")
                
                load_model()
                print(f"[Auto-Train] Custom training completed")
            else:
                print(f"[Auto-Train] Failed: {result.stderr}", file=sys.stderr)
        
        thread = threading.Thread(target=custom_training)
        thread.start()
    else:
        thread = threading.Thread(target=run_auto_training)
        thread.start()
    
    return jsonify({
        "status": "ok",
        "message": "Training job started in background"
    })


if __name__ == "__main__":
    load_model()
    setup_scheduler()
    port = int(os.environ.get("FLASK_PORT", 5001))
    print(f"ML scoring service: http://127.0.0.1:{port} (health: http://127.0.0.1:{port}/health)")
    
    try:
        app.run(host="0.0.0.0", port=port, debug=False)
    finally:
        if scheduler.running:
            scheduler.shutdown()
