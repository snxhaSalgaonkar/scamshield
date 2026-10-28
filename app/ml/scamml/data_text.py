"""Loads, cleans, deduplicates and splits the message datasets.

Real data (UCI SMS Spam Collection, Mendeley SMS Phishing) is the only data used for the
reported metrics. Synthetic data (the LLM-written Indian set and the hand-written seed
set) is added to training only, and scored separately as a "synthetic stress test".

Leakage control: messages are grouped by their normalised form (links, numbers, phone
numbers and amounts replaced by placeholders), so template variants of the same scam
land in one group; one row per group is kept, and groups with conflicting labels are
dropped.
"""
from __future__ import annotations

import io
import re
import zipfile
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import pandas as pd

from .sources import SOURCES
from .textfeat import normalize, tokens

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "data" / "raw"
HANDWRITTEN = ROOT / "data" / "handwritten" / "seed_messages.csv"
SEED = 20261028

CATEGORIES = [
    "CREDENTIAL_THEFT",      # OTP, PIN, password, card or KYC details
    "IMPERSONATION_THREAT",  # police / court / agency impersonation, arrest or legal threats
    "PAYMENT_FRAUD",         # UPI / bank transfer, collect requests, urgent money requests, fake bills
    "REMOTE_ACCESS",         # install screen-sharing or APK
    "PRIZE_LURE",            # lottery, prize, job or reward bait
    "PHISHING_LINK",         # link-led lure that fits none of the above
    "OTHER_SPAM",            # unsolicited marketing / other
]

# Weak labelling functions, applied in priority order to normalised text of malicious
# messages that have no category from their source. These are labelling heuristics for
# training data only; the app never runs them. Agreement with them is reported as
# "weak-label agreement", not accuracy.
_LF = [
    ("IMPERSONATION_THREAT", r"\b(police|cbi|court|arrest\w*|warrant|fir|customs|narcotics|cyber ?crime|cyber cell|"
                             r"legal action|investigation|officer|summons|ncb|enforcement directorate|giraftari)\b"),
    ("REMOTE_ACCESS", r"\b(anydesk|teamviewer|quicksupport|rustdesk|screen ?shar\w*|apk|remote access)\b"),
    ("CREDENTIAL_THEFT", r"\b(otp|pin|password|passcode|cvv|kyc|verify your (account|details)|login|log in|"
                         r"account (is |has been )?(blocked|suspended|locked)|card (details|number)|netbanking|security code)\b"),
    ("PAYMENT_FRAUD", r"\b(upi|gpay|paytm|phonepe|collect request|transfer|refund|bill|loan|emi|deposit|"
                      r"send (me )?money|pay (now|immediately|today))\b"),
    ("PRIZE_LURE", r"\b(won|winner|prize|lottery|claim|reward|award|congratulations|free|cash|bonus|job|"
                   r"earn|selected|guaranteed|voucher|gift)\b"),
    ("PHISHING_LINK", r"\bzurlz\b"),
]
_LF = [(c, re.compile(p)) for c, p in _LF]

_SYNTH_TOPIC = {
    "fake_kyc": "CREDENTIAL_THEFT", "fake_bank_otp": "CREDENTIAL_THEFT", "lottery": "PRIZE_LURE",
    "job_offer": "PRIZE_LURE", "loan_offer": "PAYMENT_FRAUD", "electricity_bill": "PAYMENT_FRAUD",
    "courier_customs": "IMPERSONATION_THREAT",
}


def weak_category(text: str) -> str:
    n = normalize(text)
    for cat, rx in _LF:
        if rx.search(n):
            return cat
    return "OTHER_SPAM"


def _require(key: str) -> Path:
    path = RAW / SOURCES[key].filename
    if not path.exists():
        raise FileNotFoundError(f"Missing dataset '{key}' ({path}). Run: python -m scamml.download {key}")
    return path


def load_uci() -> pd.DataFrame:
    with zipfile.ZipFile(_require("uci_sms_spam")) as z:
        raw = z.read("SMSSpamCollection").decode("utf-8", errors="replace")
    rows = [line.split("\t", 1) for line in raw.splitlines() if "\t" in line]
    df = pd.DataFrame(rows, columns=["raw_label", "text"])
    df["malicious"] = (df.raw_label == "spam").astype(int)
    df["source"] = "uci_sms_spam"
    return df


def _fix_mojibake(s: str) -> str:
    try:
        return s.encode("cp1252").decode("utf-8")
    except (UnicodeEncodeError, UnicodeDecodeError):
        return s


