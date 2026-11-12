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
} from 'lucide-react'
import { Wordmark } from '@/components/Wordmark'
import { Crest } from '@/components/Crest'

const GITHUB_REPO_URL = 'https://github.com/snxhaSalgaonkar/scamshield'
const GITHUB_RELEASE_URL = 'https://github.com/snxhaSalgaonkar/scamshield/releases/latest'
const QR_CODE_URL =
  'https://api.qrserver.com/v1/create-qr-code/?size=220x220&margin=10&data=https://github.com/snxhaSalgaonkar/scamshield/releases/latest'

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-[#07203F] text-white selection:bg-[#A65E46] selection:text-white">
      {/* ─── Global Navbar ────────────────────────────────────────────── */}
      <header className="sticky top-0 z-50 border-b border-white/10 bg-[#07203F]/85 backdrop-blur-md">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
          <Link href="/" className="flex items-center gap-3 transition-opacity hover:opacity-90">
            <Wordmark size="md" subtitle="Citizen Protection & Threat Intelligence" />
          </Link>

          <nav className="hidden items-center gap-8 md:flex">
            <a href="#features" className="text-sm font-medium text-white/70 transition-colors hover:text-white">
              Features
            </a>
            <a href="#how-it-works" className="text-sm font-medium text-white/70 transition-colors hover:text-white">
              Three-Layer AI
            </a>
            <a href="#privacy" className="text-sm font-medium text-white/70 transition-colors hover:text-white">
              Privacy First
            </a>
            <a
              href={GITHUB_REPO_URL}
              target="_blank"
              rel="noreferrer"
              className="flex items-center gap-1.5 text-sm font-medium text-white/70 transition-colors hover:text-white"
            >
              GitHub <ExternalLink className="size-3.5 opacity-60" />
            </a>
          </nav>

          <div className="flex items-center gap-3">
            <Link
              href="/login"
              className="flex items-center gap-2 rounded-xl border border-[#A65E46]/60 bg-[#A65E46]/15 px-4 py-2 text-sm font-semibold text-[#D9AA90] transition-all hover:border-[#A65E46] hover:bg-[#A65E46]/30 hover:text-white"
            >
              <Lock className="size-3.5 text-[#A65E46]" />
              <span>Police Console</span>
              <ArrowRight className="size-3.5" />
            </Link>
          </div>
        </div>
      </header>

      {/* ─── Hero Section ────────────────────────────────────────────── */}
      <section className="relative overflow-hidden px-6 pt-16 pb-20 md:pt-24 md:pb-28">
        <div className="pointer-events-none absolute -top-40 left-1/2 -z-10 h-[500px] w-[800px] -translate-x-1/2 rounded-full bg-radial from-[#A65E46]/20 via-transparent to-transparent blur-3xl" />

        <div className="mx-auto max-w-7xl">
          <div className="grid items-center gap-12 lg:grid-cols-12">
            {/* Left Column: Text & CTAs */}
            <div className="space-y-6 lg:col-span-7">
              <div className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/5 px-3.5 py-1.5 text-xs font-semibold text-[#D9AA90] backdrop-blur-sm">
                <span className="flex size-2 rounded-full bg-emerald-400 animate-pulse" />
                <span>On-Device Machine Learning • No Internet Required</span>
              </div>

              <h1 className="font-display text-4xl leading-[1.1] font-bold tracking-tight text-white sm:text-5xl lg:text-6xl">
                Stop Digital Arrest & Phone Scams{' '}
                <span className="text-transparent bg-clip-text bg-gradient-to-r from-[#D9AA90] to-[#A65E46]">
                  While They Happen.
                </span>
              </h1>

              <p className="max-w-2xl text-lg leading-relaxed text-white/70 sm:text-xl">
                A citizen-side Android guard that intercepts fake police extortion, OTP theft, remote desktop takeover,
                and smishing links in real-time. <strong>100% on-device inference. Zero call audio recorded.</strong>
              </p>

              <div className="flex flex-wrap items-center gap-4 pt-2">
                <a
                  href={GITHUB_RELEASE_URL}
                  target="_blank"
                  rel="noreferrer"
                  className="flex items-center gap-3 rounded-xl bg-[#A65E46] px-6 py-3.5 text-base font-semibold text-white shadow-lg shadow-[#A65E46]/30 transition-all hover:bg-[#8e4f3a] hover:scale-[1.02] active:scale-[0.98]"
                >
                  <Download className="size-5" />
                  <span>Download App (APK)</span>
                </a>

                <Link
                  href="/login"
                  className="flex items-center gap-2.5 rounded-xl border border-white/20 bg-white/5 px-6 py-3.5 text-base font-medium text-white transition-all hover:bg-white/10 hover:border-white/30"
                >
                  <ShieldCheck className="size-5 text-[#D9AA90]" />
                  <span>Authority Portal</span>
                </Link>
              </div>

              <div className="flex flex-wrap items-center gap-6 pt-3 text-xs text-white/50">
                <span className="flex items-center gap-1.5">
                  <CheckCircle2 className="size-4 text-emerald-400" /> Free & Open Source
                </span>
                <span className="flex items-center gap-1.5">
                  <CheckCircle2 className="size-4 text-emerald-400" /> Android 8.0 to 15
                </span>
                <span className="flex items-center gap-1.5">
                  <CheckCircle2 className="size-4 text-emerald-400" /> Zero Battery Drain
                </span>
              </div>
            </div>

            {/* Right Column: QR Code Card & Phone Preview */}
            <div className="lg:col-span-5">
              <div className="relative mx-auto max-w-sm rounded-3xl border border-white/15 bg-white/5 p-6 backdrop-blur-xl shadow-2xl">
                <div className="flex items-center justify-between border-b border-white/10 pb-4">
                  <div className="flex items-center gap-2.5">
                    <Smartphone className="size-5 text-[#D9AA90]" />
                    <div>
                      <h3 className="text-sm font-semibold text-white">Direct Phone Install</h3>
                      <p className="text-[11px] text-white/50">Release v1.0.0 • Verified Binary</p>
                    </div>
                  </div>
                  <span className="rounded-md bg-emerald-500/20 px-2 py-0.5 text-[11px] font-semibold text-emerald-300">
                    Live Build
                  </span>
                </div>

                <div className="my-6 flex flex-col items-center justify-center rounded-2xl bg-white p-4 shadow-inner">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={QR_CODE_URL}
                    alt="Scan QR code to download ScamShield APK"
                    className="size-48 rounded-lg object-contain"
                  />
                  <p className="mt-2.5 flex items-center gap-1.5 text-xs font-semibold text-gray-800">
                    <QrCode className="size-3.5 text-[#07203F]" /> Scan with phone camera
                  </p>
                </div>

                <div className="rounded-xl border border-white/10 bg-black/20 p-3 text-center text-xs text-white/70">
                  Judges & evaluators: Scan to install on any Android device immediately without Play Store sign-in.
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ─── Metric Strip ────────────────────────────────────────────── */}
      <section className="border-y border-white/10 bg-black/25 py-8">
        <div className="mx-auto grid max-w-7xl grid-cols-2 gap-6 px-6 md:grid-cols-4">
          <div className="space-y-1">
            <div className="font-display text-3xl font-bold text-white sm:text-4xl">₹1,750+ Cr</div>
            <p className="text-xs text-white/60">Estimated loss to cyber extortion in India (I4C)</p>
          </div>
          <div className="space-y-1">
            <div className="font-display text-3xl font-bold text-emerald-400 sm:text-4xl">&lt; 2.0 s</div>
            <p className="text-xs text-white/60">On-device ML call risk calculation</p>
          </div>
          <div className="space-y-1">
            <div className="font-display text-3xl font-bold text-[#D9AA90] sm:text-4xl">0 Bytes</div>
            <p className="text-xs text-white/60">Audio or SMS content uploaded to cloud</p>
          </div>
          <div className="space-y-1">
            <div className="font-display text-3xl font-bold text-white sm:text-4xl">3 Layers</div>
            <p className="text-xs text-white/60">Content NLP + Reputation + Event Correlation</p>
          </div>
        </div>
      </section>

      {/* ─── Three-Layer AI Architecture ─────────────────────────────── */}
      <section id="how-it-works" className="py-20">
        <div className="mx-auto max-w-7xl px-6">
          <div className="text-center">
            <span className="text-xs font-semibold tracking-wider text-[#D9AA90] uppercase">
              How ScamShield Protects You
            </span>
            <h2 className="mt-2 font-display text-3xl font-bold text-white sm:text-4xl">
              Other solutions detect scam words.
              <br />
              <span className="text-transparent bg-clip-text bg-gradient-to-r from-[#D9AA90] to-[#A65E46]">
                We detect scam behavior.
              </span>
            </h2>
            <p className="mx-auto mt-4 max-w-2xl text-sm leading-relaxed text-white/65">
              Scammers change their scripts, but they cannot hide coordinated multi-vector tactics. ScamShield evaluates
              three independent layers to prevent false alarms.
            </p>
          </div>

          <div className="mt-14 grid gap-8 md:grid-cols-3">
            {/* Layer 1 */}
            <div className="rounded-2xl border border-white/10 bg-white/5 p-7 backdrop-blur-sm transition-all hover:border-[#D9AA90]/40 hover:-translate-y-1">
              <div className="flex size-12 items-center justify-center rounded-xl bg-[#A65E46]/20 text-[#D9AA90]">
                <Cpu className="size-6 text-[#A65E46]" />
              </div>
              <span className="mt-5 inline-block text-xs font-bold text-[#D9AA90]">LAYER 1</span>
              <h3 className="mt-1 font-display text-xl font-semibold text-white">On-Device Content ML</h3>
              <p className="mt-3 text-sm leading-relaxed text-white/65">
                Local lightweight ML models scan incoming SMS and message tactics for phishing patterns, high-urgency
                coercion, and fake parcel/KYC links. No text ever leaves the device.
              </p>
              <div className="mt-5 border-t border-white/10 pt-4 text-xs text-white/50">
                • PhiUSIIL Link Classifier<br />• SMS Spam/Smishing NLP Head
              </div>
            </div>

            {/* Layer 2 */}
            <div className="rounded-2xl border border-white/10 bg-white/5 p-7 backdrop-blur-sm transition-all hover:border-[#D9AA90]/40 hover:-translate-y-1">
              <div className="flex size-12 items-center justify-center rounded-xl bg-[#07203F] text-[#D9AA90] border border-white/10">
                <Users className="size-6 text-[#D9AA90]" />
              </div>
              <span className="mt-5 inline-block text-xs font-bold text-[#D9AA90]">LAYER 2</span>
              <h3 className="mt-1 font-display text-xl font-semibold text-white">Crowd Reputation & Series</h3>
              <p className="mt-3 text-sm leading-relaxed text-white/65">
                Sybil-resistant reputation checking. Numbers are flagged only when 3+ independent devices confirm
                fraudulent activity, tracking entire number-series gangs (e.g. 98112-XXXXX).
              </p>
              <div className="mt-5 border-t border-white/10 pt-4 text-xs text-white/50">
                • Number series cluster detection<br />• 3+ Device verification threshold
              </div>
            </div>

            {/* Layer 3 */}
            <div className="rounded-2xl border border-[#A65E46]/50 bg-gradient-to-b from-[#A65E46]/10 to-transparent p-7 backdrop-blur-sm transition-all hover:border-[#A65E46] hover:-translate-y-1">
              <div className="flex size-12 items-center justify-center rounded-xl bg-[#A65E46] text-white">
                <Radio className="size-6" />
              </div>
              <span className="mt-5 inline-block text-xs font-bold text-amber-300">LAYER 3 (CRITICAL)</span>
              <h3 className="mt-1 font-display text-xl font-semibold text-white">Temporal Event Correlation</h3>
              <p className="mt-3 text-sm leading-relaxed text-white/65">
                Detects the fatal scam moment: an unexpected bank OTP arrives mid-call, or a remote access tool (AnyDesk,
                TeamViewer, RustDesk) is launched while on the phone with an unknown caller.
              </p>
              <div className="mt-5 border-t border-white/10 pt-4 text-xs text-white/50">
                • Mid-call OTP SMS detection<br />• Remote desktop app launch warning
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ─── Privacy by Design ───────────────────────────────────────── */}
      <section id="privacy" className="border-t border-white/10 bg-black/20 py-20">
        <div className="mx-auto max-w-7xl px-6">
          <div className="grid items-center gap-12 lg:grid-cols-12">
            <div className="space-y-6 lg:col-span-6">
              <div className="inline-flex items-center gap-2 rounded-md bg-emerald-500/15 px-3 py-1 text-xs font-semibold text-emerald-300">
                <ShieldCheck className="size-4" /> 100% Privacy by Design
              </div>

              <h2 className="font-display text-3xl font-bold text-white sm:text-4xl">
                We Protect Your Calls Without Invading Your Privacy.
              </h2>

              <p className="text-base leading-relaxed text-white/70">
                Unlike commercial caller-ID apps that upload your entire contact book and listen to conversations,
                ScamShield was built with cryptographic privacy guarantees:
              </p>

              <div className="space-y-4 pt-2">
                <div className="flex items-start gap-3">
                  <div className="flex size-6 shrink-0 items-center justify-center rounded-full bg-emerald-500/20 text-emerald-400">
                    ✓
                  </div>
                  <div>
                    <h4 className="text-sm font-semibold text-white">No INTERNET Permission in Mobile Client</h4>
                    <p className="text-xs text-white/60">
                      The core detection and ML models run completely isolated on the phone. The client cannot send
                      your personal data anywhere.
                    </p>
                  </div>
                </div>

                <div className="flex items-start gap-3">
                  <div className="flex size-6 shrink-0 items-center justify-center rounded-full bg-emerald-500/20 text-emerald-400">
                    ✓
                  </div>
                  <div>
                    <h4 className="text-sm font-semibold text-white">Zero Call Audio Surveillance</h4>
                    <p className="text-xs text-white/60">
                      We never record or tap into voice audio. Android security blocks eavesdropping, and our algorithm
                      detects fraud through structural signals.
                    </p>
                  </div>
                </div>

                <div className="flex items-start gap-3">
                  <div className="flex size-6 shrink-0 items-center justify-center rounded-full bg-emerald-500/20 text-emerald-400">
                    ✓
                  </div>
                  <div>
                    <h4 className="text-sm font-semibold text-white">No Contact Uploads or Phone Books</h4>
                    <p className="text-xs text-white/60">
                      Contacts are checked only to ensure friends and family never trigger false alerts. No contacts are
                      stored or uploaded.
                    </p>
                  </div>
                </div>
              </div>
            </div>

            <div className="lg:col-span-6">
              <div className="rounded-3xl border border-white/15 bg-gradient-to-br from-white/10 to-white/5 p-8 backdrop-blur-xl">
                <div className="flex items-center gap-4">
                  <Crest className="size-14" />
                  <div>
                    <h3 className="font-display text-2xl font-bold text-white">RakshaCall Authority Console</h3>
                    <p className="text-sm text-white/60">Law Enforcement Crime Analytics Portal</p>
                  </div>
                </div>

                <p className="mt-6 text-sm leading-relaxed text-white/75">
                  While citizens stay protected locally, police and cybercrime investigators use the linked{' '}
                  <strong>RakshaCall Dashboard</strong> to view live fraud spikes, analyze gang clusters, and issue
                  swift telecom series blocks.
                </p>

                <div className="mt-6 rounded-2xl border border-white/10 bg-black/40 p-4">
                  <div className="flex items-center justify-between text-xs text-white/60">
                    <span>Active Telemetry</span>
                    <span className="flex items-center gap-1.5 text-emerald-400">
                      <span className="size-2 rounded-full bg-emerald-400 animate-ping" /> Realtime Active
                    </span>
                  </div>
                  <div className="mt-3 flex items-center justify-between font-mono text-sm text-[#D9AA90]">
                    <span>Gang Cluster #98112-X</span>
                    <span className="text-xs text-red-400">Flagged (14 calls)</span>
                  </div>
                </div>

                <div className="mt-6">
                  <Link
                    href="/login"
                    className="flex w-full items-center justify-center gap-2 rounded-xl bg-white/10 px-5 py-3 text-sm font-semibold text-white transition-all hover:bg-white/20 hover:text-white"
                  >
                    <Lock className="size-4 text-[#A65E46]" />
                    <span>Enter Police / Authority Console</span>
                    <ArrowRight className="size-4" />
                  </Link>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ─── Footer ─────────────────────────────────────────────────── */}
      <footer className="border-t border-white/10 py-12 text-xs text-white/50">
        <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-6 px-6 md:flex-row">
          <div className="flex items-center gap-3">
            <Crest className="size-7 opacity-75" />
            <span>
              <strong>ScamShield (RakshaCall)</strong> • National Institute of Technology Goa (NIT Goa) — 2026
            </span>
          </div>

          <div className="flex items-center gap-6">
            <a href={GITHUB_REPO_URL} target="_blank" rel="noreferrer" className="hover:text-white">
              GitHub Repository
            </a>
            <a href={GITHUB_RELEASE_URL} target="_blank" rel="noreferrer" className="hover:text-white">
              Download APK
            </a>
            <Link href="/login" className="text-[#D9AA90] hover:text-white">
              Police Portal Login 🔒
            </Link>
          </div>
        </div>
      </footer>
    </div>
  )
}
