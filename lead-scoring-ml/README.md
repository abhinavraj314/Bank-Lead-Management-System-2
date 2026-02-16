# Lead Scoring - LightGBM Training

Step 1 of the LightGBM lead scoring integration: train a model on your lead data.

## Setup

```bash
cd lead-scoring-ml
pip install -r requirements.txt
```

## Training

### Option A: From MongoDB (same DB as Spring Boot)

Set `MONGODB_URI` to your connection string (same as `spring.data.mongodb.uri` in `application.yml`):

```bash
export MONGODB_URI="mongodb+srv://user:pass@cluster.mongodb.net/lead_management?retryWrites=true&w=majority"
python train.py --limit 10000 --target-column converted
```

### Option B: From CSV

Export leads to CSV (from MongoDB Compass, `mongoexport`, or your backend) and train:

```bash
python train.py --csv path/to/leads.csv --target-column converted
```

Target column must be binary labels (`0/1`, or `true/false`).

### Model configuration

The trainer uses:
- LightGBM `objective=binary`
- Metric `auc`
- Predicted output: probability in `[0, 1]`

### Output

- `models/lead_score_model.txt` - LightGBM model
- `models/feature_config.json` - Feature names and metadata

## Inference (probability output)

Run inference to get probabilities in `[0, 1]` (no thresholding or rounding):

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

## Features

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
