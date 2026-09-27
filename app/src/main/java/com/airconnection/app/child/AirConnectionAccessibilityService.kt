package com.airconnection.app.child

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AirConnectionAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AirConnectionAcc"
        var instance: AirConnectionAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Air Connection Accessibility Service Connected!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: ""
        if (pkgName == "com.android.settings") {
            if (!ChildProtectionManager.isUninstallAllowed(this)) {
                val textContent = event.text.joinToString(" ").lowercase()
                val contentDesc = event.contentDescription?.toString()?.lowercase() ?: ""
                
                if (textContent.contains("air connection") || 
                    contentDesc.contains("air connection") ||
                    textContent.contains("com.airconnection.app")
                ) {
                    Log.w(TAG, "Child attempted to inspect/uninstall Air Connection in Settings. Redirecting to Home!")
                    performGlobalActionHome()
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun performTap(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path()
            path.moveTo(x, y)
            val gestureBuilder = GestureDescription.Builder()
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            dispatchGesture(gestureBuilder.build(), null, null)
            Log.d(TAG, "Dispatched Tap at ($x, $y)")
        }
    }

    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path()
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)
            val gestureBuilder = GestureDescription.Builder()
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            dispatchGesture(gestureBuilder.build(), null, null)
            Log.d(TAG, "Dispatched Swipe from ($startX, $startY) to ($endX, $endY)")
        }
    }

    fun performGlobalActionHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun performGlobalActionBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performGlobalActionRecents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }
}
