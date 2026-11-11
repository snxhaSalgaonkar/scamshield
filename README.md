# ScamShield (RakshaCall)

[![Live Authority Console](https://img.shields.io/badge/Console-Live%20on%20Vercel-0070F3?style=for-the-badge&logo=vercel)](https://scamshield-ochre.vercel.app/)
[![Android App](https://img.shields.io/badge/Android-Kotlin%20%7C%20Compose-3DDC84?style=for-the-badge&logo=android)](app/)
[![Backend](https://img.shields.io/badge/Backend-Supabase%20Postgres-3ECF8E?style=for-the-badge&logo=supabase)](website/)

> **Live Police / Authority Dashboard:** [https://scamshield-ochre.vercel.app/](https://scamshield-ochre.vercel.app/)

**ScamShield (RakshaCall)** is an end-to-end anti-fraud intelligence platform designed to detect phone scams (digital arrest, fake police/CBI calls, OTP theft, remote-access fraud like AnyDesk, and malicious links) **while they happen**, without ever compromising citizen privacy.

---

## 🏛️ System Architecture

The platform consists of two integrated components:

```
┌────────────────────────────────────────────────────────┐
│             Citizen-Side Android App (app/)            │
│  - On-Device Machine Learning (Message & Link models)   │
│  - Real-Time Call Event Windowing (CallFeatures GBT)    │
│  - Privacy-by-design: NO INTERNET permission required  │
└───────────────────────────┬────────────────────────────┘
                            │ (Consented / Anonymous threat telemetry)
                            ▼
┌────────────────────────────────────────────────────────┐
│         Authority Intelligence Console (website/)       │
│  - Next.js (App Router) + Supabase Realtime & RLS      │
│  - Live feed of active scam calls & spike detection     │
│  - Telecom circle maps & Number-series gang clusters    │
│  - Live URL: https://scamshield-ochre.vercel.app/      │
└────────────────────────────────────────────────────────┘
```

### 1. Citizen Android App (`app/`)
* **Privacy by Design**: Has **no `INTERNET` permission** in production mode. Inference runs entirely on the device CPU/NPU.
* **Zero Audio Surveillance**: Call audio is never tapped or recorded. 
* **Multi-Signal Correlation**: Cross-references unexpected OTP/debit SMS arrivals during calls, background remote desktop apps (`AnyDesk`, `TeamViewer`, `RustDesk`), and call-forwarding (`*#21#`).
* **On-Device ML**:
  * **Model A (Message)**: Classifies SMS spam/smishing and fraud categories.
  * **Model D (Link)**: Evaluates phishing probability of registrable domains.
  * **Model B+C (Call Risk)**: Gradient boosted trees scoring call windows.

### 2. Authority Console (`website/`)
* **Live Incident Feed**: High-risk calls appear on police screens within 1–2 seconds via Supabase Realtime WebSockets.
* **Cluster & Gang Analysis**: Detects number-series rotation (e.g. `+91 98112-XXXXX`) to catch organized scam syndicates rather than individual burnable SIMs.
* **Spike Detection & Telecom Circle Map**: Visualizes fraud hotspots across telecom circles (Delhi, Maharashtra, Karnataka, etc.).
* **Demo Simulator**: Built-in test sandbox (`/dev/simulate`) for dry-runs of digital arrest, OTP distraction, and delivery scams.

---

## 🛡️ Three-Layer Detection

| Layer | Type | What It Does |
|---|---|---|
| **L1** | **Content** | Detects scam phrases, urgency, and coercion patterns on-device. |
| **L2** | **Reputation** | Multi-device reporting: numbers flagged only when 3+ independent devices confirm fraud. |
| **L3** | **Correlation** | Detects high-risk temporal overlap: bank OTP arriving mid-call or screen sharing opened. |

---

## 🚀 Deployment Guide

### A. Web Console & Backend (`website/`)

1. **Database & Auth (Supabase)**:
   - Create a project on [supabase.com](https://supabase.com).
   - In the SQL Editor, execute `001_schema.sql` through `004_seed.sql` in [`website/supabase/`](website/supabase/).
   - Add your officer email(s) in `005_authority_users.sql` and run it to grant the `authority` role.
   - Enable Anonymous Sign-ins under Authentication providers.
2. **Web Dashboard (Vercel)**:
   - Hosted at: [https://scamshield-ochre.vercel.app/](https://scamshield-ochre.vercel.app/)
   - Set root directory to `website`.
   - Add the environment variables:
     * `NEXT_PUBLIC_DATA_SOURCE=supabase`
     * `NEXT_PUBLIC_SUPABASE_URL`
     * `NEXT_PUBLIC_SUPABASE_ANON_KEY`
     * `SUPABASE_SERVICE_ROLE_KEY`
     * `SIMULATOR_ENABLED=true`
     * `LLM_PROVIDER=none`

---

### B. Citizen Android App (`app/`)

#### Prerequisites:
- Android Studio Ladybug / Meerkat (or newer)
- JDK 17+ (JDK 21 or 25 verified)
- Android SDK Platform 35 (Android 15)

#### Build APK:
```bash
# Navigate to the app directory
cd app

# Build debug APK
./gradlew assembleDebug

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

#### Install on a Physical Device:
```bash
# With USB debugging enabled:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 👥 Authors & Academic Context

* **Project**: ScamShield (RakshaCall)
* **Institution**: National Institute of Technology Goa (NIT Goa) — 2026
* **Domain**: Cyber Fraud Prevention, Real-Time On-Device ML, Public Safety Intelligence
