package com.guardian.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.guardian.ai.GeminiNanoBridge
import com.guardian.bridge.GuardianNativeModule
import com.guardian.overlay.OverlayWindowManager
import com.guardian.overlay.ReframeBottomSheet
import org.json.JSONObject
import java.util.concurrent.Executors

class GuardianAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "GuardianAccessibility"
        private const val THROTTLE_TIMEOUT_MS = 5000L
        private const val MIN_CLASSIFY_INTERVAL_MS = 3000L
        private const val MODEL_NAME = "image_classifier.tflite"

        @Volatile
        var isServiceRunning: Boolean = false

        @Volatile
        var sensitivityThreshold: Float = 0.5f
    }

    private var lastScrollTimestamp: Long = System.currentTimeMillis()
    private var lastClassificationTimestamp: Long = 0L
    private var isExtractionThrottled: Boolean = false
    private var screenshotCapable: Boolean = true
    private var lastEventSource: android.view.accessibility.AccessibilityNodeInfo? = null
    private val handler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    /** Warn on strikes 1-2, mask from strike 3. See WarningEscalator. */
    private val escalator = WarningEscalator()

    private val geminiNanoBridge by lazy { GeminiNanoBridge(applicationContext) }
    private val overlayWindowManager by lazy { OverlayWindowManager(this) }
    private val reframeBottomSheet by lazy { ReframeBottomSheet(this) }

    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private var imageClassifier: ImageClassifier? = null

    // ---- Idle scanner: runs every 5s while user is not scrolling ----
    private val throttleCheckRunnable = object : Runnable {
        override fun run() {
            try {
                val elapsed = System.currentTimeMillis() - lastScrollTimestamp
                if (elapsed >= THROTTLE_TIMEOUT_MS) {
                    if (!isExtractionThrottled) {
                        isExtractionThrottled = true
                        Log.d(TAG, "Scroll idle for ${elapsed}ms — starting idle analysis.")
                    }
                    analyzeCurrentScreen()
                }
            } catch (e: Exception) {
                Log.e(TAG, "throttleCheckRunnable error: ${e.message}")
            }
            handler.postDelayed(this, 5000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        lastScrollTimestamp = System.currentTimeMillis()
        isExtractionThrottled = false
        escalator.reset()

        handler.post(throttleCheckRunnable)
        Log.i(TAG, "✅ GuardianAccessibilityService CONNECTED and RUNNING.")

        // Prove the text model actually loaded and produces separable scores.
        // Runs off the main thread; results land in logcat under GeminiNanoBridge.
        backgroundExecutor.execute {
            try {
                geminiNanoBridge.selfTest()
            } catch (e: Throwable) {
                Log.e(TAG, "Classifier self-test crashed: ${e.message}", e)
            }
        }
    }

    // ---- DUAL STRATEGY: Node Tree ----
    private fun analyzeCurrentScreen() {
        val packageName = rootInActiveWindow?.packageName?.toString()
        if (SecureAppBlacklist.shouldSkipExtraction(packageName)) {
            Log.i(TAG, "Skipping extraction for secure/blacklisted app: $packageName")
            return
        }

        // Just read the accessibility node tree — works on ALL phones
        // Vision parsing is now handled by ScreenCaptureService via MediaProjection
        analyzeNodeTreeText(lastEventSource)
    }

    /**
     * Helper to get the root node safely. MIUI sometimes returns null for rootInActiveWindow.
     */
    private fun getSafeRootNode(eventSource: android.view.accessibility.AccessibilityNodeInfo? = null): android.view.accessibility.AccessibilityNodeInfo? {
        rootInActiveWindow?.let { return it }
        
        try {
            val windowList = windows
            Log.d(TAG, "rootInActiveWindow is null. Found ${windowList.size} windows.")
            for (window in windowList) {
                if (window.isActive || window.isFocused) {
                    window.root?.let { return it }
                }
            }
            for (window in windowList) {
                window.root?.let { return it }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error iterating windows: ${e.message}")
        }
        
        // Final fallback: use the source of the event if we have one
        eventSource?.let { 
            Log.d(TAG, "Using event source as root fallback.")
            return it 
        }
        
        return null
    }

    /**
     * Reads all text from the current screen's accessibility tree,
     * emits it to the JS Live Feed, and runs MobileBERT sentiment analysis.
     */
    private fun analyzeNodeTreeText(eventSource: android.view.accessibility.AccessibilityNodeInfo? = null) {
        val rootNode = getSafeRootNode(eventSource) ?: run {
            Log.d(TAG, "No active window root — skipping node tree analysis.")
            return
        }
        val extractedData = NodeTextExtractor.extractTextAndBounds(rootNode)
        if (extractedData.isEmpty()) {
            Log.d(TAG, "Node tree is empty — no visible text.")
            return
        }

        val textList = extractedData.map { it.text }
        val combinedText = textList.joinToString(" ").take(500)

        Log.i(TAG, "📝 Extracted ${textList.size} text nodes (${combinedText.length} chars): ${combinedText.take(80)}...")

        // Push text to the JS rolling buffer
        val bridge = GuardianNativeModule.getInstance()
        if (bridge != null) {
            bridge.emitTextBatch(textList)
            bridge.emitVisionScan(
                ocrText = combinedText,
                visionLabel = "text-node",
                visionScore = 1.0f
            )
            Log.d(TAG, "📡 Emitted text batch + vision scan to JS.")
        } else {
            Log.w(TAG, "⚠️ GuardianNativeModule.getInstance() is null — RN bridge not ready yet.")
        }

        // Run sentiment analysis on background thread (NEVER on main thread!)
        val pkg = rootNode.packageName?.toString()
        backgroundExecutor.execute {
            try {
                val classificationJson = geminiNanoBridge.generateClassification(textList, sensitivityThreshold)
                val classification = parseClassification(classificationJson)
                Log.i(TAG, "🧠 Sentiment result: $classificationJson")

                handler.post {
                    GuardianNativeModule.getInstance()?.emitVisionSentiment(classificationJson)
                    if (classification != null) {
                        applyVerdict(extractedData, classification, pkg, textList)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Background classification error: ${e.message}", e)
            }
        }
    }

    // ---- Live events: scrolling, window changes ----
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString()
        if (SecureAppBlacklist.shouldSkipExtraction(packageName)) return
        
        event.source?.let {
            lastEventSource = it
        }

        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            lastScrollTimestamp = System.currentTimeMillis()
            if (isExtractionThrottled) {
                isExtractionThrottled = false
                Log.d(TAG, "Scroll detected — resuming live extraction.")
            }
        }

        // When throttled, the idle timer handles everything
        if (isExtractionThrottled) return

        // Extract text from the UI tree for the rolling buffer
        val rootNode = getSafeRootNode(event.source) ?: return
        val extractedData = NodeTextExtractor.extractTextAndBounds(rootNode)
        if (extractedData.isEmpty()) return

        val textList = extractedData.map { it.text }
        GuardianNativeModule.getInstance()?.emitTextBatch(textList)

        // Run classification on BACKGROUND thread (never main!)
        val now = System.currentTimeMillis()
        if (now - lastClassificationTimestamp < MIN_CLASSIFY_INTERVAL_MS) return
        lastClassificationTimestamp = now

        backgroundExecutor.execute {
            try {
                val rawResponse = geminiNanoBridge.generateClassification(textList, sensitivityThreshold)
                val classification = parseClassification(rawResponse) ?: return@execute

                handler.post {
                    GuardianNativeModule.getInstance()?.emitVisionSentiment(rawResponse)
                    applyVerdict(extractedData, classification, packageName, textList)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Classification error: ${e.message}", e)
            }
        }
    }

    private fun parseClassification(raw: String): ClassificationResult? {
        return try {
            val json = JSONObject(raw)
            if (!json.has("toxic")) return null
            val toxic = json.optBoolean("toxic", false)
            val flaggedText: String? = if (json.isNull("flaggedText")) null else json.optString("flaggedText", "")
            val flaggedIndex = json.optInt("flaggedIndex", -1)
            ClassificationResult(toxic = toxic, flaggedText = flaggedText, flaggedIndex = flaggedIndex)
        } catch (e: Exception) {
            Log.w(TAG, "Parse error: ${e.message}")
            null
        }
    }

    private data class ClassificationResult(
        val toxic: Boolean,
        val flaggedText: String?,
        val flaggedIndex: Int = -1
    )



    /**
     * Single place where a verdict becomes UI. Applies the escalation policy:
     * strikes 1-2 warn with the sheet, strike 3+ also masks the flagged node.
     */
    private fun applyVerdict(
        extractedData: List<ExtractedNodeData>,
        classification: ClassificationResult,
        packageName: String?,
        textList: List<String>
    ) {
        val now = System.currentTimeMillis()

        if (!classification.toxic) {
            if (escalator.onClean(now, packageName) == WarningEscalator.Action.CLEAR) {
                Log.i(TAG, "🟢 Content clean — clearing overlay and resetting strikes.")
                overlayWindowManager.dismissOverlay()
                reframeBottomSheet.dismissSheet()
            }
            return
        }

        val action = escalator.onToxic(now, packageName)
        val strikes = escalator.strikeCount
        val displayText = classification.flaggedText ?: textList.firstOrNull() ?: "Toxic content"

        GuardianNativeModule.getInstance()?.emitOverlayNeeded(displayText)

        when (action) {
            WarningEscalator.Action.WARN -> {
                Log.w(TAG, "🟠 Strike $strikes — showing on-screen warning.")
                overlayWindowManager.showStrikeWarning(strikes, displayText)
            }

            WarningEscalator.Action.MASK -> {
                Log.w(TAG, "🔴 Strike $strikes — showing full BLACK SCREEN.")
                overlayWindowManager.showBlackScreen()
            }

            else -> Unit
        }
    }

    private fun ordinal(n: Int): String = when (n) {
        1 -> "first"
        2 -> "second"
        3 -> "third"
        else -> "${n}th"
    }

    override fun onInterrupt() {
        Log.w(TAG, "GuardianAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        handler.removeCallbacks(throttleCheckRunnable)
        escalator.reset()
        overlayWindowManager.dismissOverlay()
        reframeBottomSheet.dismissSheet()
        backgroundExecutor.shutdownNow()
        Log.i(TAG, "GuardianAccessibilityService destroyed.")
    }
}
