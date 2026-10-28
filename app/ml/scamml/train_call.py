"""Trains the EXPERIMENTAL call-context model (Models B + C merged; they share inputs and
labels, so separate models would only duplicate the same data).

Usage:
  python -m scamml.train_call                     # synthetic data (default, experimental)
  python -m scamml.train_call --reviewed FILE.csv # real, reviewed feedback exports

Reviewed files must have one column per FEATURES entry plus "label" (one of CLASSES),
labelled by a human reviewer; unreviewed app feedback must not be used.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json

import numpy as np
import pandas as pd
from sklearn.ensemble import GradientBoostingClassifier

from .artifacts import ASSETS, write_model, write_report
from .callfeat import BASELINE, CLASSES, FEATURES, generate
from .data_text import SEED
from .metrics import binary_report, threshold_for_precision
from .trees import TreeRuntime, export_gb

FORMAT = "scamml-call/1"


def load_reviewed(path: str) -> pd.DataFrame:
    df = pd.read_csv(path)
    missing = [c for c in FEATURES + ["label"] if c not in df.columns]
    if missing:
        raise ValueError(f"{path}: missing columns {missing}")
    bad = set(df.label) - set(CLASSES)
    if bad:
        raise ValueError(f"{path}: unknown labels {sorted(bad)}")
    return df[FEATURES + ["label"]]


def main(reviewed: str | None, n: int = 40000) -> dict:
    synthetic = reviewed is None
    df = generate(n, SEED) if synthetic else load_reviewed(reviewed)
    rng = np.random.default_rng(SEED)
    idx = rng.permutation(len(df))
    tr, va, te = np.split(idx, [int(0.7 * len(df)), int(0.85 * len(df))])
    X = df[FEATURES].to_numpy(dtype=np.float64)
    y = df.label.to_numpy()

    gb = GradientBoostingClassifier(n_estimators=120, max_depth=3, learning_rate=0.1, subsample=0.8,
                                    random_state=SEED).fit(X[tr], y[tr])
    model = export_gb(gb, X, CLASSES) | {"features": FEATURES, "baseline": [BASELINE[f] for f in FEATURES]}
    rt = TreeRuntime(model)

    def scam_p(rows):
        return np.array([rt.scam_probability(list(X[i])) for i in rows])

    yb = (y != "LEGIT").astype(int)
    p_va = scam_p(va)
    t_warn = threshold_for_precision(yb[va], p_va, 0.80)
    t_red = max(threshold_for_precision(yb[va], p_va, 0.95), t_warn)
    p_te = scam_p(te)
    pred_type = [CLASSES[1 + int(np.argmax(rt.proba(list(X[i]))[1:]))] for i in te]
    scam_te = yb[te] == 1
    type_acc = float(np.mean(np.array(pred_type)[scam_te] == y[te][scam_te]))

    tag = "" if synthetic else "_REVIEWED"
    report = {
        "model": "call", "format": FORMAT, "trained_at": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "training_data": "SYNTHETIC (scamml/callfeat.py generator)" if synthetic else f"reviewed file {reviewed}",
        "WARNING": ("Synthetic data: these metrics measure how well the model fits the generator's assumptions. "
                    "They are NOT evidence of real-world detection accuracy.") if synthetic else "",
        "hyperparameters": {"n_estimators": 120, "max_depth": 3, "learning_rate": 0.1, "subsample": 0.8},
        "data": {"n": len(df), "train": len(tr), "val": len(va), "test": len(te),
                 "class_counts": df.label.value_counts().to_dict()},
        "thresholds": {"warning": round(t_warn, 4), "red_flag": round(t_red, 4),
                       "selected_on": "validation: warning at precision>=0.80, red flag at precision>=0.95"},
        f"test{tag or '_SYNTHETIC'}": {
            "warning": binary_report(yb[te], p_te, t_warn),
            "red_flag": binary_report(yb[te], p_te, t_red),
            "scam_type_accuracy_on_scams": round(type_acc, 4),
        },
        "feature_importance": dict(sorted(((f, round(float(v), 4)) for f, v in zip(FEATURES, gb.feature_importances_)),
                                          key=lambda kv: -kv[1])),
    }
    version = "call-" + dt.date.today().strftime("%Y%m%d") + ("-synthetic" if synthetic else "-reviewed")
    write_model(ASSETS / "call_model.json", {
        "format": FORMAT, "version": version, "task": "call_window_scam_risk",
        **model, "thresholds": report["thresholds"],
        "experimental": {"model": synthetic},
        "provenance": {"training_data": report["training_data"], "report": "ml/reports/call_model.md"},
        "metrics_summary": {"synthetic": synthetic} | {k: report[f"test{tag or '_SYNTHETIC'}"]["warning"][k]
                                                       for k in ("precision", "recall", "f1", "pr_auc", "n")},
    })
    write_report("call_model", report)
    return report


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--reviewed", help="CSV of human-reviewed, consented feature exports")
    args = ap.parse_args()
    r = main(args.reviewed)
    key = [k for k in r if k.startswith("test")][0]
    print(json.dumps({"thresholds": r["thresholds"], "test": {k: r[key]["warning"][k] for k in
                      ("precision", "recall", "f1", "pr_auc")}, "type_acc": r[key]["scam_type_accuracy_on_scams"]}, indent=1))
