# Aura 3.1 Debug APK Package

The Android debug APK for Aura 3.1 has been built with the model provider configuration persistence fix.

## Build Summary
- **Artifact**: `app-debug.apk`
- **Output File Path**: `app/build/outputs/apk/debug/app-debug.apk`
- **Package Size**: `~23 MB`
- **Build Variant**: `debug`
- **Application ID**: `com.aistudio.aura.vqpzkm`
- **Key Features Included**:
  - Full model provider configuration persistence via `ModelProviderStorage` (`SharedPreferences`).
  - Google Gemini (REST/SSE) endpoint, model identifier, and API key persist across app swipe-close and restarts.
  - Active provider selection is persisted independently and restored upon launch without reverting to offline diagnostic mode.
  - UI feedback for the "Save Provider Config" button.
  - Secure credential handling (no API key leakage).

## Download Options

### 1. AI Studio File Explorer
1. In the file explorer sidebar, expand `app` → `build` → `outputs` → `apk` → `debug`.
2. Right-click `app-debug.apk`.
3. Select **Download**.

### 2. AI Studio Settings Menu
Use the top-right project settings menu to download the generated APK or export the project.

### 3. Direct Browser Download
- [Download app-debug.apk](https://ais-dev-vfh2mhsbm2a6efumvrlxdt-881326756045.us-east1.run.app/app-debug.apk)
