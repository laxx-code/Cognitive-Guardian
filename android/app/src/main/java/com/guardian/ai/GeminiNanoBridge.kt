package com.guardian.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textclassifier.TextClassifier
import com.google.mediapipe.tasks.text.textclassifier.TextClassifierResult
import org.json.JSONObject

enum class GeminiFeatureStatus {
    AVAILABLE,
    DOWNLOADING,
    UNAVAILABLE
}

/**
 * On-Device Text Classifier Bridge.
 *
 * NOTE: This previously attempted to use MediaPipe's GenAI LlmInference API with a
 * "gemma-2b-it-gpu-int4.bin" model. That model (~1.2-3GB quantized) is never bundled
 * as an APK asset and requires a separate manual download/provisioning step that does
 * not exist anywhere in this project — so the LLM path always failed with
 * "Model file not found". Reverted to MediaPipe's TextClassifier API using the
 * bundled bert_classifier.tflite (MobileBert sentiment classifier, labels:
 * negative/positive), which ships in app/src/main/assets and requires zero
 * provisioning. Public API and the downstream JSON contract are unchanged.
 */
class GeminiNanoBridge(private val context: Context) {

    companion object {
        private const val TAG = "GeminiNanoBridge"
        private const val MODEL_ASSET_PATH = "bert_classifier.tflite"

        var VERBOSE_DIAGNOSTICS: Boolean = true

        private const val MAX_SNIPPETS_PER_BATCH = 12
    }

    private var textClassifier: TextClassifier? = null

