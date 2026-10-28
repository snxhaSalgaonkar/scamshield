package com.scamguard.app.engine

import com.scamguard.app.bus.SignalEvent
import java.time.ZoneOffset

/** 2024-01-15 12:00:00 UTC: midday, so the odd-hours rule stays quiet unless a test wants it. */
const val T0 = 1_705_320_000_000L
const val MIN = 60_000L

fun ringing(
    ts: Long = T0,
    unknown: Boolean = true,
    international: Boolean = false,
    hidden: Boolean = false,
    hash: String? = "hash-1",
) = SignalEvent.CallRinging(ts, hash, "+91 98", unknown, international, hidden, "+91 98•••••21")

/** Builds a window from a ringing event plus further events, through the real state machine. */
fun window(ring: SignalEvent.CallRinging = ringing(), vararg events: SignalEvent, config: RiskConfig = RiskConfig()): CallWindow =
    events.fold(CallWindow.open("w1", ring)) { w, e -> w.accept(e, config) }

fun evaluate(
    w: CallWindow,
    caller: CallerContext = CallerContext(),
    history: TransactionHistory = TransactionHistory(),
    voice: Boolean = false,
    now: Long? = null,
    config: RiskConfig = RiskConfig(),
): Decision = RiskEngine.evaluate(
    RiskInput(w, caller, history, voice, now ?: (w.events.maxOf { it.ts }), ZoneOffset.UTC),
    config,
)

fun otp(ts: Long = T0 + MIN, trusted: Boolean = true) = SignalEvent.OtpReceived(ts, trusted)
fun debit(ts: Long = T0 + 2 * MIN, amount: Long = 2_000_00, payee: String? = null, balance: Long? = null) =
    SignalEvent.DebitReceived(ts, amount, payee, balance)
