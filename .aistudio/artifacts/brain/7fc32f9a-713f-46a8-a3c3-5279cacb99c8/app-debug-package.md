# Aura 3.1 Debug APK Package

The Android debug APK for Aura 3.1 has been built with real autonomous task execution, security approval gates, and model provider configuration persistence.

## Build Summary
- **Artifact**: `app-debug.apk`
- **Output File Path**: `app/build/outputs/apk/debug/app-debug.apk`
- **Package Size**: `24 MB`
- **Build Variant**: `debug`
- **Application ID**: `com.aistudio.aura.vqpzkm`
- **Key Features Included**:
  - **Real Autonomous Task Execution**:
    - Connected Autonomous Workspace UI directly to `AuraAgent` multi-step orchestration.
    - Replaced static steps with actual execution across registered tool implementations (`network_check`, `system_diagnostics`, `sandboxed_file`, `propose_notification`, etc.).
    - Strict step dependency tracking with input validation, error handling, cancellation, and step-level retry.
    - Security approval gates enforced via `ApprovalManager` for Medium- and High-Risk actions with zero bypass.
  - **Model Provider Persistence**:
    - Full model provider configuration persistence via `ModelProviderStorage` (`SharedPreferences`).
    - Google Gemini (REST/SSE) and custom network endpoints, model identifiers, and API keys persist across app restarts without reverting to offline mode.
  - **Digital Assistant & Hands-Free Integration**:
    - Preserved Android system assistant role declaration, voice interaction service, and recognition service.
    - Edge-to-edge Compose UI and Material 3 design system.

## Download Options

### 1. AI Studio File Explorer
1. In the file explorer sidebar, expand `app` → `build` → `outputs` → `apk` → `debug`.
2. Right-click `app-debug.apk`.
3. Select **Download**.

### 2. AI Studio Settings Menu
Use the project settings menu to download the generated APK or export the project.
