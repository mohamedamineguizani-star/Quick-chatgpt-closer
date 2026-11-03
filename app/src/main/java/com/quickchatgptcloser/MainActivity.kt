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
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var tokenEditText: TextInputEditText
    private lateinit var btnOpenAuthPage: Button
    private lateinit var btnSaveToken: Button
    private lateinit var btnTestConnection: Button
    private lateinit var tvConnectionResult: TextView
    private lateinit var btnTestDeleteChat: Button

    private lateinit var btnOverlayPermission: Button
    private lateinit var btnAccessibilityPermission: Button
    private lateinit var btnBatteryOptimization: Button
    private lateinit var btnUnlockRestricted: Button
    private lateinit var cardRestrictedSetting: MaterialCardView

    private lateinit var switchWipeHistory: SwitchMaterial
    private lateinit var switchDeepForceStop: SwitchMaterial

    private lateinit var btnToggleService: Button
    private lateinit var btnTestPanic: Button

    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvBatteryStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tokenEditText = findViewById(R.id.tokenEditText)
        btnOpenAuthPage = findViewById(R.id.btnOpenAuthPage)
        btnSaveToken = findViewById(R.id.btnSaveToken)
        btnTestConnection = findViewById(R.id.btnTestConnection)
        tvConnectionResult = findViewById(R.id.tvConnectionResult)
        btnTestDeleteChat = findViewById(R.id.btnTestDeleteChat)

        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnAccessibilityPermission = findViewById(R.id.btnAccessibilityPermission)
        btnBatteryOptimization = findViewById(R.id.btnBatteryOptimization)
        btnUnlockRestricted = findViewById(R.id.btnUnlockRestricted)
        cardRestrictedSetting = findViewById(R.id.cardRestrictedSetting)

        switchWipeHistory = findViewById(R.id.switchWipeHistory)
        switchDeepForceStop = findViewById(R.id.switchDeepForceStop)

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
        val savedToken = prefs.getString("chatgpt_token", "") ?: ""
        tokenEditText.setText(savedToken)

        switchWipeHistory.isChecked = prefs.getBoolean("wipe_recent_history", true)
        switchDeepForceStop.isChecked = prefs.getBoolean("deep_force_stop", true)

        switchWipeHistory.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("wipe_recent_history", isChecked).apply()
        }
        switchDeepForceStop.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("deep_force_stop", isChecked).apply()
        }

        // 1-Click: Open ChatGPT token page in browser
        btnOpenAuthPage.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/api/auth/session"))
                startActivity(intent)
                Toast.makeText(this, "Copy the 'accessToken' value from the browser page", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Save Token
        btnSaveToken.setOnClickListener {
            val token = tokenEditText.text?.toString()?.trim() ?: ""
            prefs.edit().putString("chatgpt_token", token).apply()
            Toast.makeText(this, "Token saved!", Toast.LENGTH_SHORT).show()
        }

        // Test API Connection
        btnTestConnection.setOnClickListener {
            val token = tokenEditText.text?.toString()?.trim() ?: ""
            if (token.isBlank()) {
                tvConnectionResult.text = "❌ Error: Please paste your accessToken first"
                tvConnectionResult.setTextColor(Color.parseColor("#E57373"))
                return@setOnClickListener
            }

            tvConnectionResult.text = "Testing connection..."
            tvConnectionResult.setTextColor(Color.parseColor("#FFD54F"))

            mainScope.launch {
                val result = ChatGptManager.testConnection(token)
                result.onSuccess { title ->
                    tvConnectionResult.text = "✅ Connected to ChatGPT!\nLatest Chat: \"$title\""
                    tvConnectionResult.setTextColor(Color.parseColor("#81C784"))
                    prefs.edit().putString("chatgpt_token", token).apply()
                }.onFailure { err ->
                    tvConnectionResult.text = "❌ Connection Failed:\n${err.message}"
                    tvConnectionResult.setTextColor(Color.parseColor("#E57373"))
                }
            }
        }

        // Test Delete Chat via API
        btnTestDeleteChat.setOnClickListener {
            val token = prefs.getString("chatgpt_token", "") ?: tokenEditText.text?.toString()?.trim() ?: ""
            if (token.isBlank()) {
                Toast.makeText(this, "Save your token first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val wipeHistory = prefs.getBoolean("wipe_recent_history", true)
            val countToDelete = if (wipeHistory) 5 else 1

            tvConnectionResult.text = "Deleting $countToDelete chat(s) from history via API..."
            tvConnectionResult.setTextColor(Color.parseColor("#FFD54F"))

            mainScope.launch {
                val result = ChatGptManager.deleteChats(token, countToDelete)
                result.onSuccess { deletedCount ->
                    tvConnectionResult.text = "✅ Successfully deleted $deletedCount chat(s) from account history!"
                    tvConnectionResult.setTextColor(Color.parseColor("#81C784"))
                    Toast.makeText(this@MainActivity, "Wiped $deletedCount chats", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    tvConnectionResult.text = "❌ Delete Failed: ${err.message}"
                    tvConnectionResult.setTextColor(Color.parseColor("#E57373"))
                }
            }
        }

        // Permissions
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

        btnAccessibilityPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Enable 'Quick ChatGPT Closer'", Toast.LENGTH_LONG).show()
        }

        btnUnlockRestricted.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                Toast.makeText(this, "Tap the 3 dots (⋮) top-right -> 'Allow restricted settings'", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        btnBatteryOptimization.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    @SuppressLint("BatteryLife")
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
            }
        }

        btnToggleService.setOnClickListener {
            toggleService()
        }

        // Test Panic Action (Wipe + Force Close + Main Menu)
        btnTestPanic.setOnClickListener {
            val token = prefs.getString("chatgpt_token", "") ?: tokenEditText.text?.toString()?.trim() ?: ""
            val wipeHistory = prefs.getBoolean("wipe_recent_history", true)
            val deepForceStop = prefs.getBoolean("deep_force_stop", true)

            if (token.isNotBlank()) {
                mainScope.launch {
                    ChatGptManager.deleteChats(token, if (wipeHistory) 5 else 1)
                }
            }
            ChatGPTAutomatorService.executeDeepForceStop(this, deepForceStop)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
    }

    private fun refreshPermissionStates() {
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        if (hasOverlay) {
            tvOverlayStatus.text = "Overlay: ✅ Granted"
            tvOverlayStatus.setTextColor(Color.parseColor("#81C784"))
            btnOverlayPermission.visibility = View.GONE
        } else {
            tvOverlayStatus.text = "Overlay: ❌ Not Granted"
            tvOverlayStatus.setTextColor(Color.parseColor("#E57373"))
            btnOverlayPermission.visibility = View.VISIBLE
        }

        val hasAccessibility = isAccessibilityServiceEnabled(this, ChatGPTAutomatorService::class.java)
        if (hasAccessibility) {
            tvAccessibilityStatus.text = "Accessibility: ✅ Enabled"
            tvAccessibilityStatus.setTextColor(Color.parseColor("#81C784"))
            btnAccessibilityPermission.visibility = View.GONE
            cardRestrictedSetting.visibility = View.GONE
        } else {
            tvAccessibilityStatus.text = "Accessibility: ❌ Disabled"
            tvAccessibilityStatus.setTextColor(Color.parseColor("#E57373"))
            btnAccessibilityPermission.visibility = View.VISIBLE
            cardRestrictedSetting.visibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) View.VISIBLE else View.GONE
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pm.isIgnoringBatteryOptimizations(packageName) else true
        if (isIgnoringBattery) {
            tvBatteryStatus.text = "Battery: ✅ Unrestricted"
            tvBatteryStatus.setTextColor(Color.parseColor("#81C784"))
            btnBatteryOptimization.visibility = View.GONE
        } else {
            tvBatteryStatus.text = "Battery: ⚠️ Optimized"
            tvBatteryStatus.setTextColor(Color.parseColor("#FFD54F"))
            btnBatteryOptimization.visibility = View.VISIBLE
        }

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
                Toast.makeText(this, "Panic Closer started! Look for red badge at top-right.", Toast.LENGTH_LONG).show()
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
