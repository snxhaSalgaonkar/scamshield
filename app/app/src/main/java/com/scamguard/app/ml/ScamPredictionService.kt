package com.scamguard.app.ml

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** What the message model says, in calibrated words. Never "this is a scam". */
enum class MessageVerdict { LIKELY_SCAM, SUSPICIOUS, NO_STRONG_SIGNAL, UNKNOWN, EMPTY, MODEL_UNAVAILABLE }

/** What the link model says. NO_STRONG_SIGNAL is not "safe": the model is weak on its own. */
enum class UrlVerdict { SUSPICIOUS, UNCERTAIN, NO_STRONG_SIGNAL, INVALID, MODEL_UNAVAILABLE }

/** Message scam categories (experimental head of the text model). */
enum class MessageCategory {
    CREDENTIAL_THEFT, IMPERSONATION_THREAT, PAYMENT_FRAUD, REMOTE_ACCESS, PRIZE_LURE, PHISHING_LINK, OTHER_SPAM;

    companion object {
        fun parse(name: String?) = entries.firstOrNull { it.name == name }
    }
}

data class UrlPrediction(
    /** Registrable domain only; the full URL is never kept. */
    val domain: String?,
    val probability: Double?,
    val verdict: UrlVerdict,
    val modelVersion: String?,
)

data class MessagePrediction(
    val verdict: MessageVerdict,
    /** P(spam or smishing); null when the model could not score the text. */
    val scamProbability: Double?,
    /** Experimental: category probabilities given that the message is malicious. */
    val categories: Map<MessageCategory, Double> = emptyMap(),
    /** Words and phrases from the model's vocabulary that raised the score most. */
    val indicators: List<String> = emptyList(),
    val urls: List<UrlPrediction> = emptyList(),
    val modelVersion: String? = null,
    val error: String? = null,
) {
    val topCategory: MessageCategory? get() = categories.maxByOrNull { it.value }?.key

    /** P(scam) x P(category | scam), the form the call model consumes. */
    fun categoryScore(c: MessageCategory): Double = (scamProbability ?: 0.0) * (categories[c] ?: 0.0)
}

data class CallPrediction(
    val scamProbability: Double,
    val classProbabilities: Map<String, Double>,
    /** Feature name -> drop in scam probability if that evidence were absent, largest first. */
    val factors: List<Pair<String, Double>>,
    val warningThreshold: Double,
    val redFlagThreshold: Double,
    val modelVersion: String,
    val experimental: Boolean,
)

/**
 * The app's single entry point to on-device ML. Implementations run locally only; no
 * text, number or feature ever leaves the phone.
 */
interface ScamPredictionService {
    val health: StateFlow<ModelHealth>

    /** Loads all models if not loaded yet. Safe to call repeatedly. */
    suspend fun ensureLoaded()

    suspend fun analyzeMessage(text: String): MessagePrediction
    suspend fun analyzeUrl(url: String): UrlPrediction

    /** Scores a call-window feature vector (see CallFeatures); null when the call model is unavailable. */
    fun scoreCall(features: DoubleArray): CallPrediction?
}

