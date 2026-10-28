package com.scamguard.app.ml

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** The on-device models. */
enum class ModelId(val file: String, val format: String) {
    TEXT("models/text_model.json", "scamml-text/1"),
    URL("models/url_model.json", "scamml-url/1"),
    CALL("models/call_model.json", "scamml-call/1"),
}

/** Load state of one model, shown on the Model status screen. */
sealed interface ModelState {
    data object NotLoaded : ModelState
    data class Ready(val info: ModelInfo) : ModelState
    data class Missing(val file: String) : ModelState
    data class Failed(val reason: String) : ModelState
}

data class ModelHealth(val states: Map<ModelId, ModelState> = ModelId.entries.associateWith { ModelState.NotLoaded }) {
    fun isReady(id: ModelId) = states[id] is ModelState.Ready
    val allReady: Boolean get() = ModelId.entries.all(::isReady)
}

/** Reads artefact text by asset path; null when the file does not exist. */
fun interface ModelSource {
    fun read(path: String): String?

    companion object {
        fun assets(context: Context) = ModelSource { path ->
            try {
                context.assets.open(path).bufferedReader().use { it.readText() }
            } catch (_: FileNotFoundException) {
                null
            }
        }

        fun directory(dir: File) = ModelSource { path -> File(dir, path).takeIf { it.isFile }?.readText() }
    }
}

/** A parsed artefact plus the fields every model shares. */
class LoadedModel<T>(val model: T, val info: ModelInfo, val thresholds: Map<String, Double>, val json: JSONObject)

/**
 * Loads and validates the three artefacts. A missing or malformed file makes only that
 * model unavailable; the reason is kept for the status screen. Never logs file content.
 */
object ModelLoader {

    fun <T> load(source: ModelSource, id: ModelId, parse: (JSONObject) -> T): Pair<LoadedModel<T>?, ModelState> {
        val text = try {
            source.read(id.file)
        } catch (e: IOException) {
            return null to ModelState.Failed("Could not read ${id.file}: ${e.javaClass.simpleName}")
        } ?: return null to ModelState.Missing(id.file)
        return try {
            val json = JSONObject(text)
            val format = json.getString("format")
            if (format != id.format) throw ModelFormatException("format $format, expected ${id.format}")
            // Parts without an explicit flag are treated as experimental.
            val experimental = json.optJSONObject("experimental")?.let { e -> e.keys().asSequence().associateWith { e.optBoolean(it, true) } }.orEmpty()
            val t = json.getJSONObject("thresholds")
            val thresholds = t.keys().asSequence().filter { t.opt(it) is Number }.associateWith { t.getDouble(it) }
            val info = ModelInfo(
                version = json.getString("version"),
                format = format,
                experimental = experimental,
                metrics = json.optJSONObject("metrics_summary")?.toMap().orEmpty(),
                provenance = json.optJSONObject("provenance")?.toString().orEmpty(),
                thresholds = thresholds,
            )
            val loaded = LoadedModel(parse(json), info, thresholds, json)
            loaded to ModelState.Ready(info)
        } catch (e: JSONException) {
            null to ModelState.Failed("Malformed ${id.file}: ${e.message?.take(120)}")
        } catch (e: ModelFormatException) {
            null to ModelState.Failed("Invalid ${id.file}: ${e.message}")
        } catch (e: RuntimeException) {
            null to ModelState.Failed("Could not load ${id.file}: ${e.javaClass.simpleName}")
        }
    }
}
