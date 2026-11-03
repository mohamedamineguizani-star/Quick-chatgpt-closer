package com.quickchatgptcloser

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var tokenEditText: TextInputEditText
    private lateinit var btnSaveToken: Button
    private lateinit var btnOverlayPermission: Button
    private lateinit var btnAccessibilityPermission: Button
    private lateinit var btnBatteryOptimization: Button
    private lateinit var btnUnlockRestricted: Button
    private lateinit var cardRestrictedSetting: MaterialCardView
    private lateinit var btnToggleService: Button
    private lateinit var btnTestPanic: Button

    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvBatteryStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tokenEditText = findViewById(R.id.tokenEditText)
        btnSaveToken = findViewById(R.id.btnSaveToken)
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnAccessibilityPermission = findViewById(R.id.btnAccessibilityPermission)
        btnBatteryOptimization = findViewById(R.id.btnBatteryOptimization)
        btnUnlockRestricted = findViewById(R.id.btnUnlockRestricted)
        cardRestrictedSetting = findViewById(R.id.cardRestrictedSetting)
        btnToggleService = findViewById(R.id.btnToggleService)
        btnTestPanic = findViewById(R.id.btnTestPanic)

        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        tokenEditText.setText(prefs.getString("chatgpt_token", ""))

        btnSaveToken.setOnClickListener {
            val token = tokenEditText.text?.toString()?.trim() ?: ""
            prefs.edit().putString("chatgpt_token", token).apply()
            Toast.makeText(this, "Token saved!", Toast.LENGTH_SHORT).show()
        }

        // 1. Overlay Permission
        btnOverlayPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }
            } else {
                Toast.makeText(this, "Overlay permission already granted!", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. Accessibility Permission
        btnAccessibilityPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Find 'Quick ChatGPT Closer' and toggle it ON", Toast.LENGTH_LONG).show()
        }

        // Helper button for Android 13/14/15 Restricted Settings
        btnUnlockRestricted.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                Toast.makeText(this, "Tap the 3 dots (⋮) in the top-right corner -> 'Allow restricted settings'", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Battery Optimization
        btnBatteryOptimization.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    @SuppressLint("BatteryLife")
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Battery optimization already ignored!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Start / Stop Service
        btnToggleService.setOnClickListener {
            toggleService()
        }

        // Direct Panic Test
        btnTestPanic.setOnClickListener {
            Toast.makeText(this, "Testing panic action...", Toast.LENGTH_SHORT).show()
            ChatGPTAutomatorService.executePanic(this)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
    }

    private fun refreshPermissionStates() {
        // Overlay Check
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        if (hasOverlay) {
            tvOverlayStatus.text = "1. Overlay: ✅ Granted"
            tvOverlayStatus.setTextColor(Color.parseColor("#81C784"))
            btnOverlayPermission.visibility = View.GONE
        } else {
            tvOverlayStatus.text = "1. Overlay: ❌ Not Granted"
            tvOverlayStatus.setTextColor(Color.parseColor("#E57373"))
            btnOverlayPermission.visibility = View.VISIBLE
        }

        // Accessibility Check
        val hasAccessibility = isAccessibilityServiceEnabled(this, ChatGPTAutomatorService::class.java)
        if (hasAccessibility) {
            tvAccessibilityStatus.text = "2. Accessibility: ✅ Enabled"
            tvAccessibilityStatus.setTextColor(Color.parseColor("#81C784"))
            btnAccessibilityPermission.visibility = View.GONE
            cardRestrictedSetting.visibility = View.GONE
        } else {
            tvAccessibilityStatus.text = "2. Accessibility: ❌ Disabled"
            tvAccessibilityStatus.setTextColor(Color.parseColor("#E57373"))
            btnAccessibilityPermission.visibility = View.VISIBLE
            // Show the Android 13+ helper card if accessibility is not enabled
            cardRestrictedSetting.visibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) View.VISIBLE else View.GONE
        }

        // Battery Check
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pm.isIgnoringBatteryOptimizations(packageName) else true
        if (isIgnoringBattery) {
            tvBatteryStatus.text = "3. Battery: ✅ Unrestricted"
            tvBatteryStatus.setTextColor(Color.parseColor("#81C784"))
            btnBatteryOptimization.visibility = View.GONE
        } else {
            tvBatteryStatus.text = "3. Battery: ⚠️ Optimized"
            tvBatteryStatus.setTextColor(Color.parseColor("#FFD54F"))
            btnBatteryOptimization.visibility = View.VISIBLE
        }

        // Toggle button state
        if (CornerTriggerService.isRunning) {
            btnToggleService.text = "Stop Panic Closer Guard"
            btnToggleService.setBackgroundColor(Color.parseColor("#D32F2F"))
        } else {
            btnToggleService.text = "Start Panic Closer Guard"
            btnToggleService.setBackgroundColor(Color.parseColor("#10A37F"))
        }
    }

    private fun toggleService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant Overlay permission first!", Toast.LENGTH_SHORT).show()
            return
        }

        val serviceIntent = Intent(this, CornerTriggerService::class.java)
        if (CornerTriggerService.isRunning) {
            stopService(serviceIntent)
            Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show()
        } else {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                Toast.makeText(this, "Panic Closer started! Look for the red badge at top-right.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to start service: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        refreshPermissionStates()
    }

    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
        val expectedComponentName = ComponentName(context, serviceClass)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }
}
