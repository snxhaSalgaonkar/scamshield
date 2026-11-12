import Link from 'next/link'
import {
  Shield,
  ShieldCheck,
  Download,
  QrCode,
  Lock,
  ArrowRight,
  Cpu,
  Users,
  Smartphone,
  Radio,
  Sparkles,
  Phone,
  PhoneOff,
  AlertTriangle,
} from 'lucide-react'

const GITHUB_REPO_URL = 'https://github.com/snxhaSalgaonkar/scamshield'
const GITHUB_RELEASE_URL = 'https://github.com/snxhaSalgaonkar/scamshield/releases/latest'
const QR_CODE_URL =
  'https://api.qrserver.com/v1/create-qr-code/?size=220x220&margin=10&data=https://github.com/snxhaSalgaonkar/scamshield/releases/latest'

export default function LandingPage() {
  return (
    <div className="relative min-h-screen overflow-hidden bg-gradient-to-b from-[#EBF2FC] via-[#EEF3FD] to-[#DDE7FA] text-slate-900 selection:bg-indigo-600 selection:text-white">
      {/* ─── Ambient Glow Blobs & Wave Accents (Matching Reference) ──── */}
      <div className="pointer-events-none absolute -top-40 -left-40 h-[600px] w-[600px] rounded-full bg-gradient-to-br from-blue-400/25 via-indigo-400/20 to-purple-400/15 blur-3xl" />
      <div className="pointer-events-none absolute top-40 right-0 h-[500px] w-[500px] rounded-full bg-gradient-to-bl from-purple-400/20 via-indigo-300/20 to-blue-300/10 blur-3xl" />
      <div className="pointer-events-none absolute top-[700px] left-1/3 h-[700px] w-[700px] -translate-x-1/2 rounded-full bg-blue-300/20 blur-3xl" />

      {/* ─── Floating Pill Navbar ─────────────────────────────────────── */}
      <header className="sticky top-4 z-50 mx-auto max-w-6xl px-4 sm:px-6">
        <div className="flex items-center justify-between rounded-2xl border border-white/80 bg-white/85 px-5 py-3 shadow-lg shadow-indigo-900/5 backdrop-blur-md">
          {/* Logo with Phone-in-Shield badge */}
          <Link href="/" className="flex items-center gap-3 transition-opacity hover:opacity-90">
            <div className="relative flex size-10 items-center justify-center rounded-xl bg-gradient-to-br from-blue-600 via-indigo-600 to-indigo-800 text-white shadow-md shadow-indigo-600/30">
              <Shield className="size-6 fill-white/15 text-white" />
              <Phone className="absolute size-3 text-white" />
            </div>
            <div className="flex items-baseline gap-1.5">
              <span className="font-display text-2xl font-extrabold tracking-tight text-[#0F172A]">ScamShield</span>
              <span className="size-1.5 rounded-full bg-indigo-600" />
            </div>
          </Link>

          {/* Navigation items */}
          <nav className="hidden items-center gap-7 md:flex">
            <a href="#features" className="text-sm font-semibold text-slate-600 transition-colors hover:text-slate-900">
              Features
            </a>
            <a href="#how-it-works" className="text-sm font-semibold text-slate-600 transition-colors hover:text-slate-900">
              How It Works
            </a>
            <a href="#privacy" className="text-sm font-semibold text-slate-600 transition-colors hover:text-slate-900">
              Privacy
            </a>
            <Link
              href="/login"
              className="text-sm font-semibold text-indigo-700 transition-colors hover:text-indigo-950 flex items-center gap-1"
            >
              <span>RakshaCall Console</span>
              <Lock className="size-3 text-indigo-600" />
            </Link>
          </nav>

          {/* Top-Right Download Button */}
          <div className="flex items-center gap-3">
            <a
              href={GITHUB_RELEASE_URL}
              target="_blank"
              rel="noreferrer"
              className="flex items-center gap-2 rounded-xl bg-gradient-to-r from-blue-600 to-indigo-600 px-4 py-2 text-xs font-bold text-white shadow-md shadow-indigo-500/25 transition-all hover:from-blue-700 hover:to-indigo-700 hover:scale-[1.02] active:scale-[0.98]"
            >
              <Download className="size-3.5" />
              <span>Download App</span>
            </a>
          </div>
        </div>
      </header>

      {/* ─── Hero Section ────────────────────────────────────────────── */}
      <section className="relative px-4 pt-12 pb-20 sm:px-6 md:pt-16 md:pb-28">
        <div className="mx-auto max-w-6xl">
          <div className="grid items-center gap-12 lg:grid-cols-12">
            {/* Left Column: Headlines & Actions */}
            <div className="space-y-6 lg:col-span-7">
              {/* Pill tag */}
              <div className="inline-flex items-center gap-2 rounded-full border border-indigo-200/80 bg-white/80 px-4 py-1.5 text-xs font-bold text-indigo-700 shadow-xs backdrop-blur-sm">
                <Sparkles className="size-3.5 text-indigo-600" />
                <span>AI-Powered Call Protection</span>
                <span className="size-2 rounded-full bg-purple-600" />
              </div>

              {/* Massive Impact Title */}
              <h1 className="font-display text-4xl leading-[1.08] font-extrabold tracking-tight text-[#0F172A] sm:text-5xl lg:text-[56px]">
                Stop Digital Arrest &<br />
                Phone Scams<br />
                <span className="relative text-transparent bg-clip-text bg-gradient-to-r from-indigo-600 via-purple-600 to-violet-700">
                  While They Happen.
                  <svg
                    className="absolute -bottom-2 left-0 w-full text-purple-400/40"
                    viewBox="0 0 300 12"
                    fill="none"
                    xmlns="http://www.w3.org/2000/svg"
                  >
                    <path
                      d="M2.5 8.5C75.5 2.5 225.5 2.5 297.5 8.5"
                      stroke="currentColor"
                      strokeWidth="4"
                      strokeLinecap="round"
                    />
                  </svg>
                </span>
              </h1>

              {/* Subtitle */}
              <p className="max-w-xl text-base leading-relaxed text-slate-600 sm:text-lg">
                <strong>ScamShield</strong> uses advanced on-device AI to detect suspicious calls, block scam attempts, and
                keep you safe in real time.
              </p>

              {/* Action Buttons */}
              <div className="flex flex-wrap items-center gap-4 pt-2">
                <a
                  href={GITHUB_RELEASE_URL}
                  target="_blank"
                  rel="noreferrer"
                  className="flex items-center gap-2.5 rounded-2xl bg-gradient-to-r from-blue-600 via-indigo-600 to-indigo-700 px-6 py-3.5 text-sm font-bold text-white shadow-xl shadow-indigo-600/30 transition-all hover:shadow-indigo-600/40 hover:scale-[1.02] active:scale-[0.98]"
                >
                  <Download className="size-4" />
                  <span>Download ScamShield (APK)</span>
                  <ArrowRight className="size-4 opacity-75" />
                </a>

                <Link
                  href="/login"
                  className="flex items-center gap-2 rounded-2xl border border-slate-300/80 bg-white/90 px-5 py-3.5 text-sm font-semibold text-slate-800 shadow-sm backdrop-blur-sm transition-all hover:bg-slate-50 hover:border-slate-400 hover:text-slate-900"
                >
                  <ShieldCheck className="size-4 text-indigo-600" />
                  <span>RakshaCall Authority Portal</span>
                </Link>
              </div>

              {/* 3 Trust Badges */}
              <div className="flex flex-wrap items-center gap-6 pt-4 text-xs font-semibold text-slate-600">
                <div className="flex items-center gap-2">
                  <div className="flex size-7 items-center justify-center rounded-lg bg-emerald-100/80 text-emerald-700">
                    <ShieldCheck className="size-4" />
                  </div>
                  <span>Free & Open<br />Source</span>
                </div>

                <div className="h-6 w-px bg-slate-300/60" />

                <div className="flex items-center gap-2">
                  <div className="flex size-7 items-center justify-center rounded-lg bg-emerald-100/80 text-emerald-700">
                    <Smartphone className="size-4" />
                  </div>
                  <span>Android 8.0<br />to 15</span>
                </div>

                <div className="h-6 w-px bg-slate-300/60" />

                <div className="flex items-center gap-2">
                  <div className="flex size-7 items-center justify-center rounded-lg bg-emerald-100/80 text-emerald-700">
                    <Radio className="size-4" />
                  </div>
                  <span>Zero Audio<br />Tapping</span>
                </div>
              </div>
            </div>

            {/* Right Column: Phone Mockup & Floating QR Code (Matching Reference Image) */}
            <div className="relative flex items-center justify-center lg:col-span-5">
              {/* Translucent Shield wireframe in background */}
              <div className="pointer-events-none absolute -top-8 -right-8 -z-10 text-indigo-300/30">
                <Shield className="size-80 stroke-[1.2]" />
              </div>

              {/* Floating accent dots */}
              <div className="absolute top-12 left-4 size-3.5 rounded-full bg-emerald-400 shadow-md shadow-emerald-400/40" />
              <div className="absolute top-36 -right-3 size-2.5 rounded-full bg-indigo-500 shadow-md shadow-indigo-500/40" />

              {/* ── Phone Mockup ── */}
              <div className="relative z-10 w-[270px] sm:w-[290px] rounded-[44px] bg-[#0F172A] p-3 shadow-2xl shadow-indigo-900/35 ring-1 ring-slate-800">
                {/* Outer bezel shine */}
                <div className="overflow-hidden rounded-[36px] bg-[#0A0F1D] text-white">
                  {/* Status Bar */}
                  <div className="flex items-center justify-between px-6 pt-3 pb-2 text-[11px] font-medium text-slate-400">
                    <span>9:41</span>
                    <div className="flex items-center gap-1.5">
                      <span className="size-1.5 rounded-full bg-slate-400" />
                      <span className="size-1.5 rounded-full bg-slate-400" />
                      <div className="h-2 w-3 rounded-xs border border-slate-400" />
                    </div>
                  </div>

                  {/* Incoming Call Screen */}
                  <div className="flex flex-col items-center px-4 pt-4 pb-6">
                    {/* Caller Avatar */}
                    <div className="flex size-14 items-center justify-center rounded-full bg-slate-800/90 text-slate-400 shadow-inner">
                      <Users className="size-7" />
                    </div>

                    <div className="mt-3 text-center">
                      <h4 className="text-base font-bold text-white">Unknown Number</h4>
                      <p className="text-xs text-slate-400">Incoming Call...</p>
                    </div>

                    {/* Glowing Scam Warning Card */}
                    <div className="mt-5 w-full rounded-2xl border border-rose-500/40 bg-gradient-to-b from-rose-950/80 to-rose-900/60 p-4 text-center shadow-lg shadow-rose-950/50">
                      <div className="mx-auto flex size-8 items-center justify-center rounded-full bg-rose-500/20 text-rose-400">
                        <AlertTriangle className="size-4.5" />
                      </div>
                      <h5 className="mt-2 text-xs font-bold text-rose-200">Potential Scam Detected</h5>
                      <p className="mt-0.5 text-[10px] text-rose-300/80">This call may be a fraud attempt.</p>
                    </div>

                    {/* Decline & Answer Buttons */}
                    <div className="mt-7 flex w-full items-center justify-around px-2">
                      <div className="flex flex-col items-center gap-1.5">
                        <button
                          type="button"
                          className="flex size-12 items-center justify-center rounded-full bg-rose-600 text-white shadow-lg shadow-rose-600/40"
                          aria-label="Decline call"
                        >
                          <PhoneOff className="size-5" />
                        </button>
                        <span className="text-[10px] font-semibold text-slate-400">Decline</span>
                      </div>

                      <div className="flex flex-col items-center gap-1.5">
                        <button
                          type="button"
                          className="flex size-12 items-center justify-center rounded-full bg-emerald-600 text-white shadow-lg shadow-emerald-600/40"
                          aria-label="Answer call"
                        >
                          <Phone className="size-5" />
                        </button>
                        <span className="text-[10px] font-semibold text-slate-400">Answer</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* ── Floating QR Code Card (Beside Phone) ── */}
              <div className="absolute -bottom-6 -right-6 z-20 w-44 rounded-2xl border border-white/90 bg-white p-3.5 shadow-xl shadow-indigo-950/15 sm:-right-8 sm:w-48">
                <div className="flex items-center justify-between border-b border-slate-100 pb-2">
                  <div className="flex items-center gap-1.5 text-xs font-bold text-slate-800">
                    <Download className="size-3.5 text-indigo-600" />
                    <span>Download App</span>
                  </div>
                </div>

                <div className="my-2.5 flex justify-center rounded-xl bg-slate-50 p-2 border border-slate-100">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={QR_CODE_URL}
                    alt="Scan QR code to install ScamShield"
                    className="size-32 rounded-lg object-contain"
                  />
                </div>

                <p className="flex items-center justify-center gap-1 text-[10px] font-semibold text-slate-600">
                  <QrCode className="size-3 text-indigo-600" /> Scan with phone camera
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ─── Metric Strip Card ────────────────────────────────────────── */}
      <section className="relative z-10 mx-auto -mt-6 max-w-6xl px-4 sm:px-6">
        <div className="grid grid-cols-2 gap-6 rounded-3xl border border-white/80 bg-white/90 p-8 shadow-xl shadow-indigo-900/5 backdrop-blur-md md:grid-cols-4">
          <div className="space-y-1">
            <div className="font-display text-3xl font-extrabold text-[#0F172A] sm:text-4xl">₹1,750+ Cr</div>
            <p className="text-xs font-semibold text-slate-500">Estimated cyber fraud loss in India (I4C)</p>
          </div>
          <div className="space-y-1">
            <div className="font-display text-3xl font-extrabold text-indigo-600 sm:text-4xl">&lt; 2.0 s</div>
            <p className="text-xs font-semibold text-slate-500">On-device ML call risk calculation</p>
          </div>
          <div className="space-y-1">
            <div className="font-display text-3xl font-extrabold text-emerald-600 sm:text-4xl">0 Bytes</div>
            <p className="text-xs font-semibold text-slate-500">Call audio or SMS uploaded to cloud</p>
          </div>
          <div className="space-y-1">
            <div className="font-display text-3xl font-extrabold text-purple-600 sm:text-4xl">3 Layers</div>
            <p className="text-xs font-semibold text-slate-500">Content ML + Reputation + Correlation</p>
          </div>
        </div>
      </section>

      {/* ─── Three-Layer AI Architecture ─────────────────────────────── */}
      <section id="how-it-works" className="relative py-24 px-4 sm:px-6">
        <div className="mx-auto max-w-6xl">
          <div className="text-center">
            <span className="text-xs font-extrabold tracking-wider text-indigo-600 uppercase">
              How ScamShield Protects You
            </span>
            <h2 className="mt-2 font-display text-3xl font-extrabold text-[#0F172A] sm:text-4xl">
              Other solutions detect scam words.
              <br />
              <span className="text-transparent bg-clip-text bg-gradient-to-r from-indigo-600 via-purple-600 to-blue-600">
                ScamShield detects scam behavior.
              </span>
            </h2>
            <p className="mx-auto mt-4 max-w-2xl text-sm leading-relaxed text-slate-600">
              Scammers continuously rotate numbers and alter scripts, but they cannot hide coordinated tactics.
              ScamShield correlates three independent signals:
            </p>
          </div>

          <div className="mt-14 grid gap-8 md:grid-cols-3">
            {/* Layer 1 */}
            <div className="rounded-3xl border border-white/80 bg-white/85 p-8 shadow-lg shadow-indigo-950/5 backdrop-blur-md transition-all hover:-translate-y-1 hover:shadow-xl hover:border-indigo-300">
              <div className="flex size-12 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600 border border-indigo-100">
                <Cpu className="size-6" />
              </div>
              <span className="mt-6 inline-block text-xs font-extrabold text-indigo-600">LAYER 1</span>
              <h3 className="mt-1 font-display text-xl font-bold text-slate-900">On-Device Content ML</h3>
              <p className="mt-3 text-sm leading-relaxed text-slate-600">
                Trained neural classifiers evaluate incoming SMS texts and link domains in memory. Detects urgency,
                impersonation, and fake courier/police links without any text leaving the phone.
              </p>
              <div className="mt-6 border-t border-slate-100 pt-4 text-xs font-medium text-slate-500 space-y-1.5">
                <div>• PhiUSIIL Phishing Link Model</div>
                <div>• On-Device SMS Spam & Smishing NLP</div>
              </div>
            </div>

            {/* Layer 2 */}
            <div className="rounded-3xl border border-white/80 bg-white/85 p-8 shadow-lg shadow-indigo-950/5 backdrop-blur-md transition-all hover:-translate-y-1 hover:shadow-xl hover:border-blue-300">
              <div className="flex size-12 items-center justify-center rounded-2xl bg-blue-50 text-blue-600 border border-blue-100">
                <Users className="size-6" />
              </div>
              <span className="mt-6 inline-block text-xs font-extrabold text-blue-600">LAYER 2</span>
              <h3 className="mt-1 font-display text-xl font-bold text-slate-900">Sybil-Resistant Reputation</h3>
              <p className="mt-3 text-sm leading-relaxed text-slate-600">
                A caller is flagged only when 3+ independent devices confirm reports. It also analyzes number-series
                clusters (e.g. 98112-XXXXX) to target entire scam syndicates rather than single SIMs.
              </p>
              <div className="mt-6 border-t border-slate-100 pt-4 text-xs font-medium text-slate-500 space-y-1.5">
                <div>• Number-series gang cluster detection</div>
                <div>• 3+ Distinct reporter verification gate</div>
              </div>
            </div>

            {/* Layer 3 */}
            <div className="rounded-3xl border border-indigo-200 bg-gradient-to-b from-indigo-50/70 to-white/90 p-8 shadow-lg shadow-indigo-950/5 backdrop-blur-md transition-all hover:-translate-y-1 hover:shadow-xl hover:border-indigo-400">
              <div className="flex size-12 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-600 to-purple-600 text-white shadow-md shadow-indigo-600/30">
                <Radio className="size-6" />
              </div>
              <span className="mt-6 inline-block text-xs font-extrabold text-indigo-700">LAYER 3 (CRITICAL)</span>
              <h3 className="mt-1 font-display text-xl font-bold text-slate-900">Temporal Event Correlation</h3>
              <p className="mt-3 text-sm leading-relaxed text-slate-600">
                Catches the fatal moment of fraud: an unexpected bank OTP arrives while you are on an active unknown
                call, or a screen-sharing app (AnyDesk, TeamViewer, RustDesk) is launched mid-call.
              </p>
              <div className="mt-6 border-t border-indigo-100 pt-4 text-xs font-medium text-slate-600 space-y-1.5">
                <div>• Mid-call OTP / UPI pressure intercept</div>
                <div>• Remote desktop takeover warning</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ─── Privacy & Authority Bridge Section ──────────────────────── */}
      <section id="privacy" className="relative py-20 px-4 sm:px-6">
        <div className="mx-auto max-w-6xl">
          <div className="grid items-center gap-12 lg:grid-cols-12">
            {/* Left: Privacy Highlights */}
            <div className="space-y-6 lg:col-span-7">
              <div className="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-3.5 py-1 text-xs font-bold text-emerald-800">
                <ShieldCheck className="size-4 text-emerald-600" />
                <span>100% Cryptographic Privacy by Design</span>
              </div>

              <h2 className="font-display text-3xl font-extrabold text-[#0F172A] sm:text-4xl">
                We Stop Scams Without Invading Your Personal Life.
              </h2>

              <p className="text-base leading-relaxed text-slate-600">
                Unlike commercial caller-ID apps that upload your entire contact book and listen to calls,
                <strong> ScamShield</strong> is built on non-negotiable citizen privacy guarantees:
              </p>

              <div className="space-y-3 pt-1">
                <div className="flex items-start gap-3 rounded-2xl border border-white/80 bg-white/85 p-4 shadow-sm">
                  <div className="flex size-6 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-emerald-700 text-xs font-bold">
                    ✓
                  </div>
                  <div>
                    <h4 className="text-sm font-bold text-slate-900">No INTERNET Permission in Mobile Client</h4>
                    <p className="text-xs text-slate-600 mt-0.5">
                      The ScamShield Android app has no internet permission. Inference happens strictly on your local CPU.
                      Your personal data cannot physically leave the device.
                    </p>
                  </div>
                </div>

                <div className="flex items-start gap-3 rounded-2xl border border-white/80 bg-white/85 p-4 shadow-sm">
                  <div className="flex size-6 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-emerald-700 text-xs font-bold">
                    ✓
                  </div>
                  <div>
                    <h4 className="text-sm font-bold text-slate-900">Zero Audio Surveillance</h4>
                    <p className="text-xs text-slate-600 mt-0.5">
                      We never record or tap into voice conversations. Our detection algorithms analyze structural and
                      behavioral events without listening to what you say.
                    </p>
                  </div>
                </div>

                <div className="flex items-start gap-3 rounded-2xl border border-white/80 bg-white/85 p-4 shadow-sm">
                  <div className="flex size-6 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-emerald-700 text-xs font-bold">
                    ✓
                  </div>
                  <div>
                    <h4 className="text-sm font-bold text-slate-900">No Contact Book Uploads</h4>
                    <p className="text-xs text-slate-600 mt-0.5">
                      Your address book stays private. Contacts are read locally only to ensure friends and family never
                      trigger false alarms.
                    </p>
                  </div>
                </div>
              </div>
            </div>

            {/* Right: RakshaCall Authority Portal Card */}
            <div className="lg:col-span-5">
              <div className="rounded-3xl border border-slate-800 bg-[#0A0F1D] p-8 text-white shadow-2xl shadow-slate-950/40">
                <div className="flex items-center gap-3.5">
                  <div className="flex size-11 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-500 to-purple-600 text-white shadow-md shadow-indigo-500/25">
                    <Lock className="size-5" />
                  </div>
                  <div>
                    <h3 className="font-display text-xl font-bold text-white">RakshaCall Authority</h3>
                    <p className="text-xs text-slate-400">Police & Cybercrime Analytics Console</p>
                  </div>
                </div>

                <p className="mt-5 text-sm leading-relaxed text-slate-300">
                  While citizens stay protected on their phones via <strong>ScamShield</strong>, verified police officers
                  and cybercrime investigators access the companion <strong>RakshaCall Dashboard</strong> to view live fraud
                  spikes, investigate syndicate clusters, and recommend carrier-level blocks.
                </p>

                <div className="mt-6 rounded-2xl border border-slate-800 bg-slate-900/80 p-4">
                  <div className="flex items-center justify-between text-xs font-semibold text-slate-400">
                    <span>Active Telemetry Status</span>
                    <span className="flex items-center gap-1.5 text-emerald-400">
                      <span className="size-2 rounded-full bg-emerald-400 animate-ping" /> Realtime Active
                    </span>
                  </div>
                  <div className="mt-2.5 flex items-center justify-between font-mono text-xs text-slate-300">
                    <span>Active Spike Circle: Delhi</span>
                    <span className="font-bold text-rose-400">Flagged</span>
                  </div>
                </div>

                <div className="mt-6">
                  <Link
                    href="/login"
                    className="flex w-full items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-blue-600 via-indigo-600 to-purple-600 px-5 py-3.5 text-sm font-bold text-white shadow-lg shadow-indigo-600/30 transition-all hover:scale-[1.02] active:scale-[0.98]"
                  >
                    <Lock className="size-4" />
                    <span>Enter RakshaCall Police Console</span>
                    <ArrowRight className="size-4" />
                  </Link>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ─── Footer ─────────────────────────────────────────────────── */}
      <footer className="border-t border-slate-200/80 bg-white/70 py-12 text-xs font-medium text-slate-500 backdrop-blur-md">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-6 px-4 sm:px-6 md:flex-row">
          <div className="flex items-center gap-2.5">
            <div className="flex size-6 items-center justify-center rounded-lg bg-indigo-600 text-white">
              <ShieldCheck className="size-4" />
            </div>
            <span>
              <strong className="text-slate-800">ScamShield</strong> • Built in 24 hours during a Hackathon at NIT Goa (2026)
            </span>
          </div>

          <div className="flex items-center gap-6">
            <a href={GITHUB_REPO_URL} target="_blank" rel="noreferrer" className="hover:text-slate-900">
              GitHub Repository
            </a>
            <a href={GITHUB_RELEASE_URL} target="_blank" rel="noreferrer" className="hover:text-slate-900">
              Download App (APK)
            </a>
            <Link href="/login" className="font-bold text-indigo-700 hover:text-indigo-950">
              RakshaCall Police Console 🔒
            </Link>
          </div>
        </div>
      </footer>
    </div>
  )
}
