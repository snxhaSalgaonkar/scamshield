"""Text normalisation and feature extraction for the message classifier.

This is the specification the Android app reimplements in
app/src/main/java/com/scamguard/app/ml/TextFeaturizer.kt. Everything is deliberately
portable: regexes use explicit ASCII classes (no \\w, \\b, \\s or \\d, whose meaning
differs between Python, java.util.regex and Android's ICU engine), whitespace is a fixed
list, and tokens are runs of code points in Unicode categories L*, N* or M*.
Golden vectors produced by golden.py check that both sides agree.
"""
from __future__ import annotations

import re
import unicodedata

# Placeholders keep the fact that a message had a link, phone number or amount without
# letting the model memorise the specific value.
URL_TOKEN = "zurlz"
EMAIL_TOKEN = "zemailz"
PHONE_TOKEN = "zphonez"
MONEY_TOKEN = "zmoneyz"
NUM_TOKEN = "znumz"
PLACEHOLDERS = {URL_TOKEN: "a link", EMAIL_TOKEN: "an email address", PHONE_TOKEN: "a phone number",
                MONEY_TOKEN: "a money amount", NUM_TOKEN: "a number"}

WHITESPACE = "\t\n\r\x0b\x0c\x1c\x1d\x1e\x1f\x85\xa0                　"
_WS_RE = re.compile("[" + re.escape(WHITESPACE) + "]")

TLDS = ("com|in|net|org|info|xyz|top|ly|co|me|io|app|site|online|click|link|live|tk|ml|ga|cf|gq|"
        "biz|us|uk|cc|ru|cn|shop|store|vip|icu|buzz|club|pw|win|bid|loan|work|gov|edu|bank|money")

URL_RE = re.compile(
    r"(?:[a-z][a-z0-9+.-]*://|www\.)[^ ]+"
    r"|(?<![a-z0-9@._-])[a-z0-9][a-z0-9-]*(?:\.[a-z0-9-]+)*\.(?:" + TLDS + r")(?![a-z0-9-])(?:/[^ ]*)?"
)
EMAIL_RE = re.compile(r"[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}")
MONEY_RE = re.compile(r"(?:rs\.?|inr|usd|eur|gbp|₹|£|\$|€) ?[0-9][0-9,]*(?:\.[0-9]+)?")
PHONE_RE = re.compile(r"\+?[0-9][0-9 -]{7,}[0-9]")
NUM_RE = re.compile(r"[0-9]+")

CHAR_NGRAMS = (3, 5)
MAX_TOKEN_CHARS = 30


def normalize(text: str) -> str:
    """NFKC, lower-case, fixed whitespace set, then placeholders for links, numbers etc."""
    t = unicodedata.normalize("NFKC", text or "").lower()
    t = _WS_RE.sub(" ", t)
    t = URL_RE.sub(" " + URL_TOKEN + " ", t)
    t = EMAIL_RE.sub(" " + EMAIL_TOKEN + " ", t)
    t = MONEY_RE.sub(" " + MONEY_TOKEN + " ", t)
    t = PHONE_RE.sub(" " + PHONE_TOKEN + " ", t)
    t = NUM_RE.sub(" " + NUM_TOKEN + " ", t)
    return t


def is_token_char(ch: str) -> bool:
    return unicodedata.category(ch)[0] in ("L", "N", "M")


def tokens(normalized: str) -> list[str]:
    out, cur = [], []
    for ch in normalized:
        if is_token_char(ch):
            cur.append(ch)
        elif cur:
            out.append("".join(cur))
            cur = []
    if cur:
        out.append("".join(cur))
    return out


def char_ngrams(token: str, lo: int = CHAR_NGRAMS[0], hi: int = CHAR_NGRAMS[1]) -> list[str]:
    padded = " " + token[:MAX_TOKEN_CHARS] + " "
    grams = []
    for n in range(lo, hi + 1):
        for i in range(0, len(padded) - n + 1):
            grams.append("c:" + padded[i:i + n])
    return grams


def analyze(text: str) -> list[str]:
    """All features of one message, with repeats (counts matter for TF)."""
    toks = tokens(normalize(text))
    feats = ["w:" + t for t in toks]
    feats += ["b:" + a + " " + b for a, b in zip(toks, toks[1:])]
    for t in toks:
        feats += char_ngrams(t)
    return feats


def word_tokens(text: str) -> list[str]:
    return tokens(normalize(text))
