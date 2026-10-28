# RakshaCall — pitch kit

Screenshots of every page (mock data): `docs/screenshots/`. Retake them on real Supabase data before the final deck.

## Slides (7)
1. **Problem.** "Digital arrest" and OTP-theft calls: victims are kept on the line, isolated and told to move money. Police only learn after the money is gone. *(Insert sourced I4C / NCRB figures; don't use unsourced numbers.)*
2. **Solution.** An Android app scores every unknown call live and disconnects scams. Police get a live, privacy-safe intelligence console.
3. **Architecture.** Phone (rules, SMS, correlation, PII scrubbing, fusion) → Supabase (reports, numbers, RLS, triggers, Realtime) → Next.js console + `/api/analyze`. Use the diagram in SHARED_CONTRACT §3.
4. **Three-layer detection.** L1 Content, L2 Reputation, L3 Correlation, plus safety floors. Key line: *"Other solutions detect scam words. We detect scam behaviour."* Show the Detected-via chart: most catches need no audio.
5. **Privacy by design.** No transcript leaves the phone (only tactic phrases), PII scrubbed, device shown as an opaque hash, consent. Anti-poisoning: a number is flagged only by 3+ independent devices, max 20 reports per device per hour, citizens can mark calls safe.
6. **Live demo.** Run order below.
7. **Results, limits and future.** Accuracy and false-positive rate from the Kotlin team's `RESULTS.md`, plus the console's live FPR card. Future: telecom-side audio analysis, CNAP, 1930 / I4C integration, Play Integrity, on-device models.

## Demo run order (about 4 min)
Before you start: re-run `supabase/004_seed.sql` (refreshes timestamps), open `/dashboard` on the projector, and keep `/dev/simulate` open in a second tab as a safety net.
1. **Scenario A, digital arrest (voice).** A teammate plays the "CBI officer"; the phone disconnects. The report pops into the live feed with a toast. Open it to show tactics, L1/L2/L3, the pattern match, and "conversation never leaves the phone".
2. **Scenario B, wrong-number OTP.** No scam words; an OTP arrives and the phone disconnects. The feed shows "detected via SMS + correlation". Numbers page: +91 98112 55501 turns red (3rd device). Clusters: 98112-XXXXX; click "Recommend series block".
3. **Scenario C, delivery OTP.** Warning only, no disconnect. Mark one report "False positive" and the FPR card updates.
4. **Spike.** Simulator → "Burst: spike in one circle" brings up the spike banner. Finish on `/command` (projector wall).
If the phone or network fails, the simulator has the same scenario buttons (`wrong_number_otp`, `digital_arrest`, `delivery_otp`).

Record a backup video of this run (Win+Alt+R in Xbox Game Bar, or OBS) and keep a copy on 2 laptops.

## Judge Q&A, one line each
- **False positives?** Warnings start at 40, disconnect only at 90+ (or 75+ after a 5-second countdown the user can cancel); trusted contacts get half the score; delivery OTPs only warn; officers and citizens both feed the live false-positive rate.
- **Privacy?** Conversations, SMS text and OTPs never leave the phone. Reports carry scores and tactic phrases only, PII-scrubbed, with an opaque device ID.
- **Fake / malicious reports?** A number is flagged only by 3+ distinct devices, never one; max 20 reports per device per hour; citizens' "safe" votes can un-flag a number; RLS stops a device from reporting as another.
- **How do you access call audio?** Android doesn't allow third-party call recording. The optional "scan this call" bubble uses speakerphone speech-to-text with consent. The system still works without audio, via SMS, reputation and correlation.
- **Why not just power off / block the number?** Scammers rotate numbers. We block the call in the moment and give police the number-series clusters, so they can act on the gang, not one SIM.
- **Why would police trust it?** Every report shows why it fired: layers in agreement, the tactics, the pattern match. Officers set the status, and escalation produces a ready 1930 summary.
