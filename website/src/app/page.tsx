import Link from 'next/link'
import {
  ShieldCheck,
  Download,
  QrCode,
  Lock,
  ExternalLink,
  ArrowRight,
  CheckCircle2,
  Cpu,
  Users,
  Smartphone,
  Radio,
  Sparkles,
} from 'lucide-react'

const GITHUB_REPO_URL = 'https://github.com/snxhaSalgaonkar/scamshield'
const GITHUB_RELEASE_URL = 'https://github.com/snxhaSalgaonkar/scamshield/releases/latest'
const QR_CODE_URL =
  'https://api.qrserver.com/v1/create-qr-code/?size=220x220&margin=10&data=https://github.com/snxhaSalgaonkar/scamshield/releases/latest'

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-[#F8FAFC] text-slate-900 selection:bg-indigo-600 selection:text-white">
      {/* ─── Global Navbar ────────────────────────────────────────────── */}
      <header className="sticky top-0 z-50 border-b border-slate-200/80 bg-white/80 backdrop-blur-md">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
          <Link href="/" className="flex items-center gap-3 transition-opacity hover:opacity-90">
            <div className="flex size-10 items-center justify-center rounded-xl bg-indigo-600 text-white shadow-md shadow-indigo-600/25">
              <ShieldCheck className="size-6" />
            </div>
            <div>
              <div className="flex items-baseline gap-2">
                <span className="font-display text-2xl font-bold tracking-tight text-slate-900">ScamShield</span>
                <span className="text-xs font-semibold text-indigo-600">v1.0</span>
              </div>
              <p className="text-[11px] font-medium text-slate-500">Citizen Scam Call & SMS Guard</p>
            </div>
          </Link>

          <nav className="hidden items-center gap-8 md:flex">
            <a href="#features" className="text-sm font-medium text-slate-600 transition-colors hover:text-slate-900">
              Features
            </a>
            <a href="#how-it-works" className="text-sm font-medium text-slate-600 transition-colors hover:text-slate-900">
              3-Layer AI
            </a>
            <a href="#privacy" className="text-sm font-medium text-slate-600 transition-colors hover:text-slate-900">
              Privacy First
            </a>
            <a
              href={GITHUB_REPO_URL}
              target="_blank"
              rel="noreferrer"
              className="flex items-center gap-1.5 text-sm font-medium text-slate-600 transition-colors hover:text-slate-900"
            >
              GitHub <ExternalLink className="size-3.5 opacity-60" />
            </a>
          </nav>

          <div className="flex items-center gap-3">
            <Link
              href="/login"
              className="flex items-center gap-2 rounded-xl border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 shadow-sm transition-all hover:border-slate-400 hover:bg-slate-50 hover:text-slate-900"
            >
              <Lock className="size-3.5 text-indigo-600" />
              <span>RakshaCall Console</span>
              <ArrowRight className="size-3.5 opacity-60" />
            </Link>
          </div>
        </div>
      </header>

      {/* ─── Hero Section ────────────────────────────────────────────── */}
      <section className="relative overflow-hidden px-6 pt-16 pb-20 md:pt-24 md:pb-28">
        {/* Soft background ambient glows */}
        <div className="pointer-events-none absolute -top-40 left-1/2 -z-10 h-[500px] w-[800px] -translate-x-1/2 rounded-full bg-indigo-100/70 blur-3xl" />
        <div className="pointer-events-none absolute top-60 right-10 -z-10 h-[400px] w-[500px] rounded-full bg-blue-100/50 blur-3xl" />

        <div className="mx-auto max-w-7xl">
          <div className="grid items-center gap-12 lg:grid-cols-12">
            {/* Left Column: Headline & Action */}
            <div className="space-y-6 lg:col-span-7">
              <div className="inline-flex items-center gap-2 rounded-full border border-indigo-200 bg-indigo-50/80 px-3.5 py-1.5 text-xs font-semibold text-indigo-700">
                <Sparkles className="size-3.5 text-indigo-600" />
                <span>On-Device Machine Learning • 100% Offline Inference</span>
              </div>

              <h1 className="font-display text-4xl leading-[1.12] font-bold tracking-tight text-slate-900 sm:text-5xl lg:text-6xl">
                Stop Digital Arrest & Phone Scams{' '}
                <span className="text-transparent bg-clip-text bg-gradient-to-r from-indigo-600 to-blue-600">
                  While They Happen.
                </span>
              </h1>

              <p className="max-w-2xl text-lg leading-relaxed text-slate-600 sm:text-xl">
                <strong>ScamShield</strong> is a citizen-first Android application that intercepts fake police extortion,
                OTP theft, remote-screen takeover (AnyDesk), and phishing links in real time.
                <span className="block mt-1 font-medium text-slate-700">
                  Runs 100% on your phone. Zero call audio recorded. No internet access required.
                </span>
              </p>

              <div className="flex flex-wrap items-center gap-4 pt-2">
                <a
                  href={GITHUB_RELEASE_URL}
                  target="_blank"
                  rel="noreferrer"
                  className="flex items-center gap-3 rounded-xl bg-indigo-600 px-6 py-3.5 text-base font-semibold text-white shadow-lg shadow-indigo-600/25 transition-all hover:bg-indigo-700 hover:scale-[1.02] active:scale-[0.98]"
                >
                  <Download className="size-5" />
                  <span>Download ScamShield (APK)</span>
                </a>

                <Link
                  href="/login"
                  className="flex items-center gap-2.5 rounded-xl border border-slate-300 bg-white px-6 py-3.5 text-base font-semibold text-slate-700 shadow-sm transition-all hover:bg-slate-50 hover:border-slate-400"
                >
                  <Lock className="size-5 text-indigo-600" />
                  <span>RakshaCall Authority Portal</span>
                </Link>
              </div>

              <div className="flex flex-wrap items-center gap-6 pt-3 text-xs font-medium text-slate-500">
                <span className="flex items-center gap-1.5">
                  <CheckCircle2 className="size-4 text-emerald-600" /> Free & Open Source
                </span>
                <span className="flex items-center gap-1.5">
                  <CheckCircle2 className="size-4 text-emerald-600" /> Android 8.0 to 15
                </span>
                <span className="flex items-center gap-1.5">
                  <CheckCircle2 className="size-4 text-emerald-600" /> Zero Audio Tapping
                </span>
              </div>
            </div>

            {/* Right Column: Clean QR Code Card */}
            <div className="lg:col-span-5">
              <div className="relative mx-auto max-w-sm rounded-3xl border border-slate-200/90 bg-white p-7 shadow-xl shadow-slate-200/60">
                <div className="flex items-center justify-between border-b border-slate-100 pb-4">
                  <div className="flex items-center gap-2.5">
                    <Smartphone className="size-5 text-indigo-600" />
                    <div>
                      <h3 className="text-sm font-semibold text-slate-900">Direct Phone Install</h3>
                      <p className="text-[11px] text-slate-500">ScamShield v1.0.0 • Verified APK</p>
                    </div>
                  </div>
                  <span className="rounded-full bg-emerald-50 px-2.5 py-0.5 text-[11px] font-semibold text-emerald-700 border border-emerald-200">
                    Live
                  </span>
                </div>

                <div className="my-6 flex flex-col items-center justify-center rounded-2xl bg-slate-50 p-4 border border-slate-100">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={QR_CODE_URL}
                    alt="Scan QR code to install ScamShield APK"
                    className="size-48 rounded-xl object-contain shadow-sm"
                  />
                  <p className="mt-3 flex items-center gap-1.5 text-xs font-semibold text-slate-700">
                    <QrCode className="size-3.5 text-indigo-600" /> Scan with phone camera to download
                  </p>
                </div>

                <div className="rounded-xl bg-slate-50 border border-slate-200/60 p-3 text-center text-xs text-slate-600">
                  Judges & evaluators: Scan with your smartphone to download and test ScamShield instantly.
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ─── Metric Strip ────────────────────────────────────────────── */}
      <section className="border-y border-slate-200/80 bg-white py-10">
        <div className="mx-auto grid max-w-7xl grid-cols-2 gap-8 px-6 md:grid-cols-4">
          <div className="space-y-1">
            <div className="font-display text-3xl font-bold text-slate-900 sm:text-4xl">₹1,750+ Cr</div>
            <p className="text-xs text-slate-500 font-medium">Estimated cyber fraud loss in India (I4C)</p>
          </div>
          <div className="space-y-1">
            <div className="font-display text-3xl font-bold text-indigo-600 sm:text-4xl">&lt; 2.0 s</div>
            <p className="text-xs text-slate-500 font-medium">On-device ML call risk calculation</p>
          </div>
          <div className="space-y-1">
            <div className="font-display text-3xl font-bold text-emerald-600 sm:text-4xl">0 Bytes</div>
            <p className="text-xs text-slate-500 font-medium">Call audio or SMS uploaded to cloud</p>
          </div>
          <div className="space-y-1">
            <div className="font-display text-3xl font-bold text-slate-900 sm:text-4xl">3 Layers</div>
            <p className="text-xs text-slate-500 font-medium">Content ML + Reputation + Correlation</p>
          </div>
        </div>
      </section>

      {/* ─── Three-Layer AI Architecture ─────────────────────────────── */}
      <section id="how-it-works" className="py-24">
        <div className="mx-auto max-w-7xl px-6">
          <div className="text-center">
            <span className="text-xs font-bold tracking-wider text-indigo-600 uppercase">
              How ScamShield Protects You
            </span>
            <h2 className="mt-2 font-display text-3xl font-bold text-slate-900 sm:text-4xl">
              Other solutions detect scam words.
              <br />
              <span className="text-transparent bg-clip-text bg-gradient-to-r from-indigo-600 to-blue-600">
                ScamShield detects scam behavior.
              </span>
            </h2>
            <p className="mx-auto mt-4 max-w-2xl text-sm leading-relaxed text-slate-600">
              Scammers continuously rotate phone numbers and tweak their scripts, but they cannot hide coordinated
              tactics. ScamShield correlates three independent signals:
            </p>
          </div>

          <div className="mt-14 grid gap-8 md:grid-cols-3">
            {/* Layer 1 */}
            <div className="rounded-2xl border border-slate-200 bg-white p-7 shadow-sm transition-all hover:shadow-md hover:border-indigo-300">
              <div className="flex size-12 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600 border border-indigo-100">
                <Cpu className="size-6" />
              </div>
              <span className="mt-5 inline-block text-xs font-bold text-indigo-600">LAYER 1</span>
              <h3 className="mt-1 font-display text-xl font-bold text-slate-900">On-Device Content ML</h3>
              <p className="mt-3 text-sm leading-relaxed text-slate-600">
                Trained neural classifiers evaluate incoming SMS texts and link domains in memory. Detects urgency,
                impersonation, and fake courier/police links without any text leaving the phone.
              </p>
              <div className="mt-5 border-t border-slate-100 pt-4 text-xs text-slate-500 space-y-1">
                <div>• PhiUSIIL Phishing Link Model</div>
                <div>• On-Device SMS Spam & Smishing NLP</div>
              </div>
            </div>

            {/* Layer 2 */}
            <div className="rounded-2xl border border-slate-200 bg-white p-7 shadow-sm transition-all hover:shadow-md hover:border-indigo-300">
              <div className="flex size-12 items-center justify-center rounded-xl bg-blue-50 text-blue-600 border border-blue-100">
                <Users className="size-6" />
              </div>
              <span className="mt-5 inline-block text-xs font-bold text-blue-600">LAYER 2</span>
              <h3 className="mt-1 font-display text-xl font-bold text-slate-900">Sybil-Resistant Reputation</h3>
              <p className="mt-3 text-sm leading-relaxed text-slate-600">
                A caller is flagged only when 3+ independent devices confirm reports. It also analyzes number-series
                clusters (e.g. 98112-XXXXX) to target entire scam syndicates rather than single SIMs.
              </p>
              <div className="mt-5 border-t border-slate-100 pt-4 text-xs text-slate-500 space-y-1">
                <div>• Number-series gang cluster detection</div>
                <div>• 3+ Distinct reporter verification gate</div>
              </div>
            </div>

            {/* Layer 3 */}
            <div className="rounded-2xl border border-indigo-200 bg-gradient-to-b from-indigo-50/50 to-white p-7 shadow-sm transition-all hover:shadow-md hover:border-indigo-400">
              <div className="flex size-12 items-center justify-center rounded-xl bg-indigo-600 text-white">
                <Radio className="size-6" />
              </div>
              <span className="mt-5 inline-block text-xs font-bold text-indigo-700">LAYER 3 (CRITICAL)</span>
              <h3 className="mt-1 font-display text-xl font-bold text-slate-900">Temporal Event Correlation</h3>
              <p className="mt-3 text-sm leading-relaxed text-slate-600">
                Catches the fatal moment of fraud: an unexpected bank OTP SMS arrives while you are on an active unknown
                call, or a screen-sharing app (AnyDesk, TeamViewer, RustDesk) is launched mid-call.
              </p>
              <div className="mt-5 border-t border-indigo-100 pt-4 text-xs text-slate-600 space-y-1">
                <div>• Mid-call OTP / UPI pressure intercept</div>
                <div>• Remote desktop takeover warning</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ─── Privacy Guarantee Section ───────────────────────────────── */}
      <section id="privacy" className="border-t border-slate-200 bg-slate-50 py-24">
        <div className="mx-auto max-w-7xl px-6">
          <div className="grid items-center gap-12 lg:grid-cols-12">
            <div className="space-y-6 lg:col-span-7">
              <div className="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-800">
                <ShieldCheck className="size-4 text-emerald-600" />
                <span>100% Cryptographic Privacy by Design</span>
              </div>

              <h2 className="font-display text-3xl font-bold text-slate-900 sm:text-4xl">
                We Stop Scams Without Invading Your Personal Life.
              </h2>

              <p className="text-base leading-relaxed text-slate-600">
                Unlike commercial Truecaller-style apps that upload your entire contact book and listen to calls,
                <strong> ScamShield</strong> is built on non-negotiable citizen privacy guarantees:
              </p>

              <div className="space-y-4 pt-2">
                <div className="flex items-start gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-xs">
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

                <div className="flex items-start gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-xs">
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

                <div className="flex items-start gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-xs">
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

            {/* Authority Link Card */}
            <div className="lg:col-span-5">
              <div className="rounded-3xl border border-slate-200 bg-white p-8 shadow-xl shadow-slate-200/50">
                <div className="flex items-center gap-3">
                  <div className="flex size-11 items-center justify-center rounded-xl bg-slate-900 text-white">
                    <Lock className="size-5 text-indigo-400" />
                  </div>
                  <div>
                    <h3 className="font-display text-xl font-bold text-slate-900">RakshaCall Authority</h3>
                    <p className="text-xs text-slate-500">Law Enforcement Analytics Portal</p>
                  </div>
                </div>

                <p className="mt-5 text-sm leading-relaxed text-slate-600">
                  While citizens stay protected on their phones via <strong>ScamShield</strong>, verified police and
                  cybercrime investigators access the companion <strong>RakshaCall Dashboard</strong> to view live fraud
                  spikes, investigate syndicate clusters, and recommend carrier-level blocks.
                </p>

                <div className="mt-6 rounded-2xl border border-slate-200 bg-slate-50 p-4">
                  <div className="flex items-center justify-between text-xs text-slate-500 font-medium">
                    <span>Active Telemetry Status</span>
                    <span className="flex items-center gap-1.5 text-emerald-600">
                      <span className="size-2 rounded-full bg-emerald-500 animate-ping" /> Realtime Active
                    </span>
                  </div>
                  <div className="mt-2.5 flex items-center justify-between font-mono text-xs text-slate-700">
                    <span>Active Spike Circle: Delhi</span>
                    <span className="font-semibold text-rose-600">Flagged</span>
                  </div>
                </div>

                <div className="mt-6">
                  <Link
                    href="/login"
                    className="flex w-full items-center justify-center gap-2 rounded-xl bg-slate-900 px-5 py-3 text-sm font-semibold text-white transition-all hover:bg-slate-800"
                  >
                    <Lock className="size-4 text-indigo-400" />
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
      <footer className="border-t border-slate-200 bg-white py-12 text-xs text-slate-500">
        <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-6 px-6 md:flex-row">
          <div className="flex items-center gap-2.5">
            <div className="flex size-6 items-center justify-center rounded-lg bg-indigo-600 text-white">
              <ShieldCheck className="size-4" />
            </div>
            <span>
              <strong>ScamShield</strong> • National Institute of Technology Goa (NIT Goa) — 2026
            </span>
          </div>

          <div className="flex items-center gap-6">
            <a href={GITHUB_REPO_URL} target="_blank" rel="noreferrer" className="hover:text-slate-900">
              GitHub Repository
            </a>
            <a href={GITHUB_RELEASE_URL} target="_blank" rel="noreferrer" className="hover:text-slate-900">
              Download App (APK)
            </a>
            <Link href="/login" className="font-semibold text-indigo-600 hover:text-indigo-800">
              RakshaCall Police Console 🔒
            </Link>
          </div>
        </div>
      </footer>
    </div>
  )
}
