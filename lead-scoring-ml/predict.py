#!/usr/bin/env python3
"""
Run LightGBM inference and output raw conversion probabilities (0-1).
No thresholding or rounding is applied.
"""
import argparse
import json
import sys
from pathlib import Path

import lightgbm as lgb
import pandas as pd

from features import FEATURE_NAMES, extract_features_from_lead


def load_input(path: str) -> list[dict]:
    with open(path, "r", encoding="utf-8") as f:
        payload = json.load(f)
    if isinstance(payload, dict):
        return [payload]
    if isinstance(payload, list):
        return payload
    raise ValueError("Input JSON must be an object or an array of objects")


def main():
    parser = argparse.ArgumentParser(description="Predict lead conversion probabilities with LightGBM")
    parser.add_argument("--input", required=True, help="Input JSON file (single lead object or list)")
    parser.add_argument("--model", default="models/lead_score_model.txt", help="Path to trained LightGBM model")
    parser.add_argument("--output", help="Optional output JSON file. Defaults to stdout")
    args = parser.parse_args()

    model_path = Path(args.model)
    if not model_path.exists():
        print(f"Error: model not found at {model_path}", file=sys.stderr)
        sys.exit(1)

    leads = load_input(args.input)
    rows = [extract_features_from_lead(lead) for lead in leads]
    X = pd.DataFrame(rows, columns=FEATURE_NAMES)

    model = lgb.Booster(model_file=str(model_path))
    probs = model.predict(X)

    predictions = []
    for lead, prob in zip(leads, probs):
        predictions.append(
            {
                "leadId": lead.get("leadId") or lead.get("lead_id"),
                "probability": float(prob),
            }
        )

    if args.output:
        with open(args.output, "w", encoding="utf-8") as f:
            json.dump(predictions, f, indent=2)
    else:
        json.dump(predictions, sys.stdout, indent=2)
        sys.stdout.write("\n")


if __name__ == "__main__":
    main()
