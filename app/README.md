# Scam Call Guard (scamshield)

A citizen-side Android app that helps people spot phone scams — digital-arrest and fake-police
calls, OTP theft, remote-access ("install AnyDesk") fraud, call forwarding and call merging,
UPI/payment pressure, and scam SMS and links — **while they happen**, using trained machine-learning
models that run entirely on the phone.

- No call audio is recorded or analysed. No message text, phone number or contact leaves the phone.
- The app has **no INTERNET permission**. All inference is local; no API key or cloud service.
- Predictions are worded as risk ("Potential scam detected", "High-risk signs"), never as proof.

```
app/   Android app (this Gradle project: Kotlin, Jetpack Compose, Room, DataStore)
ml/    Training, evaluation, export and tests for the on-device models (see ../ml/README.md)
```

## How detection works

```
 Collectors (permissions)            On-device ML                          Output
 ─────────────────────────           ─────────────────────────────         ──────────────────────
 CallScreener / CallMonitor ──┐
 SmsReceiver ── text ─────────┼──► Model A: message classifier ─┐
                              │    Model D: link (domain) model  ─┤ probabilities only
 AppMonitor (usage access) ───┤                                   ▼
 ForwardingChecker (*#21#) ───┼──► Call window (events) ──► Model B+C: call-risk GBT ──► level + type
 NetworkLossMonitor ──────────┤         CallFeatures (33)        (+ counterfactual factors)   │
 Reported/trusted numbers ────┘                                                               ▼
                                      safety gates (known caller, red flag never rests on one signal)
                                                                     │
                                         notifications / full-screen alert / history / feedback
```

| Model | What it predicts | Trained on | Held-out result | Status |
|---|---|---|---|---|
| A. Message | spam/smishing probability; scam category | UCI SMS Spam + Mendeley SMS Phishing (+ synthetic Indian examples, train only) | real test (n=882): precision 0.956, recall 0.935, F1 0.945, PR-AUC 0.982, FPR 0.8% | binary head validated; **category head experimental** |
| D. Link | phishing probability of a link's registrable domain | PhiUSIIL (171k unique domains) | PR-AUC 0.715; at the "likely phishing" threshold precision 0.976 / recall 0.24 | validated but weak → supporting evidence only |
| B+C. Call risk | scam probability + scam type for a call window | **synthetic** generator | only synthetic metrics exist | **experimental** |

