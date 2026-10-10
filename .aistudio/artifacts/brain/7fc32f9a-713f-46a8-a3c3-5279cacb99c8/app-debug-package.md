# Aura Debug APK Package

The debug APK for Aura is built and ready for installation on your physical Android test device.

## Package Summary
- **Filename**: `app-debug.apk`
- **Application ID**: `com.aistudio.aura.vqpzkm`
- **Build Variant**: `debug`
- **Size**: `24,037,903 bytes (~23 MB)`
- **Build Status**: Verified with 0 errors across all unit and integration tests

## Ways to Download the APK

### 1. Direct Web Download (In-Browser)
Open the app development URL in your browser to download directly:
- **Download Link**: [Download app-debug.apk](https://ais-dev-vfh2mhsbm2a6efumvrlxdt-881326756045.us-east1.run.app/app-debug.apk)

### 2. Code Editor File Explorer
In the left sidebar of AI Studio:
1. Navigate to: `app` → `build` → `outputs` → `apk` → `debug`
2. Right-click on **`app-debug.apk`**
3. Select **Download**

### 3. Settings Menu Export
In the top-right header of AI Studio:
1. Click the **Project / Settings** menu (three dots or gear icon)
2. Select **Generate / Download APK** or **Export Project as ZIP**

---

## Installation via ADB (if connected via USB)
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Physical Device Verification Target
This APK verifies the full Continuous Chat loop on physical hardware:
1. Activate Continuous Chat (floating/mic indicator flashes).
2. Speak a query (e.g., "Report status").
3. AuraAgent processes and generates the response.
4. Android TextToSpeech synthesizes speech without hanging or timing out.
5. Response plays aloud through the speaker.
6. System cleanly transitions back to listening for the next interaction.
