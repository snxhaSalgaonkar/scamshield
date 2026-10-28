"""Model B+C: features of one call window, and the SYNTHETIC generator used to train the
experimental call-context model.

Why synthetic: no public dataset pairs phone-call metadata (unknown caller, OTP/debit
SMS timing, call forwarding, remote-access apps, ...) with scam labels. The generator
below encodes documented assumptions about how scam and legitimate unknown calls look.
A model trained on it can only reproduce those assumptions; its metrics measure fit to
the generator, NOT real-world accuracy. The same pipeline retrains on real, reviewed,
consented feedback exports (see train_call.py --reviewed) once such data exists.

Every feature is something the app can observe at inference time (CallFeatures.kt
builds the same vector). Nothing is derived from call audio.
"""
from __future__ import annotations

import numpy as np
import pandas as pd

FEATURES = [
    "is_hidden", "is_international", "number_unavailable", "calls_last_hour", "reported_number",
    "known_bank_number", "user_marked_safe", "user_reported_scam", "answered", "talk_minutes", "odd_hour",
    "otp_count", "otp_untrusted_sender", "debit_count", "debit_amount_ratio", "history_available",
    "first_time_payee", "balance_near_zero", "forwarding_enabled", "second_call_merged", "remote_app_opened",
    "sideloaded_install", "link_received", "link_then_install", "network_lost", "otp_then_debit_5min",
    "sms_analyzed", "max_sms_scam_prob", "sms_impersonation", "sms_credential", "sms_payment",
    "sms_remote_access", "max_url_prob",
]
CLASSES = ["LEGIT", "OTP_THEFT", "MONEY_TRANSFER_PRESSURE", "CALL_FORWARDING_HIJACK", "CALL_MERGE_FRAUD",
           "REMOTE_ACCESS_FRAUD", "DIGITAL_ARREST"]
PRIORS = [0.72, 0.06, 0.05, 0.03, 0.03, 0.05, 0.06]
# Value each feature takes when the evidence is absent, used for counterfactual explanations.
BASELINE = {f: 0.0 for f in FEATURES} | {"calls_last_hour": 1.0}

_SMS_CAT = {"DIGITAL_ARREST": "sms_impersonation", "OTP_THEFT": "sms_credential",
            "MONEY_TRANSFER_PRESSURE": "sms_payment", "REMOTE_ACCESS_FRAUD": "sms_remote_access",
            "CALL_FORWARDING_HIJACK": "sms_credential", "CALL_MERGE_FRAUD": "sms_credential"}

# Per-class probability of each event during the window (before permission masking).
_P = {
    #            otp   debit fwd    merge  remote sideload link  net
    "LEGIT":     (0.08, 0.05, 0.002, 0.01, 0.003, 0.005, 0.06, 0.02),
    "OTP_THEFT": (0.95, 0.45, 0.01, 0.02, 0.03, 0.02, 0.15, 0.03),
    "MONEY_TRANSFER_PRESSURE": (0.40, 0.85, 0.01, 0.02, 0.05, 0.03, 0.20, 0.03),
    "CALL_FORWARDING_HIJACK": (0.70, 0.30, 0.90, 0.03, 0.02, 0.02, 0.10, 0.05),
    "CALL_MERGE_FRAUD": (0.80, 0.30, 0.02, 0.90, 0.02, 0.02, 0.05, 0.03),
    "REMOTE_ACCESS_FRAUD": (0.40, 0.60, 0.01, 0.02, 0.85, 0.30, 0.50, 0.03),
    "DIGITAL_ARREST": (0.15, 0.50, 0.01, 0.03, 0.10, 0.03, 0.15, 0.05),
}
# Median talk time in minutes once answered (lognormal).
_TALK = {"LEGIT": 2.0, "OTP_THEFT": 6.0, "MONEY_TRANSFER_PRESSURE": 8.0, "CALL_FORWARDING_HIJACK": 5.0,
         "CALL_MERGE_FRAUD": 5.0, "REMOTE_ACCESS_FRAUD": 15.0, "DIGITAL_ARREST": 45.0}
_SCAM_SMS = {"LEGIT": 0.03, "OTP_THEFT": 0.15, "MONEY_TRANSFER_PRESSURE": 0.30, "CALL_FORWARDING_HIJACK": 0.10,
             "CALL_MERGE_FRAUD": 0.10, "REMOTE_ACCESS_FRAUD": 0.40, "DIGITAL_ARREST": 0.35}


def generate(n: int, seed: int) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    labels = rng.choice(len(CLASSES), size=n, p=PRIORS)
    rows = [_one(CLASSES[k], rng) for k in labels]
    df = pd.DataFrame(rows, columns=FEATURES)
    y = labels.copy()
    noise = rng.random(n) < 0.02  # label noise: some reported scams are wrong, some scams go unreported
    y[noise] = np.where(y[noise] == 0, rng.integers(1, len(CLASSES), noise.sum()), 0)
    df["label"] = [CLASSES[k] for k in y]
    return df


