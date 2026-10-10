# Aura — Autonomous Personal AI Agent for Android

Aura is a native Android AI assistant and autonomous agent application built in Kotlin and Jetpack Compose. It features voice interaction (Push-to-Talk and Continuous Chat), pluggable model provider architecture (OpenAI-compatible, Ollama, Gemini, Anthropic, and Offline Diagnostic), and an extensible tool execution framework.

---

## Architecture Overview

Aura operates through clean decoupled layers:

1. **Model Provider Layer** (`com.example.aura.core.provider`):
   - `ModelProvider`: Agnostic abstraction defining non-streaming and real-time SSE streaming methods.
   - `NetworkModelProvider`: Production implementation for OpenAI-compatible REST and SSE endpoints with full function/tool-calling protocol support.
   - `ModelProviderRegistry`: Central registry managing active providers and configurations dynamically.
   - `ConfigurableOfflineProvider`: Diagnostic provider for zero-credential offline testing and architecture verification.

2. **Agent Orchestration Engine** (`com.example.aura.core.agent`):
   - `AuraAgent`: Orchestrates conversation sessions, multi-turn tool loops, Android permission checks, human-in-the-loop approvals, and audit logging.
   - Dynamically delegates to the active model provider in `ModelProviderRegistry`.
   - Automatically executes tool requests, feeds tool outcomes back into the conversation, and obtains the final model response.

3. **Tool & Skill System** (`com.example.aura.core.tools`):
   - `ToolRegistry`: Discovers, registers, and exposes tools as provider-agnostic JSON schemas.
   - `WebAccessTool` (`web_access`): Public HTTP/HTTPS URL reader for web investigation and status checks.
   - Other built-in tools: `SystemDiagnosticsTool`, `DeviceNetworkCheckTool`, `SandboxedFileTool`, `NotificationTool`, `NotificationProposalTool`, `AndroidDeviceActionsTool`, `CalendarTool`, `MessagingTool`, and `BrowserAutomationTool`.

4. **Continuous Chat & Voice Pipeline** (`com.example.aura.core.voice`):
   - `VoiceInteractionManager`: Orchestrates `Listening` → `Thinking` → `Speaking` → `Listening` cycles.
   - `SpeechToTextEngine`: Audio transcription powered by Android SpeechRecognizer or test doubles.
   - `TextToSpeechEngine`: Spoken response synthesis with defensive timeout protection and recovery.

---

## Live Provider Configuration & Setup

Aura connects to any OpenAI-compatible provider (OpenAI, OpenRouter, Groq, Together AI, or local Ollama / LM Studio).

### 1. Secure API Key Setup

API keys are managed securely via the **Secrets Gradle Plugin** and `BuildConfig`. **Never commit API keys to version control.**

1. In the AI Studio **Secrets panel** (or locally in the project root directory), configure the following values:

   ```properties
   OPENAI_API_KEY=sk-your-actual-api-key-here
   OPENAI_BASE_URL=https://api.openai.com/v1
   OPENAI_MODEL=gpt-4o-mini
   ```

2. When `OPENAI_API_KEY` is provided and is not `UNCONFIGURED`, Aura automatically activates `openai_provider` as the default model provider on startup.

3. For custom OpenAI-compatible providers:
   - **OpenRouter**: Set `OPENAI_BASE_URL=https://openrouter.ai/api/v1` and `OPENAI_MODEL=meta-llama/llama-3.1-8b-instruct`.
   - **Groq**: Set `OPENAI_BASE_URL=https://api.groq.com/openai/v1` and `OPENAI_MODEL=llama-3.3-70b-versatile`.
   - **Local Ollama** (Android Emulator): Set `OPENAI_BASE_URL=http://10.0.2.2:11434/v1` and `OPENAI_MODEL=llama3.2:latest` (no API key required for local endpoints).

---

## End-to-End Agent / Tool Execution Flow

The complete voice-to-model-to-tool-to-speech loop operates as follows:

