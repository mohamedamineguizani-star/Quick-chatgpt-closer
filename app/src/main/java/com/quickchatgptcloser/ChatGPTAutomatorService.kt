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
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                am?.killBackgroundProcesses("com.openai.chatgpt")
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isDeleting = false

    private val newChatKeywords = listOf(
        "new chat", "nouvelle discussion", "nuevo chat", "nuova chat", "neuer chat", "create"
    )

    private val deleteKeywords = listOf(
        "delete", "delete chat", "supprimer", "eliminar", "löschen", "cancella", "verwijderen"
    )

    private val menuKeywords = listOf(
        "more options", "more", "options", "actions", "conversation", "menu", "plus d'options", "más opciones"
    )

    private val drawerKeywords = listOf(
        "open navigation", "navigation drawer", "open drawer", "sidebar", "menu", "navigation"
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

        // Hard safety timeout: Dismiss to home within 500ms so user is never stranded
        handler.postDelayed({
            if (isDeleting) {
                closeAppAndGoHome(context)
            }
        }, 500)

        if (currentPkg == "com.openai.chatgpt" && rootNode != null) {
            // STEP 1: Immediately tap "New chat" so active conversation is wiped from the screen
            clickNewChat(rootNode)

            // STEP 2: Try to find and click "Delete" if options are already visible
            if (findAndClickDelete(rootNode)) {
                handler.postDelayed({ closeAppAndGoHome(context) }, 120)
                return
            }

            // STEP 3: Click options menu or open drawer to delete from account
            if (clickMenuOrDrawer(rootNode)) {
                // Event listener handles menu/drawer response
            } else {
                handler.postDelayed({ closeAppAndGoHome(context) }, 150)
            }
        } else {
            closeAppAndGoHome(context)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isDeleting || event == null) return

        val rootNode = rootInActiveWindow ?: return
        val currentPkg = rootNode.packageName?.toString()

        if (currentPkg == "com.openai.chatgpt") {
            // If delete button appears in popup or confirmation dialog, click it
            if (findAndClickDelete(rootNode)) {
                handler.postDelayed({
                    closeAppAndGoHome(applicationContext)
                }, 80)
            } else {
                // If drawer opened, long-press the first conversation item
                longClickFirstHistoryItem(rootNode)
            }
        }
    }

    private fun clickNewChat(rootNode: AccessibilityNodeInfo): Boolean {
        for (kw in newChatKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(kw)
            for (node in nodes) {
                if (clickNodeOrParent(node)) return true
            }
        }

        val screenWidth = getScreenWidth()
        val bounds = Rect()

        fun searchTopRightCompose(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            node.getBoundsInScreen(bounds)

            if (bounds.top in 0..350 && bounds.right > screenWidth * 0.5) {
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                for (kw in newChatKeywords) {
                    if (desc.contains(kw)) {
                        if (clickNodeOrParent(node)) return true
                    }
                }
            }
            for (i in 0 until node.childCount) {
                if (searchTopRightCompose(node.getChild(i))) return true
            }
            return false
        }

        return searchTopRightCompose(rootNode)
    }

    private fun clickMenuOrDrawer(rootNode: AccessibilityNodeInfo): Boolean {
        // Try top-right "..." menu
        for (keyword in menuKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            for (node in nodes) {
                if (clickNodeOrParent(node)) return true
            }
        }

        // Try top-left drawer icon
        val bounds = Rect()
        fun searchTopLeftDrawer(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            node.getBoundsInScreen(bounds)

            if (bounds.top in 0..350 && bounds.left < 250) {
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                for (kw in drawerKeywords) {
                    if (desc.contains(kw)) {
                        if (clickNodeOrParent(node)) return true
                    }
                }
                if (node.isClickable && bounds.width() in 30..250 && bounds.height() in 30..250) {
                    if (clickNodeOrParent(node)) return true
                }
            }
            for (i in 0 until node.childCount) {
                if (searchTopLeftDrawer(node.getChild(i))) return true
            }
            return false
        }

        return searchTopLeftDrawer(rootNode)
    }

    private fun longClickFirstHistoryItem(rootNode: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        fun searchFirstListItem(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            node.getBoundsInScreen(bounds)

            if (bounds.top in 200..600 && bounds.left < 400 && node.isClickable) {
                if (node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                    return true
                }
            }
            for (i in 0 until node.childCount) {
                if (searchFirstListItem(node.getChild(i))) return true
            }
            return false
        }
        return searchFirstListItem(rootNode)
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

        // 1. Immediately press Home (returns to home screen)
        performGlobalAction(GLOBAL_ACTION_HOME)

        // 2. Kill background process so ChatGPT does not retain active activity in RAM
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
