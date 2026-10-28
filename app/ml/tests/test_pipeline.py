"""Tests for preprocessing, label validation, export round-trips and shipped artefacts.

Small synthetic records are used where a test only needs to exercise code; no metric in
the reports comes from them.
"""
from __future__ import annotations

import json

import numpy as np
import pandas as pd
import pytest
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.linear_model import LogisticRegression

from scamml import textfeat, urlfeat
from scamml.artifacts import ASSETS, GOLDEN
from scamml.callfeat import BASELINE, CLASSES, FEATURES, generate
from scamml.data_text import CATEGORIES, dedupe, weak_category
from scamml.linear import LinearRuntime, export_binary, export_multinomial, export_vectorizer, make_vectorizer
from scamml.train_call import load_reviewed
from scamml.trees import TreeRuntime, export_gb


# --- text preprocessing -------------------------------------------------------------

def test_normalize_replaces_sensitive_values_with_placeholders():
    n = textfeat.normalize("Pay Rs. 4,999 to http://x.top/a or call +91 98765 43210, code 1234, mail a@b.com")
    assert "4,999" not in n and "98765" not in n and "1234" not in n and "x.top" not in n and "a@b.com" not in n
    for tok in ("zmoneyz", "zurlz", "zphonez", "znumz", "zemailz"):
        assert tok in n


def test_tokens_keep_devanagari_marks_together():
    toks = textfeat.tokens(textfeat.normalize("आपका खाता बंद"))
    assert toks == ["आपका", "खाता", "बंद"]


def test_nfkc_and_fixed_whitespace():
    assert textfeat.tokens(textfeat.normalize("ＯＴＰ now ok")) == ["otp", "now", "ok"]


def test_analyze_is_deterministic_and_counts_repeats():
    a = textfeat.analyze("win win")
    assert a == textfeat.analyze("win win")
    assert a.count("w:win") == 2 and "b:win win" in a and "c: wi" in a


def test_empty_and_blank_input():
    assert textfeat.analyze("") == [] and textfeat.analyze("   ") == []


# --- url preprocessing --------------------------------------------------------------

@pytest.mark.parametrize("url,host,domain", [
    ("HTTPS://www.Example.com/path?q=1", "example.com", "example.com"),
    ("user:pw@login.evil.xyz:8080/x", "login.evil.xyz", "evil.xyz"),
    ("https://a.b.bank.co.in", "a.b.bank.co.in", "bank.co.in"),
    ("10.0.0.1/admin", "10.0.0.1", "10.0.0.1"),
])
def test_host_and_registrable_domain(url, host, domain):
    assert urlfeat.host_of(url) == host
    assert urlfeat.registrable_domain(host) == domain


def test_invalid_urls_have_no_features():
    for u in ("", "not a url", "http://", "localhost"):
        assert urlfeat.analyze_url(u) == []


def test_url_features_ignore_scheme_www_and_path():
    assert urlfeat.analyze_url("https://www.shop.com/a/b") == urlfeat.analyze_url("http://shop.com")


def test_extract_urls_does_not_take_email_domains():
    assert urlfeat.extract_urls("mail a.b@example.org now") == []
    assert urlfeat.extract_urls("visit bit.ly/x1.") == ["bit.ly/x1"]


# --- dataset handling and labels ----------------------------------------------------

def test_dedupe_drops_conflicts_and_template_duplicates():
    df = pd.DataFrame({
        "text": ["Call 0900111 now", "Call 0900222 now", "hello there", "Hello there!", "same", "same"],
        "malicious": [1, 1, 0, 1, 0, 0], "source": ["t"] * 6,
    })
    out, stats = dedupe(df)
    assert stats["conflicting_groups_dropped"] == 1  # "hello there" with both labels
    assert sorted(out.text) == ["Call 0900111 now", "same"]


def test_weak_categories_are_valid():
    assert weak_category("You are under digital arrest, police case filed") == "IMPERSONATION_THREAT"
    assert weak_category("install anydesk now") == "REMOTE_ACCESS"
    assert weak_category("share your otp") == "CREDENTIAL_THEFT"
    assert weak_category("lovely weather") == "OTHER_SPAM"
    assert {weak_category(t) for t in ("x", "won prize")} <= set(CATEGORIES)


def test_reviewed_call_data_is_validated(tmp_path):
    good = tmp_path / "good.csv"
    df = generate(20, seed=1)
    df.to_csv(good, index=False)
    assert len(load_reviewed(str(good))) == 20
    bad = tmp_path / "bad.csv"
    df.assign(label="NOT_A_CLASS").to_csv(bad, index=False)
    with pytest.raises(ValueError, match="unknown labels"):
        load_reviewed(str(bad))
    df.drop(columns=["talk_minutes"]).to_csv(bad, index=False)
    with pytest.raises(ValueError, match="missing columns"):
        load_reviewed(str(bad))


