# Lead Scoring – LightGBM Training

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
python train.py --limit 10000
```

### Option B: From CSV

Export leads to CSV (from MongoDB Compass, `mongoexport`, or your backend) and train:

```bash
python train.py --csv path/to/leads.csv
```

CSV columns should map to Lead fields, e.g. `email`, `phoneNumber`, `aadharNumber`, `name`, `pId`, `sourceId`, `createdAt`, `sourcesSeen`, `productsSeen`.

### With real conversion target

If you have conversion or qualification labels:

```bash
# CSV with a 'converted' column (0/1)
python train.py --csv leads.csv --target-column converted

# Or use leadScore from DB if already populated
python train.py --target-column leadScore
```

### Output

- `models/lead_score_model.txt` – LightGBM model (used by the Python scoring service in Step 2)
- `models/feature_config.json` – Feature names and metadata

## Default behaviour (no conversion data)

Without `--target-column`, the script uses the **rule-based score** as a proxy target. The model learns a similar pattern to your current `LeadScoringService`, and can later be retrained on real conversion data.

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
