"""Writes golden vectors that the Kotlin unit tests replay to prove the app's featurizers
and runtimes give the same outputs as the Python reference on the shipped artefacts.

Usage: python -m scamml.golden   (run after training; output goes to app/src/test/resources/ml)
"""
from __future__ import annotations

import json

import numpy as np

from . import textfeat, urlfeat
from .artifacts import ASSETS, GOLDEN
from .callfeat import FEATURES, generate
from .linear import LinearRuntime
from .trees import TreeRuntime

# Authored for this test; not taken from any dataset.
MESSAGES = [
    "",
    "   ",
    "Hi, are we meeting at 5?",
    "Your OTP for login is 482913. Do not share it with anyone.",
    "URGENT! Your SBI account is blocked. Update KYC now at http://sbi-kyc-update.top/verify or call +91 98765 43210",
    "This is Inspector Sharma from Cyber Crime. You are under digital arrest. Transfer Rs. 50,000 to the safe account.",
    "Install AnyDesk and share the code so we can process your refund of ₹2,499.50",
    "Congratulations!!! You have WON £1000 cash prize. Txt CLAIM to 80086 now",
    "आपका खाता बंद कर दिया जाएगा। तुरंत KYC अपडेट करें",
    "Aapka parcel customs mein ruka hai, fee bharo warna police case hoga",
    "Price drop on www.Example-Shop.com/deals?id=7 today",
    "Mail me at someone.name@example.org about the 3 tickets",
    "Ünïcödé ﬁ ligature and full-width ＯＴＰ １２３４ test",
    "zero​width and emoji 🎉🎉 party tonight",
    "Rs.500 debited from a/c XX1234 via UPI to merchant@okaxis. Avl bal Rs 12,000.00",
    "WIN WIN WIN " * 40,
]
URLS = [
    "http://sbi-kyc-update.top/login", "https://www.onlinesbi.sbi/", "HTTPS://NetBanking.HDFCBank.com/x?y=1",
    "user:pass@evil-site.xyz:8080/path", "192.168.1.10/pay", "http://xn--pypal-4ve.com", "www.co.uk",
    "https://a.b.example.co.in/q", "not a url", "", "bit.ly/abc123", "www2.paytm-cashback-offer.online/claim",
]


def main() -> None:
    GOLDEN.mkdir(parents=True, exist_ok=True)
    text_model = json.load(open(ASSETS / "text_model.json", encoding="utf-8"))
    rt = LinearRuntime(text_model, textfeat.analyze)
    text_cases = []
    for m in MESSAGES:
        x = rt.features(m)
        text_cases.append({
            "text": m,
            "normalized": textfeat.normalize(m),
            "tokens": textfeat.tokens(textfeat.normalize(m)),
            "feature_count": len(textfeat.analyze(m)),
            "nonzero": len(x),
            "binary": rt.binary_proba(m),
            "category": rt.category_proba(m),
            "top_terms": [t for t, _ in rt.contributions(m)[:5]],
        })
    (GOLDEN / "golden_text.json").write_text(json.dumps(text_cases, ensure_ascii=False, indent=1), encoding="utf-8")

    url_model = json.load(open(ASSETS / "url_model.json", encoding="utf-8"))
    urt = LinearRuntime(url_model, urlfeat.analyze_url)
    url_cases = []
    for u in URLS:
        host = urlfeat.host_of(u)
        url_cases.append({
            "url": u, "host": host, "domain": urlfeat.registrable_domain(host) if host else None,
            "features": urlfeat.analyze_url(u), "proba": urt.binary_proba(u) if host else None,
        })
    extract = [{"text": m, "urls": urlfeat.extract_urls(m)} for m in MESSAGES]
    (GOLDEN / "golden_url.json").write_text(json.dumps({"urls": url_cases, "extract": extract}, ensure_ascii=False, indent=1),
                                            encoding="utf-8")

    call_model = json.load(open(ASSETS / "call_model.json"))
    crt = TreeRuntime(call_model)
    rows = generate(40, seed=7)[FEATURES].to_numpy(dtype=np.float64).tolist()
    call_cases = [{"x": x, "proba": crt.proba(x).tolist(), "scam": crt.scam_probability(x),
                   "explain": [[n, d] for n, d in crt.explain(x)[:3]]} for x in rows]
    (GOLDEN / "golden_call.json").write_text(json.dumps({"features": FEATURES, "cases": call_cases}, indent=1))
    print(f"wrote {len(text_cases)} text, {len(url_cases)} url, {len(call_cases)} call cases to {GOLDEN}")


if __name__ == "__main__":
    main()
