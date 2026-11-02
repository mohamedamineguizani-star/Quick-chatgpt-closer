package com.quickchatgptcloser

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ChatGPTAutomatorService : AccessibilityService() {

    companion object {
        @Volatile
        var isPanicActive = false

        fun triggerForceStop(context: Context) {
            isPanicActive = true
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:com.openai.chatgpt")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isPanicActive || event == null) return

        val rootNode = rootInActiveWindow ?: return

        // Step 1: Look for and click "Force stop" button on Settings App Details page
        val forceStopLabels = listOf("Force stop", "Forcer l'arrêt", "Forzar detención", "Beenden erzwingen")
        var forceStopClicked = false

        for (label in forceStopLabels) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                if (node.isEnabled && node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    forceStopClicked = true
                    break
                }
                node.parent?.let { parent ->
                    if (parent.isEnabled && parent.isClickable) {
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        forceStopClicked = true
                        return@let
                    }
                }
            }
            if (forceStopClicked) break
        }

        // Step 2: Confirm popup dialog ("OK" / "Force stop")
        val confirmLabels = listOf("OK", "Force stop", "Forcer l'arrêt", "Forzar detención")
        for (label in confirmLabels) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    finishPanicAction()
                    return
                }
            }
        }
    }

    private fun finishPanicAction() {
        isPanicActive = false
        // Immediately return to Home screen
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onInterrupt() {
        isPanicActive = false
    }
}
