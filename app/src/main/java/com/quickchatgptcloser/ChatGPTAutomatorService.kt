package com.quickchatgptcloser

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ChatGPTAutomatorService : AccessibilityService() {

    companion object {
        private const val TAG = "ChatGPTAutomator"

        @Volatile
        var instance: ChatGPTAutomatorService? = null

        fun executeDeepForceStop(context: Context, deepForceStop: Boolean = true) {
            val service = instance
            if (service != null) {
                service.performForceStopAndExit(context, deepForceStop)
            } else {
                // Fallback: Immediate Home + Background Kill
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                am?.killBackgroundProcesses("com.openai.chatgpt")
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isForceStopping = false
    private var hasClickedForceStop = false

    // Multilingual support for "Force stop" button across Android ROMs (Samsung, Pixel, Xiaomi, etc.)
    private val forceStopKeywords = listOf(
        "force stop",
        "forcer l'arrêt",
        "forcer l'arret",
        "forzar detención",
        "forzar detencion",
        "forzar cierre",
        "إيقاف إجباري",
        "فرض الإيقاف",
        "beenden erzwingen",
        "stoppen erzwingen",
        "interrompi",
        "arresto forzato",
        "forçar parada",
        "forcar parada",
        "zorla durdur",
        "принудительно остановить",
        "остановить"
    )

    private val confirmDialogKeywords = listOf(
        "ok",
        "force stop",
        "forcer l'arrêt",
        "forzar detención",
        "موافق",
        "подтвердить"
    )

    private val forceStopResourceIds = listOf(
        "button_force_stop",
        "force_stop",
        "right_button",
        "button1",
        "button3",
        "btn_force_stop"
    )

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

    fun performForceStopAndExit(context: Context, deepForceStop: Boolean) {
        // 1. Immediately drop to Home Screen (Phone Apps / Main Menu)
        performGlobalAction(GLOBAL_ACTION_HOME)

        // 2. Kill background processes
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        try {
            am?.killBackgroundProcesses("com.openai.chatgpt")
        } catch (e: Exception) {
            Log.e(TAG, "Process kill error: ${e.message}")
        }

        if (!deepForceStop) {
            return
        }

        // 3. Automated Deep Force Stop via Android Settings
        isForceStopping = true
        hasClickedForceStop = false

        // Strict 220ms safety timeout: Never leave user in Settings under any circumstance
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (isForceStopping) {
                finishAndReturnHome()
            }
        }, 220)

        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:com.openai.chatgpt")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app details: ${e.message}")
            finishAndReturnHome()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isForceStopping || event == null) return

        val rootNode = rootInActiveWindow ?: return
        val pkg = rootNode.packageName?.toString() ?: ""

        if (!pkg.contains("settings")) return

        if (!hasClickedForceStop) {
            // Step A: Find and click "Force stop" button
            if (findAndClickForceStopButton(rootNode)) {
                hasClickedForceStop = true
            }
        } else {
            // Step B: Find and click "OK" in confirmation dialog
            if (findAndClickConfirmDialog(rootNode)) {
                finishAndReturnHome()
            }
        }
    }

    private fun findAndClickForceStopButton(rootNode: AccessibilityNodeInfo): Boolean {
        // 1. Check by Resource ID
        for (resId in forceStopResourceIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.settings:id/$resId")
            for (node in nodes) {
                if (node.isEnabled && clickNodeOrParent(node)) return true
            }
        }

        // 2. Check by Multilingual Text
        for (keyword in forceStopKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            for (node in nodes) {
                val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").lowercase()
                if (text.contains(keyword) && node.isEnabled) {
                    if (clickNodeOrParent(node)) return true
                }
            }
        }
        return false
    }

    private fun findAndClickConfirmDialog(rootNode: AccessibilityNodeInfo): Boolean {
        // Check standard android dialog button1 (Positive Button: OK / Force Stop)
        val dialogOkNodes = rootNode.findAccessibilityNodeInfosByViewId("android:id/button1")
        for (node in dialogOkNodes) {
            if (clickNodeOrParent(node)) return true
        }

        // Check text
        for (keyword in confirmDialogKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            for (node in nodes) {
                if (clickNodeOrParent(node)) return true
            }
        }
        return false
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        node.parent?.let { parent ->
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        return false
    }

    private fun finishAndReturnHome() {
        isForceStopping = false
        hasClickedForceStop = false
        handler.removeCallbacksAndMessages(null)

        // Always end cleanly on Phone Apps / Home Screen
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onInterrupt() {
        finishAndReturnHome()
    }
}
