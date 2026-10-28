"""Registry of every external dataset the pipeline uses, with provenance and licence.

Nothing here is invented: each entry points at a public download that was checked to
exist. ``sha256`` pins the exact file that produced the shipped models; a mismatch
(upstream changed the file) stops the download so results stay reproducible.
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Source:
    key: str
    title: str
    url: str
    filename: str
    licence: str
    citation: str
    homepage: str
    sha256: str | None
    synthetic: bool = False
    notes: str = ""


SOURCES: dict[str, Source] = {
    s.key: s
    for s in [
        Source(
            key="uci_sms_spam",
            title="SMS Spam Collection (UCI ML Repository, id 228)",
            url="https://archive.ics.uci.edu/static/public/228/sms+spam+collection.zip",
            filename="uci_sms_spam.zip",
            licence="CC BY 4.0",
            citation="Almeida, T. & Hidalgo, J. (2011). SMS Spam Collection. UCI Machine Learning Repository. https://doi.org/10.24432/C5CC84",
            homepage="https://archive.ics.uci.edu/dataset/228/sms+spam+collection",
            sha256="1587ea43e58e82b14ff1f5425c88e17f8496bfcdb67a583dbff9eefaf9963ce3",
            notes="5,574 English messages (mostly UK/Singapore, collected ~2011). 'spam' includes premium-rate prize scams and marketing.",
        ),
        Source(
            key="mendeley_smishing",
            title="SMS Phishing Dataset for Machine Learning and Pattern Recognition (Mendeley Data, v1)",
            url="https://data.mendeley.com/public-files/datasets/f45bkkt8pr/files/edb361de-918d-469f-9106-e84823830665/file_downloaded",
            filename="mendeley_smishing.zip",
            licence="CC BY 4.0",
            citation="Mishra, S. & Soni, D. (2022). SMS Phishing Dataset for Machine Learning and Pattern Recognition. Mendeley Data, V1. https://doi.org/10.17632/f45bkkt8pr.1",
            homepage="https://data.mendeley.com/datasets/f45bkkt8pr/1",
            sha256=None,
            notes="5,971 messages labelled ham / spam / smishing; text was OCR'd from screenshots, so some noise.",
        ),
        Source(
            key="phiusiil_url",
            title="PhiUSIIL Phishing URL Dataset (UCI ML Repository, id 967)",
            url="https://archive.ics.uci.edu/static/public/967/phiusiil+phishing+url+dataset.zip",
            filename="phiusiil.zip",
            licence="CC BY 4.0",
            citation="Prasad, A. & Chandra, S. (2024). PhiUSIIL: A diverse security profile empowered phishing URL detection framework based on similarity index and incremental learning. Computers & Security 136. https://doi.org/10.24432/C5HP4Q",
            homepage="https://archive.ics.uci.edu/dataset/967/phiusiil+phishing+url+dataset",
            sha256=None,
            notes="235,795 URLs. Only the URL string is used (no page features), so the model can run without visiting a link.",
        ),
        Source(
            key="indian_scam_synthetic",
            title="Indian Scam SMS (synthetic, audited) — Hugging Face Ridham115/indian-scam-sms-synthetic-audited",
            url="https://huggingface.co/datasets/Ridham115/indian-scam-sms-synthetic-audited/resolve/main/data/synthetic_audited.csv",
            filename="indian_scam_synthetic.csv",
            licence="CC BY 4.0",
            citation="Ridham115 (2026). Indian Scam SMS (synthetic, audited). Hugging Face dataset.",
            homepage="https://huggingface.co/datasets/Ridham115/indian-scam-sms-synthetic-audited",
            sha256=None,
            synthetic=True,
            notes="LLM-written Indian-context scam and genuine look-alike messages. Used for training only; never for reported real-world metrics.",
        ),
    ]
}
