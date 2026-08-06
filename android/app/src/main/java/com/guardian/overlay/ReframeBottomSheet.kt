package com.guardian.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

class ReframeBottomSheet(private val context: Context) {

    companion object {
        private const val TAG = "ReframeBottomSheet"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var activeSheetView: View? = null

    /**
     * Shows a native dismissible bottom sheet containing AI reframing text.
     */
    fun showReframeSheet(reframedText: String, onDismiss: (() -> Unit)? = null) {
        dismissSheet()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Dynamically build layout view
        val sheetView = TextView(context).apply {
            text = "COGNITIVE GUARDIAN REFRAME:\n\n$reframedText\n\n[ Tap to Dismiss ]"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#EE1A1A1A"))
            setPadding(48, 48, 48, 48)
            textSize = 15f
            setOnClickListener {
                dismissSheet()
                onDismiss?.invoke()
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        try {
            windowManager.addView(sheetView, params)
            activeSheetView = sheetView
            Log.d(TAG, "Displayed ReframeBottomSheet.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to display ReframeBottomSheet: ${e.message}", e)
        }
    }

    fun dismissSheet() {
        val view = activeSheetView ?: return
        try {
            windowManager.removeView(view)
            Log.d(TAG, "Dismissed ReframeBottomSheet.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss ReframeBottomSheet: ${e.message}")
        } finally {
            activeSheetView = null
        }
    }
}
