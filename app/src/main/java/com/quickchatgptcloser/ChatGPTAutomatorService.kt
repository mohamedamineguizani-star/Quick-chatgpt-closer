package com.quickchatgptcloser

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class ChatGPTAutomatorService : AccessibilityService() {

    companion object {
        private const val TAG = "ChatGPTAutomator"

        @Volatile
        var instance: ChatGPTAutomatorService? = null

        fun executePanic(context: Context) {
            val service = instance
            if (service != null) {
                service.closeAppInstantly(context)
            } else {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                am?.killBackgroundProcesses("com.openai.chatgpt")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
    }

    fun closeAppInstantly(context: Context) {
        // 1. Instantly return to Home Screen (disappears ChatGPT without touching anything on screen)
        performGlobalAction(GLOBAL_ACTION_HOME)

        // 2. Kill background process so ChatGPT does not keep its active state in RAM
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.killBackgroundProcesses("com.openai.chatgpt")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to kill process: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No blind clicks needed - API handles guaranteed deletion, Home handles guaranteed closing
    }

    override fun onInterrupt() {}
}
