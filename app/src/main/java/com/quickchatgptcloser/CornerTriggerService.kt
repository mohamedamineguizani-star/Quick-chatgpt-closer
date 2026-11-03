package com.quickchatgptcloser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
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
    private val TRIPLE_TAP_WINDOW_MS = 950L

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private const val TAG = "CornerTriggerService"
        const val CHANNEL_ID = "quick_closer_guard_channel"
        const val NOTIFICATION_ID = 2001
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startSafeForeground()
        setupCornerOverlay()
    }

    private fun startSafeForeground() {
        val notification = buildForegroundNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed startForeground with type: ${e.message}")
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (err: Exception) {
                Log.e(TAG, "Fatal fallback startForeground: ${err.message}")
            }
        }
    }

    private fun setupCornerOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = resources.displayMetrics.density
        // 60dp size gives an easy, comfortable touch target
        val targetSize = (60 * density).toInt()
        // 48dp top margin ensures it sits comfortably below the status bar / camera cutout
        val topMargin = (48 * density).toInt()
        val rightMargin = (12 * density).toInt()

        val params = WindowManager.LayoutParams(
            targetSize, targetSize,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = rightMargin
            y = topMargin
        }

        // Circular badge with semi-transparent crimson background and white border
        val badgeBackground = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(175, 210, 30, 30))
            setStroke((2 * density).toInt(), Color.argb(200, 255, 255, 255))
        }

        cornerOverlayView = View(this).apply {
            background = badgeBackground
            elevation = 10f

            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val now = System.currentTimeMillis()

                    // Quick visual bounce feedback
                    v.animate().scaleX(1.25f).scaleY(1.25f).setDuration(60).withEndAction {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(60).start()
                    }.start()

                    if (now - lastTapTime <= TRIPLE_TAP_WINDOW_MS) {
                        tapCount++
                        if (tapCount >= 3) {
                            tapCount = 0
                            // Strong double buzz for panic trigger
                            vibrateDoubleBuzz()
                            executePanicActions()
                        } else {
                            // Short tick vibration on 2nd tap
                            vibrateTick()
                        }
                    } else {
                        tapCount = 1
                        // Short tick vibration on 1st tap
                        vibrateTick()
                    }
                    lastTapTime = now
                    true
                } else false
            }
        }

        try {
            windowManager.addView(cornerOverlayView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay view: ${e.message}", e)
        }
    }

    private fun vibrateTick() {
        try {
            val vibrator = getVibrator()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(35)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun vibrateDoubleBuzz() {
        try {
            val vibrator = getVibrator()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 100, 70, 150)
                val amplitudes = intArrayOf(0, 255, 0, 255)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 100, 70, 150), -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun executePanicActions() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val token = prefs.getString("chatgpt_token", "") ?: ""
        val wipeRecentHistory = prefs.getBoolean("wipe_recent_history", true)

        if (token.isNotBlank()) {
            serviceScope.launch {
                val countToDelete = if (wipeRecentHistory) 5 else 1
                ChatGptManager.deleteChats(token, countToDelete)
            }
        }

        // 1. Removes ChatGPT completely from the Android Active App List (Recents / Multitasker)
        // 2. Destroys activity snapshot and terminates the process
        // 3. Exits cleanly to Home Screen / Phone Apps Menu
        ChatGPTAutomatorService.executeWipeAndRemoveFromRecents(applicationContext)
    }

    private fun buildForegroundNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Quick Closer Guard",
                NotificationManager.IMPORTANCE_LOW
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
            .setContentText("Triple-tap top-right red badge to panic close ChatGPT.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
