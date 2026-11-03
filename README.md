# Quick AI Closer (ChatGPT & Claude) 🚀

An Android utility that runs in the background, listens for a quick **triple-tap in the top-right corner of your screen**, instantly **removes the active AI app (Claude or ChatGPT) from Android's active apps / Recents list**, executes a **deep system Force-Stop**, and **deletes conversations directly via API**.

---

## ⚡ How It Works

1. **Persistent Background Guard**: Runs as a lightweight Android `ForegroundService` with battery optimization exclusions, ensuring it is never killed by Android OS.
2. **Discreet Corner Trigger**: Displays a discreet touch surface in the top-right corner of the screen. Tapping it 3 times activates the panic sequence.
3. **Auto-Detects Active AI App**: Seamlessly targets **Claude** (`com.anthropic.claude`), **ChatGPT** (`com.openai.chatgpt`), or any last active foreground app.
4. **Removes From Active App List (Recents)**: Automatically opens Android's task switcher (Recents) and swipes away the active app card (or clicks "Clear all"), ensuring no task snapshot remains in the multitasker.
5. **Automated Deep System Force-Stop**: Uses an `AccessibilityService` to open the app's details page in Android Settings, clicks **"Force stop"**, confirms the system dialog, and returns cleanly to the Home screen—all within ~300–400 milliseconds.
6. **Silent Cloud Conversation Deletion (Optional)**:
   - **ChatGPT**: Direct HTTP PATCH request to delete/hide recent conversations.
   - **Claude**: Direct HTTP DELETE request to wipe recent conversations from Claude.ai.

---

## 📱 Permissions Required

Because Android security prevents regular apps from terminating other apps or reading full-screen touch events, this app requires standard Android permission grants:

1. **Display over other apps (`SYSTEM_ALERT_WINDOW`)**: Places the discreet corner touch zone on the screen.
2. **Accessibility Service (`BIND_ACCESSIBILITY_SERVICE`)**: Automates dismissing the card from Recents and clicking "Force stop" in Settings.
3. **Battery Optimization Exemption**: Ensures Android doesn't put the guard service to sleep when idle.

---

## 🛠️ Project Structure

```
Quick-chatgpt-closer/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/quickchatgptcloser/
│       │   ├── MainActivity.kt
│       │   ├── CornerTriggerService.kt
│       │   ├── ChatGPTAutomatorService.kt
│       │   ├── ChatGptManager.kt
│       │   └── ClaudeManager.kt
│       └── res/
│           ├── layout/activity_main.xml
│           ├── xml/accessibility_service_config.xml
│           └── values/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 🚀 Setup & Installation

1. Open this project in **Android Studio** or download the latest release APK.
2. In the app:
   - Tap **"Grant"** for Display Over Other Apps.
   - Tap **"Enable"** for Accessibility Service (**Quick AI Closer**).
   - Tap **"Ignore"** for Battery Optimization.
   - *(Optional)* Configure Claude `sessionKey` and/or ChatGPT `accessToken` for cloud history wipe.
   - Tap **"Start Panic Closer Guard"**.

---

## 🔒 Obtaining Account Session Tokens (Optional)

### Claude (claude.ai):
1. Open [claude.ai](https://claude.ai) in your browser and log in.
2. Press `F12` -> **Application / Storage** -> **Cookies**.
3. Copy the value of the `sessionKey` cookie (starts with `sk-ant-sid01-...`).
4. Paste it into the Claude field in the app and tap **Save Claude**.

### ChatGPT (chatgpt.com):
1. Open [chatgpt.com/api/auth/session](https://chatgpt.com/api/auth/session) in your browser.
2. Copy the `accessToken` value.
3. Paste it into the ChatGPT field in the app and tap **Save ChatGPT**.
