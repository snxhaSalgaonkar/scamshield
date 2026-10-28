"""Gradient-boosted tree export and the reference runtime used for golden vectors.

Matches TreeEnsemble.kt: features are compared as float32 (as sklearn does), each class
sums its trees' leaf values (learning rate already applied) on top of its init score,
and probabilities are a softmax over classes.
"""
from __future__ import annotations

import numpy as np

from .linear import r


def export_gb(gb, X_init: np.ndarray, classes: list[str]) -> dict:
    """Exports a fitted multi-class GradientBoostingClassifier."""
    order = [list(gb.classes_).index(c) for c in classes]
    init = gb._raw_predict_init(X_init[:1])[0]
    trees = []
    for k in order:
        class_trees = []
        for est in gb.estimators_[:, k]:
            t = est.tree_
            class_trees.append({
                "feature": [int(v) for v in t.feature],
                "threshold": [float(np.float32(v)) if f >= 0 else 0.0 for v, f in zip(t.threshold, t.feature)],
                "left": [int(v) for v in t.children_left],
                "right": [int(v) for v in t.children_right],
                "value": [r(float(v) * gb.learning_rate) for v in t.value[:, 0, 0]],
            })
        trees.append(class_trees)
    return {"classes": classes, "init": [r(float(init[k])) for k in order], "trees": trees}


class TreeRuntime:
    def __init__(self, model: dict):
        self.m = model
        self.features = model["features"]

    def raw(self, x: list[float]) -> np.ndarray:
        xf = np.asarray(x, dtype=np.float32)
        out = np.array(self.m["init"], dtype=np.float64)
        for k, class_trees in enumerate(self.m["trees"]):
            for t in class_trees:
                node = 0
                while t["left"][node] != -1:
                    node = t["left"][node] if xf[t["feature"][node]] <= np.float32(t["threshold"][node]) else t["right"][node]
                out[k] += t["value"][node]
        return out

    def proba(self, x: list[float]) -> np.ndarray:
        z = self.raw(x)
        z -= z.max()
        e = np.exp(z)
        return e / e.sum()

    def scam_probability(self, x: list[float]) -> float:
        return float(1.0 - self.proba(x)[self.m["classes"].index("LEGIT")])

    def explain(self, x: list[float]) -> list[tuple[str, float]]:
        """Drop in scam probability when each present feature is reset to its baseline."""
        base = self.scam_probability(x)
        out = []
        for i, name in enumerate(self.features):
            b = self.m["baseline"][i]
            if x[i] != b:
                cf = list(x)
                cf[i] = b
                out.append((name, base - self.scam_probability(cf)))
        return sorted(out, key=lambda kv: -kv[1])
