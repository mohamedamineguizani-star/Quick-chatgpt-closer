# Quick ChatGPT Closer 🚀

An Android utility that runs in the background, listens for a quick **triple-tap in the top-right corner of your screen**, instantly **deletes your latest conversation in ChatGPT**, and **force-closes the ChatGPT app** via automated Android settings.

---

## ⚡ How It Works

1. **Persistent Background Guard**: Runs as a lightweight Android `ForegroundService` with battery optimization exclusions, ensuring it is never killed by Android OS.
2. **Discreet Corner Trigger**: Displays a tiny 90×90px touch surface in the top-right corner of the screen. Tapping it 3 times within 600ms activates the panic sequence.
3. **Silent Background Chat Deletion**: Sends an encrypted, direct HTTP PATCH request to ChatGPT's backend (`https://chatgpt.com/backend-api/conversation/<id>`) to immediately delete or hide the last active conversation.
4. **Automated Force Stop**: Uses an `AccessibilityService` to open ChatGPT's App Details page in Android Settings, automatically clicks **"Force stop"**, confirms the system dialog, and returns immediately to the Home screen—all within ~150–200 milliseconds.

---

## 📱 Permissions Required

Because Android security strictly prevents regular apps from secretly killing other apps or reading full-screen touch events, this app requires standard Android permission grants:

1. **Display over other apps (`SYSTEM_ALERT_WINDOW`)**: Required to place the discreet corner touch zone on the screen.
2. **Accessibility Service (`BIND_ACCESSIBILITY_SERVICE`)**: Required to automate clicking the "Force stop" and confirmation buttons in Settings.
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
│       │   └── ChatGptManager.kt
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

1. Open this project in **Android Studio**.
2. Connect your Android device (or launch an emulator) and click **Run (Shift + F10)**.
3. In the app:
   - Tap **"1. Allow Display Over Other Apps"** and toggle the permission on.
   - Tap **"2. Enable Accessibility Service"**, find **Quick ChatGPT Closer** under Installed Services / Accessibility, and turn it on.
   - Tap **"3. Ignore Battery Optimization"** so the service remains active indefinitely.
   - *(Optional)* Paste your ChatGPT Bearer token to enable backend conversation deletion.
   - Tap **"Start Panic Closer Guard"**.

---

## 🔒 Obtaining Your ChatGPT Session Token (Optional)

1. Open [chatgpt.com](https://chatgpt.com) in your web browser and log in.
2. Press `F12` to open Developer Tools and select the **Network** tab.
3. Send any prompt or refresh the page, click on any request to `backend-api`, and view **Request Headers**.
4. Copy the value of the `Authorization` header (`Bearer eyJhbGciOi...`).
5. Paste it into the token field in the app and tap **Save Token**.
