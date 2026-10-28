package com.scamguard.app.ml

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** Thrown when an artefact is missing a field or has inconsistent sizes. */
class ModelFormatException(message: String) : Exception(message)

/** Where a model's evaluation numbers come from, shown on the Model status screen. */
data class ModelInfo(
    val version: String,
    val format: String,
    /** Which outputs are experimental, e.g. {"binary": false, "category": true}. */
    val experimental: Map<String, Boolean>,
    /** Held-out metrics from the training report (precision, recall, f1, pr_auc, ...). */
    val metrics: Map<String, Any?>,
    val provenance: String,
    /** Decision thresholds chosen on validation data, e.g. warning / red_flag. */
    val thresholds: Map<String, Double> = emptyMap(),
) {
    fun isExperimental(part: String) = experimental[part] ?: true
}

/**
 * Sparse TF-IDF features followed by logistic regression (binary) and optionally a
 * multinomial head. Port of ml/scamml/linear.py LinearRuntime.
 */
class LinearModel(
    private val index: Map<String, Int>,
    private val idf: DoubleArray,
    private val coef: DoubleArray,
    private val intercept: Double,
    val classes: List<String>,
    private val classCoef: Array<DoubleArray>,
    private val classIntercept: DoubleArray,
    private val terms: Array<String>,
) {
    val vocabularySize: Int get() = idf.size

    fun contains(term: String) = term in index

    /** L2-normalised sublinear TF-IDF over in-vocabulary features: index -> weight. */
    fun vectorize(features: List<String>): Map<Int, Double> {
        val counts = HashMap<Int, Int>()
        for (f in features) index[f]?.let { counts[it] = (counts[it] ?: 0) + 1 }
        val w = counts.mapValues { (i, c) -> (1.0 + ln(c.toDouble())) * idf[i] }
        val norm = sqrt(w.values.sumOf { it * it })
        return if (norm > 0) w.mapValues { it.value / norm } else emptyMap()
    }

    fun probability(x: Map<Int, Double>): Double {
        var z = intercept
        for ((i, v) in x) z += coef[i] * v
        return 1.0 / (1.0 + exp(-z))
    }

    fun classProbabilities(x: Map<Int, Double>): DoubleArray {
        if (classes.isEmpty()) return DoubleArray(0)
        val z = classIntercept.copyOf()
        for (k in classes.indices) for ((i, v) in x) z[k] += classCoef[k][i] * v
        val max = z.max()
        val e = DoubleArray(z.size) { exp(z[it] - max) }
        val sum = e.sum()
        return DoubleArray(z.size) { e[it] / sum }
    }

    /** Terms ranked by how much they pushed the binary score up. */
    fun contributions(x: Map<Int, Double>): List<Pair<String, Double>> =
        x.map { (i, v) -> terms[i] to coef[i] * v }.sortedByDescending { it.second }

    companion object {
        fun fromJson(json: JSONObject): LinearModel {
            val vocab = json.getJSONArray("vocab")
            val terms = Array(vocab.length()) { vocab.getString(it) }
            val idf = json.getJSONArray("idf").toDoubles()
            val binary = json.getJSONObject("binary")
            val coef = binary.getJSONArray("coef").toDoubles()
            if (idf.size != terms.size || coef.size != terms.size) {
                throw ModelFormatException("vocab/idf/coef sizes differ: ${terms.size}/${idf.size}/${coef.size}")
            }
            val cat = json.optJSONObject("category")
            val classes = cat?.getJSONArray("classes")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
            val classCoef = cat?.getJSONArray("coef")?.let { a -> Array(a.length()) { a.getJSONArray(it).toDoubles() } } ?: emptyArray()
            val classIntercept = cat?.getJSONArray("intercept")?.toDoubles() ?: DoubleArray(0)
            if (classCoef.size != classes.size || classIntercept.size != classes.size || classCoef.any { it.size != terms.size }) {
                throw ModelFormatException("category head shape does not match vocabulary")
            }
            val index = HashMap<String, Int>(terms.size * 2)
            terms.forEachIndexed { i, t -> index[t] = i }
            return LinearModel(index, idf, coef, binary.getDouble("intercept"), classes, classCoef, classIntercept, terms)
        }
    }
}

