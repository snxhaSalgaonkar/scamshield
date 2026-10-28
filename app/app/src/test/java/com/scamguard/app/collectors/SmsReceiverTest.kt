package com.scamguard.app.collectors

import com.scamguard.app.bus.EventBus
import com.scamguard.app.bus.SignalEvent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SmsReceiverTest {

    @Test fun `debit with link emits debit and link, nothing text-bearing`() = runTest {
        val bus = EventBus()
        val got = async(start = CoroutineStart.UNDISPATCHED) { bus.events.take(2).toList() }
        SmsReceiver.emit(bus, SmsParser.classify("Rs 500 debited from A/c 1. Not you? visit demo-help.xyz"), senderTrusted = false, ts = 10)
        val events = got.await().map { it.event }
        assertEquals(listOf(SignalEvent.DebitReceived(10, 500_00, null, null), SignalEvent.LinkReceived(10)), events)
    }

    @Test fun `OTP carries only whether the sender is trusted`() = runTest {
        val bus = EventBus()
        val got = async(start = CoroutineStart.UNDISPATCHED) { bus.events.take(1).toList() }
        SmsReceiver.emit(bus, SmsParser.classify("OTP 551234 for login"), senderTrusted = true, ts = 5)
        assertEquals(SignalEvent.OtpReceived(5, senderTrusted = true), got.await().single().event)
    }
}
