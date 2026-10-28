# Scam Call Guard — ML pipeline

Trains, evaluates and exports the three on-device models the Android app runs. Every
model ships as a JSON artefact in `app/app/src/main/assets/models/` and is executed by a
small pure-Kotlin runtime in `com.scamguard.app.ml` (no TFLite/ONNX dependency, no
network, no API key). Golden vectors prove the Kotlin runtime reproduces this Python
reference to 1e-9.

| Model | Task | Algorithm | Training data | Status |
|---|---|---|---|---|
| **A. Message** (`text_model.json`) | Is an SMS spam/smishing? + scam category | Sublinear TF-IDF (word 1-2-grams, char 3-5-grams) → logistic regression; multinomial LR category head | UCI SMS Spam + Mendeley SMS Phishing (real) + Indian LLM-written set + hand-written seed (synthetic, train only) | Binary head: evaluated on real held-out data. **Category head: experimental** |
| **D. Link** (`url_model.json`) | Is a link's domain likely phishing? | TF-IDF over registrable-domain char n-grams + TLD/length/hyphen/digit buckets → logistic regression | PhiUSIIL URL dataset (real) | Evaluated on held-out domains; weak, used as supporting evidence |
| **B+C. Call risk** (`call_model.json`) | Scam probability and scam type of a call window | Gradient-boosted trees (7 classes) over 33 observable features incl. sequence features and Model A/D outputs | **Synthetic** (documented generator) | **Experimental** — no real labelled call data exists |

B (call context) and C (event patterns) are one model: they would use the same inputs
and the same labels, so splitting them would only duplicate data. Sequence features
(`otp_then_debit_5min`, `link_then_install`, SMS-model outputs during the call) carry
the "event pattern" part.

## Setup

```bash
cd ml
uv venv --python 3.12 .venv            # or: python3.12 -m venv .venv
uv pip install --python .venv/bin/python -r requirements.txt   # or .venv/bin/pip install -r requirements.txt
```

## Reproduce everything

```bash
.venv/bin/python -m scamml.download          # fetches 4 public datasets into data/raw/, checks SHA-256
.venv/bin/python -m scamml.train_text        # Model A  -> assets/models/text_model.json, reports/text_model.*
.venv/bin/python -m scamml.train_text --no-synthetic   # ablation only (not exported)
.venv/bin/python -m scamml.train_url         # Model D  -> assets/models/url_model.json, reports/url_model.*
.venv/bin/python -m scamml.train_call        # Model B+C -> assets/models/call_model.json, reports/call_model.*
.venv/bin/python -m scamml.golden            # golden vectors -> app/app/src/test/resources/ml/
.venv/bin/python -m pytest                   # 23 tests: preprocessing, labels, export round-trips, artefacts
```

Then from `app/`: `./gradlew testDebugUnitTest` replays the golden vectors in Kotlin.
Seeds are fixed (`SEED = 20261028`), so reruns give the same splits and models. Runtime
on a 12-core laptop: text ~1.5 min, URL ~1.3 min, call ~1 min.

## Data provenance

