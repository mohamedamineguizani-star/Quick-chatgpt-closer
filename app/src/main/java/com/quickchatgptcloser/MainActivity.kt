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
import android.widget.RadioButton
import android.widget.RadioGroup
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

    // Target Selection
    private lateinit var rgTargetApp: RadioGroup
    private lateinit var rbTargetAuto: RadioButton
    private lateinit var rbTargetClaude: RadioButton
    private lateinit var rbTargetChatGPT: RadioButton
    private lateinit var rbTargetLastActive: RadioButton

    // Switches
    private lateinit var switchRemoveFromRecents: SwitchMaterial
    private lateinit var switchDeepForceStop: SwitchMaterial
    private lateinit var switchWipeHistory: SwitchMaterial

    // Claude Views
    private lateinit var btnOpenClaudePage: Button
    private lateinit var claudeTokenEditText: TextInputEditText
    private lateinit var btnSaveClaudeToken: Button
    private lateinit var btnTestClaudeConnection: Button
    private lateinit var tvClaudeConnectionResult: TextView
    private lateinit var btnTestDeleteClaudeChat: Button

    // ChatGPT Views
    private lateinit var btnOpenAuthPage: Button
    private lateinit var tokenEditText: TextInputEditText
    private lateinit var btnSaveToken: Button
    private lateinit var btnTestConnection: Button
    private lateinit var tvConnectionResult: TextView
    private lateinit var btnTestDeleteChat: Button

    // Permissions & System
    private lateinit var btnOverlayPermission: Button
    private lateinit var btnAccessibilityPermission: Button
    private lateinit var btnBatteryOptimization: Button
    private lateinit var btnUnlockRestricted: Button
    private lateinit var cardRestrictedSetting: MaterialCardView

    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvBatteryStatus: TextView

    // Action Buttons
    private lateinit var btnToggleService: Button
    private lateinit var btnTestPanic: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        loadPreferences()
        setupListeners()

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun initViews() {
        rgTargetApp = findViewById(R.id.rgTargetApp)
        rbTargetAuto = findViewById(R.id.rbTargetAuto)
        rbTargetClaude = findViewById(R.id.rbTargetClaude)
        rbTargetChatGPT = findViewById(R.id.rbTargetChatGPT)
        rbTargetLastActive = findViewById(R.id.rbTargetLastActive)

        switchRemoveFromRecents = findViewById(R.id.switchRemoveFromRecents)
        switchDeepForceStop = findViewById(R.id.switchDeepForceStop)
        switchWipeHistory = findViewById(R.id.switchWipeHistory)

        btnOpenClaudePage = findViewById(R.id.btnOpenClaudePage)
        claudeTokenEditText = findViewById(R.id.claudeTokenEditText)
        btnSaveClaudeToken = findViewById(R.id.btnSaveClaudeToken)
        btnTestClaudeConnection = findViewById(R.id.btnTestClaudeConnection)
        tvClaudeConnectionResult = findViewById(R.id.tvClaudeConnectionResult)
        btnTestDeleteClaudeChat = findViewById(R.id.btnTestDeleteClaudeChat)

        btnOpenAuthPage = findViewById(R.id.btnOpenAuthPage)
        tokenEditText = findViewById(R.id.tokenEditText)
        btnSaveToken = findViewById(R.id.btnSaveToken)
        btnTestConnection = findViewById(R.id.btnTestConnection)
        tvConnectionResult = findViewById(R.id.tvConnectionResult)
        btnTestDeleteChat = findViewById(R.id.btnTestDeleteChat)

        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnAccessibilityPermission = findViewById(R.id.btnAccessibilityPermission)
        btnBatteryOptimization = findViewById(R.id.btnBatteryOptimization)
        btnUnlockRestricted = findViewById(R.id.btnUnlockRestricted)
        cardRestrictedSetting = findViewById(R.id.cardRestrictedSetting)

        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus)

        btnToggleService = findViewById(R.id.btnToggleService)
        btnTestPanic = findViewById(R.id.btnTestPanic)
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        // Target Mode
        when (prefs.getString("target_app_mode", "auto")) {
            "claude" -> rbTargetClaude.isChecked = true
            "chatgpt" -> rbTargetChatGPT.isChecked = true
            "last_active" -> rbTargetLastActive.isChecked = true
            else -> rbTargetAuto.isChecked = true
        }

        // Action Switches
        switchRemoveFromRecents.isChecked = prefs.getBoolean("remove_from_recents", true)
        switchDeepForceStop.isChecked = prefs.getBoolean("deep_force_stop", true)
        switchWipeHistory.isChecked = prefs.getBoolean("wipe_recent_history", true)

        // Tokens
        tokenEditText.setText(prefs.getString("chatgpt_token", ""))
        claudeTokenEditText.setText(prefs.getString("claude_token", ""))
    }

    private fun setupListeners() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        // Target App Mode
        rgTargetApp.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbTargetClaude -> "claude"
                R.id.rbTargetChatGPT -> "chatgpt"
                R.id.rbTargetLastActive -> "last_active"
                else -> "auto"
            }
            prefs.edit().putString("target_app_mode", mode).apply()
        }

        // Switches
        switchRemoveFromRecents.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("remove_from_recents", isChecked).apply()
        }
        switchDeepForceStop.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("deep_force_stop", isChecked).apply()
        }
        switchWipeHistory.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("wipe_recent_history", isChecked).apply()
        }

        // Claude: Open claude.ai in browser
        btnOpenClaudePage.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai"))
                startActivity(intent)
                Toast.makeText(this, "Log in, open DevTools/Cookies, and copy 'sessionKey'", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Claude: Save Token
        btnSaveClaudeToken.setOnClickListener {
            val token = claudeTokenEditText.text?.toString()?.trim() ?: ""
            prefs.edit().putString("claude_token", token).apply()
            Toast.makeText(this, "Claude sessionKey saved!", Toast.LENGTH_SHORT).show()
        }

        // Claude: Test Connection
        btnTestClaudeConnection.setOnClickListener {
            val token = claudeTokenEditText.text?.toString()?.trim() ?: ""
            if (token.isBlank()) {
                tvClaudeConnectionResult.text = "❌ Error: Please paste your Claude sessionKey first"
                tvClaudeConnectionResult.setTextColor(Color.parseColor("#E57373"))
                return@setOnClickListener
            }

            tvClaudeConnectionResult.text = "Testing Claude connection..."
            tvClaudeConnectionResult.setTextColor(Color.parseColor("#FFD54F"))

            mainScope.launch {
                val result = ClaudeManager.testConnection(token)
                result.onSuccess { title ->
                    tvClaudeConnectionResult.text = "✅ Connected to Claude!\nLatest Chat: \"$title\""
                    tvClaudeConnectionResult.setTextColor(Color.parseColor("#81C784"))
                    prefs.edit().putString("claude_token", token).apply()
                }.onFailure { err ->
                    tvClaudeConnectionResult.text = "❌ Connection Failed:\n${err.message}"
                    tvClaudeConnectionResult.setTextColor(Color.parseColor("#E57373"))
                }
            }
        }

        // Claude: Delete Chat Test
        btnTestDeleteClaudeChat.setOnClickListener {
            val token = prefs.getString("claude_token", "") ?: claudeTokenEditText.text?.toString()?.trim() ?: ""
            if (token.isBlank()) {
                Toast.makeText(this, "Save your Claude sessionKey first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val wipeHistory = prefs.getBoolean("wipe_recent_history", true)
            val countToDelete = if (wipeHistory) 5 else 1

            tvClaudeConnectionResult.text = "Deleting $countToDelete Claude chat(s) via API..."
            tvClaudeConnectionResult.setTextColor(Color.parseColor("#FFD54F"))

            mainScope.launch {
                val result = ClaudeManager.deleteChats(token, countToDelete)
                result.onSuccess { deletedCount ->
                    tvClaudeConnectionResult.text = "✅ Successfully deleted $deletedCount Claude chat(s) from account history!"
                    tvClaudeConnectionResult.setTextColor(Color.parseColor("#81C784"))
                    Toast.makeText(this@MainActivity, "Wiped $deletedCount Claude chats", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    tvClaudeConnectionResult.text = "❌ Delete Failed: ${err.message}"
                    tvClaudeConnectionResult.setTextColor(Color.parseColor("#E57373"))
                }
            }
        }

        // ChatGPT: Open Auth Page
        btnOpenAuthPage.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/api/auth/session"))
                startActivity(intent)
                Toast.makeText(this, "Copy the 'accessToken' value from the browser page", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // ChatGPT: Save Token
        btnSaveToken.setOnClickListener {
            val token = tokenEditText.text?.toString()?.trim() ?: ""
            prefs.edit().putString("chatgpt_token", token).apply()
            Toast.makeText(this, "ChatGPT Token saved!", Toast.LENGTH_SHORT).show()
        }

        // ChatGPT: Test Connection
        btnTestConnection.setOnClickListener {
            val token = tokenEditText.text?.toString()?.trim() ?: ""
            if (token.isBlank()) {
                tvConnectionResult.text = "❌ Error: Please paste your accessToken first"
                tvConnectionResult.setTextColor(Color.parseColor("#E57373"))
                return@setOnClickListener
            }

            tvConnectionResult.text = "Testing ChatGPT connection..."
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

        // ChatGPT: Delete Chat Test
        btnTestDeleteChat.setOnClickListener {
            val token = prefs.getString("chatgpt_token", "") ?: tokenEditText.text?.toString()?.trim() ?: ""
            if (token.isBlank()) {
                Toast.makeText(this, "Save your ChatGPT token first!", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Enable 'Quick AI Closer'", Toast.LENGTH_LONG).show()
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

        // Test Panic Action (Wipe + Force Stop + Remove from Active App List + Exit Home)
        btnTestPanic.setOnClickListener {
            val chatgptToken = prefs.getString("chatgpt_token", "") ?: tokenEditText.text?.toString()?.trim() ?: ""
            val claudeToken = prefs.getString("claude_token", "") ?: claudeTokenEditText.text?.toString()?.trim() ?: ""
            val wipeHistory = prefs.getBoolean("wipe_recent_history", true)
            val countToDelete = if (wipeHistory) 5 else 1

            if (chatgptToken.isNotBlank()) {
                mainScope.launch {
                    ChatGptManager.deleteChats(chatgptToken, countToDelete)
                }
            }

            if (claudeToken.isNotBlank()) {
                mainScope.launch {
                    ClaudeManager.deleteChats(claudeToken, countToDelete)
                }
            }

            ChatGPTAutomatorService.executeWipeAndRemoveFromRecents(this)
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
