"""Trains Model D, the link classifier, on PhiUSIIL URL strings (no page content).

Usage: python -m scamml.train_url

Inputs are reduced to the registrable domain (see urlfeat.py for why). Rows are
deduplicated per registrable domain; domains seen with both labels are dropped. Because
every input is a distinct domain, a random split is also a domain-grouped split.
"""
from __future__ import annotations

import datetime as dt
import json
import warnings
import zipfile

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score

from . import urlfeat
from .artifacts import ASSETS, write_model, write_report
from .data_text import RAW, SEED, _stratified
from .linear import LinearRuntime, export_binary, export_vectorizer, make_vectorizer
from .metrics import binary_report, threshold_for_f1, threshold_for_precision
from .sources import SOURCES

warnings.filterwarnings("ignore", message="Unknown solver options")
FORMAT = "scamml-url/1"

# Spot checks only (not metrics): well-known legitimate Indian domains the app will meet.
SPOT_CHECKS = ["https://www.onlinesbi.sbi/", "https://netbanking.hdfcbank.com/netbanking/", "https://www.icicibank.com",
               "https://paytm.com", "https://www.phonepe.com", "https://www.incometax.gov.in", "https://uidai.gov.in",
               "http://sbi-kyc-update.top/login", "http://hdfc-netbanking-verify.xyz", "http://192.168.10.5/pay"]


def load() -> tuple[pd.DataFrame, dict]:
    path = RAW / SOURCES["phiusiil_url"].filename
    if not path.exists():
        raise FileNotFoundError(f"Missing {path}. Run: python -m scamml.download phiusiil_url")
    with zipfile.ZipFile(path) as z:
        df = pd.read_csv(z.open("PhiUSIIL_Phishing_URL_Dataset.csv"), usecols=["URL", "label"])
    df["malicious"] = 1 - df.label  # PhiUSIIL: label 1 = legitimate
    hosts = df.URL.map(urlfeat.host_of)
    df["domain"] = [urlfeat.registrable_domain(h) if h else None for h in hosts]
    n_in = len(df)
    df = df.dropna(subset=["domain"])
    labels = df.groupby("domain").malicious.nunique()
    conflicting = set(labels[labels > 1].index)
    df = df[~df.domain.isin(conflicting)].drop_duplicates("domain").reset_index(drop=True)
    df["text"] = df.domain
    return df, {"rows_in": n_in, "conflicting_domains_dropped": len(conflicting), "unique_domains": len(df),
                "malicious": int(df.malicious.sum())}


def main() -> dict:
    df, stats = load()
    train, val, test = _stratified(df, (0.70, 0.15, 0.15), SEED)
    vec = make_vectorizer(urlfeat.analyze_url, min_df=3, max_features=30000)
    xtr = vec.fit_transform(train.text)
    xva = vec.transform(val.text)
    best = None
    for c in (1.0, 4.0, 16.0):
        clf = LogisticRegression(C=c, max_iter=2000, random_state=SEED).fit(xtr, train.malicious)
        ap = average_precision_score(val.malicious, clf.predict_proba(xva)[:, 1])
        if best is None or ap > best[0]:
            best = (ap, c, clf)
    _, c_best, clf = best
    terms, idf, cols = export_vectorizer(vec)
    model = {"vocab": terms, "idf": idf, "binary": export_binary(clf, cols)}
    rt = LinearRuntime(model, urlfeat.analyze_url)

    p_val = np.array([rt.binary_proba(t) for t in val.text])
    t_warn = threshold_for_f1(val.malicious.values, p_val)
    t_high = max(threshold_for_precision(val.malicious.values, p_val, 0.98), t_warn)
    p_test = np.array([rt.binary_proba(t) for t in test.text])

    report = {
        "model": "url", "format": FORMAT, "trained_at": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "hyperparameters": {"C": c_best, "max_features": 30000, "min_df": 3,
                            "features": "registrable-domain char 3-5-grams, TLD, length/hyphen/digit buckets, punycode, IPv4"},
        "data": stats | {s: {"n": len(d), "malicious": int(d.malicious.sum())} for s, d in
                         (("train", train), ("val", val), ("test", test))},
        "thresholds": {"warning": round(t_warn, 4), "high": round(t_high, 4), "selected_on": "validation"},
        "test": {"at_warning_threshold": binary_report(test.malicious.values, p_test, t_warn),
                 "at_high_threshold": binary_report(test.malicious.values, p_test, t_high)},
        "base_rate_caveat": "PhiUSIIL is ~43% phishing after dedup; real links in SMS have a different base rate, "
                            "so precision in the field will differ.",
        "spot_checks_NOT_METRICS": {u: round(rt.binary_proba(u), 4) for u in SPOT_CHECKS},
    }
    version = "url-" + dt.date.today().strftime("%Y%m%d")
    write_model(ASSETS / "url_model.json", {
        "format": FORMAT, "version": version, "task": "link_phishing",
        **model, "thresholds": report["thresholds"],
        "two_level_suffixes": sorted(urlfeat.TWO_LEVEL_SUFFIXES),
        "experimental": {"binary": False},
        "provenance": {"datasets": ["phiusiil_url"], "evaluation": "held-out domains", "report": "ml/reports/url_model.md"},
        "metrics_summary": {k: report["test"]["at_warning_threshold"][k]
                            for k in ("precision", "recall", "f1", "pr_auc", "false_positive_rate", "n")},
    })
    write_report("url_model", report)
    return report


if __name__ == "__main__":
    r = main()
    print(json.dumps({"thresholds": r["thresholds"], "test": {k: r["test"]["at_warning_threshold"][k] for k in
                      ("precision", "recall", "f1", "pr_auc", "false_positive_rate")},
                      "spot": r["spot_checks_NOT_METRICS"], "data": r["data"]}, indent=1))
