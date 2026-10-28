"""Trains Model A, the message classifier: a binary "suspicious message" head (spam or
smishing vs legitimate) and an experimental scam-category head.

Usage: python -m scamml.train_text [--no-synthetic]

Writes the app artefact (assets/models/text_model.json) and reports/text_model.{json,md}.
Every reported number is computed by LinearRuntime on the exported JSON.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json

import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score

import warnings

from . import textfeat
from .artifacts import ASSETS, REPORTS, write_model, write_report
from .data_text import CATEGORIES, SEED, build_splits
from .linear import LinearRuntime, export_binary, export_multinomial, export_vectorizer, make_vectorizer
from .metrics import binary_report, threshold_for_f1, threshold_for_precision

warnings.filterwarnings("ignore", message="Unknown solver options")
FORMAT = "scamml-text/1"
C_GRID = (0.5, 1.0, 2.0, 4.0, 8.0, 16.0, 32.0)


def coverage(rt: LinearRuntime, text: str) -> tuple[int, float]:
    toks = textfeat.word_tokens(text)
    if not toks:
        return 0, 0.0
    return len(toks), sum(("w:" + t) in rt.index for t in toks) / len(toks)


def fit(splits, use_synthetic: bool):
    train = splits.train
    if use_synthetic:
        train = train._append(splits.synth_train, ignore_index=True)
    vec = make_vectorizer(textfeat.analyze, min_df=2, max_features=20000)
    xtr = vec.fit_transform(train.text)
    xva = vec.transform(splits.val.text)

    best = None
    for c in C_GRID:
        clf = LogisticRegression(C=c, max_iter=3000, random_state=SEED).fit(xtr, train.malicious)
        ap = average_precision_score(splits.val.malicious, clf.predict_proba(xva)[:, 1])
        if best is None or ap > best[0]:
            best = (ap, c, clf)
    _, c_bin, clf = best

    mal = train[train.malicious == 1]
    val_mal = splits.val[splits.val.malicious == 1]
    best_cat = None
    for c in C_GRID:
        cat = LogisticRegression(C=c, max_iter=3000, random_state=SEED).fit(vec.transform(mal.text), mal.category)
        acc = float((cat.predict(vec.transform(val_mal.text)) == val_mal.category).mean())
        if best_cat is None or acc > best_cat[0]:
            best_cat = (acc, c, cat)
    return vec, clf, c_bin, best_cat[2], best_cat[1], train


def main(use_synthetic: bool) -> dict:
    splits = build_splits(use_synthetic=use_synthetic)
    vec, clf, c_bin, cat, c_cat, train = fit(splits, use_synthetic)
    terms, idf, cols = export_vectorizer(vec)
    classes = [c for c in CATEGORIES if c in set(cat.classes_)]
    model = {"vocab": terms, "idf": idf, "binary": export_binary(clf, cols),
             "category": export_multinomial(cat, cols, classes)}
    rt = LinearRuntime(model, textfeat.analyze)

    def probs(df):
        return np.array([rt.binary_proba(t) for t in df.text])

    p_val = probs(splits.val)
    t_warn = threshold_for_f1(splits.val.malicious.values, p_val)
    t_high = max(threshold_for_precision(splits.val.malicious.values, p_val, 0.98), t_warn)

    cov = [coverage(rt, t) for t in splits.val.text]
    min_cov = float(np.quantile([c for n, c in cov if n > 0], 0.01))
    ood = {"min_tokens": 2, "min_coverage": round(min_cov, 4)}

    p_test = probs(splits.test)
    y_test = splits.test.malicious.values
    report = {
        "model": "text", "format": FORMAT, "trained_at": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "use_synthetic_training_data": use_synthetic,
        "hyperparameters": {"binary_C": c_bin, "category_C": c_cat, "max_features": 20000, "min_df": 2,
                            "features": "word unigrams + bigrams + char 3-5-grams within tokens, sublinear TF-IDF, L2"},
        "data": splits.stats,
        "thresholds": {"warning": round(t_warn, 4), "high": round(t_high, 4), "selected_on": "validation (real data)"},
        "ood": ood,
        "test_real": {
            "at_warning_threshold": binary_report(y_test, p_test, t_warn),
            "at_high_threshold": binary_report(y_test, p_test, t_high),
            "by_source": {s: binary_report(y_test[m], p_test[m], t_warn)
                          for s in splits.test.source.unique() for m in [(splits.test.source == s).values]},
        },
    }
    if len(splits.synth_holdout):
        ps = probs(splits.synth_holdout)
        report["synthetic_holdout_NOT_REAL_WORLD"] = binary_report(splits.synth_holdout.malicious.values, ps, t_warn)

    mal = splits.test[splits.test.malicious == 1]
    pred = [classes[int(np.argmax(rt.category_proba(t)))] for t in mal.text]
    report["category_head_EXPERIMENTAL"] = {
        "classes": classes,
        "note": "Real messages have only weak (heuristic) category labels; this is agreement with those labels, not accuracy.",
        "weak_label_agreement_test_real": round(float(np.mean(np.array(pred) == mal.category.values)), 4),
        "train_support": train[train.malicious == 1].category.value_counts().to_dict(),
        "test_real_support": mal.category.value_counts().to_dict(),
    }
    if len(splits.synth_holdout):
        sm = splits.synth_holdout[splits.synth_holdout.malicious == 1]
        sp = [classes[int(np.argmax(rt.category_proba(t)))] for t in sm.text]
        report["category_head_EXPERIMENTAL"]["synthetic_holdout_agreement_NOT_REAL_WORLD"] = round(
            float(np.mean(np.array(sp) == sm.category.values)), 4)

    version = "text-" + dt.date.today().strftime("%Y%m%d") + ("" if use_synthetic else "-realonly")
    artefact = {
        "format": FORMAT, "version": version, "task": "message_scam_intent",
        "featurizer": {"char_ngrams": list(textfeat.CHAR_NGRAMS), "max_token_chars": textfeat.MAX_TOKEN_CHARS,
                       "tf": "sublinear", "norm": "l2"},
        **model,
        "thresholds": report["thresholds"], "ood": ood,
        "placeholders": textfeat.PLACEHOLDERS,
        "experimental": {"category": True, "binary": False},
        "provenance": {"datasets": ["uci_sms_spam", "mendeley_smishing"] +
                       (["indian_scam_synthetic", "handwritten_seed"] if use_synthetic else []),
                       "evaluation": "real held-out test split only", "report": "ml/reports/text_model.md"},
        "metrics_summary": {k: report["test_real"]["at_warning_threshold"][k]
                            for k in ("precision", "recall", "f1", "pr_auc", "false_positive_rate", "n")},
    }
    if use_synthetic:
        write_model(ASSETS / "text_model.json", artefact)
        write_report("text_model", report)
    else:
        (REPORTS / "text_model_realonly_ablation.json").write_text(json.dumps(report, indent=2))
    return report


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-synthetic", action="store_true", help="ablation: real data only, not exported to the app")
    args = ap.parse_args()
    r = main(use_synthetic=not args.no_synthetic)
    print(json.dumps({"thresholds": r["thresholds"], "test": {k: r["test_real"]["at_warning_threshold"][k] for k in
                                                              ("precision", "recall", "f1", "pr_auc", "false_positive_rate")}}, indent=2))
