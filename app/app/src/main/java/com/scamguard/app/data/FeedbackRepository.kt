package com.scamguard.app.data

import com.scamguard.app.data.db.AppDatabase
import com.scamguard.app.data.db.DecisionEntity
import com.scamguard.app.data.db.MessageCheckEntity
import com.scamguard.app.data.db.PredictionFeedbackEntity
import com.scamguard.app.ml.CallFeatures
import org.json.JSONArray
import org.json.JSONObject

/** The user's verdict on a model prediction. */
enum class ModelFeedback { CORRECT, FALSE_POSITIVE, MISSED_SCAM, UNSURE }

/**
 * Stores feedback on predictions and builds the consented export. Feedback never changes
 * a deployed model: exports are marked unreviewed, and ml/scamml/train_call.py only
 * accepts files a human has reviewed and labelled.
 */
class FeedbackRepository(private val db: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {

    suspend fun onCallWindow(d: DecisionEntity, verdict: ModelFeedback) {
        db.feedback().insert(
            PredictionFeedbackEntity(
                ts = clock(), targetType = "CALL_WINDOW", targetId = d.windowId, verdict = verdict.name,
                predictedLevel = d.peakLevel, predictedType = d.scamType, probability = d.probability,
                modelVersion = d.modelVersion, features = d.features, simulated = d.simulated,
            )
        )
    }

    suspend fun onMessage(m: MessageCheckEntity, verdict: ModelFeedback) {
        db.feedback().insert(
            PredictionFeedbackEntity(
                ts = clock(), targetType = "MESSAGE", targetId = "msg-${m.id}", verdict = verdict.name,
                predictedLevel = m.verdict, predictedType = m.category, probability = m.scamProbability,
                modelVersion = m.modelVersion, features = null, simulated = m.simulated,
            )
        )
    }

    /** Feedback on a manual check: only the model's output is kept, never the checked text. */
    suspend fun onManualCheck(verdict: ModelFeedback, predicted: String, probability: Double?, category: String?, modelVersion: String?) {
        db.feedback().insert(
            PredictionFeedbackEntity(
                ts = clock(), targetType = "MANUAL_CHECK", targetId = "manual-${clock()}", verdict = verdict.name,
                predictedLevel = predicted, predictedType = category, probability = probability,
                modelVersion = modelVersion, features = null, simulated = false,
            )
        )
    }

    suspend fun exportJson(): String = buildExport(db.feedback().all(), db.decisions().all(), db.messageChecks().all()).toString(2)

    companion object {
        /**
         * Pure export builder (unit tested). Contains model outputs, feature vectors and the
         * user's verdicts. Number hashes, masks, prefixes and message text are left out.
         */
        fun buildExport(
            feedback: List<PredictionFeedbackEntity>,
            decisions: List<DecisionEntity>,
            messages: List<MessageCheckEntity>,
        ): JSONObject = JSONObject().apply {
            put("format", "scamguard-feedback-export/1")
            put("reviewed", false)
            put("note", "User feedback, NOT reviewed labels. A reviewer must label rows before any training use.")
            put("call_features", JSONArray(CallFeatures.NAMES))
            put("feedback", JSONArray(feedback.map { f ->
                JSONObject().apply {
                    put("ts", f.ts); put("target_type", f.targetType); put("user_verdict", f.verdict)
                    put("predicted_level", f.predictedLevel ?: JSONObject.NULL)
                    put("predicted_type", f.predictedType ?: JSONObject.NULL)
                    put("probability", f.probability ?: JSONObject.NULL)
                    put("model_version", f.modelVersion ?: JSONObject.NULL)
                    put("features", f.features?.let { JSONArray(it.split(",").map(String::toDouble)) } ?: JSONObject.NULL)
                    put("simulated", f.simulated)
                }
            }))
            put("call_decisions", JSONArray(decisions.map { d ->
                JSONObject().apply {
                    put("started_at", d.startedAt); put("peak_level", d.peakLevel); put("scam_type", d.scamType)
                    put("score_source", d.scoreSource); put("probability", d.probability ?: JSONObject.NULL)
                    put("model_version", d.modelVersion ?: JSONObject.NULL); put("simulated", d.simulated)
                }
            }))
            put("message_checks", JSONArray(messages.map { m ->
                JSONObject().apply {
                    put("ts", m.ts); put("verdict", m.verdict); put("probability", m.scamProbability ?: JSONObject.NULL)
                    put("category", m.category ?: JSONObject.NULL); put("link_count", m.linkCount)
                    put("model_version", m.modelVersion ?: JSONObject.NULL); put("simulated", m.simulated)
                }
            }))
        }
    }
}
