"""Evaluation helpers: binary metrics, threshold selection, calibration."""
from __future__ import annotations

import numpy as np
from sklearn.metrics import (average_precision_score, brier_score_loss, confusion_matrix,
                             f1_score, precision_recall_curve, precision_score, recall_score,
                             roc_auc_score)


def binary_report(y: np.ndarray, p: np.ndarray, threshold: float) -> dict:
    yhat = (p >= threshold).astype(int)
    tn, fp, fn, tp = confusion_matrix(y, yhat, labels=[0, 1]).ravel()
    return {
        "n": int(len(y)),
        "positives": int(y.sum()),
        "threshold": round(float(threshold), 4),
        "precision": round(float(precision_score(y, yhat, zero_division=0)), 4),
        "recall": round(float(recall_score(y, yhat, zero_division=0)), 4),
        "f1": round(float(f1_score(y, yhat, zero_division=0)), 4),
        "false_positive_rate": round(float(fp / max(fp + tn, 1)), 4),
        "pr_auc": round(float(average_precision_score(y, p)), 4) if 0 < y.sum() < len(y) else None,
        "roc_auc": round(float(roc_auc_score(y, p)), 4) if 0 < y.sum() < len(y) else None,
        "brier": round(float(brier_score_loss(y, p)), 4),
        "confusion_matrix": {"tn": int(tn), "fp": int(fp), "fn": int(fn), "tp": int(tp)},
        "calibration": calibration_bins(y, p),
    }


def calibration_bins(y: np.ndarray, p: np.ndarray, bins: int = 10) -> list[dict]:
    edges = np.linspace(0, 1, bins + 1)
    out = []
    for lo, hi in zip(edges[:-1], edges[1:]):
        m = (p >= lo) & ((p < hi) if hi < 1 else (p <= hi))
        if m.sum():
            out.append({"range": [round(lo, 2), round(hi, 2)], "n": int(m.sum()),
                        "mean_predicted": round(float(p[m].mean()), 4), "observed_rate": round(float(y[m].mean()), 4)})
    return out


def threshold_for_f1(y: np.ndarray, p: np.ndarray) -> float:
    prec, rec, thr = precision_recall_curve(y, p)
    f1 = 2 * prec * rec / np.maximum(prec + rec, 1e-12)
    return float(thr[int(np.argmax(f1[:-1]))])


def threshold_for_precision(y: np.ndarray, p: np.ndarray, target: float) -> float:
    """Lowest threshold whose validation precision reaches [target] (most recall)."""
    prec, _, thr = precision_recall_curve(y, p)
    ok = np.where(prec[:-1] >= target)[0]
    return float(thr[ok[0]]) if len(ok) else float(thr[-1])
