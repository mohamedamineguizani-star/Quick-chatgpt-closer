package com.quickchatgptcloser

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ChatGPTAutomatorService : AccessibilityService() {

    companion object {
        private const val TAG = "ChatGPTAutomator"

        @Volatile
        var instance: ChatGPTAutomatorService? = null

        fun executePanic(context: Context) {
            val service = instance
            if (service != null) {
                service.startPanicSequence(context)
            } else {
                // If service is not bound yet, immediately kill background process
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                am?.killBackgroundProcesses("com.openai.chatgpt")
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isDeleting = false

    private val deleteKeywords = listOf(
        "delete", "delete chat", "supprimer", "eliminar", "löschen", "cancella", "verwijderen"
    )

    private val menuKeywords = listOf(
        "more options", "more", "options", "actions", "conversation", "menu", "plus d'options", "más opciones"
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

    fun startPanicSequence(context: Context) {
        isDeleting = true
        val rootNode = rootInActiveWindow
        val currentPkg = rootNode?.packageName?.toString()

        // Safety timeout: If anything takes more than 450ms, force exit to home immediately
        handler.postDelayed({
            if (isDeleting) {
                closeAppAndGoHome(context)
            }
        }, 450)

        if (currentPkg == "com.openai.chatgpt" && rootNode != null) {
            // First check if a delete option or confirmation is already visible
            if (findAndClickDelete(rootNode)) {
                handler.postDelayed({ closeAppAndGoHome(context) }, 100)
                return
            }

            // Click the options menu in ChatGPT
            val menuClicked = clickMenuButton(rootNode)
            if (!menuClicked) {
                // If menu icon isn't found, close directly
                closeAppAndGoHome(context)
            }
        } else {
            // ChatGPT is not currently active on screen: immediately go home and kill background process
            closeAppAndGoHome(context)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isDeleting || event == null) return

        val rootNode = rootInActiveWindow ?: return
        val currentPkg = rootNode.packageName?.toString()

        if (currentPkg == "com.openai.chatgpt") {
            // Once the popup menu or delete confirmation dialog appears, click Delete
            if (findAndClickDelete(rootNode)) {
                // Short 80ms delay to let the action dispatch, then dismiss to Home
                handler.postDelayed({
                    closeAppAndGoHome(applicationContext)
                }, 80)
            }
        }
    }

    private fun clickMenuButton(rootNode: AccessibilityNodeInfo): Boolean {
        // Method 1: Search by content-description or text
        for (keyword in menuKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            for (node in nodes) {
                if (clickNodeOrParent(node)) return true
            }
        }

        // Method 2: Search top-right area for the 3-dots / action button
        val screenWidth = getScreenWidth()
        val bounds = Rect()

        fun searchTopRight(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            node.getBoundsInScreen(bounds)

            // Top right app bar zone: top < 350px, right > 60% of screen width
            if (bounds.top in 0..350 && bounds.right > screenWidth * 0.6) {
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                for (kw in menuKeywords) {
                    if (desc.contains(kw)) {
                        if (clickNodeOrParent(node)) return true
                    }
                }
                if (node.isClickable && bounds.width() in 30..300 && bounds.height() in 30..300) {
                    if (clickNodeOrParent(node)) return true
                }
            }

            for (i in 0 until node.childCount) {
                if (searchTopRight(node.getChild(i))) return true
            }
            return false
        }

        return searchTopRight(rootNode)
    }

    private fun findAndClickDelete(rootNode: AccessibilityNodeInfo): Boolean {
        for (keyword in deleteKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            for (node in nodes) {
                val text = node.text?.toString()?.lowercase() ?: ""
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                if (text.contains(keyword) || desc.contains(keyword)) {
                    if (clickNodeOrParent(node)) return true
                }
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

    private fun closeAppAndGoHome(context: Context) {
        isDeleting = false
        handler.removeCallbacksAndMessages(null)

        // 1. Immediately press Home (vanishes ChatGPT without ever opening Settings)
        performGlobalAction(GLOBAL_ACTION_HOME)

        // 2. Terminate ChatGPT background processes
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.killBackgroundProcesses("com.openai.chatgpt")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to kill background process: ${e.message}")
        }
    }

    private fun getScreenWidth(): Int {
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return 1080
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)
        return metrics.widthPixels
    }

    override fun onInterrupt() {
        isDeleting = false
    }
}
