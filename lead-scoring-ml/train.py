#!/usr/bin/env python3
"""
Train a LightGBM lead scoring model.

Data sources:
  1. MongoDB: set MONGODB_URI env var (same DB as Spring Boot)
  2. CSV: use --csv path/to/leads.csv (must have columns matching Lead model)

Target:
  - Requires a binary label column (default: converted) with values 0/1
    or true/false.
"""
import argparse
import json
import os
import sys
from pathlib import Path

# Load .env from script dir or project root so MONGODB_URI is set
try:
    from dotenv import load_dotenv
    _script_dir = Path(__file__).resolve().parent
    load_dotenv(_script_dir / ".env")
    load_dotenv(_script_dir.parent / ".env")
except ImportError:
    pass

import lightgbm as lgb
import pandas as pd
from sklearn.model_selection import train_test_split

from sklearn.metrics import roc_auc_score

from features import FEATURE_NAMES, extract_features_from_lead, leads_to_dataframe


def load_leads_from_mongodb(uri: str, limit: int = 50_000, 
                             start_date: str = None, end_date: str = None,
                             date_field: str = "statusUpdatedAt") -> list[dict]:
    """Load leads from MongoDB (lead_management.leads).
    
    Args:
        uri: MongoDB connection URI
        limit: Maximum number of leads to load
        start_date: Start date filter (YYYY-MM-DD), optional
        end_date: End date filter (YYYY-MM-DD), optional
        date_field: Field to filter by date (default: statusUpdatedAt)
    """
    from pymongo import MongoClient
    from datetime import datetime

    client = MongoClient(uri)
    db = client.get_default_database()
    collection = db["leads"]
    
    query = {}
    
    # Add date range filter if provided
    if start_date or end_date:
        # Dates are stored as strings in ISO format, so query with string values
        start_str = start_date if start_date else "1900-01-01"
        end_str = end_date + "T23:59:59" if end_date else "2099-12-31T23:59:59"
        
        date_query = {"$gte": start_str, "$lte": end_str}
        
        # Combine status/state check with date field check using $and
        query["$and"] = [
            {"$or": [{"status": "CLOSED"}, {"state": "CLOSED"}]},
            {"$or": [{"converted": True}, {"converted": False}]},
            {"$or": [
                {"statusUpdatedAt": date_query},
                {"updatedAt": date_query}
            ]}
        ]
    else:
        # No date filter - just filter by status/state and converted
        query["$or"] = [
            {"status": "CLOSED"},
            {"state": "CLOSED"}
        ]
        query["$or"] = [
            {"converted": True},
            {"converted": False}
        ]
    
    print(f"Query: {query}")
    cursor = collection.find(query).limit(limit)
    leads = list(cursor)
    # Convert ObjectId and datetime for JSON serialization in feature extraction
    for lead in leads:
        if "_id" in lead:
            lead["_id"] = str(lead["_id"])
    return leads


def load_leads_from_csv(path: str) -> list[dict]:
    """Load leads from CSV. Maps common column names to Lead model fields."""
    df = pd.read_csv(path)
    # Normalize column names
    col_map = {
        "email": "email",
        "phone": "phoneNumber",
        "phone_number": "phoneNumber",
        "phoneNumber": "phoneNumber",
        "aadhar": "aadharNumber",
        "aadhar_number": "aadharNumber",
        "aadharNumber": "aadharNumber",
        "name": "name",
        "p_id": "pId",
        "pId": "pId",
        "product_id": "pId",
        "source_id": "sourceId",
        "sourceId": "sourceId",
        "created_at": "createdAt",
        "createdAt": "createdAt",
        "lead_score": "leadScore",
        "leadScore": "leadScore",
        "sources_seen": "sourcesSeen",
        "sourcesSeen": "sourcesSeen",
        "products_seen": "productsSeen",
        "productsSeen": "productsSeen",
    }
    records = []
    for _, row in df.iterrows():
        rec = {}
        for k, v in row.items():
            key = col_map.get(str(k).strip(), k)
            if pd.isna(v):
                rec[key] = None
            else:
                rec[key] = v
        records.append(rec)
    return records


