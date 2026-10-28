"""Downloads the public datasets listed in sources.py into data/raw/.

Usage: python -m scamml.download [key ...]

A file whose SHA-256 does not match the pinned value is rejected. Missing sources are
reported, not faked: training scripts then stop with a message naming the missing file.
"""
from __future__ import annotations

import hashlib
import json
import sys
import urllib.request
from pathlib import Path

from .sources import SOURCES

RAW = Path(__file__).resolve().parent.parent / "data" / "raw"


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def fetch(key: str) -> Path:
    src = SOURCES[key]
    RAW.mkdir(parents=True, exist_ok=True)
    out = RAW / src.filename
    if not out.exists():
        tmp = out.with_suffix(out.suffix + ".part")
        req = urllib.request.Request(src.url, headers={"User-Agent": "scamshield-ml/1.0"})
        with urllib.request.urlopen(req, timeout=120) as r, tmp.open("wb") as f:
            while chunk := r.read(1 << 20):
                f.write(chunk)
        tmp.rename(out)
    digest = sha256(out)
    if src.sha256 and digest != src.sha256:
        out.unlink()
        raise RuntimeError(f"{key}: SHA-256 mismatch (got {digest}, pinned {src.sha256}); upstream file changed")
    return out


def main(keys: list[str]) -> int:
    manifest = {}
    failed = []
    for key in keys or list(SOURCES):
        try:
            path = fetch(key)
            manifest[key] = {"file": path.name, "sha256": sha256(path), "bytes": path.stat().st_size}
            print(f"ok   {key:24s} {path.name} {manifest[key]['sha256'][:12]}")
        except Exception as e:  # noqa: BLE001 - report every failure, keep going
            failed.append(key)
            print(f"FAIL {key:24s} {e}", file=sys.stderr)
    (RAW / "manifest.json").write_text(json.dumps(manifest, indent=2))
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
