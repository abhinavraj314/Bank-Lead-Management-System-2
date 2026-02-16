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

import lightgbm as lgb
import pandas as pd
from sklearn.model_selection import train_test_split

from features import FEATURE_NAMES, extract_features_from_lead, leads_to_dataframe


def load_leads_from_mongodb(uri: str, limit: int = 50_000) -> list[dict]:
    """Load leads from MongoDB (lead_management.leads)."""
    from pymongo import MongoClient

    client = MongoClient(uri)
    db = client.get_default_database()
    collection = db["leads"]
    cursor = collection.find({}).limit(limit)
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
        leads = load_leads_from_mongodb(uri, limit=args.limit)
        print(f"Loaded {len(leads)} leads from MongoDB")

    if len(leads) < 50:
        print(
            "Warning: Very few leads. Consider adding more data for a meaningful model.",
            file=sys.stderr,
        )

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
    y_val_series = pd.Series(y_val).astype(int)
    pred_val_series = pd.Series(pred_val)
    pos = pred_val_series[y_val_series == 1]
    neg = pred_val_series[y_val_series == 0]
    auc = ((pos.values[:, None] > neg.values[None, :]).mean()
           + 0.5 * (pos.values[:, None] == neg.values[None, :]).mean())
    print(f"Validation AUC: {auc:.4f}")


if __name__ == "__main__":
    main()