def main():
    parser = argparse.ArgumentParser(description="Train LightGBM lead scoring model")
    parser.add_argument("--csv", type=str, help="Path to CSV with leads (optional)")
    parser.add_argument("--limit", type=int, default=50_000, help="Max leads from MongoDB (default 50000)")
    parser.add_argument("--target-column", type=str, default="converted",
                        help="Binary target column name (default: converted)")
    parser.add_argument("--output-dir", type=str, default="models", help="Directory to save model and config")
    parser.add_argument("--test-size", type=float, default=0.2, help="Fraction for test set (default 0.2)")
    parser.add_argument("--random-state", type=int, default=42)
    parser.add_argument("--start-date", type=str, 
                        help="Start date for filtering (YYYY-MM-DD). When set, only loads leads closed in this range.")
    parser.add_argument("--end-date", type=str,
                        help="End date for filtering (YYYY-MM-DD). When set, only loads leads closed in this range.")
    parser.add_argument("--date-field", type=str, default="statusUpdatedAt",
                        help="Date field to filter by (default: statusUpdatedAt, also checks updatedAt)")
    parser.add_argument("--reload-url", type=str, default=None,
                        help="URL to call after successful training (e.g. http://localhost:5001/reload)")
    parser.add_argument("--min-leads", type=int, default=50,
                        help="Minimum leads required for training (default: 50). If fewer, skip training.")
    args = parser.parse_args()

    # Load leads
    if args.csv:
        if not os.path.isfile(args.csv):
            print(f"Error: CSV not found: {args.csv}", file=sys.stderr)
            sys.exit(1)
        leads = load_leads_from_csv(args.csv)
        print(f"Loaded {len(leads)} leads from CSV")
    else:
        uri = os.environ.get("MONGODB_URI")
        if not uri:
            print(
                "Error: Set MONGODB_URI env var or use --csv path/to/leads.csv",
                file=sys.stderr,
            )
            sys.exit(1)
        leads = load_leads_from_mongodb(
            uri, 
            limit=args.limit,
            start_date=args.start_date,
            end_date=args.end_date,
            date_field=args.date_field
        )
        if args.start_date and args.end_date:
            print(f"Loaded {len(leads)} leads from MongoDB (date range: {args.start_date} to {args.end_date})")
        else:
            print(f"Loaded {len(leads)} leads from MongoDB")

    if len(leads) < args.min_leads:
        print(
            f"Warning: Only {len(leads)} leads loaded (minimum: {args.min_leads}). Skipping training.",
            file=sys.stderr,
        )
        sys.exit(0)  # Exit gracefully, not an error

    # Build feature matrix and binary target (0/1 only)
    df = leads_to_dataframe(leads, target_column=args.target_column)
    if "_target" not in df.columns:
        print(
            f"Error: target column '{args.target_column}' not found. "
            "Provide a binary 0/1 label column (e.g. converted).",
            file=sys.stderr,
        )
        sys.exit(1)

    X = df[FEATURE_NAMES]
    y_raw = df["_target"]

    # Strict binary label parsing: accepts 0/1, true/false, yes/no
    y = pd.to_numeric(y_raw, errors="coerce")
    invalid_numeric = y.isna()
    if invalid_numeric.any():
        y_str = y_raw.astype(str).str.strip().str.lower()
        y = y.where(~invalid_numeric, None)
        y = y.astype("object")
        y = y.mask(invalid_numeric & y_str.isin(["true", "yes", "y"]), 1.0)
        y = y.mask(invalid_numeric & y_str.isin(["false", "no", "n"]), 0.0)
        y = pd.to_numeric(y, errors="coerce")

    valid_mask = y.isin([0.0, 1.0])
    if not valid_mask.all():
        bad_count = int((~valid_mask).sum())
        print(
            f"Error: target column '{args.target_column}' contains {bad_count} non-binary values. "
            "Only 0/1 (or true/false) are allowed.",
            file=sys.stderr,
        )
        sys.exit(1)

    X = X.loc[valid_mask]
    y = y.loc[valid_mask].astype(int)

    if len(y.unique()) < 2:
        print(
            "Error: target has only one class after cleaning. Need both 0 and 1 to train binary model.",
            file=sys.stderr,
        )
        sys.exit(1)

    # Train/validation split
    X_train, X_val, y_train, y_val = train_test_split(
        X, y, test_size=args.test_size, random_state=args.random_state, stratify=y
    )

    # LightGBM dataset
    train_data = lgb.Dataset(X_train, label=y_train, feature_name=FEATURE_NAMES)
    val_data = lgb.Dataset(X_val, label=y_val, reference=train_data, feature_name=FEATURE_NAMES)

    params = {
        "objective": "binary",
        "metric": "auc",
        "boosting_type": "gbdt",
        "num_leaves": 31,
        "learning_rate": 0.05,
        "feature_fraction": 0.9,
        "bagging_fraction": 0.8,
        "bagging_freq": 5,
        "verbose": -1,
        "seed": args.random_state,
    }

    callbacks = [lgb.early_stopping(50, verbose=False), lgb.log_evaluation(period=50)]

    model = lgb.train(
        params,
        train_data,
        num_boost_round=500,
        valid_sets=[train_data, val_data],
        valid_names=["train", "valid"],
        callbacks=callbacks,
    )

    # Save model and config
    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    model_path = out_dir / "lead_score_model.txt"
    model.save_model(str(model_path))
    print(f"Model saved to {model_path}")

    config = {
        "feature_names": FEATURE_NAMES,
        "version": "1.1",
        "target": args.target_column,
        "objective": "binary",
        "prediction": "probability",
    }
    config_path = out_dir / "feature_config.json"
    with open(config_path, "w") as f:
        json.dump(config, f, indent=2)
    print(f"Feature config saved to {config_path}")

    # Quick evaluation
    pred_val = model.predict(X_val)
    auc = roc_auc_score(y_val, pred_val)
    print(f"Validation AUC: {auc:.4f}")

    # Optionally reload the live ML service
    if args.reload_url:
        try:
            import requests
            response = requests.post(args.reload_url, timeout=10)
            if response.status_code == 200:
                print(f"Successfully called reload: {args.reload_url}")
            else:
                print(f"Warning: Reload call failed with status {response.status_code}: {response.text}", file=sys.stderr)
        except Exception as e:
            print(f"Warning: Failed to call reload URL: {e}", file=sys.stderr)


if __name__ == "__main__":
    main()
