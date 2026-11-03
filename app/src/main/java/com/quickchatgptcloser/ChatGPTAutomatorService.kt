package com.quickchatgptcloser

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
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

        const val PKG_CHATGPT = "com.openai.chatgpt"
        const val PKG_CLAUDE = "com.anthropic.claude"

        @Volatile
        var instance: ChatGPTAutomatorService? = null

        @Volatile
        var lastForegroundPackage: String = PKG_CHATGPT

        @Volatile
        var lastAiPackage: String = PKG_CHATGPT

        fun executeWipeAndRemoveFromRecents(context: Context) {
            val service = instance
            if (service != null) {
                service.executePanicSequence(context)
            } else {
                // Fallback: Immediate Home + Background Kill
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                am?.killBackgroundProcesses(PKG_CHATGPT)
                am?.killBackgroundProcesses(PKG_CLAUDE)

                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(homeIntent)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    private enum class PanicState {
        IDLE,
        SWIPING_RECENTS,
        FORCE_STOPPING_SETTINGS
    }

    private var currentState = PanicState.IDLE
    private var currentTargetPackage: String = PKG_CHATGPT
    private var hasClickedForceStop = false
    private var hasConfirmedDialog = false

    private val clearAllKeywords = listOf(
        "close all", "clear all", "tout fermer", "tout effacer", "cerrar todo", "borrar todo", "مسح الكل", "alle schließen", "chiudi tutto"
    )

    private val clearAllResourceIds = listOf(
        "clear_all", "clear_all_button", "button_clear_all", "clear_all_recents_button", "clearAnimView"
    )

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
        "остановить",
        "закрыть"
    )

    private val confirmDialogKeywords = listOf(
        "ok",
        "force stop",
        "forcer l'arrêt",
        "forzar detención",
        "forzar detencion",
        "موافق",
        "подтвердить",
        "ja",
        "si",
        "oui"
    )

    private val forceStopResourceIds = listOf(
        "button_force_stop",
        "force_stop",
        "right_button",
        "button1",
        "button3",
        "btn_force_stop",
        "com.android.settings:id/button_force_stop",
        "com.android.settings:id/force_stop_button"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected with gesture capability and multi-app support")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
    }

    fun executePanicSequence(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val targetMode = prefs.getString("target_app_mode", "auto") ?: "auto"
        val deepForceStop = prefs.getBoolean("deep_force_stop", true)
        val removeFromRecents = prefs.getBoolean("remove_from_recents", true)

        currentTargetPackage = determineTargetPackage(targetMode)
        Log.d(TAG, "Executing panic for target: $currentTargetPackage (mode: $targetMode, deepForceStop: $deepForceStop)")

        // 1. Immediately kill background processes for both Claude and ChatGPT (and target package)
        killProcesses(context, currentTargetPackage)

        // 2. Clear any lingering handler callbacks
        handler.removeCallbacksAndMessages(null)

        if (removeFromRecents) {
            startRecentsDismissal(context, deepForceStop)
        } else if (deepForceStop) {
            startForceStopViaSettings(context)
        } else {
            finishAndReturnHome(context)
        }
    }

    private fun determineTargetPackage(mode: String): String {
        return when (mode) {
            "claude" -> PKG_CLAUDE
            "chatgpt" -> PKG_CHATGPT
            "last_active" -> {
                if (lastForegroundPackage.isNotBlank() && !isIgnoredPackage(lastForegroundPackage)) {
                    lastForegroundPackage
                } else {
                    lastAiPackage
                }
            }
            else -> {
                // "auto" mode: prioritize Claude or ChatGPT if either was active, else last foreground app
                if (lastForegroundPackage == PKG_CLAUDE || lastForegroundPackage == PKG_CHATGPT) {
                    lastForegroundPackage
                } else if (lastAiPackage.isNotBlank()) {
                    lastAiPackage
                } else if (lastForegroundPackage.isNotBlank() && !isIgnoredPackage(lastForegroundPackage)) {
                    lastForegroundPackage
                } else {
                    PKG_CHATGPT
                }
            }
        }
    }

    private fun isIgnoredPackage(pkg: String): Boolean {
        if (pkg == packageName || pkg == "com.android.systemui" || pkg.contains("settings")) {
            return true
        }
        val launcherPkgs = getLauncherPackages()
        if (launcherPkgs.contains(pkg)) {
            return true
        }
        val lower = pkg.lowercase()
        return lower.contains("launcher") || lower.contains("trebuchet") || lower.contains("home")
    }

    private fun getLauncherPackages(): Set<String> {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfos.mapNotNull { it.activityInfo?.packageName }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun startRecentsDismissal(context: Context, deepForceStop: Boolean) {
        currentState = PanicState.SWIPING_RECENTS

        // Step 1: Open Recents (Task Switcher / Active Apps)
        performGlobalAction(GLOBAL_ACTION_RECENTS)

        // Step 2: Schedule automated fling gesture to swipe the active card away
        handler.postDelayed({
            if (currentState == PanicState.SWIPING_RECENTS) {
                performSwipeUpToDismiss(context, deepForceStop)
            }
        }, 180)

        // Safety timeout for Recents phase: If gesture/click hangs, move to Settings or Home
        handler.postDelayed({
            if (currentState == PanicState.SWIPING_RECENTS) {
                if (deepForceStop) {
                    startForceStopViaSettings(context)
                } else {
                    finishAndReturnHome(context)
                }
            }
        }, 550)
    }

    private fun performSwipeUpToDismiss(context: Context, deepForceStop: Boolean) {
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels.toFloat()
        val screenHeight = metrics.heightPixels.toFloat()

        // Fling up from center of screen (swipes the active card away from the multitasker)
        val swipePath = Path().apply {
            moveTo(screenWidth * 0.5f, screenHeight * 0.65f)
            lineTo(screenWidth * 0.5f, screenHeight * 0.12f)
        }

        val stroke = GestureDescription.StrokeDescription(swipePath, 0, 160)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                handler.postDelayed({
                    if (deepForceStop) {
                        startForceStopViaSettings(context)
                    } else {
                        finishAndReturnHome(context)
                    }
                }, 100)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                handler.postDelayed({
                    if (deepForceStop) {
                        startForceStopViaSettings(context)
                    } else {
                        finishAndReturnHome(context)
                    }
                }, 100)
            }
        }, null)
    }

    private fun startForceStopViaSettings(context: Context) {
        currentState = PanicState.FORCE_STOPPING_SETTINGS
        hasClickedForceStop = false
        hasConfirmedDialog = false

        // Launch Settings App Details for target app with NO_ANIMATION and EXCLUDE_FROM_RECENTS
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$currentTargetPackage")
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app details settings: ${e.message}")
            finishAndReturnHome(context)
            return
        }

        // Strict safety timeout: Never leave user stranded in Settings
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (currentState == PanicState.FORCE_STOPPING_SETTINGS) {
                finishAndReturnHome(context)
            }
        }, 450)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: ""

        // Track foreground and AI apps whenever user switches windows
        if (pkgName.isNotBlank() && currentState == PanicState.IDLE) {
            if (pkgName == PKG_CLAUDE) {
                lastForegroundPackage = PKG_CLAUDE
                lastAiPackage = PKG_CLAUDE
            } else if (pkgName == PKG_CHATGPT) {
                lastForegroundPackage = PKG_CHATGPT
                lastAiPackage = PKG_CHATGPT
            } else if (!isIgnoredPackage(pkgName)) {
                lastForegroundPackage = pkgName
            }
        }

        when (currentState) {
            PanicState.SWIPING_RECENTS -> {
                handleRecentsEvent()
            }
            PanicState.FORCE_STOPPING_SETTINGS -> {
                handleSettingsEvent(pkgName)
            }
            PanicState.IDLE -> {
                // Do nothing
            }
        }
    }

    private fun handleRecentsEvent() {
        val rootNode = rootInActiveWindow ?: return

        // If a "Clear all" / "Close all" button is visible, click it immediately
        for (resId in clearAllResourceIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(resId)
            for (node in nodes) {
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val deepForceStop = prefs.getBoolean("deep_force_stop", true)
                    handler.removeCallbacksAndMessages(null)
                    handler.postDelayed({
                        if (deepForceStop) {
                            startForceStopViaSettings(applicationContext)
                        } else {
                            finishAndReturnHome(applicationContext)
                        }
                    }, 100)
                    return
                }
            }
        }

        for (kw in clearAllKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(kw)
            for (node in nodes) {
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val deepForceStop = prefs.getBoolean("deep_force_stop", true)
                    handler.removeCallbacksAndMessages(null)
                    handler.postDelayed({
                        if (deepForceStop) {
                            startForceStopViaSettings(applicationContext)
                        } else {
                            finishAndReturnHome(applicationContext)
                        }
                    }, 100)
                    return
                }
            }
        }
    }

    private fun handleSettingsEvent(pkgName: String) {
        val rootNode = rootInActiveWindow ?: return

        if (!hasClickedForceStop) {
            // Find and click "Force stop" button
            val clicked = findAndClickForceStopButton(rootNode)
            if (clicked) {
                hasClickedForceStop = true
            }
        } else {
            // Find and click "OK" or positive confirmation in dialog
            val confirmed = findAndClickConfirmDialog(rootNode)
            if (confirmed) {
                hasConfirmedDialog = true
                finishAndReturnHome(applicationContext)
            }
        }
    }

    private fun findAndClickForceStopButton(rootNode: AccessibilityNodeInfo): Boolean {
        // 1. By Resource ID
        for (resId in forceStopResourceIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(resId)
            for (node in nodes) {
                if (!node.isEnabled) {
                    // Button is disabled, app is already stopped!
                    finishAndReturnHome(applicationContext)
                    return true
                }
                if (clickNodeOrParent(node)) return true
            }
        }

        // 2. By Text
        for (keyword in forceStopKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            for (node in nodes) {
                val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").lowercase()
                if (text.contains(keyword)) {
                    if (!node.isEnabled) {
                        finishAndReturnHome(applicationContext)
                        return true
                    }
                    if (clickNodeOrParent(node)) return true
                }
            }
        }
        return false
    }

    private fun findAndClickConfirmDialog(rootNode: AccessibilityNodeInfo): Boolean {
        // Standard Android dialog positive button
        val dialogOkNodes = rootNode.findAccessibilityNodeInfosByViewId("android:id/button1")
        for (node in dialogOkNodes) {
            if (clickNodeOrParent(node)) return true
        }

        // Text matches in dialog
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

    private fun finishAndReturnHome(context: Context) {
        currentState = PanicState.IDLE
        hasClickedForceStop = false
        hasConfirmedDialog = false
        handler.removeCallbacksAndMessages(null)

        // Return immediately to Home Screen
        performGlobalAction(GLOBAL_ACTION_HOME)

        // Kill processes again to ensure full termination
        killProcesses(context, currentTargetPackage)
    }

    private fun killProcesses(context: Context, targetPkg: String) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
            am.killBackgroundProcesses(PKG_CHATGPT)
            am.killBackgroundProcesses(PKG_CLAUDE)
            if (targetPkg != PKG_CHATGPT && targetPkg != PKG_CLAUDE && targetPkg.isNotBlank()) {
                am.killBackgroundProcesses(targetPkg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error killing background processes: ${e.message}")
        }
    }

    override fun onInterrupt() {
        finishAndReturnHome(applicationContext)
    }
}
