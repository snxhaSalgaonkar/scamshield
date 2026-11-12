# ScamShield

[![Live Website](https://img.shields.io/badge/Website-Live%20on%20Vercel-0070F3?style=for-the-badge&logo=vercel)](https://scamsheild-security.vercel.app/)
[![Android App](https://img.shields.io/badge/Android-Kotlin%20%7C%20Compose-3DDC84?style=for-the-badge&logo=android)](app/)
[![Backend](https://img.shields.io/badge/Backend-Supabase%20Postgres-3ECF8E?style=for-the-badge&logo=supabase)](website/)
[![Feature Audit](https://img.shields.io/badge/Feature%20Audit-View%20Report-8B5CF6?style=for-the-badge)](FEATURE_AUDIT.md)

> **🌐 Live:** [https://scamsheild-security.vercel.app/](https://scamsheild-security.vercel.app/)
> **🔒 RakshaCall Police Console:** [https://scamsheild-security.vercel.app/login](https://scamsheild-security.vercel.app/login)

**ScamShield** is an end-to-end anti-fraud intelligence platform that detects phone scams — digital arrest, fake police/CBI calls, OTP theft, remote-access fraud (AnyDesk), and phishing links — **while they happen**, without ever compromising citizen privacy.

**Built in 24 hours during a Hackathon at NIT Goa (2026).**

---

## 🏛️ System Architecture

The platform has two sides: a citizen-facing Android app and a police-facing web console.

```
┌──────────────────────────────────────────────────────────┐
│              Citizen Android App  (app/)                  │
│  • On-Device ML: SMS spam, phishing URL, call risk        │
│  • Real-Time Call Event Windowing (GBT call model)        │
│  • Privacy-by-design: NO INTERNET permission              │
│  • Mid-call OTP/remote-desktop correlation (Layer 3)      │
└──────────────────────────┬───────────────────────────────┘
                           │  (Future: consented threat telemetry)
                           ▼
┌──────────────────────────────────────────────────────────┐
│          RakshaCall Authority Console  (website/)         │
│  • Next.js 16 + Supabase Realtime & RLS                   │
│  • Live feed of scam calls & spike detection               │
│  • Telecom circle analysis & number-series clusters        │
│  • Citizen landing page with APK download & QR code        │
│  • Live: https://scamsheild-security.vercel.app/           │
└──────────────────────────────────────────────────────────┘
```

### Citizen Android App (`app/`)

| Capability | Details |
|---|---|
| **Privacy by Design** | Has **no `INTERNET` permission**. All ML inference runs entirely on the device CPU. |
| **Zero Audio Surveillance** | Call audio is never tapped or recorded. No `RECORD_AUDIO` permission. |
| **Multi-Signal Correlation** | Cross-references OTP/debit SMS arrivals during calls, remote desktop apps (AnyDesk, TeamViewer, RustDesk), and call-forwarding status. |
| **On-Device ML** | **Text model**: SMS spam/smishing classifier (TF-IDF + logistic regression). **URL model**: Phishing domain evaluator. **Call model**: Gradient boosted trees scoring call risk windows. |
| **Demo Mode** | Built-in simulator runs invented events through the real ML pipeline; every result is tagged "Simulated". |

### RakshaCall Authority Console (`website/`)

| Capability | Details |
|---|---|
| **Live Incident Feed** | High-risk calls appear on police screens within 1–2 seconds via Supabase Realtime. |
| **Cluster & Gang Analysis** | Detects number-series rotation (e.g. `+91 98112-XXXXX`) to catch organized scam syndicates. |
| **Spike Detection** | Flags telecom circles where last-hour reports are 3× the 24h hourly average. |
| **Officer Authentication** | Supabase Auth with `authority` role enforcement. Non-authority accounts are rejected. |
| **Citizen Portal** | Public landing page at `/` with APK download, QR code, and project overview. |

---

## 🛡️ Three-Layer Detection

| Layer | Type | What It Does | Status |
|---|---|---|---|
| **L1** | **Content ML** | Detects scam phrases, urgency, impersonation, and phishing URLs on-device. | ✅ Implemented |
| **L2** | **Reputation** | Numbers flagged only when 3+ independent devices confirm fraud. | ⚠️ DB logic ready, app not wired (no internet) |
| **L3** | **Correlation** | Detects bank OTP arriving mid-call or screen-sharing app opened during a call. | ✅ Implemented |

---

## 📋 Feature Audit

We maintain an honest **[Feature Audit](FEATURE_AUDIT.md)** that documents:

| Category | Count |
|----------|-------|
| ✅ Fully implemented and running | **24 features** |
| ⚠️ Backend-ready but not wired end-to-end | **6 features** |
| ❌ Cannot implement (OS/platform limitation) | **3 features** |
| 🔮 Planned for next version | **10 features** |

👉 **[Read the full Feature Audit →](FEATURE_AUDIT.md)**

---

## 🚀 Deployment Guide

### A. Web Console & Backend (`website/`)

1. **Database & Auth (Supabase)**:
   - Create a project on [supabase.com](https://supabase.com).
   - In the SQL Editor, execute the migration files in order:
     - `001_schema.sql` → `002_rls.sql` → `003_triggers_rpcs.sql` → `004_seed.sql` → `005_authority_users.sql`
   - All files are in [`website/supabase/`](website/supabase/).
   - Enable **Anonymous Sign-ins** under Authentication → Providers.

2. **Web Dashboard (Vercel)**:
   - Import the GitHub repo, set root directory to `website`.
   - Add environment variables:

     | Variable | Value |
     |---|---|
     | `NEXT_PUBLIC_DATA_SOURCE` | `supabase` (use `mock` for demo without backend) |
     | `NEXT_PUBLIC_SUPABASE_URL` | Your Supabase project URL |
     | `NEXT_PUBLIC_SUPABASE_ANON_KEY` | Your Supabase anon key |
     | `SUPABASE_SERVICE_ROLE_KEY` | Your Supabase service role key |
     | `SIMULATOR_ENABLED` | `true` |
     | `LLM_PROVIDER` | `none` |

---

### B. Citizen Android App (`app/`)

**Prerequisites:**
- Android Studio Ladybug / Meerkat (or newer)
- JDK 17+ (JDK 21 or 25 verified)
- Android SDK Platform 35 (Android 15)

**Build APK:**
```bash
cd app
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

**Install on device:**
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔗 Project Links

| Link | Description |
|---|---|
| [ScamShield Website](https://scamsheild-security.vercel.app/) | Citizen landing page — download APK, learn about the project |
| [RakshaCall Console](https://scamsheild-security.vercel.app/login) | Police/authority login — restricted access |
| [Feature Audit](FEATURE_AUDIT.md) | Honest breakdown of implemented vs. planned features |
| [GitHub Releases](https://github.com/snxhaSalgaonkar/scamshield/releases/latest) | Download the latest APK |

---

## 🔐 Privacy Guarantees

- **No INTERNET permission** in the Android app — data physically cannot leave the device.
- **No audio recording** — scam detection uses behavioral signals, not voice analysis.
- **No contact uploads** — contacts are checked locally to prevent false alarms on known callers.
- **No SMS text stored** — messages are analyzed in memory and discarded.
- **Delete all data** option available in app settings.

---

## 🛠️ Tech Stack

| Component | Technology |
|---|---|
| Android App | Kotlin, Jetpack Compose, Room, TFLite/custom ML models |
| Web Frontend | Next.js 16.4 (App Router), TypeScript, Tailwind CSS |
| Backend | Supabase (PostgreSQL, Auth, Realtime, RLS) |
| ML Models | TF-IDF + Logistic Regression (text/URL), Gradient Boosted Trees (call risk) |
| Deployment | Vercel (web), GitHub Releases (APK) |

---

## 👥 Team & Context

- **Project**: ScamShield (with RakshaCall authority console)
- **Context**: Built in **24 hours** during a hackathon at **NIT Goa (2026)**
- **Domain**: Cyber Fraud Prevention · Real-Time On-Device ML · Public Safety Intelligence
- **Repository**: [github.com/snxhaSalgaonkar/scamshield](https://github.com/snxhaSalgaonkar/scamshield)
