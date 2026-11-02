package com.quickchatgptcloser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var tokenEditText: TextInputEditText
    private lateinit var btnSaveToken: Button
    private lateinit var btnOverlayPermission: Button
    private lateinit var btnAccessibilityPermission: Button
    private lateinit var btnBatteryOptimization: Button
    private lateinit var btnToggleService: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tokenEditText = findViewById(R.id.tokenEditText)
        btnSaveToken = findViewById(R.id.btnSaveToken)
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnAccessibilityPermission = findViewById(R.id.btnAccessibilityPermission)
        btnBatteryOptimization = findViewById(R.id.btnBatteryOptimization)
        btnToggleService = findViewById(R.id.btnToggleService)

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        tokenEditText.setText(prefs.getString("chatgpt_token", ""))

        btnSaveToken.setOnClickListener {
            val token = tokenEditText.text?.toString()?.trim() ?: ""
            prefs.edit().putString("chatgpt_token", token).apply()
            Toast.makeText(this, "Token saved successfully!", Toast.LENGTH_SHORT).show()
        }

        btnOverlayPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission already granted!", Toast.LENGTH_SHORT).show()
            }
        }

        btnAccessibilityPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Enable 'Quick ChatGPT Closer' under Installed Services", Toast.LENGTH_LONG).show()
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
                } else {
                    Toast.makeText(this, "Battery optimization already ignored!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnToggleService.setOnClickListener {
            toggleService()
        }

        updateServiceButtonState()
    }

    override fun onResume() {
        super.onResume()
        updateServiceButtonState()
    }

    private fun updateServiceButtonState() {
        if (CornerTriggerService.isRunning) {
            btnToggleService.text = "Stop Panic Closer Guard"
            btnToggleService.setBackgroundColor(getColor(R.color.accent))
        } else {
            btnToggleService.text = "Start Panic Closer Guard"
            btnToggleService.setBackgroundColor(getColor(R.color.primary))
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "Panic Closer Guard started in background!", Toast.LENGTH_SHORT).show()
        }
        updateServiceButtonState()
    }
}