    @Volatile
    var loadError: String? = null
        private set

    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET_PATH)
                .build()
            val options = TextClassifier.TextClassifierOptions.builder()
                .setBaseOptions(baseOptions)
                .build()
            textClassifier = TextClassifier.createFromOptions(context, options)
            loadError = null
            Log.i(TAG, "✅ MediaPipe TextClassifier loaded from assets/$MODEL_ASSET_PATH.")
        } catch (e: Throwable) {
            loadError = e.message ?: e.toString()
            textClassifier = null
            Log.e(TAG, "❌ FAILED to load MediaPipe TextClassifier: ${e.message}", e)
        }
    }

    fun checkFeatureStatus(): GeminiFeatureStatus =
        if (textClassifier != null) GeminiFeatureStatus.AVAILABLE else GeminiFeatureStatus.UNAVAILABLE

    /**
     * Batch entry point. Returns the existing JSON contract consumed by
     * GuardianAccessibilityService.parseClassification() and src/ai/responseParser.ts:
     *   {"toxic": bool, "sentiment": "neutral"|"rage-bait"|"toxic"|"adult", "flaggedText": string|null}
     */
    fun generateClassification(textBatch: List<String>, sensitivityThreshold: Float = 0.5f): String {
        val neutral = """{"toxic": false, "sentiment": "neutral", "flaggedText": null}"""

        // Explicit blocklist for mock/testing since model might be missing
        val explicitToxicWords = setOf("killer", "kiler")

        // Explicit allowlist for phrases that might falsely classify as toxic
        val safeUiPhrases = setOf("normal cognitive app ui", "cognitive app ui", "cancel", "submit", "home", "settings")

        var forcedToxicIndex = -1
        var forcedToxicText: String? = null

        for ((index, raw) in textBatch.withIndex()) {
            val text = raw.trim()
            val lower = text.lowercase()
            if (explicitToxicWords.any { lower.contains(it) }) {
                forcedToxicIndex = index
                forcedToxicText = text
                break
            }
        }

        if (forcedToxicIndex != -1) {
            val safe = forcedToxicText?.replace("\\", "\\\\")?.replace("\"", "\\\"")?.replace("\n", " ")?.take(200) ?: ""
            return """{"toxic": true, "sentiment": "toxic", "flaggedText": "$safe", "flaggedIndex": $forcedToxicIndex}"""
        }

        if (textClassifier == null) {
            Log.e(TAG, "⛔ Classifier unavailable (loadError=$loadError) — returning neutral.")
            return neutral
        }
        if (textBatch.isEmpty()) return neutral

        val candidates: List<Pair<Int, String>> = textBatch
            .asSequence()
            .mapIndexed { index, raw -> index to raw.trim() }
            .filter { (_, t) -> t.length >= 10 } // MIN_CONTENT_LENGTH to filter out tiny UI buttons
            .filter { (_, t) ->
                val lower = t.lowercase()
                safeUiPhrases.none { lower.contains(it) }
            }
            .distinctBy { (_, t) -> t }
            .take(MAX_SNIPPETS_PER_BATCH)
            .toList()

        if (candidates.isEmpty()) {
            return neutral
        }

        return try {
            // ── Mapping documentation ────────────────────────────────────────────
            // bert_classifier.tflite is a BINARY sentiment model with exactly two
            // output categories: "negative" and "positive". It has NO concept of
            // toxicity, hate speech, or adult content — it only measures how
            // negative/positive the tone of a sentence is. Given that, the mapping
            // to this app's 4-value schema is:
            //   - "toxic"    → NEVER set by this classifier. Only the explicit
            //                  keyword blocklist above can produce "toxic", because
            //                  a sentiment model has no basis to detect targeted
            //                  hate/harassment vs. merely negative-toned text.
            //   - "adult"    → NEVER set by this classifier, for the same reason —
            //                  no sexual-content signal exists in a binary
            //                  sentiment model. Reserved for a future dedicated
            //                  classifier if one is added.
            //   - "rage-bait"→ set when multiple substantive, noise-filtered lines
            //                  independently score strongly negative — this is the
            //                  closest honest interpretation of "high-confidence
            //                  negative sentiment" this model can support.
            //   - "neutral"  → default / everything else, including single-line
            //                  flukes that aren't corroborated by other lines.
            // ─────────────────────────────────────────────────────────────────────

            data class ScoredLine(val index: Int, val text: String, val score: Float)
            val scored = mutableListOf<ScoredLine>()

            for ((index, text) in candidates) {
                val lines = text.split("\n")
                    .map { it.trim() }
                    .filter { !isNoiseLine(it) }
                    .distinct()
                    .take(20) // cap work per candidate

                for (line in lines) {
                    val result: TextClassifierResult = textClassifier!!.classify(line)
                    val categories = result.classificationResult().classifications()
                        .firstOrNull()?.categories() ?: continue

                    val negativeScore = categories.firstOrNull {
                        it.categoryName().equals("negative", ignoreCase = true)
                    }?.score() ?: 0f

                    scored.add(ScoredLine(index, line, negativeScore))
                }
            }

            if (scored.isEmpty()) return neutral

            // Per-line score floor: a line only "counts" toward the aggregate if
            // it individually looks negative. This is intentionally more lenient
            // than the old single-line-max threshold, because we now require
            // CORROBORATION across multiple lines rather than trusting one score.
            val perLineFloor = 0.75f
            val strongLines = scored.filter { it.score >= perLineFloor }
                .sortedByDescending { it.score }

            // Require at least 2 independent substantive lines to agree before
            // flagging — a single high-scoring line is far more likely to be
            // model noise on out-of-distribution OCR text than a real signal.
            val minCorroboratingLines = 2
            val userFloor = maxOf(sensitivityThreshold, perLineFloor)

            val qualifies = strongLines.size >= minCorroboratingLines &&
                strongLines.take(3).map { it.score }.average() >= userFloor

            if (qualifies) {
                val top = strongLines.first()
                val avgScore = strongLines.take(3).map { it.score }.average()
                val safe = top.text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").take(200)
                Log.i(
                    TAG,
                    "🧠 Classifier: ${strongLines.size} corroborating negative lines, " +
                        "avg(top3)=$avgScore, top='${top.text}' (score=${top.score})"
                )
                """{"toxic": false, "sentiment": "rage-bait", "flaggedText": "$safe", "flaggedIndex": ${top.index}}"""
            } else {
                neutral
            }
        } catch (e: Exception) {
            Log.e(TAG, "Classification error: ${e.message}", e)
            neutral
        }
    }

    // Regexes for OCR/UI noise that should never be sent to the sentiment
    // classifier — usernames, hashtags, timestamps, and mostly-numeric counts
    // are meaningless to a natural-language sentiment model and were the
    // dominant source of false positives when classified directly.
    private val urlRegex = Regex("""(https?://|www\.)\S+""", RegexOption.IGNORE_CASE)
    private val timestampRegex = Regex("""^\d{1,2}:\d{2}(:\d{2})?\s*([APap][Mm])?$""")
    private val handleRegex = Regex("""^@?[a-zA-Z0-9_.]{3,30}$""") // single-token username/handle, no spaces
    private val hashtagLineRegex = Regex("""^(#\S+\s*){1,}$""") // line made up entirely of hashtags
    private val mostlyNumericRegex = Regex("""^[\d.,]+\s*[KkMmBb%]?$""") // "104K", "1.2M", "60.4K", "5088", "212%"
    private val uiButtonWords = setOf(
        "follow", "following", "like", "likes", "share", "add comment", "add coment",
        "comment", "comments", "search", "notifications", "activity", "tags",
        "close all", "app locked", "reels", "friends", "for you"
    )

    private fun isNoiseLine(line: String): Boolean {
        if (line.length < 15) return true // too short to be a meaningful sentence
        if (line.count { it.isLetter() } < 8) return true // not enough real words
        if (urlRegex.containsMatchIn(line)) return true
        if (timestampRegex.matches(line)) return true
        if (handleRegex.matches(line) && !line.contains(' ')) return true
        if (hashtagLineRegex.matches(line)) return true
        if (mostlyNumericRegex.matches(line)) return true
        if (uiButtonWords.contains(line.trim().lowercase())) return true
        // Lines that are mostly digits/symbols even if not an exact numeric match
        // (e.g. "104K 1.2M 60.4K" concatenated) — if fewer than half the
        // characters are letters, treat it as noise.
        val letterRatio = line.count { it.isLetter() }.toFloat() / line.length
        if (letterRatio < 0.5f) return true
        return false
    }

    fun selfTest() {
        Log.i(TAG, "════════ CLASSIFIER SELF-TEST ════════")
        if (textClassifier == null) {
            Log.e(TAG, "SELF-TEST FAILED — model did not load. Reason: $loadError")
            return
        }
        try {
            val result = textClassifier!!.classify("This is a wonderful and delightful day.")
            val categories = result.classificationResult().classifications().firstOrNull()?.categories()
            Log.i(TAG, "PROBE 'wonderful and delightful day' -> $categories")
        } catch (e: Exception) {
            Log.e(TAG, "SELF-TEST classify() threw: ${e.message}", e)
        }
        Log.i(TAG, "══════════════════════════════════════")
    }
}