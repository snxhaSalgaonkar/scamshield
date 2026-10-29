package com.scamguard.app.collectors

import com.scamguard.app.bus.ScreenCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCallTextTest {

    private fun parse(
        text: String?,
        pkg: String = "com.whatsapp",
        title: String? = "+91 98112 55501",
        callType: Int? = null,
        category: Boolean = true,
        chrono: Boolean = false,
    ) = VideoCallParser.parse(pkg, category, callType, title, text, null, chrono)

    @Test fun `whatsapp incoming and ongoing video calls`() {
        assertEquals(VideoCallPhase.INCOMING, parse("Incoming video call")?.phase)
        assertEquals(VideoCallPhase.ONGOING, parse("Ongoing video call")?.phase)
        assertEquals("+91 98112 55501", parse("Incoming video call")?.caller)
    }

    @Test fun `call style type wins over text`() {
        assertEquals(VideoCallPhase.ONGOING, parse("Incoming video call", callType = VideoCallParser.CALL_TYPE_ONGOING)?.phase)
    }

    @Test fun `a running timer means the call is connected`() {
        assertEquals(VideoCallPhase.ONGOING, parse("WhatsApp video call", chrono = true)?.phase)
    }

    @Test fun `voice calls, chats and other apps are ignored`() {
        assertNull(parse("Incoming voice call"))
        assertNull(parse("Sent you a video", category = false))
        assertNull(parse("Incoming video call", pkg = "com.example.chat"))
    }

    private fun voice(
        text: String?,
        pkg: String = "com.whatsapp",
        title: String? = "+91 98112 55501",
        callType: Int? = null,
        category: Boolean = true,
        chrono: Boolean = false,
    ) = VideoCallParser.parseVoice(pkg, category, callType, title, text, null, chrono)

    @Test fun `whatsapp incoming and ongoing voice calls`() {
        assertEquals(VideoCallPhase.INCOMING, voice("Incoming voice call")?.phase)
        assertEquals(VideoCallPhase.ONGOING, voice("Ongoing voice call")?.phase)
        assertEquals(VideoCallPhase.ONGOING, voice("WhatsApp call", chrono = true)?.phase)
        assertEquals(false, voice("Incoming voice call")?.video)
        assertEquals("+91 98112 55501", voice("Incoming voice call")?.caller)
    }

    @Test fun `voice parser leaves video, missed calls and chats alone`() {
        assertNull(voice("Incoming video call"))
        assertNull(voice("Missed voice call"))
        assertNull(voice("Hi, are you free?", category = false))
        assertNull(voice("Incoming voice call", pkg = "com.example.chat"))
    }

    @Test fun `number titles are told apart from names`() {
        assertTrue(VideoCallParser.looksLikeNumber("+91 98112 55501"))
        assertTrue(VideoCallParser.looksLikeNumber("(022) 2345-6789"))
        assertFalse(VideoCallParser.looksLikeNumber("Mumbai Police"))
        assertFalse(VideoCallParser.looksLikeNumber("Ravi 2"))
        assertFalse(VideoCallParser.looksLikeNumber(null))
    }

    @Test fun `fake officer documents are recognised`() {
        val text = "CENTRAL BUREAU OF INVESTIGATION\nARREST WARRANT\nCase No. 4471/2026 under PMLA\nTransfer to RBI verification account"
        assertEquals(
            setOf(ScreenCategory.LAW_ENFORCEMENT, ScreenCategory.LEGAL_THREAT, ScreenCategory.PAYMENT_DEMAND),
            AuthorityText.categories(text),
        )
    }

    @Test fun `a single stray word is not evidence`() {
        assertEquals(emptySet<ScreenCategory>(), AuthorityText.categories("Breaking news: police say traffic is slow"))
        assertEquals(emptySet<ScreenCategory>(), AuthorityText.categories(""))
    }

    @Test fun `words must match whole`() {
        // "tips", "first" and "edit" must not match "ips", "fir" or "ed".
        assertEquals(emptySet<ScreenCategory>(), AuthorityText.categories("tips for the first edit"))
    }

    @Test fun `hindi text is recognised`() {
        assertTrue(ScreenCategory.LAW_ENFORCEMENT in AuthorityText.categories("सीबीआई: आपके खिलाफ वारंट जारी"))
    }

    @Test fun `official-sounding caller names`() {
        assertTrue(AuthorityText.isAuthorityName("CBI Officer Sharma"))
        assertTrue(AuthorityText.isAuthorityName("Mumbai Police Cyber Cell"))
        assertTrue(AuthorityText.isAuthorityName("Govt of India"))
        assertFalse(AuthorityText.isAuthorityName("Priya"))
        assertFalse(AuthorityText.isAuthorityName("Edward"))
        assertFalse(AuthorityText.isAuthorityName(null))
    }
}
