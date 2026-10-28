package com.scamguard.app.ml

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * Replays golden vectors written by ml/scamml/golden.py: the Kotlin featurizers and
 * runtimes must reproduce the Python reference on the shipped artefacts.
 */
class MlParityTest {
    private val tol = 1e-9

    private fun model(name: String) = JSONObject(File(ASSETS_DIR, "models/$name").readText())

    @Test fun `text normalisation, tokens and probabilities match Python`() {
        val m = LinearModel.fromJson(model("text_model.json"))
        JSONArray(golden("golden_text.json")).objects().forEach { c ->
            val text = c.getString("text")
            assertEquals(text, c.getString("normalized"), TextFeaturizer.normalize(text))
            val tokens = c.getJSONArray("tokens").let { a -> List(a.length()) { a.getString(it) } }
            assertEquals(text, tokens, TextFeaturizer.tokens(TextFeaturizer.normalize(text)))
            assertEquals(text, c.getInt("feature_count"), TextFeaturizer.analyze(text).size)
            val x = m.vectorize(TextFeaturizer.analyze(text))
            assertEquals(text, c.getInt("nonzero"), x.size)
            assertEquals(text, c.getDouble("binary"), m.probability(x), tol)
            val cat = c.getJSONArray("category")
            m.classProbabilities(x).forEachIndexed { k, p -> assertEquals(text, cat.getDouble(k), p, tol) }
        }
    }

    @Test fun `url parsing, features and probabilities match Python`() {
        val json = model("url_model.json")
        val m = LinearModel.fromJson(json)
        val f = UrlFeaturizer(json.getJSONArray("two_level_suffixes").toStrings().toSet())
        val g = JSONObject(golden("golden_url.json"))
        g.getJSONArray("urls").objects().forEach { c ->
            val url = c.getString("url")
            val host = f.hostOf(url)
            if (c.isNull("host")) {
                assertNull(url, host)
                return@forEach
            }
            assertEquals(url, c.getString("host"), host)
            assertEquals(url, c.getString("domain"), f.registrableDomain(host!!))
            assertEquals(url, c.getJSONArray("features").toStrings(), f.analyze(url))
            assertEquals(url, c.getDouble("proba"), m.probability(m.vectorize(f.analyze(url))), tol)
        }
        g.getJSONArray("extract").objects().forEach { c ->
            assertEquals(c.getString("text"), c.getJSONArray("urls").toStrings(), f.extractUrls(c.getString("text")))
        }
    }

    @Test fun `call model probabilities and explanations match Python`() {
        val m = TreeEnsemble.fromJson(model("call_model.json"))
        val g = JSONObject(golden("golden_call.json"))
        assertEquals(g.getJSONArray("features").toStrings(), CallFeatures.NAMES)
        assertEquals(CallFeatures.NAMES, m.features)
        g.getJSONArray("cases").objects().forEachIndexed { i, c ->
            val x = c.getJSONArray("x").toDoubles()
            val expected = c.getJSONArray("proba").toDoubles()
            m.probabilities(x).forEachIndexed { k, p -> assertEquals("case $i class $k", expected[k], p, tol) }
            assertEquals("case $i", c.getDouble("scam"), m.scamProbability(x), tol)
            val explain = c.getJSONArray("explain")
            val got = m.explain(x)
            for (j in 0 until explain.length()) {
                val e = explain.getJSONArray(j)
                assertEquals("case $i factor $j", e.getString(0), got[j].first)
                assertEquals("case $i factor $j", e.getDouble(1), got[j].second, tol)
            }
        }
    }
}
