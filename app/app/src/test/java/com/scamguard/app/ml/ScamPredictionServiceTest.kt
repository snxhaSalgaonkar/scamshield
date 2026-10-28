package com.scamguard.app.ml

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Runs the real shipped models (no mocks) and the failure paths around them. */
class ScamPredictionServiceTest {
    private val service = shippedModels()

    @Test fun `all shipped models load`() = runTest {
        service.ensureLoaded()
        assertTrue(service.health.value.states.toString(), service.health.value.allReady)
        val text = service.health.value.states[ModelId.TEXT] as ModelState.Ready
        assertFalse(text.info.isExperimental("binary"))
        assertTrue(text.info.isExperimental("category"))
        val call = service.health.value.states[ModelId.CALL] as ModelState.Ready
        assertTrue("call model trained on synthetic data must be marked experimental", call.info.isExperimental("model"))
    }

    @Test fun `scam and ordinary messages get different, model-derived scores`() = runTest {
        val scam = service.analyzeMessage("URGENT: your bank account is blocked. Verify your KYC now at http://bank-kyc-verify.top or it will be closed")
        val normal = service.analyzeMessage("Hey, are we still meeting for lunch tomorrow at the usual place?")
        assertEquals(MessageVerdict.LIKELY_SCAM, scam.verdict)
        assertEquals(MessageVerdict.NO_STRONG_SIGNAL, normal.verdict)
        assertTrue(scam.scamProbability!! > normal.scamProbability!!)
        assertTrue(scam.indicators.isNotEmpty())
        assertEquals(1, scam.urls.size)
        assertEquals("bank-kyc-verify.top", scam.urls[0].domain)
        assertNotNull(scam.modelVersion)
        // Category probabilities form a distribution.
        assertEquals(1.0, scam.categories.values.sum(), 1e-9)
    }

    @Test fun `empty, too short and unfamiliar text get no verdict instead of a guess`() = runTest {
        assertEquals(MessageVerdict.EMPTY, service.analyzeMessage("   ").verdict)
        assertEquals(MessageVerdict.UNKNOWN, service.analyzeMessage("ok").verdict)
        assertEquals(MessageVerdict.UNKNOWN, service.analyzeMessage("ຂໍ້ຄວາມນີ້ເປັນພາສາລາວທີ່ບໍ່ເຄີຍເຫັນມາກ່ອນ ແລະ ບໍ່ມີຄຳສັບ").verdict)
    }

    @Test fun `indicators never echo numbers, links or amounts from the message`() = runTest {
        val p = service.analyzeMessage("Share OTP 482913 now or Rs 49,999 will be debited, call +91 98765 43210 or visit http://x-verify.top/a")
        p.indicators.forEach { word ->
            assertFalse(word, word.any(Char::isDigit))
            assertFalse(word, "x-verify" in word)
        }
    }

    @Test fun `links are judged by domain only and invalid input is rejected`() = runTest {
        val a = service.analyzeUrl("https://www.Example-Login-Verify.top/path?q=1")
        val b = service.analyzeUrl("example-login-verify.top")
        assertEquals("example-login-verify.top", a.domain)
        assertEquals(a.probability!!, b.probability!!, 1e-12)
        assertEquals(UrlVerdict.INVALID, service.analyzeUrl("not a link").verdict)
        assertNotEquals(a.probability, service.analyzeUrl("https://www.wikipedia.org").probability)
    }

    @Test fun `missing model files leave only that model unavailable`() = runTest {
        val noText = OnDeviceScamPredictionService(
            { path -> if (path == ModelId.TEXT.file) null else ModelSource.directory(ASSETS_DIR).read(path) },
            Dispatchers.Unconfined,
        )
        val p = noText.analyzeMessage("Your account is blocked, click http://verify-now.top")
        assertEquals(MessageVerdict.MODEL_UNAVAILABLE, p.verdict)
        assertNull(p.scamProbability)
        assertEquals(1, p.urls.size) // the link model still works
        assertTrue(noText.health.value.states[ModelId.TEXT] is ModelState.Missing)
        assertTrue(noText.health.value.isReady(ModelId.CALL))
    }

    @Test fun `malformed, wrong-format and unreadable artefacts fail safely`() = runTest {
        val broken = OnDeviceScamPredictionService({ path ->
            when (path) {
                ModelId.TEXT.file -> "{ not json"
                ModelId.URL.file -> """{"format":"something-else/9","version":"x","thresholds":{}}"""
                else -> throw IOException("disk error")
            }
        }, Dispatchers.Unconfined)
        broken.ensureLoaded()
        val states = broken.health.value.states
        assertTrue(states[ModelId.TEXT] is ModelState.Failed)
        assertTrue((states[ModelId.URL] as ModelState.Failed).reason.contains("format"))
        assertTrue(states[ModelId.CALL] is ModelState.Failed)
        assertNull(broken.scoreCall(DoubleArray(CallFeatures.NAMES.size)))
        assertEquals(UrlVerdict.MODEL_UNAVAILABLE, broken.analyzeUrl("http://a.top").verdict)
    }

    @Test fun `inconsistent model shapes are rejected`() {
        val (loaded, state) = ModelLoader.load({
            """{"format":"scamml-text/1","version":"t","thresholds":{"warning":0.5},"vocab":["a","b"],"idf":[1.0],"binary":{"coef":[1,2],"intercept":0}}"""
        }, ModelId.TEXT, LinearModel::fromJson)
        assertNull(loaded)
        assertTrue(state is ModelState.Failed)
    }

    @Test fun `call scoring rejects a vector of the wrong length`() {
        val err = runCatching { service.scoreCall(DoubleArray(3)) }.exceptionOrNull()
        assertTrue(err is IllegalArgumentException)
    }
}