```
[User Spoken Input]
        │
        ▼
[SpeechToTextEngine] ──> emits final transcription
        │
        ▼
[VoiceInteractionManager] ──> transitions to VoiceEngineState.Thinking
        │
        ▼
[AuraAgent]
        │
        ├─ Turn 1: Sends CompletionRequest (messages, tools) to Live Model
        │
        ▼
[NetworkModelProvider] (OpenAI / SSE Stream)
        │
        ├─ Model requests tool: e.g. web_access(url: "https://example.com/status")
        │
        ▼
[ToolRegistry & AuraAgent]
        │
        ├─ Resolves tool: WebAccessTool
        ├─ Validates arguments and security policy
        ├─ Executes tool in background
        ├─ Records observation result: e.g. "HTTP 200: System status OK"
        │
        ▼
[AuraAgent]
        │
        ├─ Turn 2: Sends updated messages (User + Assistant Tool Calls + Tool Result) to Live Model
        │
        ▼
[NetworkModelProvider]
        │
        ├─ Model synthesizes tool result and streams final answer: "The website is operational."
        │
        ▼
[VoiceInteractionManager.speakResponse]
        │
        ▼
[TextToSpeechEngine] ──> Speaks final response aloud
        │
        ▼
[VoiceInteractionManager] ──> Returns to VoiceEngineState.Listening (Ready for next input)
```

---

## Error Handling

Aura includes comprehensive defensive guards across the pipeline:

- **Missing Provider Credentials**: Surfaces an authentication error ("Model authentication error: API key is not configured for OpenAI") and speaks the guidance aloud before returning to listening.
- **Network Failure / Timeouts**: Gracefully catches HTTP errors (401, 429, 500) and connection failures, transitioning to a clean error state without crashing.
- **Malformed Tool Calls**: If a model returns invalid JSON syntax in tool arguments, the error is captured and returned to the model conversation as a tool error so the model can recover or explain the issue.
- **Unrecognized Tools / Missing Permissions**: If a model requests an unavailable tool or lacks system permissions, an explanatory error is saved and returned to the model loop.
- **TTS Initialization & Playback Timeouts**: Defensive timeouts guarantee the interaction never hangs indefinitely if the system speech synthesis engine fails.

---

---

## Model Provider Configuration & Persistence (Aura 3.1)

Aura supports dynamic runtime configuration and persistent storage for all AI model providers:

- **Pluggable Providers**:
  - **Google Gemini (REST/SSE)**: `gemini-1.5-flash`, `gemini-1.5-pro`
  - **OpenAI**: `gpt-4o-mini`, `gpt-4o`
  - **Anthropic Claude**: `claude-3-5-sonnet-20241022`
  - **Ollama (Local / LAN)**: `llama3.2:latest` (e.g. `http://10.0.2.2:11434/v1`)
  - **Custom OpenAI-Compatible Endpoint**: Any compatible local or remote inference endpoint
  - **Aura Architecture Diagnostic (Offline)**: Zero-credential offline test provider

### Persistence Behavior & App Restarts

- **Persistent Storage**: All provider configurations (endpoints, model identifiers, API keys) are persisted using Android private persistent storage (`ModelProviderStorage` backed by `SharedPreferences`).
- **Separate Active Provider Tracking**: The selected active provider is persisted independently from provider configurations.
- **Startup Loading**: Saved configurations and the active provider selection are loaded during application startup before initializing `ModelProviderRegistry`.
- **No Unconditional Fallback**: The offline diagnostic provider is only selected on fresh install when no credentials/configs exist, or when explicitly chosen by the user. Saved selections (e.g., Google Gemini) survive app swipe-close and simulated restarts.
- **Save Feedback**: The "Save Provider Config" button validates parameters, commits to persistent storage, and displays immediate success or error feedback.
- **Security**: API keys are stored in private app storage and masked by `SecretSanitizer`, never logged or exposed.

---

## Testing & Verification

Run the complete test suite:

```bash
gradle :app:testDebugUnitTest
```

Key test suites:
- `AuraModelProviderPersistenceTest`: Validates that model provider configurations (including Google Gemini) and the active provider survive simulated app restarts, input validation, and secure credential handling.
- `AuraLiveModelToolLoopTest`: Validates the end-to-end Voice → Live Model → Tool → Result → Speech loop and error recovery.
- `NetworkModelProviderTest`: Validates OpenAI SSE streaming, tool-call chunk accumulation, auth state detection, and HTTP error handling.
- `AuraContinuousChatTest`: Validates Continuous Chat state machine, barge-in, and lifecycle handling.
- `AndroidTextToSpeechEngineTest`: Validates speech synthesis queueing, timeouts, and lifecycle cleanup.
