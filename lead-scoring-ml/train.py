#!/usr/bin/env python3
"""
Train a LightGBM lead scoring model.

Data sources:
  1. MongoDB: set MONGODB_URI env var (same DB as Spring Boot)
  2. CSV: use --csv path/to/leads.csv (must have columns matching Lead model)

Target:
  - With real conversion data: add column 'converted' (0/1) or 'target' to CSV,
    or store in MongoDB and set --target-column
  - Without conversion data: uses rule-based score as proxy (from leadScore or computed)
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


def compute_rule_based_target(lead: dict) -> float:
    """
    Compute rule-based score (0–100) as proxy target when no conversion data exists.
    Mirrors LeadScoringService logic.
    """
    score = 0
    if lead.get("email") and str(lead["email"]).strip():
        score += 30
    if lead.get("phoneNumber") and str(lead["phoneNumber"]).strip():
        score += 30
    if lead.get("phone_number") and str(lead["phone_number"]).strip():
        score += 30
    if lead.get("aadharNumber") and str(lead["aadharNumber"]).strip():
        score += 20
    if lead.get("aadhar_number") and str(lead["aadhar_number"]).strip():
        score += 20
    if lead.get("name") and str(lead["name"]).strip():
        score += 10
    sources = lead.get("sourcesSeen") or lead.get("sources_seen") or []
    if isinstance(sources, (list, tuple)) and len(sources) > 1:
        score += 10
    products = lead.get("productsSeen") or lead.get("products_seen") or []
    if isinstance(products, (list, tuple)) and len(products) > 1:
        score += 10
    return min(score, 100) / 100.0  # Normalize to 0–1 for regression


def main():
    parser = argparse.ArgumentParser(description="Train LightGBM lead scoring model")
    parser.add_argument("--csv", type=str, help="Path to CSV with leads (optional)")
    parser.add_argument("--limit", type=int, default=50_000, help="Max leads from MongoDB (default 50000)")
    parser.add_argument("--target-column", type=str, help="Column name for target (e.g. converted, leadScore)")
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

    # Build feature matrix and target
    df = leads_to_dataframe(leads, target_column=args.target_column)

    if args.target_column and "_target" in df.columns:
        y = df["_target"].values
        X = df[FEATURE_NAMES]
    else:
        # Use rule-based score as proxy target
        y = [compute_rule_based_target(l) for l in leads]
        X = df[FEATURE_NAMES]
        if len(y) != len(X):
            # Align lengths (leads_to_dataframe may drop rows if target_column was used)
            X = leads_to_dataframe(leads)
            y = [compute_rule_based_target(l) for l in leads]
        y = pd.Series(y)
        print("Using rule-based score as proxy target (no conversion data)")

    # Train/validation split
    X_train, X_val, y_train, y_val = train_test_split(
        X, y, test_size=args.test_size, random_state=args.random_state
    )

    # LightGBM dataset
    train_data = lgb.Dataset(X_train, label=y_train, feature_name=FEATURE_NAMES)
    val_data = lgb.Dataset(X_val, label=y_val, reference=train_data, feature_name=FEATURE_NAMES)

    params = {
        "objective": "regression",
        "metric": "rmse",
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
        "version": "1.0",
        "target": args.target_column or "rule_based_proxy",
    }
    config_path = out_dir / "feature_config.json"
    with open(config_path, "w") as f:
        json.dump(config, f, indent=2)
    print(f"Feature config saved to {config_path}")

    # Quick evaluation
    pred_val = model.predict(X_val)
    rmse = ((pred_val - y_val) ** 2).mean() ** 0.5
    print(f"Validation RMSE: {rmse:.4f}")


if __name__ == "__main__":
    main()
