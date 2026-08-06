package com.guardian.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class OverlayWindowManager(private val context: Context) {

    companion object {
        private const val TAG = "OverlayWindowManager"

        /**
         * Refuse to mask anything covering more than this fraction of the screen.
         *
         * The accessibility tree is walked root-first, and container nodes carry a
         * contentDescription with full-screen boundsInScreen. Masking one of those
         * blacks out the entire display — which is what made the overlay look like
         * a crash rather than a content mask.
         */
        private const val MAX_SCREEN_FRACTION = 0.6

        /** Hard safety net so an overlay can never be stranded on screen. */
        private const val AUTO_DISMISS_MS = 20_000L
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var activeOverlayView: View? = null

    private val autoDismissRunnable = Runnable {
        Log.w(TAG, "Auto-dismissing overlay after ${AUTO_DISMISS_MS}ms safety timeout.")
        dismissOverlay()
    }

    /**
     * Returns true when the rect is a plausible content region: non-empty, on
     * screen, and not effectively the whole display.
     */
    fun isMaskableBounds(bounds: Rect): Boolean {
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            Log.w(TAG, "Rejecting mask: empty bounds $bounds")
            return false
        }
        val dm = context.resources.displayMetrics
        val screenArea = dm.widthPixels.toLong() * dm.heightPixels.toLong()
        if (screenArea <= 0L) return true

        val area = bounds.width().toLong() * bounds.height().toLong()
        val fraction = area.toDouble() / screenArea.toDouble()
        if (fraction > MAX_SCREEN_FRACTION) {
            Log.w(
                TAG,
                "Rejecting mask: bounds $bounds cover ${"%.0f".format(fraction * 100)}% of screen " +
                    "(limit ${"%.0f".format(MAX_SCREEN_FRACTION * 100)}%). Likely a root container, not a post."
            )
            return false
        }
        return true
    }

    /**
     * Draws a mask over [bounds]. Returns false if the bounds were rejected or the
     * window could not be added, so the caller can fall back to a sheet-only warning.
     */
    fun showOverlayAt(bounds: Rect): Boolean {
        if (!isMaskableBounds(bounds)) return false

        dismissOverlay()

        val view = View(context).apply {
            setBackgroundColor(Color.parseColor("#E5000000")) // 90% opaque black mask
        }

        val layoutType = if (context is android.accessibilityservice.AccessibilityService) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.left
            y = bounds.top
        }

        return try {
            windowManager.addView(view, params)
            activeOverlayView = view
            handler.removeCallbacks(autoDismissRunnable)
            handler.postDelayed(autoDismissRunnable, AUTO_DISMISS_MS)
            Log.d(TAG, "Added overlay at bounds: $bounds")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add WindowManager overlay: ${e.message}", e)
            activeOverlayView = null
            false
        }
    }

    /**
     * Moves the active overlay as the user scrolls. Dismisses instead if the node
     * has grown to an implausible size (e.g. the view recycled into a container).
     */
    fun updateOverlayPosition(bounds: Rect) {
        val view = activeOverlayView ?: return
        if (!isMaskableBounds(bounds)) {
            dismissOverlay()
            return
        }

        val params = view.layoutParams as WindowManager.LayoutParams
        params.x = bounds.left
        params.y = bounds.top
        params.width = bounds.width()
        params.height = bounds.height()

        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update overlay position: ${e.message}")
            dismissOverlay()
        }
    }

    fun isShowing(): Boolean = activeOverlayView != null

    fun dismissOverlay() {
        handler.removeCallbacks(autoDismissRunnable)
        val view = activeOverlayView ?: return
        try {
            windowManager.removeView(view)
            Log.d(TAG, "Dismissed overlay view.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove overlay view: ${e.message}")
        } finally {
            activeOverlayView = null
        }
    }

    /**
     * Shows a warning at the top of the screen indicating the strike count.
     */
    fun showStrikeWarning(strikes: Int, text: String) {
        dismissOverlay()

        val view = android.widget.TextView(context).apply {
            this.text = "⚠️ Strike $strikes/3: Toxic Content Detected"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#EE1A1A1A"))
            setPadding(48, 48, 48, 48)
            textSize = 16f
        }

        val layoutType = if (context is android.accessibilityservice.AccessibilityService) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100 // Slight offset from top
        }

        try {
            windowManager.addView(view, params)
            activeOverlayView = view
            handler.removeCallbacks(autoDismissRunnable)
            handler.postDelayed(autoDismissRunnable, 4000L) // auto dismiss warning after 4s
            Log.d(TAG, "Added strike warning overlay.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add warning overlay: ${e.message}", e)
            activeOverlayView = null
        }
    }

    /**
     * Shows a completely black screen on strike 3.
     */
    fun showBlackScreen() {
        dismissOverlay()

        val view = View(context).apply {
            setBackgroundColor(Color.BLACK)
        }

        val layoutType = if (context is android.accessibilityservice.AccessibilityService) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager.addView(view, params)
            activeOverlayView = view
            handler.removeCallbacks(autoDismissRunnable)
            handler.postDelayed(autoDismissRunnable, AUTO_DISMISS_MS) // auto dismiss after safety timeout
            Log.d(TAG, "Added BLACK SCREEN overlay.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add black screen overlay: ${e.message}", e)
            activeOverlayView = null
        }
    }
}
