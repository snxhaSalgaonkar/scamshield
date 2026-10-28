# Link / domain model (Model D): mentor brief

## One slide

**Title: Link model: is this domain phishing? (name only, never opens the link)**

| | |
|---|---|
| **Input** | The registrable domain only, e.g. `sbi-kyc-update.top` |
| **Features** | TLD · 3–5 character chunks of the name · length / hyphen / digit buckets · punycode · IP flag |
| **Model** | TF-IDF + logistic regression, runs offline on the phone (weights exported as `url_model.json`) |
| **Data** | PhiUSIIL (UCI): 235,795 URLs → 171,478 unique domains; train/val/test = 70/15/15 with no domain in two splits |
| **Thresholds** | Chosen on validation only: warning 0.33, high 0.80 |

| Held-out test (25,721 domains) | Warning | High |
|---|---|---|
| Precision | 64.7% | **97.6%** |
| Recall | 58.5% | 24.0% |
| False-positive rate | 10.7% | **0.2%** |

**Takeaway:** very few false alarms at the high threshold, but it misses about 3 in 4 phishing domains, so it is supporting evidence, not a verdict.
**Safety net:** Python and Kotlin must produce identical features and probabilities (golden-file parity test, tolerance 1e-9).

**Known limits:** domain-only (compromised or path-based phishing invisible) · real banks can score high (`onlinesbi.sbi` 0.63) · approximate suffix list · dataset base rate ≠ real SMS.

## Speaking script (about 90 seconds)

"Model D checks whether a link's domain looks like phishing. It never opens the link, so nothing leaves the phone and it can't tip off the scammer.

We reduce the link to its domain, because in this dataset legitimate URLs have no paths or subdomains and a full-URL model would just learn that artefact. From the domain name we build features: the ending, overlapping three-to-five-letter chunks, and the length, hyphen and digit counts. A logistic regression on those, trained on about 171 thousand unique domains, gives a phishing probability.

The split is by domain, so the test set contains domains the model has never seen, and the thresholds are tuned on validation only, not on test.

At the strict threshold it's 97.6 percent precise with only 0.2 percent false alarms, but it catches 24 percent of phishing domains. At the looser warning threshold it catches 58 percent but wrongly flags about 1 in 10 safe domains. So we use it as supporting evidence next to the other layers, not on its own.

Weights are exported to JSON and the Android app re-implements the same maths in Kotlin. A parity test runs the same URLs through both and requires identical results, so what we evaluated is what ships.

Honest limits: it only sees the domain, so a phishing page on a hacked legitimate site is invisible, and some real bank domains score high. Next steps would be adding allowlisting for known bank domains and re-measuring on real SMS links."

## Q&A

- **Why logistic regression?** Tiny, fast, offline, and every score can be explained by which chunks pushed it up.
- **Why not fetch the page?** Privacy, speed, and fetching can alert the scammer.
- **Test leakage?** No: unique domains per split, thresholds chosen on validation.
- **Parity?** `MlParityTest.kt` + `golden_url.json`.
- **Fix before the meeting:** `train_url.py` says the dataset is "~43% phishing after dedup", but the report's counts give 25% (42,904 of 171,478). The 43% is the legitimate share of the raw data. Correct the text and re-run training.