def _one(cls: str, rng: np.random.Generator) -> list[float]:
    scam = cls != "LEGIT"
    b = lambda p: float(rng.random() < p)  # noqa: E731
    f = dict.fromkeys(FEATURES, 0.0)

    f["number_unavailable"] = b(0.25)
    f["is_hidden"] = b(0.10 if cls == "DIGITAL_ARREST" else 0.08 if scam else 0.03)
    f["is_international"] = b(0.20 if cls == "DIGITAL_ARREST" else 0.12 if scam else 0.04)
    if not f["number_unavailable"]:
        r = rng.random()
        if scam:
            f["calls_last_hour"] = 1.0 if r < 0.6 else 2.0 if r < 0.8 else float(rng.integers(3, 8))
        else:
            f["calls_last_hour"] = 1.0 if r < 0.9 else 2.0 if r < 0.98 else 3.0
        f["reported_number"] = b(0.12 if scam else 0.005)
        f["known_bank_number"] = b(0.003 if scam else 0.03)
        f["user_marked_safe"] = b(0.002 if scam else 0.02)
        f["user_reported_scam"] = b(0.05 if scam else 0.002)
    else:
        f["calls_last_hour"] = 1.0
    f["odd_hour"] = b(0.12 if scam else 0.05)
    f["answered"] = b(0.9 if scam else 0.8)
    if f["answered"]:
        f["talk_minutes"] = float(min(240.0, rng.lognormal(np.log(_TALK[cls]), 0.8)))

    sms_ok, usage_ok, fwd_ok = rng.random() < 0.85, rng.random() < 0.40, rng.random() < 0.50
    p_otp, p_debit, p_fwd, p_merge, p_remote, p_side, p_link, p_net = _P[cls]
    if f["answered"]:
        if sms_ok:
            if rng.random() < p_otp:
                f["otp_count"] = float(min(5, 1 + rng.poisson(0.6 if scam else 0.2)))
                f["otp_untrusted_sender"] = b(0.10 if scam else 0.03)
            if rng.random() < p_debit:
                f["debit_count"] = float(min(5, 1 + rng.poisson(0.5 if scam else 0.1)))
                f["history_available"] = b(0.6)
                if f["history_available"]:
                    med = 10.0 if cls == "DIGITAL_ARREST" else 6.0 if scam else 1.0
                    f["debit_amount_ratio"] = float(min(50.0, rng.lognormal(np.log(med), 0.9)))
                f["first_time_payee"] = b(0.8 if scam else 0.3)
                f["balance_near_zero"] = b(0.3 if scam else 0.03)
            if f["otp_count"] and f["debit_count"]:
                f["otp_then_debit_5min"] = b(0.8 if scam else 0.3)
            f["link_received"] = b(p_link)
        f["forwarding_enabled"] = b(p_fwd) if fwd_ok else 0.0
        f["second_call_merged"] = b(p_merge)
        f["remote_app_opened"] = b(p_remote) if usage_ok else 0.0
        f["sideloaded_install"] = b(p_side)
        f["link_then_install"] = b(0.7) if (f["link_received"] and f["sideloaded_install"]) else 0.0
        f["network_lost"] = b(p_net)
        if sms_ok and (f["otp_count"] or f["debit_count"] or f["link_received"] or rng.random() < 0.3):
            f["sms_analyzed"] = 1.0
            if rng.random() < _SCAM_SMS[cls]:
                p = float(rng.beta(6, 2))
                f["max_sms_scam_prob"] = p
                cat = _SMS_CAT.get(cls) or str(rng.choice(["sms_payment", "sms_credential"]))
                for c in ("sms_impersonation", "sms_credential", "sms_payment", "sms_remote_access"):
                    f[c] = p * float(rng.uniform(0.5, 0.95) if c == cat else rng.uniform(0, 0.15))
            else:
                p = float(rng.beta(1, 15))
                f["max_sms_scam_prob"] = p
                for c in ("sms_impersonation", "sms_credential", "sms_payment", "sms_remote_access"):
                    f[c] = p * float(rng.uniform(0, 0.4))
        if f["link_received"]:
            f["max_url_prob"] = float(rng.beta(4, 3) if scam else rng.beta(2, 5))

    # Windows are scored while they are still open: train on partial views too.
    r = rng.random()
    if r < 0.15:
        for k in FEATURES[8:]:
            f[k] = 0.0
    elif r < 0.50:
        f["talk_minutes"] *= float(rng.uniform(0.1, 1.0))
        for k in FEATURES[11:]:
            if f[k] and rng.random() < 0.5:
                f[k] = BASELINE[k]
        if not (f["otp_count"] and f["debit_count"]):
            f["otp_then_debit_5min"] = 0.0
        if not (f["link_received"] and f["sideloaded_install"]):
            f["link_then_install"] = 0.0
    return [f[k] for k in FEATURES]