/** Runs the shipped artefacts with the Kotlin runtimes in this package. */
class OnDeviceScamPredictionService(
    private val source: ModelSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ScamPredictionService {
    private val _health = MutableStateFlow(ModelHealth())
    override val health: StateFlow<ModelHealth> = _health.asStateFlow()

    @Volatile private var text: LoadedModel<LinearModel>? = null
    @Volatile private var url: LoadedModel<Pair<LinearModel, UrlFeaturizer>>? = null
    @Volatile private var call: LoadedModel<TreeEnsemble>? = null
    @Volatile private var loaded = false

    override suspend fun ensureLoaded() = withContext(dispatcher) { loadBlocking() }

    @Synchronized
    private fun loadBlocking() {
        if (loaded) return
        val (t, ts) = ModelLoader.load(source, ModelId.TEXT, LinearModel::fromJson)
        val (u, us) = ModelLoader.load(source, ModelId.URL) { j ->
            val suffixes = j.getJSONArray("two_level_suffixes").toStrings().toSet()
            LinearModel.fromJson(j) to UrlFeaturizer(suffixes)
        }
        val (c, cs) = ModelLoader.load(source, ModelId.CALL, TreeEnsemble::fromJson)
        text = t; url = u; call = c
        _health.update { ModelHealth(mapOf(ModelId.TEXT to ts, ModelId.URL to us, ModelId.CALL to cs)) }
        loaded = true
    }

    override suspend fun analyzeMessage(text: String): MessagePrediction = withContext(dispatcher) {
        loadBlocking()
        predictMessage(text)
    }

    override suspend fun analyzeUrl(url: String): UrlPrediction = withContext(dispatcher) {
        loadBlocking()
        predictUrl(url)
    }

    override fun scoreCall(features: DoubleArray): CallPrediction? {
        loadBlocking()
        val m = call ?: return null
        val probs = m.model.probabilities(features)
        return CallPrediction(
            scamProbability = 1.0 - probs[m.model.classes.indexOf(TreeEnsemble.LEGIT)],
            classProbabilities = m.model.classes.zip(probs.toList()).toMap(),
            factors = m.model.explain(features),
            warningThreshold = m.thresholds["warning"] ?: DEFAULT_WARNING,
            redFlagThreshold = m.thresholds["red_flag"] ?: DEFAULT_RED,
            modelVersion = m.info.version,
            experimental = m.info.isExperimental("model"),
        )
    }

    private fun predictMessage(raw: String): MessagePrediction {
        val urls = url?.let { u -> u.model.second.extractUrls(raw).take(MAX_URLS).map(::predictUrl) }.orEmpty()
        val m = text ?: return MessagePrediction(MessageVerdict.MODEL_UNAVAILABLE, null, urls = urls,
            error = (health.value.states[ModelId.TEXT] as? ModelState.Failed)?.reason)
        if (raw.isBlank()) return MessagePrediction(MessageVerdict.EMPTY, null, modelVersion = m.info.version)

        val words = TextFeaturizer.tokens(TextFeaturizer.normalize(raw))
        val minTokens = m.json.optJSONObject("ood")?.optInt("min_tokens", 2) ?: 2
        val minCoverage = m.json.optJSONObject("ood")?.optDouble("min_coverage", 0.4) ?: 0.4
        val coverage = if (words.isEmpty()) 0.0 else words.count { m.model.contains("w:$it") }.toDouble() / words.size
        val x = m.model.vectorize(TextFeaturizer.analyze(raw))
        val p = m.model.probability(x)
        val cats = m.model.classProbabilities(x).withIndex()
            .mapNotNull { (i, v) -> MessageCategory.parse(m.model.classes[i])?.let { it to v } }.toMap()
        val ood = words.size < minTokens || coverage < minCoverage
        val verdict = when {
            ood -> MessageVerdict.UNKNOWN
            p >= (m.thresholds["high"] ?: DEFAULT_RED) -> MessageVerdict.LIKELY_SCAM
            p >= (m.thresholds["warning"] ?: DEFAULT_WARNING) -> MessageVerdict.SUSPICIOUS
            else -> MessageVerdict.NO_STRONG_SIGNAL
        }
        return MessagePrediction(verdict, p, cats, indicators(m.model, x), urls, m.info.version)
    }

    private fun predictUrl(raw: String): UrlPrediction {
        val u = url ?: return UrlPrediction(null, null, UrlVerdict.MODEL_UNAVAILABLE, null)
        val (model, featurizer) = u.model
        val host = featurizer.hostOf(raw) ?: return UrlPrediction(null, null, UrlVerdict.INVALID, u.info.version)
        val p = model.probability(model.vectorize(featurizer.analyze(raw)))
        val verdict = when {
            p >= (u.thresholds["high"] ?: DEFAULT_RED) -> UrlVerdict.SUSPICIOUS
            p >= (u.thresholds["warning"] ?: DEFAULT_WARNING) -> UrlVerdict.UNCERTAIN
            else -> UrlVerdict.NO_STRONG_SIGNAL
        }
        return UrlPrediction(featurizer.registrableDomain(host), p, verdict, u.info.version)
    }

    /** Readable word features (not character fragments) that pushed the score up. */
    private fun indicators(model: LinearModel, x: Map<Int, Double>): List<String> =
        model.contributions(x).asSequence()
            .filter { it.second > 0 && (it.first.startsWith("w:") || it.first.startsWith("b:")) }
            .map { readable(it.first.substring(2)) }
            .distinct().take(MAX_INDICATORS).toList()

    private fun readable(term: String): String = term.split(' ').joinToString(" ") { PLACEHOLDERS[it] ?: it }

    private companion object {
        const val MAX_URLS = 5
        const val MAX_INDICATORS = 6
        const val DEFAULT_WARNING = 0.5
        const val DEFAULT_RED = 0.8
        val PLACEHOLDERS = mapOf(
            TextFeaturizer.URL_TOKEN to "[link]", TextFeaturizer.EMAIL_TOKEN to "[email]",
            TextFeaturizer.PHONE_TOKEN to "[phone number]", TextFeaturizer.MONEY_TOKEN to "[amount]",
            TextFeaturizer.NUM_TOKEN to "[number]",
        )
    }
}