| Key | Source | Licence | Use |
|---|---|---|---|
| `uci_sms_spam` | [UCI SMS Spam Collection](https://archive.ics.uci.edu/dataset/228/sms+spam+collection), Almeida & Hidalgo 2011 | CC BY 4.0 | train/val/**test** |
| `mendeley_smishing` | [SMS Phishing Dataset](https://data.mendeley.com/datasets/f45bkkt8pr/1), Mishra & Soni 2022 | CC BY 4.0 | train/val/**test** |
| `phiusiil_url` | [PhiUSIIL Phishing URL Dataset](https://archive.ics.uci.edu/dataset/967/phiusiil+phishing+url+dataset), Prasad & Chandra 2024 | CC BY 4.0 | train/val/**test** |
| `indian_scam_synthetic` | [Ridham115/indian-scam-sms-synthetic-audited](https://huggingface.co/datasets/Ridham115/indian-scam-sms-synthetic-audited) (LLM-written, audited) | CC BY 4.0 | **train only** + separately reported "synthetic holdout" |
| `handwritten_seed` | `data/handwritten/seed_messages.csv`, written for this project (digital-arrest, remote-access and look-alike genuine messages) | project | **train only** |
| call generator | `scamml/callfeat.py` | project | call model (synthetic) |

Exact file hashes are pinned in `scamml/sources.py`; `data/raw/manifest.json` records what
was downloaded. `data/raw/` is not committed.

### Leakage and imbalance handling

- **Messages:** grouped by normalised token sequence (links, numbers, phones, amounts →
  placeholders) so template variants of one scam fall in one group; one row per group;
  groups with conflicting labels dropped. UCI and Mendeley overlap heavily: 11,545 rows →
  5,876 unique. Stratified 70/15/15 split. Synthetic rows that duplicate a real group are dropped.
- **URLs:** deduplicated per registrable domain (235,795 rows → 171,478 domains; 246
  conflicting domains dropped), so train/val/test never share a domain.
- **Imbalance:** no resampling. Thresholds are chosen on the validation split
  (F1-optimal / target precision) and PR-AUC is reported alongside precision/recall.

## Results (from `reports/`, computed on the exported artefacts)

### Model A — message classifier, real held-out test (n=882, 138 malicious)

| Threshold | Precision | Recall | F1 | FPR | PR-AUC | ROC-AUC | Brier |
|---|---|---|---|---|---|---|---|
| 0.394 (val F1-optimal; equals the 0.98-precision threshold) | 0.956 | 0.935 | 0.945 | 0.008 | 0.982 | 0.995 | 0.014 |

Confusion matrix: TN 738, FP 6, FN 9, TP 129. By source: UCI F1 0.955 (n=762), Mendeley F1 0.926 (n=120).

- **Ablation:** trained on real data only, the same test gives F1 0.957 / PR-AUC 0.986.
  Adding synthetic Indian data costs ~0.01 F1 on these UK/global messages; it is kept
  because the real test contains almost no Indian-context scams (digital arrest, UPI,
  KYC), which is exactly what the synthetic data covers. That trade-off is not measured
  on real Indian data, because none is publicly available.
- **Synthetic holdout (NOT real-world):** F1 0.911 on 316 held-out LLM-written messages.
- **Category head (experimental):** 90.6% agreement with heuristic weak labels on real test
  scams — agreement, not accuracy. Real test support for impersonation is 1 message and for
  remote-access 0; training support for remote-access is 11 hand-written messages.
- **Out-of-distribution:** messages with < 2 tokens or < 40% known words (1st percentile of
  validation coverage) get "cannot judge" instead of a score.

### Model D — link classifier, held-out domains (n=25,721, 25% phishing)

| Threshold | Precision | Recall | F1 | FPR | PR-AUC |
|---|---|---|---|---|---|
| 0.328 (val F1-optimal, "uncertain") | 0.647 | 0.585 | 0.614 | 0.107 | 0.715 |
| 0.805 (val precision ≥ 0.98, "likely phishing") | 0.976 | 0.240 | — | 0.002 | — |

This is deliberately weak. In PhiUSIIL every legitimate URL is `https://www.<domain>` with
no path, and 55% of phishing hosts have subdomains against 4% of legitimate ones, so a model
given the full URL learns those artefacts (and would flag genuine `netbanking.hdfcbank.com`
links). The model therefore sees **only the registrable domain**. It cannot catch
phishing on compromised legitimate domains or in paths. The app only calls a link
"likely phishing" above 0.805 and never calls a link safe.

### Model B+C — call risk, SYNTHETIC test (n=6,000)

Warning threshold 0.33 (val precision ≥ 0.80): precision 0.81, recall 0.74, PR-AUC 0.86.
Red flag 0.73 (val precision ≥ 0.95): precision 0.96, recall 0.60. Scam-type accuracy on
scams 0.53. **These numbers only show the model fits its generator.** The most important
feature is talk time (40% of importance), which reflects the generator's assumptions.

## Experimental call model: what would make it real

1. Collect consented, reviewed call-window feature vectors: the app's *Privacy and data →
   Export* writes them (no numbers, no text) with the user's feedback, marked `reviewed: false`.
2. A human reviewer labels each row with one of `scamml.callfeat.CLASSES` and saves a CSV with
   the 33 feature columns + `label`.
3. `python -m scamml.train_call --reviewed reviewed.csv` trains and exports a non-synthetic
   model; the artefact version then ends in `-reviewed` and the app drops the experimental tag.

User feedback is never used automatically: the loader refuses files without the reviewed
label column, and unreviewed exports are a different format.

## Layout

```
ml/
  scamml/sources.py      dataset registry (URLs, licences, pinned hashes)
  scamml/download.py     fetch + verify
  scamml/textfeat.py     text normalisation/featurisation (spec for TextFeaturizer.kt)
  scamml/urlfeat.py      URL parsing/featurisation (spec for UrlFeaturizer.kt)
  scamml/data_text.py    loading, dedup, splits, weak category labels
  scamml/linear.py       TF-IDF/LR export + reference runtime
  scamml/trees.py        gradient-boosting export + reference runtime + explanations
  scamml/callfeat.py     call features + synthetic generator
  scamml/train_*.py      training/evaluation/export per model
  scamml/golden.py       golden vectors for the Kotlin parity tests
  scamml/metrics.py      metrics, thresholds, calibration
  data/handwritten/      authored seed messages (synthetic)
  reports/               generated metrics (JSON + Markdown)
  tests/                 pytest suite
```
