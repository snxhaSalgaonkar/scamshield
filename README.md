# ScamShield

ScamShield is a prototype for on-device detection of suspicious calls, SMS messages, and links. It pairs an Android app with **RakshaCall**, a web console for viewing and reviewing reports. The Android app currently operates locally and does not send reports to the web console.

| Project | Purpose | Documentation |
|---|---|---|
| [`app/`](app/) | Android app built with Kotlin and Jetpack Compose | [Android guide](app/README.md) |
| [`app/ml/`](app/ml/) | Model training, evaluation, and export pipeline | [ML guide](app/ml/README.md) |
| [`website/`](website/) | Next.js RakshaCall console and Supabase SQL | [Console guide](website/README.md), [deployment and setup](website/SETUP.md) |
| [`FEATURE_AUDIT.md`](FEATURE_AUDIT.md) | Implemented features, limitations, and roadmap | Feature audit |

## Live project

- Public site: [scamsheild-security.vercel.app](https://scamsheild-security.vercel.app/)
- RakshaCall officer login: [scamsheild-security.vercel.app/login](https://scamsheild-security.vercel.app/login)
- Android APK: [latest GitHub release](https://github.com/snxhaSalgaonkar/scamshield/releases/latest)

The officer console requires an Auth user with an `authority` role. Demo credentials are not production credentials. See [the Supabase setup guide](website/SETUP.md).

## What is implemented

### Android app

- On-device SMS classification, link-domain scoring, and call-risk scoring.
- Call, SMS, and device-event correlation with local alerts and history.
- Local reported/trusted numbers, feedback, and user-initiated data deletion/export.
- A simulator that runs invented scenarios through the app's detection pipeline.
- No `INTERNET` permission. The app does not upload reports or synchronize reputation with Supabase.

The call model is trained on synthetic data and remains experimental. The link model is supporting evidence. See [the Android guide](app/README.md) and [feature audit](FEATURE_AUDIT.md) for limitations.

### RakshaCall web console

- Supabase Auth for officer sign-in, with access restricted to profiles whose role is `authority`.
- Reports dashboard, number history, clusters, telecom-circle map, command view, CSV export, and simulator.
- Supabase schema, row-level security, triggers, and RPCs under [`website/supabase/`](website/supabase/).
- Mock mode for local demos and Supabase mode for a configured backend.

The database and web console can show seeded or simulated data. This does not mean the Android app is currently sending real incidents to Supabase.

## Quick start

### Web console: mock mode

Requirements: Node.js and npm compatible with the versions used by the project.

```powershell
cd website
npm install
npm run dev
```

Open `http://localhost:3000`. In mock mode, the login accepts any email and password for demonstration. Mock mode is not authentication and must not be treated as a secure deployment. Configure Supabase before using officer accounts or real data; follow [website/SETUP.md](website/SETUP.md).

### Android app

Requirements: Android Studio, JDK 17, and Android SDK Platform 35. From the repository root:

```powershell
cd app
./gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is written to `app/app/build/outputs/apk/debug/app-debug.apk` (relative to the repository root). Install it on a connected device with:

```powershell
adb install -r app/app/build/outputs/apk/debug/app-debug.apk
```

For Linux/macOS, use `./gradlew` instead of `./gradlew.bat`.

## Supabase and deployment

The web console uses a Supabase project. Apply SQL migrations `001_schema.sql` through `004_seed.sql` in order, create/confirm Auth users for officers, then edit and run `005_authority_users.sql` to grant authority access. Configure the Supabase project base URL (for example, `https://<project-ref>.supabase.co`) without `/rest/v1` or `/auth/v1`.

For Vercel, set the project root directory to `website`, configure the environment variables in [the setup guide](website/SETUP.md), and redeploy after changing any `NEXT_PUBLIC_*` values. Keep the service-role key server-side; never commit secrets or put them in client-exposed variables.

## Verification commands

From `website/`:

```bash
npm run lint
npm run typecheck
npm test
```

`npm test` runs the SQL and logic suites. The integration and semantic endpoint suites have separate commands documented in [website/README.md](website/README.md). From `app/`, Android unit tests run with `./gradlew.bat testDebugUnitTest` on Windows or `./gradlew testDebugUnitTest` on Linux/macOS. Model pipeline tests and reproduction steps are in [app/ml/README.md](app/ml/README.md).

## Privacy and limitations

- Android inference is local. The app manifest does not request internet access.
- Phone audio is not recorded or analyzed. The app does not scan WhatsApp, RCS, or other apps' messages.
- SMS and contact permissions are used on-device; the project states that message text and contacts are not uploaded.
- Model scores are risk indicators, not proof. Call scoring is experimental, and carrier/OS restrictions affect some signals.
- Cloud reporting, cross-device reputation, and pre-ring Supabase lookup are database-ready but are not wired into the Android app.

Read [FEATURE_AUDIT.md](FEATURE_AUDIT.md) before presenting prototype behavior as a production capability.

## Repository

[github.com/snxhaSalgaonkar/scamshield](https://github.com/snxhaSalgaonkar/scamshield)