The call model replaced the previous fixed-weight point system as the **primary detector**. The old
rules (`RiskEngine`) remain only as a clearly labelled fallback ("Rule baseline — call model
unavailable") when the model file cannot be loaded, and as a regression test. Full method, datasets,
licences, leakage handling and metrics: [`../ml/README.md`](../ml/README.md) and `../ml/reports/`.

### Conventional code (not ML) still does

- Collect signals, parse OTP/debit SMS structure (`SmsParser`), hash numbers.
- Gate: known contacts and trusted numbers are never scored.
- Safety: a red flag must not rest on one signal — if removing the strongest factor would drop the
  model's estimate below the warning threshold, it is shown as a warning. "This was me" silences a window.
- Out-of-distribution guard: too-short or unfamiliar-language text gets "cannot judge", not a score.
- Ending a call always needs a tap. Nothing is blocked, rejected or changed automatically.

## What works on a real device, and what is experimental or demo-only

| Feature | Real device | Notes |
|---|---|---|
| Unknown-caller detection (call-screening role, Android 10+) | ✅ | Without the role (Android 8–9 or declined) every incoming call is treated as unknown. |
| Call state, talk time, second call / merge signal | ✅ | `READ_PHONE_STATE`. |
| SMS: OTP/debit parsing + ML message classification + link model | ✅ | `RECEIVE_SMS`. Only SMS; WhatsApp/RCS/other apps are **not** visible. Hindi/regional text is often "cannot judge". |
| Suspicious-message alerts and history (no text stored) | ✅ | Trusted bank sender IDs are recorded but do not alert. |
| Manual "Check a message or link" | ✅ | Text is analysed in memory and not saved. Links are never opened. |
| Call-risk score, scam type, factors, alerts | ✅ runs, ⚠️ **experimental** | Model trained on synthetic data; tagged "Experimental" in the UI. |
| Call forwarding check (`*#21#` via `sendUssdRequest`) | ⚠️ carrier-dependent | Many carriers return nothing parseable → shown as unavailable, never guessed. Checked at service start (if stale) and after each unknown call. |
| Remote-access app opened during a call | ⚠️ needs usage access | Only AnyDesk, TeamViewer QuickSupport, RustDesk are watched (package-visibility `<queries>`). |
| New app installs / sideloading during a call | ✅ for apps visible to the app | Android 11+ package visibility limits which installs are seen. |
| Mobile-data loss during a call | ✅ | Weak evidence; tunnels and lifts look the same. |
| Number reporting / trusting | ✅ | Local only; hashed with a per-install pepper. Settings → Reported and trusted numbers. |
| Feedback (correct / false positive / missed scam / unsure) and consented JSON export | ✅ | Never retrains anything automatically. |
| Delete all data | ✅ | Settings → Privacy and data. |
| Voice / call-audio analysis | ❌ not implemented | The toggle is disabled and says so. Android does not let a normal app capture the other party's audio. |
| Scenario Simulator (Demo Mode) | 🎬 demo | Invented events and invented SMS texts through the **real** pipeline and models; every result is tagged "Simulated". |

## Permissions

| Permission | Why | If denied |
|---|---|---|
| `RECEIVE_SMS` | OTP/debit/link events, message model | No SMS-based signals; manual check still works |
| `READ_PHONE_STATE` | call state; forwarding check | No call windows |
| Call-screening role | caller number → hash, unknown check | Every call treated as unknown, no reputation |
| `READ_CONTACTS` | "is the caller a contact?" (yes/no only) | Contacts treated as unknown |
| `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT` | alerts | In-app banner only |
| `CALL_PHONE` | `*#21#` forwarding status (never places calls) | Forwarding unavailable |
| `ANSWER_PHONE_CALLS` | "End call" button (user tap only) | Button hidden |
| Usage access (special) | remote-access apps opened during a call | Not detected |
| `ACCESS_NETWORK_STATE` | mobile-data loss | Not detected |

Settings → Health and permissions shows each capability's live state and how to fix it.

## Build, test and install

Requirements: JDK 17+ (verified with JDK 25), Android SDK platform 35, `local.properties` pointing at the SDK.

```bash
cd app
./gradlew testDebugUnitTest assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

ML pipeline (Python 3.12), from the repository root:

```bash
cd ml
uv venv --python 3.12 .venv && uv pip install --python .venv/bin/python -r requirements.txt
.venv/bin/python -m scamml.download
.venv/bin/python -m scamml.train_text && .venv/bin/python -m scamml.train_url && .venv/bin/python -m scamml.train_call
.venv/bin/python -m scamml.golden
.venv/bin/python -m pytest
```

After retraining, rerun `golden` and the Gradle tests: the Kotlin parity tests fail if the app's
featurizers or runtimes ever disagree with Python.

### Runtime and size

- Runtime: pure Kotlin (`com.scamguard.app.ml`) over JSON artefacts — no native libraries.
  Models load once in the background at app start (and lazily on first use); inference runs on
  `Dispatchers.Default`, never the main thread.
- Artefacts: text 1.9 MB, URL 0.8 MB, call 0.4 MB (3.1 MB uncompressed in assets).
- A missing or corrupt artefact disables only that model; Settings → Models and signals shows the
  reason, and call scoring falls back to the labelled rule baseline.

## Tests

- `ml/`: 23 pytest tests (preprocessing, dedup/label validation, export round-trips vs scikit-learn,
  shipped artefacts, golden consistency).
- `app/`: JVM unit tests including golden-vector parity for all three models, real-model inference
  (no mocks), missing/malformed/wrong-format artefacts, the call scorer's gates and fallback, feature
  building, scenario expectations with the shipped models, privacy of stored/exported data, plus the
  earlier engine, parser, monitor and capability tests.

## Privacy

- Message text is read in memory by `SmsParser` and the message model and then dropped. Stored per
  suspicious message: verdict, probabilities, experimental category, link count, and up to six
  words from the model's public-data vocabulary that raised the score.
- Numbers are stored only as peppered hashes plus a display mask.
- Export (user-initiated, with a confirmation dialog, to a file the user picks) contains model
  outputs, feature vectors and feedback — no text, numbers, hashes or masks.

## Limitations and next steps

1. **The call model is not validated on real calls.** It needs consented, human-reviewed
   call-window data (`train_call --reviewed`) before its scores mean more than "matches our assumptions".
2. **Message model domain shift:** real test data is UK/global English (~2011–2022); Indian scams are
   covered only by synthetic training data. Collect and label real Indian SMS (with consent) and
   evaluate on them before relying on Indian-context accuracy.
3. **Category head** is trained on weak labels and few remote-access / digital-arrest examples.
4. **Link model** sees only the domain; phishing on compromised domains or in paths is missed.
5. Hindi/regional-language messages are mostly out of distribution.
6. No voice/content analysis of calls; forwarding detection depends on the carrier; remote-access
   detection needs usage access and covers four apps.
7. No instrumented (on-device) tests yet: the Room v1→v2 migration SQL was checked against Room's
   generated schema, and the new screens compile but are not exercised by automated UI tests.
8. Thresholds come from validation splits whose base rates differ from real life (PhiUSIIL is 25%
   phishing after dedup), so field precision will differ.
