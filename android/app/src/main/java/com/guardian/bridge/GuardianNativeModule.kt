package com.guardian.bridge

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import android.util.Log
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.guardian.accessibility.GuardianAccessibilityService
import com.guardian.overlay.OverlayWindowManager
import com.guardian.overlay.ReframeBottomSheet
import com.guardian.overlay.ScreenCaptureService

class GuardianNativeModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val MODULE_NAME = "GuardianNativeModule"
        private const val TAG = "GuardianNativeModule"
        private const val REQUEST_CODE_MEDIA_PROJECTION = 1001

        @Volatile
        private var instance: GuardianNativeModule? = null

        fun getInstance(): GuardianNativeModule? = instance
    }

    private val activityEventListener: ActivityEventListener = object : BaseActivityEventListener() {
        override fun onActivityResult(activity: android.app.Activity?, requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
                if (resultCode == android.app.Activity.RESULT_OK && data != null) {
                    val serviceIntent = Intent(reactApplicationContext, ScreenCaptureService::class.java).apply {
                        putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                        putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        reactApplicationContext.startForegroundService(serviceIntent)
                    } else {
                        reactApplicationContext.startService(serviceIntent)
                    }
                } else {
                    Log.w(TAG, "MediaProjection permission denied by user.")
                }
            }
        }
    }

    init {
        instance = this
        reactContext.addActivityEventListener(activityEventListener)
    }

    override fun getName(): String = MODULE_NAME

    // Shared instances
    private var geminiNanoBridge: com.guardian.ai.GeminiNanoBridge? = null

    fun getGeminiBridge(): com.guardian.ai.GeminiNanoBridge {
        if (geminiNanoBridge == null) {
            geminiNanoBridge = com.guardian.ai.GeminiNanoBridge(reactApplicationContext)
        }
        return geminiNanoBridge!!
    }

    @ReactMethod
    fun startExtraction(promise: Promise) {
        val isServiceRunning = GuardianAccessibilityService.isServiceRunning || ScreenCaptureService.isServiceRunning
        if (!isServiceRunning) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                reactApplicationContext.startActivity(intent)
                Log.i(TAG, "Directed user to Accessibility Settings.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open Accessibility Settings: \${e.message}")
            }
        } else {
            Log.i(TAG, "GuardianAccessibilityService already running.")
        }
    }

    @ReactMethod
    fun stopExtraction() {
        Log.i(TAG, "stopExtraction called from React Native.")
        GuardianAccessibilityService.isServiceRunning = false
    }

    /**
     * Called from JS SettingsScreen whenever the user changes sensitivity.
     * Propagates the value to the running AccessibilityService.
     */
    @ReactMethod
    fun setThreshold(sensitivity: Double) {
        val threshold = sensitivity.toFloat().coerceIn(0f, 1f)
        Log.i(TAG, "setThreshold: $threshold")
        GuardianAccessibilityService.sensitivityThreshold = threshold
    }

    /**
     * Draws native overlay + bottom sheet from the JS side (used by DevMenu test button).
     */
    @ReactMethod
    fun showNativeOverlay(flaggedText: String, rectTop: Int, rectLeft: Int, rectBottom: Int, rectRight: Int) {
        try {
            val bounds = Rect(rectLeft, rectTop, rectRight, rectBottom)
            val overlayManager = OverlayWindowManager(reactApplicationContext)
            overlayManager.showOverlayAt(bounds)

            val sheet = ReframeBottomSheet(reactApplicationContext)
            sheet.showReframeSheet(
                "You've been exposed to: \"$flaggedText\". The algorithm is optimizing for outrage. Take a breath?"
            ) {
                overlayManager.dismissOverlay()
            }
        } catch (e: Exception) {
            Log.e(TAG, "showNativeOverlay failed: ${e.message}")
        }
    }

    fun showOverlayBlocking() {
        try {
            val metrics = reactApplicationContext.resources.displayMetrics
            val bounds = Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
            val overlayManager = OverlayWindowManager(reactApplicationContext)
            overlayManager.showOverlayAt(bounds)

            val sheet = ReframeBottomSheet(reactApplicationContext)
            sheet.showReframeSheet(
                "Toxic Visual or Video Content Detected. Take a breath?"
            ) {
                overlayManager.dismissOverlay()
            }
        } catch (e: Exception) {
            Log.e(TAG, "showOverlayBlocking failed: ${e.message}")
        }
    }

    @ReactMethod
    fun addListener(eventName: String?) {
        // Required for RN built in Event Emitter Calls.
    }

    @ReactMethod
    fun removeListeners(count: Int?) {
        // Required for RN built in Event Emitter Calls.
    }

    @ReactMethod
    fun startMediaProjection() {
        val currentActivity = currentActivity
        if (currentActivity != null) {
            val mediaProjectionManager = reactApplicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val intent = mediaProjectionManager.createScreenCaptureIntent()
            currentActivity.startActivityForResult(intent, REQUEST_CODE_MEDIA_PROJECTION)
        }
    }

    /**
     * Emits text batch event to JavaScript listeners.
     */
    fun emitTextBatch(textList: List<String>) {
        if (!reactApplicationContext.hasActiveReactInstance()) {
            return
        }

        val array = Arguments.createArray()
        for (text in textList) {
            array.pushString(text)
        }

        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit("onTextBatch", array)
    }

    /**
     * Emits overlay trigger event to JavaScript listeners.
     */
    fun emitOverlayNeeded(flaggedText: String) {
        if (!reactApplicationContext.hasActiveReactInstance()) {
            return
        }

        val params = Arguments.createMap().apply {
            putString("flaggedText", flaggedText)
        }

        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit("onOverlayNeeded", params)
    }

    /**
     * Emits real-time vision scan results (OCR text + image classification) to JS for the live activity feed.
     */
    fun emitVisionScan(ocrText: String, visionLabel: String, visionScore: Float) {
        if (!reactApplicationContext.hasActiveReactInstance()) {
            return
        }

        val params = Arguments.createMap().apply {
            putString("ocrText", ocrText)
            putString("visionLabel", visionLabel)
            putDouble("visionScore", visionScore.toDouble())
            putDouble("timestamp", System.currentTimeMillis().toDouble())
        }

        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit("onVisionScan", params)
    }

    /**
     * Emits sentiment analysis result from MobileBERT to JS for the live activity feed.
     */
    fun emitVisionSentiment(classificationJson: String) {
        if (!reactApplicationContext.hasActiveReactInstance()) {
            return
        }

        val params = Arguments.createMap().apply {
            putString("result", classificationJson)
            putDouble("timestamp", System.currentTimeMillis().toDouble())
        }

        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit("onVisionSentiment", params)
    }
}
