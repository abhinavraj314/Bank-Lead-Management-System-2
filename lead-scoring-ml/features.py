"""
Feature extraction for lead scoring.
Must stay in sync with the Python scoring service and Java client.
"""
import pandas as pd
from datetime import datetime, timezone


# Ordered feature names - must match training and serving
FEATURE_NAMES = [
    "has_email",
    "has_phone",
    "has_aadhar",
    "has_name",
    "num_sources_seen",
    "num_products_seen",
    "days_since_created",
    "p_id_personal_loan",
    "p_id_credit_card",
    "p_id_home_loan",
    "p_id_other",
    "income",
    "credit_score",
    "loan_amount",
    "emp_salaried",
    "emp_self_employed",
]


def _get(d: dict, *keys, default=None):
    """Get value from dict with multiple possible keys (camelCase or snake_case)."""
    for k in keys:
        if k in d and d[k] is not None:
            return d[k]
    return default


def _non_empty(val) -> bool:
    if val is None:
        return False
    s = str(val).strip()
    return len(s) > 0


def _safe_int(val, default: int = 0) -> int:
    if val is None:
        return default
    try:
        return int(float(val))
    except (TypeError, ValueError):
        return default


def extract_features_from_lead(lead: dict) -> dict:
    """
    Extract feature dict from a single lead (MongoDB document or API response).
    Handles both camelCase (Mongo/Java) and snake_case field names.
    """
    email = _get(lead, "email")
    phone = _get(lead, "phoneNumber", "phone_number")
    aadhar = _get(lead, "aadharNumber", "aadhar_number")
    name = _get(lead, "name")
    sources_seen = _get(lead, "sourcesSeen", "sources_seen") or []
    products_seen = _get(lead, "productsSeen", "products_seen") or []
    created_at = _get(lead, "createdAt", "created_at")
    p_id = _get(lead, "pId", "p_id") or ""

    # Financial fields
    income = _safe_int(_get(lead, "income"))
    credit_score = _safe_int(_get(lead, "creditScore", "credit_score"))
    loan_amount = _safe_int(_get(lead, "loanAmount", "loan_amount"))
    employment_type = str(_get(lead, "employmentType", "employment_type") or "").upper().strip()

    # days_since_created (use UTC to avoid timezone mismatch)
    days = 0
    if created_at:
        try:
            dt = pd.to_datetime(created_at, utc=True)
            now = pd.Timestamp.now(tz="UTC")
            days = max(0, (now - dt).days)
        except Exception:
            pass

    # Product one-hot (mutually exclusive)
    p_upper = str(p_id).upper() if p_id else ""
    p_id_personal_loan = 1 if "PERSONAL" in p_upper else 0
    p_id_credit_card = 1 if "CREDIT" in p_upper or "CARD" in p_upper else 0
    p_id_home_loan = 1 if "HOME" in p_upper else 0
    # Resolve conflicts: HOME_LOAN should only be home_loan, not personal_loan
    if p_id_home_loan and p_id_personal_loan:
        p_id_personal_loan = 0
    p_id_other = 1 if not (p_id_personal_loan or p_id_credit_card or p_id_home_loan) else 0

    # Employment type one-hot
    emp_salaried = 1 if employment_type == "SALARIED" else 0
    emp_self_employed = 1 if employment_type == "SELF_EMPLOYED" else 0

    return {
        "has_email": 1 if _non_empty(email) else 0,
        "has_phone": 1 if _non_empty(phone) else 0,
        "has_aadhar": 1 if _non_empty(aadhar) else 0,
        "has_name": 1 if _non_empty(name) else 0,
        "num_sources_seen": len(sources_seen) if isinstance(sources_seen, (list, tuple)) else 0,
        "num_products_seen": len(products_seen) if isinstance(products_seen, (list, tuple)) else 0,
        "days_since_created": days,
        "p_id_personal_loan": p_id_personal_loan,
        "p_id_credit_card": p_id_credit_card,
        "p_id_home_loan": p_id_home_loan,
        "p_id_other": p_id_other,
        "income": income,
        "credit_score": credit_score,
        "loan_amount": loan_amount,
        "emp_salaried": emp_salaried,
        "emp_self_employed": emp_self_employed,
    }


def leads_to_dataframe(leads: list[dict], target_column: str | None = None) -> pd.DataFrame:
    """
    Convert list of leads to a feature DataFrame.
    If target_column is provided and exists in each lead, use it as target.
    """
    rows = [extract_features_from_lead(l) for l in leads]
    df = pd.DataFrame(rows, columns=FEATURE_NAMES)

    if target_column:
        targets = []
        for lead in leads:
            val = _get(lead, target_column)
            if val is not None:
                try:
                    targets.append(float(val))
                except (TypeError, ValueError):
                    targets.append(None)
            else:
                targets.append(None)
        df["_target"] = targets
        df = df.dropna(subset=["_target"])

    return df
