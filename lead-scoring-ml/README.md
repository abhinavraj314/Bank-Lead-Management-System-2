# Lead Scoring - LightGBM ML Service

ML-based lead scoring using LightGBM. Includes training pipeline and a Flask API service that the Java backend calls for real-time scoring.

## Setup

```bash
cd lead-scoring-ml
pip install -r requirements.txt
```

## Step 1: Train the Model

### Option A: From MongoDB (same DB as Spring Boot)

Set `MONGODB_URI` to your connection string (same as `spring.data.mongodb.uri` in `application.yml`):

```bash
export MONGODB_URI="mongodb+srv://user:pass@cluster.mongodb.net/lead_management?retryWrites=true&w=majority"
python train.py --limit 10000 --target-column converted
```

### Option B: From CSV

```bash
python train.py --csv path/to/leads.csv --target-column converted
```

Target column must be binary labels (`0/1`, or `true/false`).

### Output

- `models/lead_score_model.txt` - LightGBM model
- `models/feature_config.json` - Feature names and metadata

## Step 2: Start the Scoring Service

The Flask API serves predictions to the Java backend:

```bash
python app.py
```

The service runs on port **5001** by default. Override with:

```bash
FLASK_PORT=5001 python app.py
```

### API Endpoints

| Method | Endpoint   | Description                          |
|--------|------------|--------------------------------------|
| GET    | /health    | Service health + model status        |
| POST   | /predict   | Score leads (single or batch)        |
| POST   | /reload    | Reload model from disk after retrain |

### Predict Request

```json
{
  "leads": [
    {
      "leadId": "L123",
      "email": "test@example.com",
      "phoneNumber": "9876543210",
      "pId": "PERSONAL_LOAN",
      "income": 50000,
      "creditScore": 750,
      "employmentType": "SALARIED",
      "loanAmount": 500000
    }
  ]
}
```

### Predict Response

```json
{
  "predictions": [
    { "leadId": "L123", "probability": 0.8721 }
  ]
}
```

## Features (v2)

| Feature              | Description                                    |
|----------------------|------------------------------------------------|
| has_email            | 1 if email present                             |
| has_phone            | 1 if phone present                             |
| has_aadhar           | 1 if aadhar present                            |
| has_name             | 1 if name present                              |
| num_sources_seen     | Count of sources seen                          |
| num_products_seen    | Count of products seen                         |
| days_since_created   | Days since lead creation                       |
| p_id_*               | Product-type indicators (personal loan, etc.)  |
| income               | Monthly income (0 if unknown)                  |
| credit_score         | Credit score (0 if unknown)                    |
| loan_amount          | Loan amount requested (0 if unknown)           |
| emp_salaried         | 1 if employment type is salaried               |
| emp_self_employed    | 1 if employment type is self-employed          |

## CLI Inference

For ad-hoc predictions without the Flask service:

```bash
python predict.py --input leads.json --model models/lead_score_model.txt
```

Input can be:
- a single lead JSON object
- an array of lead objects

Output:

```json
[
  { "leadId": "L123", "probability": 0.8721 }
]
```
