package com.quickchatgptcloser

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.ActivityManager
import android.content.Context
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ChatGPTAutomatorService : AccessibilityService() {

    companion object {
        private const val TAG = "ChatGPTAutomator"

        @Volatile
        var instance: ChatGPTAutomatorService? = null

        fun executeWipeAndRemoveFromRecents(context: Context) {
            val service = instance
            if (service != null) {
                service.removeAppFromActiveListAndExit(context)
            } else {
                // Fallback: Immediate Home + Background Kill
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                am?.killBackgroundProcesses("com.openai.chatgpt")
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isClearingRecents = false

    private val clearAllKeywords = listOf(
        "close all", "clear all", "tout fermer", "tout effacer", "cerrar todo", "borrar todo", "مسح الكل", "alle schließen", "chiudi tutto"
    )

    private val clearAllResourceIds = listOf(
        "clear_all", "clear_all_button", "button_clear_all", "clear_all_recents_button", "clearAnimView"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected with gesture capability")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
    }

    fun removeAppFromActiveListAndExit(context: Context) {
        isClearingRecents = true

        // 1. Open the Android Active App List (Recents / Task Switcher)
        performGlobalAction(GLOBAL_ACTION_RECENTS)

        // 2. Schedule automated fling gesture to swipe the active ChatGPT card away
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            performSwipeUpToDismiss(context)
        }, 180)

        // Safety timeout: If anything hangs, force exit to Home
        handler.postDelayed({
            if (isClearingRecents) {
                isClearingRecents = false
                performGlobalAction(GLOBAL_ACTION_HOME)
                killProcess(context)
            }
        }, 550)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isClearingRecents || event == null) return

        val rootNode = rootInActiveWindow ?: return

        // On the Recents screen, if a "Close all" / "Clear all" button is visible, click it!
        // This removes ALL apps from the active app list and immediately drops to the home screen.
        for (resId in clearAllResourceIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(resId)
            for (node in nodes) {
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    isClearingRecents = false
                    handler.postDelayed({
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        killProcess(applicationContext)
                    }, 100)
                    return
                }
            }
        }

        for (kw in clearAllKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(kw)
            for (node in nodes) {
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    isClearingRecents = false
                    handler.postDelayed({
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        killProcess(applicationContext)
                    }, 100)
                    return
                }
            }
        }
    }

    private fun performSwipeUpToDismiss(context: Context) {
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels.toFloat()
        val screenHeight = metrics.heightPixels.toFloat()

        // Fling up from center of screen (swipes the active ChatGPT card away from the multitasker)
        val swipePath = Path().apply {
            moveTo(screenWidth * 0.5f, screenHeight * 0.65f)
            lineTo(screenWidth * 0.5f, screenHeight * 0.12f)
        }

        val stroke = GestureDescription.StrokeDescription(swipePath, 0, 160)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                isClearingRecents = false
                // ChatGPT is now removed from the active app list! Exit to Home screen
                handler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    killProcess(context)
                }, 120)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                isClearingRecents = false
                performGlobalAction(GLOBAL_ACTION_HOME)
                killProcess(context)
            }
        }, null)
    }

    private fun killProcess(context: Context) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.killBackgroundProcesses("com.openai.chatgpt")
        } catch (e: Exception) {
            Log.e(TAG, "Error killing background processes: ${e.message}")
        }
    }

    override fun onInterrupt() {
        isClearingRecents = false
        performGlobalAction(GLOBAL_ACTION_HOME)
    }
}