/**
 * Multi-class gradient-boosted trees. Port of ml/scamml/trees.py TreeRuntime: features are
 * compared as 32-bit floats, as scikit-learn does, so splits land the same way.
 */
class TreeEnsemble(
    val features: List<String>,
    val classes: List<String>,
    private val init: DoubleArray,
    private val trees: List<List<Tree>>,
    val baseline: DoubleArray,
) {
    class Tree(val feature: IntArray, val threshold: FloatArray, val left: IntArray, val right: IntArray, val value: DoubleArray) {
        fun leafValue(x: FloatArray): Double {
            var node = 0
            while (left[node] != -1) node = if (x[feature[node]] <= threshold[node]) left[node] else right[node]
            return value[node]
        }
    }

    fun probabilities(x: DoubleArray): DoubleArray {
        require(x.size == features.size) { "expected ${features.size} features, got ${x.size}" }
        val xf = FloatArray(x.size) { x[it].toFloat() }
        val z = init.copyOf()
        trees.forEachIndexed { k, classTrees -> classTrees.forEach { z[k] += it.leafValue(xf) } }
        val max = z.max()
        val e = DoubleArray(z.size) { exp(z[it] - max) }
        val sum = e.sum()
        return DoubleArray(z.size) { e[it] / sum }
    }

    fun scamProbability(x: DoubleArray): Double = 1.0 - probabilities(x)[classes.indexOf(LEGIT)]

    /** For each feature that differs from its baseline: drop in scam probability when reset. */
    fun explain(x: DoubleArray): List<Pair<String, Double>> {
        val base = scamProbability(x)
        return features.indices.filter { x[it] != baseline[it] }.map { i ->
            val cf = x.copyOf().also { it[i] = baseline[i] }
            features[i] to base - scamProbability(cf)
        }.sortedByDescending { it.second }
    }

    companion object {
        const val LEGIT = "LEGIT"

        fun fromJson(json: JSONObject): TreeEnsemble {
            val features = json.getJSONArray("features").toStrings()
            val classes = json.getJSONArray("classes").toStrings()
            val init = json.getJSONArray("init").toDoubles()
            val baseline = json.getJSONArray("baseline").toDoubles()
            val treesJson = json.getJSONArray("trees")
            if (LEGIT !in classes || init.size != classes.size || treesJson.length() != classes.size || baseline.size != features.size) {
                throw ModelFormatException("call model shape is inconsistent")
            }
            val trees = List(treesJson.length()) { k ->
                val arr = treesJson.getJSONArray(k)
                List(arr.length()) { t ->
                    val o = arr.getJSONObject(t)
                    val feature = o.getJSONArray("feature").toInts()
                    if (feature.any { it >= features.size }) throw ModelFormatException("tree uses unknown feature")
                    Tree(
                        feature,
                        o.getJSONArray("threshold").toDoubles().let { d -> FloatArray(d.size) { d[it].toFloat() } },
                        o.getJSONArray("left").toInts(),
                        o.getJSONArray("right").toInts(),
                        o.getJSONArray("value").toDoubles(),
                    )
                }
            }
            return TreeEnsemble(features, classes, init, trees, baseline)
        }
    }
}

internal fun JSONArray.toDoubles() = DoubleArray(length()) { getDouble(it) }
internal fun JSONArray.toInts() = IntArray(length()) { getInt(it) }
internal fun JSONArray.toStrings() = List(length()) { getString(it) }

internal fun JSONObject.toMap(): Map<String, Any?> =
    keys().asSequence().associateWith { k -> opt(k)?.takeUnless { it == JSONObject.NULL } }
