package com.scamguard.app.ml

import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The shipped artefacts, read from the module's assets folder (unit tests run in app/). */
val ASSETS_DIR = File("src/main/assets")

fun shippedModels(): OnDeviceScamPredictionService =
    OnDeviceScamPredictionService(ModelSource.directory(ASSETS_DIR), Dispatchers.Unconfined)

fun golden(name: String): String =
    requireNotNull(object {}.javaClass.getResource("/ml/$name")) { "missing golden file $name; run python -m scamml.golden" }.readText()

fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