def test_generator_is_reproducible_and_valid():
    a, b = generate(300, seed=3), generate(300, seed=3)
    pd.testing.assert_frame_equal(a, b)
    assert list(a.columns) == FEATURES + ["label"]
    assert set(a.label) <= set(CLASSES)
    assert a[FEATURES].notna().all().all()
    assert (a.max_sms_scam_prob.between(0, 1)).all() and (a.max_url_prob.between(0, 1)).all()


# --- training, serialisation and runtime parity ---------------------------------------

def _toy_text():
    pos = ["win cash prize now", "claim your prize call now", "urgent account blocked verify otp", "free reward click link"] * 5
    neg = ["see you at lunch", "meeting moved to 4", "call me when free", "thanks for dinner"] * 5
    return pos + neg, np.array([1] * len(pos) + [0] * len(neg))


def test_linear_export_matches_sklearn():
    texts, y = _toy_text()
    vec = make_vectorizer(textfeat.analyze, min_df=1, max_features=500)
    X = vec.fit_transform(texts)
    clf = LogisticRegression(C=4.0, max_iter=1000).fit(X, y)
    cats = np.array(["PRIZE_LURE" if "prize" in t or "reward" in t else "CREDENTIAL_THEFT" for t in texts])
    cat = LogisticRegression(max_iter=1000).fit(X, cats)
    terms, idf, cols = export_vectorizer(vec)
    model = {"vocab": terms, "idf": idf, "binary": export_binary(clf, cols),
             "category": export_multinomial(cat, cols, ["CREDENTIAL_THEFT", "PRIZE_LURE"])}
    json.loads(json.dumps(model))  # serialisable
    rt = LinearRuntime(model, textfeat.analyze)
    probe = ["win a prize", "lunch at 4", "verify otp now", ""]
    expected = clf.predict_proba(vec.transform(probe))[:, 1]
    got = [rt.binary_proba(t) for t in probe]
    assert np.allclose(got, expected, atol=1e-5)
    exp_cat = cat.predict_proba(vec.transform(probe))
    order = [list(cat.classes_).index(c) for c in ["CREDENTIAL_THEFT", "PRIZE_LURE"]]
    assert np.allclose([rt.category_proba(t) for t in probe], exp_cat[:, order], atol=1e-5)


def test_tree_export_matches_sklearn():
    df = generate(1500, seed=5)
    X, y = df[FEATURES].to_numpy(dtype=np.float64), df.label.to_numpy()
    gb = GradientBoostingClassifier(n_estimators=15, max_depth=3, random_state=0).fit(X, y)
    present = [c for c in CLASSES if c in set(y)]
    model = export_gb(gb, X, present) | {"features": FEATURES, "baseline": [BASELINE[f] for f in FEATURES]}
    rt = TreeRuntime(json.loads(json.dumps(model)))
    order = [list(gb.classes_).index(c) for c in present]
    expected = gb.predict_proba(X[:200])[:, order]
    got = np.array([rt.proba(list(x)) for x in X[:200]])
    assert np.allclose(got, expected, atol=1e-5)


def test_explanations_only_cover_present_evidence():
    model = json.load(open(ASSETS / "call_model.json"))
    rt = TreeRuntime(model)
    x = [BASELINE[f] for f in FEATURES]
    assert rt.explain(x) == []
    x[FEATURES.index("forwarding_enabled")] = 1.0
    names = [n for n, _ in rt.explain(x)]
    assert names == ["forwarding_enabled"]


# --- shipped artefacts and golden vectors -------------------------------------------

@pytest.mark.parametrize("name,fmt", [("text_model.json", "scamml-text/1"), ("url_model.json", "scamml-url/1"),
                                      ("call_model.json", "scamml-call/1")])
def test_shipped_artefacts_are_complete(name, fmt):
    m = json.load(open(ASSETS / name, encoding="utf-8"))
    assert m["format"] == fmt and m["version"] and m["provenance"] and m["metrics_summary"] and m["thresholds"]
    if "vocab" in m:
        assert len(m["vocab"]) == len(m["idf"]) == len(m["binary"]["coef"])
        assert len(set(m["vocab"])) == len(m["vocab"])
    if name == "call_model.json":
        assert m["features"] == FEATURES and m["classes"][0] == "LEGIT"
        assert m["experimental"]["model"] is True or "reviewed" in m["version"]


def test_golden_vectors_match_shipped_artefacts():
    text = json.load(open(GOLDEN / "golden_text.json", encoding="utf-8"))
    rt = LinearRuntime(json.load(open(ASSETS / "text_model.json", encoding="utf-8")), textfeat.analyze)
    for case in text:
        assert textfeat.normalize(case["text"]) == case["normalized"]
        assert abs(rt.binary_proba(case["text"]) - case["binary"]) < 1e-12
    call = json.load(open(GOLDEN / "golden_call.json"))
    crt = TreeRuntime(json.load(open(ASSETS / "call_model.json")))
    for case in call["cases"]:
        assert abs(crt.scam_probability(case["x"]) - case["scam"]) < 1e-12
