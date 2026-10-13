# Aura Android Application - Download & Release Verification (v1.0.3 Build 4)

### Direct Web Download
* **Landing Page**: [Download Page](https://ais-dev-rym3yimjx2igvjbuh4xvx5-291246183707.us-east1.run.app/)
* **Direct APK Download**: [Download Aura APK (v1.0.3)](https://ais-dev-rym3yimjx2igvjbuh4xvx5-291246183707.us-east1.run.app/app-debug.apk)

---

### APK Specifications & Checksums
* **Application Name**: Aura
* **Application ID**: `com.aistudio.aura.vqpzkm`
* **Version Name**: `1.0.3`
* **Version Code**: `4`
* **Target SDK**: Android 16 (SDK 36)
* **Min SDK**: Android 7.0 (API 24)
* **File Size**: 24,147,629 bytes (~24.1 MB)
* **SHA-256 Checksum**:
  ```text
  f1cb6a767d2d07577b36ffcd936244533835d28fc91b816069d5fbefb1e53b99
  ```

---

### Feature Summary in this Build
1. **Dedicated Settings Screen (`SettingsScreen.kt`)**: Added to top-level navigation (`AuraNavDestination.SETTINGS`) with 5 dedicated categories: Providers, Voice & Speech, Autonomous Mode, Permissions, and Tools.
2. **Natural Voice & Speech Synthesis**: Speech rate slider (0.5x–2.0x), pitch slider (0.5x–2.0x), selectable device voices and locales, live voice preview player, and persistent preferences via `AuraPreferencesRepository`.
3. **Continuous Hands-Free Autonomous Mode**: Auto-transition between listening, thinking, and speaking without holding push-to-talk. Real-time state indicators, mute/unmute, and pause/resume controls.
4. **Android Permissions Matrix**: Displays status (Granted/Missing) for `RECORD_AUDIO`, `POST_NOTIFICATIONS`, and `READ_CALENDAR`, explains why each is required, and provides dynamic permission request flows with a direct link to Android System Settings.
5. **Tool Registry & Launching**: Integrated 10 operational tools with risk classification badges, parameter schema inspection, test triggers, and safe app launching (`launch_app` action).
6. **Provider Configuration & Security**: Encrypted persistent storage for Gemini, OpenAI, Anthropic Claude, Ollama, and custom endpoints. No hardcoded secrets or API keys leaked.

---

### Installation Instructions
1. Download `app-debug.apk` directly from your browser.
2. If Android prompts that downloading APK files may be harmful, tap **Download anyway**.
3. Open the downloaded APK and allow **Install unknown apps** for your browser.
4. If Google Play Protect warns about debug signing, tap **More details** &rarr; **Install anyway**.
5. *Note*: If updating over an existing install signed with a different key, uninstall the previous version first to avoid Android's `INSTALL_FAILED_UPDATE_INCOMPATIBLE` error.
