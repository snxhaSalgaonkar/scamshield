# ScamShield — Feature Audit

> This audit was performed against the source code on 12 November 2026.
> It honestly distinguishes what is **running code** vs. what is **described but not wired** vs. what **cannot work** due to platform limits.

---

## ✅ Fully Implemented (24 Features)

| # | Feature | Component | Evidence |
|---|---------|-----------|----------|
| 1 | On-device SMS spam/smishing classification | Android | `SmsReceiver.kt`, `SmsParser.kt`, TF-IDF `text_model.json` in assets |
| 2 | On-device phishing URL detection | Android | `UrlFeaturizer.kt`, `url_model.json` in assets |
| 3 | On-device call risk scoring (gradient-boosted trees) | Android | `CallRiskScorer.kt`, `TreeEnsemble` in `Models.kt`, `call_model.json` |
| 4 | Unknown-caller detection (Android 10+ call-screening role) | Android | `CallScreener.kt` — uses `CallScreeningService` |
| 5 | Call state, talk time, second-call / merge detection | Android | `CallMonitor.kt`, `MergeDetector.kt` |
| 6 | Mid-call OTP / bank SMS correlation (Layer 3) | Android | `SmsReceiver` → `EventBus` → `CallWindow` / `RiskEngine` |
| 7 | Remote-access app detection (AnyDesk, TeamViewer, RustDesk) | Android | `AppMonitor.kt`, `<queries>` in `AndroidManifest.xml` |
| 8 | Mobile-data loss during a call | Android | `NetworkLossMonitor.kt` |
| 9 | Call forwarding check (`*#21#` USSD) | Android | `ForwardingChecker.kt` — carrier-dependent |
| 10 | Full-screen scam alert overlay | Android | `FullScreenAlertActivity`, `AlertDispatcher.kt` |
| 11 | Local number reporting / trusting (on-device only) | Android | `NumbersRepository.kt`, `LocalReputation.kt` — hashed with per-install pepper |
| 12 | Manual "Check a message or link" | Android | In-memory analysis, text not saved |
| 13 | User feedback (correct / false positive / missed / unsure) | Android | `FeedbackRepository.kt` |
| 14 | Delete all data | Android | `AppContainer.deleteAllData()` — clears every table |
| 15 | Scenario Simulator (Demo Mode) | Android | Invented events through real ML pipeline, tagged "Simulated" |
| 16 | No INTERNET permission — privacy guarantee | Android | `AndroidManifest.xml` — no `android.permission.INTERNET` |
| 17 | Zero audio tapping — no `RECORD_AUDIO` permission | Android | Verified: not in manifest |
| 18 | No contact-book uploads | Android | `READ_CONTACTS` used locally only (no internet = physically can't upload) |
| 19 | Auto-start on boot | Android | `BootReceiver` in manifest |
| 20 | Web dashboard — officer login via Supabase Auth | Website | `website/src/app/login/` |
| 21 | Web dashboard — view reports, numbers, stats | Website | `data.supabase.ts`, `data.mock.ts` |
| 22 | Web dashboard — Realtime subscriptions (reports & numbers) | Website | `003_triggers_rpcs.sql` enables Realtime publication |
| 23 | Supabase database schema — tables, RLS, triggers, RPCs | Database | 5 migration SQL files in `website/supabase/` |
| 24 | Citizen landing page with APK download + QR code | Website | `website/src/app/page.tsx` |

---

## ⚠️ Partially Implemented (Backend-Ready but Not Wired End-to-End)

| # | Feature | What Exists | What's Missing | Effort |
|---|---------|-------------|----------------|--------|
| 1 | Cloud-based number reporting (phone → Supabase) | `BackendGateway` interface, full DB triggers, RLS policies | App uses `LocalOnlyGateway` (no-op) and has no `INTERNET` permission | Medium (1-2 days) |
| 2 | Sybil-resistant reputation ("3+ devices flag a number") | DB trigger `refresh_number` counts `DISTINCT device_id`, sets `is_flagged` ≥ 3 | No phones send reports, so it only fires with simulated data | Depends on #1 |
| 3 | Number-series gang cluster detection (prefix analysis) | `number_series` table, `number_prefix` auto-filled on insert, dashboard can group by prefix | No automated cluster detection algorithm; officers review manually | Medium |
| 4 | Pre-ring reputation lookup (`get_number_reputation` RPC) | RPC exists in Supabase | App can't call it (no internet) | Same as #1 |
| 5 | Call risk model accuracy | Model exists and runs, produces scores | Trained on **synthetic data** — tagged "Experimental" in UI | Needs real-world data |
| 6 | Hindi/regional SMS classification | Works for English | Hindi/regional text often returns "cannot judge" (out-of-distribution) | Needs multilingual training data |

---

## ❌ Cannot Be Implemented (Platform Limitation)

| # | Feature | Why It's Impossible |
|---|---------|---------------------|
| 1 | **Voice / call-audio analysis** | Android does not allow normal apps to capture the other party's audio. The app UI has a disabled toggle that explains this. |
| 2 | **WhatsApp / RCS / Telegram message scanning** | Android `RECEIVE_SMS` works for standard SMS only. Other messaging apps are sandboxed — no third-party access without Accessibility Service (banned by Google Play for this use). |
| 3 | **Automatic call blocking without user tap** | Deliberate design choice. The app uses `CallScreeningService` but never rejects calls automatically. "Ending a call always needs a tap." |

---

## 🔮 Future Roadmap (v2.0)

| # | Feature | What It Would Take | Priority |
|---|---------|---------------------|----------|
| 1 | Optional internet permission toggle | Add `INTERNET` to manifest with runtime toggle; switch to `SupabaseGateway` when enabled | 🔴 High |
| 2 | Real `SupabaseGateway` implementation | Implement all 8 `BackendGateway` methods using Supabase SDK; DTOs already exist in `Dtos.kt` | 🔴 High |
| 3 | Dashboard map view (fraud heatmap) | Map component plotting reports by `telecom_circle`; data already exists | 🟡 Medium |
| 4 | iOS app | Rewrite in Swift/SwiftUI; iOS has `CallKit` but no call-screening equivalent | 🟡 Medium |
| 5 | Automated syndicate detection (ML on prefix clusters) | Clustering algorithm on `number_series` + temporal data | 🟡 Medium |
| 6 | Multi-language SMS model | Retrain TF-IDF + logistic regression with Hindi/Marathi/Tamil/Bengali datasets | 🟡 Medium |
| 7 | Push notifications to officers | Supabase Edge Function → FCM push when a number crosses 3-reporter threshold | 🟢 Low |
| 8 | APK auto-update (in-app update check) | Check GitHub Releases API for newer tags; requires internet permission | 🟢 Low |
| 9 | Real training data pipeline | Collect consented feedback JSON exports, aggregate, retrain periodically | 🟡 Medium |
| 10 | Carrier-level block recommendations | Dashboard UI for officers to export flagged numbers as CSV for TRAI/DoT | 🟢 Low |

---

## Summary

| Category | Count |
|----------|-------|
| ✅ Fully implemented and running | **24** |
| ⚠️ Backend-ready but not wired end-to-end | **6** |
| ❌ Cannot implement (OS/platform limitation) | **3** |
| 🔮 Planned for next version | **10** |

> **Key insight:** The most impactful v2.0 change would be adding **optional internet permission** + `SupabaseGateway`. This single change unlocks cloud reporting, reputation sync, pre-ring lookup, and makes the "3+ devices flag" system work with real data.

