package com.quickchatgptcloser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CornerTriggerService : Service() {

    private lateinit var windowManager: WindowManager
    private var cornerOverlayView: View? = null

    private var tapCount = 0
    private var lastTapTime = 0L
    private val TRIPLE_TAP_WINDOW_MS = 600L

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "quick_closer_guard_channel"
        const val NOTIFICATION_ID = 2001
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        setupCornerOverlay()
    }

    private fun setupCornerOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 90x90 px touch area in the top-right corner
        val params = WindowManager.LayoutParams(
            90, 90,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 0
        }

        cornerOverlayView = View(this).apply {
            // Subtle faint red indicator (can be set to Color.TRANSPARENT once tested)
            setBackgroundColor(Color.argb(35, 255, 0, 0))

            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime <= TRIPLE_TAP_WINDOW_MS) {
                        tapCount++
                        if (tapCount >= 3) {
                            tapCount = 0
                            executePanicActions()
                        }
                    } else {
                        tapCount = 1
                    }
                    lastTapTime = now
                    true
                } else false
            }
        }

        try {
            windowManager.addView(cornerOverlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun executePanicActions() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val token = prefs.getString("chatgpt_token", "") ?: ""

        // 1. Silent backend chat delete in parallel if token is provided
        if (token.isNotBlank()) {
            serviceScope.launch {
                ChatGptManager.deleteLastChat(token)
            }
        }

        // 2. Direct In-App deletion & Instant close to Home (No Settings screen!)
        ChatGPTAutomatorService.executePanic(applicationContext)
    }

    private fun buildForegroundNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Panic Guard Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps the triple-tap trigger active in the background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Quick ChatGPT Closer Active")
            .setContentText("Triple-tap top-right corner to close ChatGPT.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        cornerOverlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
