package com.guardian.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.guardian.ai.GeminiNanoBridge
import com.guardian.bridge.GuardianNativeModule
import org.json.JSONObject
import java.util.concurrent.Executors

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val NOTIFICATION_CHANNEL_ID = "guardian_screen_capture"
        private const val NOTIFICATION_ID = 101
        private const val CAPTURE_INTERVAL_MS = 5000L
        private const val MODEL_NAME = "image_classifier.tflite"

        @Volatile
        var isServiceRunning = false
        var sensitivityThreshold: Float = 0.5f

        const val EXTRA_RESULT_CODE = "RESULT_CODE"
        const val EXTRA_RESULT_DATA = "RESULT_DATA"
    }

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    private val geminiNanoBridge by lazy { GeminiNanoBridge(applicationContext) }
    private val overlayWindowManager by lazy { OverlayWindowManager(applicationContext) }
    private val reframeBottomSheet by lazy { ReframeBottomSheet(applicationContext) }
    private val escalator = com.guardian.accessibility.WarningEscalator()
    
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private var imageClassifier: ImageClassifier? = null
    
    private var isCapturing = false

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
        initImageClassifier()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val action = intent.action
        if (action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode == 0 || resultData == null) {
            Log.e(TAG, "Invalid intent extras, stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())
        isServiceRunning = true

        setupMediaProjection(resultCode, resultData)
        
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Live Screen Analysis",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = "STOP"
        }
        val pendingStopIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Guardian is Active")
            .setContentText("Analyzing screen content for protection.")
            // Use a default Android icon since we may not have a specific drawable
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .addAction(android.R.drawable.ic_delete, "Stop", pendingStopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                Log.i(TAG, "MediaProjection stopped.")
                stopSelf()
            }
        }, handler)

        setupVirtualDisplay()
    }

    private var lastCaptureTime = 0L

    private fun setupVirtualDisplay() {
        val metrics = resources.displayMetrics
        val density = metrics.densityDpi
        val width = metrics.widthPixels / 2
        val height = metrics.heightPixels / 2

        // Create ImageReader
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        imageReader?.setOnImageAvailableListener({ reader ->
            val now = System.currentTimeMillis()
            if (now - lastCaptureTime < CAPTURE_INTERVAL_MS) {
                // Too soon, just acquire and close to free the buffer
                try {
                    reader.acquireLatestImage()?.close()
                } catch (e: Exception) { }
                return@setOnImageAvailableListener
            }
            
            try {
                val image = reader.acquireLatestImage()
                if (image != null) {
                    lastCaptureTime = now
                    try {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * image.width

                        val bitmap = Bitmap.createBitmap(
                            image.width + rowPadding / pixelStride,
                            image.height,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)
                        
                        // Crop out the padding
                        val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                        bitmap.recycle()

                        backgroundExecutor.execute {
                            try {
                                processVisionFrame(croppedBitmap)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing image frame: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing image frame: ${e.message}")
                    } finally {
                        try { image.close() } catch (ignored: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error acquiring image: ${e.message}")
            }
        }, handler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "GuardianScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        isCapturing = true
        Log.i(TAG, "✅ VirtualDisplay set up. Width: $width, Height: $height")
    }

    private fun initImageClassifier() {
        backgroundExecutor.execute {
            try {
                val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath(MODEL_NAME)
                val optionsBuilder = ImageClassifier.ImageClassifierOptions.builder()
                    .setBaseOptions(baseOptionsBuilder.build())
                    .setMaxResults(3)
                imageClassifier = ImageClassifier.createFromOptions(this, optionsBuilder.build())
                Log.i(TAG, "✅ Image Classifier loaded in ScreenCaptureService.")
            } catch (e: Exception) {
                Log.w(TAG, "Image Classifier not available: ${e.message}")
            }
        }
    }

    private fun processVisionFrame(bitmap: Bitmap) {
        var visionLabel = "unknown"
        var visionScore = 0.0f
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val results = imageClassifier?.classify(mpImage)
            val topCategory = results?.classificationResult()?.classifications()?.firstOrNull()?.categories()?.firstOrNull()
            if (topCategory != null) {
                visionLabel = topCategory.categoryName()
                visionScore = topCategory.score()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image classification failed: ${e.message}")
        }

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val finalVisionLabel = visionLabel
        val finalVisionScore = visionScore
        
        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val extractedText = visionText.text
                if (extractedText.isNotBlank()) {
                    Log.d(TAG, "OCR (${extractedText.length} chars): ${extractedText.take(100)}")
                    
                    handler.post {
                        GuardianNativeModule.getInstance()?.emitVisionScan(
                            ocrText = extractedText, visionLabel = finalVisionLabel, visionScore = finalVisionScore
                        )
                        GuardianNativeModule.getInstance()?.emitTextBatch(listOf("[OCR] $extractedText"))
                    }

                    backgroundExecutor.execute {
                        try {
                            val json = geminiNanoBridge.generateClassification(listOf(extractedText), sensitivityThreshold)
                            val cls = parseClassification(json)
                            handler.post { GuardianNativeModule.getInstance()?.emitVisionSentiment(json) }
                            
                            if (cls?.toxic == true) {
                                val flaggedText = cls.flaggedText ?: extractedText.take(100)
                                handler.post {
                                    GuardianNativeModule.getInstance()?.emitOverlayNeeded(flaggedText)
                                    
                                    val now = System.currentTimeMillis()
                                    val action = escalator.onToxic(now, null)
                                    val strikes = escalator.strikeCount

                                    when (action) {
                                        com.guardian.accessibility.WarningEscalator.Action.WARN -> {
                                            Log.w(TAG, "🟠 Vision Strike $strikes — showing on-screen warning.")
                                            overlayWindowManager.showStrikeWarning(strikes, flaggedText)
                                        }
                                        com.guardian.accessibility.WarningEscalator.Action.MASK -> {
                                            Log.w(TAG, "🔴 Vision Strike $strikes — showing full BLACK SCREEN.")
                                            overlayWindowManager.showBlackScreen()
                                        }
                                        else -> Unit
                                    }
                                }
                            } else {
                                val now = System.currentTimeMillis()
                                if (escalator.onClean(now, null) == com.guardian.accessibility.WarningEscalator.Action.CLEAR) {
                                    handler.post {
                                        overlayWindowManager.dismissOverlay()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Sentiment analysis failed: ${e.message}")
                        }
                    }
                }
            }
            .addOnCompleteListener { 
                bitmap.recycle() 
            }
    }
    
    private fun parseClassification(raw: String): ClassificationResult? {
        return try {
            val json = JSONObject(raw)
            if (!json.has("toxic")) return null
            val toxic = json.optBoolean("toxic", false)
            val flaggedText: String? = if (json.isNull("flaggedText")) null else json.optString("flaggedText", "")
            ClassificationResult(toxic = toxic, flaggedText = flaggedText)
        } catch (e: Exception) {
            null
        }
    }

    private data class ClassificationResult(val toxic: Boolean, val flaggedText: String?)

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        isCapturing = false
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        Log.i(TAG, "ScreenCaptureService destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