def load_mendeley() -> pd.DataFrame:
    with zipfile.ZipFile(_require("mendeley_smishing")) as z:
        df = pd.read_csv(io.BytesIO(z.read("Dataset_5971.csv")), encoding="cp1252", encoding_errors="replace")
    df = df.rename(columns={"TEXT": "text", "LABEL": "raw_label"})[["raw_label", "text"]].dropna()
    df["text"] = df.text.astype(str).map(_fix_mojibake).str.strip()
    df["raw_label"] = df.raw_label.str.lower().str.strip()
    df["malicious"] = df.raw_label.isin(["spam", "smishing"]).astype(int)
    df["source"] = "mendeley_smishing"
    return df


def load_indian_synthetic() -> pd.DataFrame:
    df = pd.read_csv(_require("indian_scam_synthetic"))
    out = pd.DataFrame({"text": df.text.astype(str), "raw_label": df.label})
    out["malicious"] = (df.label == "scam").astype(int)
    out["category"] = [(_SYNTH_TOPIC.get(t) if lab == "scam" else None) for t, lab in zip(df.scam_type, df.label)]
    out["source"] = "indian_scam_synthetic"
    return out


def load_handwritten() -> pd.DataFrame:
    df = pd.read_csv(HANDWRITTEN)
    out = pd.DataFrame({"text": df.text.astype(str), "raw_label": df.label})
    out["malicious"] = (df.label == "scam").astype(int)
    out["category"] = df.category.where(df.label == "scam", None)
    out["source"] = "handwritten_seed"
    return out


def dedupe(df: pd.DataFrame) -> tuple[pd.DataFrame, dict]:
    df = df[df.text.str.strip().str.len() > 0].copy()
    df["group"] = df.text.map(lambda t: " ".join(tokens(normalize(t))))
    labels = df.groupby("group").malicious.nunique()
    conflicting = set(labels[labels > 1].index)
    before = len(df)
    df = df[~df.group.isin(conflicting)].drop_duplicates("group").reset_index(drop=True)
    return df, {"rows_in": before, "conflicting_groups_dropped": len(conflicting), "rows_out": len(df)}


@dataclass
class TextSplits:
    train: pd.DataFrame
    val: pd.DataFrame
    test: pd.DataFrame
    synth_train: pd.DataFrame
    synth_holdout: pd.DataFrame
    stats: dict


def _stratified(df: pd.DataFrame, fracs: tuple[float, ...], seed: int) -> list[pd.DataFrame]:
    rng = np.random.default_rng(seed)
    parts = [[] for _ in fracs]
    for _, g in df.groupby("malicious"):
        idx = rng.permutation(g.index.values)
        cuts = np.cumsum([int(round(f * len(idx))) for f in fracs[:-1]])
        for i, chunk in enumerate(np.split(idx, cuts)):
            parts[i].append(chunk)
    return [df.loc[np.concatenate(p)].sample(frac=1, random_state=seed).reset_index(drop=True) for p in parts]


def build_splits(use_synthetic: bool = True, seed: int = SEED) -> TextSplits:
    real, real_stats = dedupe(pd.concat([load_uci(), load_mendeley()], ignore_index=True))
    real["synthetic"] = False
    real["category"] = [weak_category(t) if m else None for t, m in zip(real.text, real.malicious)]
    train, val, test = _stratified(real, (0.70, 0.15, 0.15), seed)

    stats = {"real": real_stats | {"malicious": int(real.malicious.sum())}}
    synth_train = synth_holdout = real.iloc[0:0]
    if use_synthetic:
        synth = pd.concat([load_indian_synthetic(), load_handwritten()], ignore_index=True)
        synth, s_stats = dedupe(synth)
        synth = synth[~synth.group.isin(set(real.group))]
        synth["synthetic"] = True
        synth["category"] = [
            (c if isinstance(c, str) else weak_category(t)) if m else None
            for t, m, c in zip(synth.text, synth.malicious, synth.category)
        ]
        hand = synth[synth.source == "handwritten_seed"]
        llm_train, llm_holdout = _stratified(synth[synth.source != "handwritten_seed"], (0.8, 0.2), seed)
        synth_train = pd.concat([llm_train, hand], ignore_index=True)
        synth_holdout = llm_holdout
        stats["synthetic"] = s_stats | {"train": len(synth_train), "holdout": len(synth_holdout)}
    for name, part in (("train", train), ("val", val), ("test", test)):
        stats[name] = {"n": len(part), "malicious": int(part.malicious.sum()),
                       "by_source": part.source.value_counts().to_dict()}
    return TextSplits(train, val, test, synth_train, synth_holdout, stats)
