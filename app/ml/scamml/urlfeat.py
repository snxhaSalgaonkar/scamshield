"""URL extraction and features for the link classifier. Reimplemented in UrlFeaturizer.kt.

Only the registrable domain is used. In PhiUSIIL every legitimate URL is
"https://www.<domain>" with no path and rarely a subdomain, so scheme, "www", path and
subdomain would let a model separate the classes by dataset artefact rather than by
anything that generalises. The cost: phishing pages on compromised legitimate domains
or hidden in a path cannot be recognised. Links are never fetched.
"""
from __future__ import annotations

import re
import unicodedata

from .textfeat import URL_RE, _WS_RE

# Approximate public-suffix handling: common two-level suffixes only. Documented limitation.
TWO_LEVEL_SUFFIXES = frozenset("""
co.uk org.uk ac.uk gov.uk me.uk ltd.uk plc.uk net.uk
co.in org.in net.in gov.in ac.in nic.in res.in firm.in gen.in ind.in edu.in
com.au net.au org.au edu.au gov.au co.nz org.nz govt.nz
co.jp ne.jp or.jp ac.jp go.jp com.cn net.cn org.cn gov.cn com.hk com.tw com.sg com.my
com.br com.mx com.ar com.co com.pe com.tr com.pk com.ng com.eg com.sa com.bd com.np
co.za co.kr co.id co.th co.il com.ua com.vn com.ph
""".split())

_SCHEME_RE = re.compile(r"^[a-z][a-z0-9+.-]*://")
_WWW_RE = re.compile(r"^www[0-9]*\.")
_IPV4_RE = re.compile(r"^[0-9]{1,3}(?:\.[0-9]{1,3}){3}$")
_HOST_CHARS_RE = re.compile(r"^[a-z0-9.-]+$")


def extract_urls(text: str) -> list[str]:
    t = _WS_RE.sub(" ", unicodedata.normalize("NFKC", text or "").lower())
    return [m.group(0).rstrip(".,;:!?)]}'\"") for m in URL_RE.finditer(t)]


def host_of(url: str) -> str | None:
    s = _WS_RE.sub(" ", unicodedata.normalize("NFKC", url or "").lower()).strip()
    s = _SCHEME_RE.sub("", s)
    for sep in "/?#":
        s = s.split(sep, 1)[0]
    s = s.rsplit("@", 1)[-1]
    s = s.split(":", 1)[0].strip(".")
    s = _WWW_RE.sub("", s)
    if not s or not _HOST_CHARS_RE.match(s) or "." not in s:
        return None
    return s


def registrable_domain(host: str) -> str:
    if _IPV4_RE.match(host):
        return host
    labels = [x for x in host.split(".") if x]
    if len(labels) >= 3 and ".".join(labels[-2:]) in TWO_LEVEL_SUFFIXES:
        return ".".join(labels[-3:])
    return ".".join(labels[-2:])


def _bucket(n: int, edges: tuple[int, ...]) -> int:
    for i, e in enumerate(edges):
        if n <= e:
            return i
    return len(edges)


def analyze_url(url: str) -> list[str]:
    host = host_of(url)
    if host is None:
        return []
    if _IPV4_RE.match(host):
        return ["ip"]
    dom = registrable_domain(host)
    labels = dom.split(".")
    name = labels[0]
    feats = ["t:" + labels[-1]]
    if len(labels) == 3:
        feats.append("s2:" + ".".join(labels[1:]))
    padded = "^" + name + "$"
    for n in range(3, 6):
        for i in range(0, len(padded) - n + 1):
            feats.append("c:" + padded[i:i + n])
    feats.append("len:%d" % _bucket(len(name), (4, 7, 10, 14, 20)))
    feats.append("hy:%d" % min(name.count("-"), 3))
    feats.append("dg:%d" % _bucket(sum(ch.isascii() and ch.isdigit() for ch in name), (0, 2, 5)))
    if name.startswith("xn--"):
        feats.append("puny")
    return feats
