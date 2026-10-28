"""Sparse TF-IDF + logistic-regression models: training helpers, JSON export and a
reference runtime that scores from the exported JSON exactly as LinearModel.kt does.

Evaluation numbers in the reports are computed with this runtime on the exported
artefact (rounded coefficients included), not with the in-memory sklearn object, so
the figures describe what actually ships.
"""
from __future__ import annotations

import math
from collections import Counter
from typing import Callable

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer

SIG = 7  # significant digits kept in exported floats


def make_vectorizer(analyzer: Callable[[str], list[str]], min_df: int, max_features: int) -> TfidfVectorizer:
    return TfidfVectorizer(
        analyzer=analyzer, lowercase=False, min_df=min_df, max_features=max_features,
        sublinear_tf=True, smooth_idf=True, norm="l2", dtype=np.float64,
    )


def r(x: float) -> float:
    return float(f"{x:.{SIG}g}")


def export_vectorizer(vec: TfidfVectorizer) -> tuple[list[str], list[float], np.ndarray]:
    """Vocabulary in index order, rounded idf, and the column order used."""
    vocab = sorted(vec.vocabulary_.items(), key=lambda kv: kv[1])
    terms = [t for t, _ in vocab]
    cols = np.array([i for _, i in vocab])
    idf = [r(v) for v in vec.idf_[cols]]
    return terms, idf, cols


def export_binary(clf, cols: np.ndarray) -> dict:
    return {"coef": [r(v) for v in clf.coef_[0][cols]], "intercept": r(clf.intercept_[0])}


def export_multinomial(clf, cols: np.ndarray, classes: list[str]) -> dict:
    coef, intercept = clf.coef_, clf.intercept_
    if coef.shape[0] == 1:  # two classes: sklearn keeps one row; softmax over [0, w] equals sigmoid(w)
        coef, intercept = np.vstack([np.zeros_like(coef[0]), coef[0]]), np.array([0.0, intercept[0]])
    order = [list(clf.classes_).index(c) for c in classes]
    return {
        "classes": classes,
        "coef": [[r(v) for v in coef[k][cols]] for k in order],
        "intercept": [r(intercept[k]) for k in order],
    }


class LinearRuntime:
    """Scores text from an exported model dict: TF-IDF features then sigmoid / softmax."""

    def __init__(self, model: dict, analyzer: Callable[[str], list[str]]):
        self.analyzer = analyzer
        self.index = {t: i for i, t in enumerate(model["vocab"])}
        self.idf = np.array(model["idf"], dtype=np.float64)
        self.binary = model.get("binary")
        self.category = model.get("category")
        if self.binary:
            self.bcoef = np.array(self.binary["coef"], dtype=np.float64)
        if self.category:
            self.ccoef = np.array(self.category["coef"], dtype=np.float64)
            self.cint = np.array(self.category["intercept"], dtype=np.float64)

    def features(self, text: str) -> dict[int, float]:
        counts = Counter(f for f in self.analyzer(text) if f in self.index)
        w = {self.index[f]: (1.0 + math.log(c)) * self.idf[self.index[f]] for f, c in counts.items()}
        norm = math.sqrt(sum(v * v for v in w.values()))
        return {i: v / norm for i, v in w.items()} if norm > 0 else {}

    def binary_logit(self, x: dict[int, float]) -> float:
        return self.binary["intercept"] + sum(self.bcoef[i] * v for i, v in x.items())

    def binary_proba(self, text: str) -> float:
        z = self.binary_logit(self.features(text))
        return 1.0 / (1.0 + math.exp(-z))

    def category_proba(self, text: str) -> list[float]:
        x = self.features(text)
        z = self.cint.copy()
        for i, v in x.items():
            z += self.ccoef[:, i] * v
        z -= z.max()
        e = np.exp(z)
        return list(e / e.sum())

    def contributions(self, text: str) -> list[tuple[str, float]]:
        x = self.features(text)
        terms = {i: t for t, i in self.index.items() if i in x}
        return sorted(((terms[i], self.bcoef[i] * v) for i, v in x.items()), key=lambda kv: -kv[1])
