package com.example.aura.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aura.core.agent.AgentState
import com.example.aura.core.agent.OrchestrationPhase
import com.example.aura.core.provider.StreamingState
import com.example.aura.core.session.SessionMode
import com.example.aura.core.session.SessionState
import com.example.aura.core.voice.VoiceEngineState
import com.example.aura.core.voice.VoiceMode
import com.example.aura.data.entities.MessageEntity
import com.example.aura.ui.AuraMainViewModel
import com.example.aura.ui.components.AgentStatusBadge
import com.example.aura.ui.components.PendingApprovalCard
import com.example.aura.ui.theme.AuraBgCard
import com.example.aura.ui.theme.AuraBgCardElevated
import com.example.aura.ui.theme.AuraBgDeep
import com.example.aura.ui.theme.AuraBorder
import com.example.aura.ui.theme.AuraCriticalRed
import com.example.aura.ui.theme.AuraCyanPrimary
import com.example.aura.ui.theme.AuraSafeGreen
import com.example.aura.ui.theme.AuraTextMuted
import com.example.aura.ui.theme.AuraTextPrimary
import com.example.aura.ui.theme.AuraTextSecondary
import com.example.aura.ui.theme.AuraVioletSecondary
import com.example.aura.ui.theme.AuraWarningAmber

@Composable
fun ConsoleScreen(
    viewModel: AuraMainViewModel,
    modifier: Modifier = Modifier
) {
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val sessionState by activeSession.state.collectAsStateWithLifecycle()
    val sessionMode by activeSession.mode.collectAsStateWithLifecycle()
    val streamingText by activeSession.currentStreamingText.collectAsStateWithLifecycle()
    val messages by viewModel.sessionMessages.collectAsStateWithLifecycle()
    val agentState by viewModel.agentState.collectAsStateWithLifecycle()
    val pendingApprovals by viewModel.pendingApprovals.collectAsStateWithLifecycle()
    val streamingState by viewModel.streamingState.collectAsStateWithLifecycle()
    val orchestrationState by viewModel.orchestrationState.collectAsStateWithLifecycle()
    val voiceEngineState by viewModel.voiceEngineState.collectAsStateWithLifecycle()
    val voiceMode by viewModel.voiceMode.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Microphone runtime permission request
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (voiceMode == VoiceMode.CONTINUOUS_CONVERSATION) {
                viewModel.startContinuousConversation()
            } else {
                viewModel.startPushToTalk()
            }
        }
    }

    LaunchedEffect(messages.size, streamingText.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuraBgDeep)
            .padding(16.dp)
    ) {
        // --- Session Header Bar ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AURA COMMAND CONSOLE",
                    color = AuraCyanPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Session #${activeSession.id.take(8)}",
                    color = AuraTextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                AgentStatusBadge(state = agentState)

                Spacer(modifier = Modifier.width(8.dp))

                if (agentState == AgentState.THINKING ||
                    agentState == AgentState.EXECUTING_TOOL ||
                    agentState == AgentState.AWAITING_APPROVAL ||
                    sessionState == SessionState.STREAMING
                ) {
                    IconButton(
                        onClick = { viewModel.interruptAgent() },
                        modifier = Modifier
                            .size(36.dp)
                            .background(AuraCriticalRed.copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, AuraCriticalRed, CircleShape)
                            .testTag("interrupt_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Interrupt Aura",
                            tint = AuraCriticalRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Orchestration Cycle Status Bar ---
        if (orchestrationState.currentPhase != OrchestrationPhase.IDLE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AuraBgCardElevated, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CYCLE: ${orchestrationState.currentPhase.name}",
                    color = AuraCyanPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                orchestratorDetailText(orchestrationState.currentPhase)?.let { detail ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• $detail",
                        color = AuraTextMuted,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // --- Session Mode Switcher ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SessionMode.entries.forEach { mode ->
                val isSelected = sessionMode == mode
                AssistChip(
                    onClick = { viewModel.setSessionMode(mode) },
                    label = {
                        Text(
                            text = when (mode) {
                                SessionMode.INTERACTIVE_TEXT -> "Text Mode"
                                SessionMode.VOICE_STREAM -> "Voice Mode"
                                SessionMode.AUTONOMOUS_GOAL -> "Autonomous Mode"
                            },
                            fontSize = 11.sp,
                            color = if (isSelected) AuraCyanPrimary else AuraTextSecondary
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isSelected) AuraBgCardElevated else Color.Transparent
                    ),
                    border = AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = if (isSelected) AuraCyanPrimary else AuraBorder
                    ),
                    modifier = Modifier.testTag("mode_chip_${mode.name}")
                )
            }
        }

        // --- Voice Architecture Panel (when in VOICE_STREAM mode) ---
        if (sessionMode == SessionMode.VOICE_STREAM) {
            Spacer(modifier = Modifier.height(8.dp))
            VoiceControlPanel(
                voiceMode = voiceMode,
                engineState = voiceEngineState,
                onSetVoiceMode = { mode ->
                    viewModel.setVoiceMode(mode)
                    if (mode == VoiceMode.CONTINUOUS_CONVERSATION) {
                        if (!viewModel.hasRecordPermission()) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.startContinuousConversation()
                        }
                    }
                },
                onPushToTalkStart = {
                    if (!viewModel.hasRecordPermission()) {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        viewModel.startPushToTalk()
                    }
                },
                onPushToTalkStop = { viewModel.stopPushToTalk() },
                onStartContinuous = {
                    viewModel.setVoiceMode(VoiceMode.CONTINUOUS_CONVERSATION)
                    if (!viewModel.hasRecordPermission()) {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        viewModel.startContinuousConversation()
                    }
                },
                onStopVoice = { viewModel.stopVoiceInteraction() }
            )
        }

        // --- Pending Approval Alerts ---
        if (pendingApprovals.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            pendingApprovals.forEach { request ->
                PendingApprovalCard(
                    request = request,
                    onApprove = { viewModel.approveRequest(request.id) },
                    onReject = { viewModel.rejectRequest(request.id) }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Messages Timeline ---
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty() && streamingText.isEmpty()) {
                item {
                    EmptyConsoleHero(
                        onQuickPrompt = { prompt ->
                            viewModel.sendMessage(prompt)
                        }
                    )
                }
            }

            items(messages, key = { it.id }) { msg ->
                MessageBubble(message = msg)
            }

            if (streamingText.isNotEmpty()) {
                item {
                    StreamingBubble(
                        text = streamingText,
                        streamingState = streamingState
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Input Row ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        text = if (sessionMode == SessionMode.VOICE_STREAM) "Voice mode active or type message..." else "Instruct Aura...",
                        color = AuraTextMuted,
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("console_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AuraCyanPrimary,
                    unfocusedBorderColor = AuraBorder,
                    focusedTextColor = AuraTextPrimary,
                    unfocusedTextColor = AuraTextPrimary,
                    cursorColor = AuraCyanPrimary
                ),
                shape = RoundedCornerShape(20.dp),
                maxLines = 4
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = { viewModel.retryLastMessage() },
                modifier = Modifier.size(40.dp).testTag("retry_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Retry Last Command", tint = AuraTextMuted)
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (inputText.isNotBlank()) AuraCyanPrimary else AuraBorder,
                        CircleShape
                    )
                    .testTag("send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Command",
                    tint = if (inputText.isNotBlank()) Color.Black else AuraTextMuted
                )
            }
        }
    }
}

@Composable
fun VoiceControlPanel(
    voiceMode: VoiceMode,
    engineState: VoiceEngineState,
    onSetVoiceMode: (VoiceMode) -> Unit,
    onPushToTalkStart: () -> Unit,
    onPushToTalkStop: () -> Unit,
    onStartContinuous: () -> Unit,
    onStopVoice: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AuraBgCardElevated),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AuraCyanPrimary.copy(alpha = 0.5f)))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (engineState is VoiceEngineState.Listening) Icons.Default.Mic else Icons.Default.MicNone,
                        contentDescription = "Microphone",
                        tint = if (engineState is VoiceEngineState.Listening) AuraCyanPrimary else AuraTextMuted
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "VOICE ENGINE",
                        color = AuraCyanPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Mode Selector
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AssistChip(
                        onClick = { onSetVoiceMode(VoiceMode.PUSH_TO_TALK) },
                        label = { Text("Push-To-Talk", fontSize = 10.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (voiceMode == VoiceMode.PUSH_TO_TALK) AuraCyanPrimary.copy(alpha = 0.2f) else Color.Transparent
                        ),
                        modifier = Modifier.testTag("voice_mode_ptt")
                    )
                    AssistChip(
                        onClick = { onSetVoiceMode(VoiceMode.CONTINUOUS_CONVERSATION) },
                        label = { Text("Continuous", fontSize = 10.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (voiceMode == VoiceMode.CONTINUOUS_CONVERSATION) AuraCyanPrimary.copy(alpha = 0.2f) else Color.Transparent
                        ),
                        modifier = Modifier.testTag("voice_mode_continuous")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Voice State status description
            val stateText = when (engineState) {
                is VoiceEngineState.Idle -> "Idle. Tap to activate microphone."
                is VoiceEngineState.Listening -> "Listening for user speech..."
                is VoiceEngineState.SpeechDetected -> "Voice activity detected (energy: ${"%.2f".format(engineState.energy)})"
                is VoiceEngineState.Transcribing -> "Transcribing speech input..."
                is VoiceEngineState.Thinking -> "Aura processing response..."
                is VoiceEngineState.SynthesizingSpeech -> "Synthesizing text-to-speech audio..."
                is VoiceEngineState.Speaking -> "Aura speaking (Barge-in enabled: speak to interrupt)"
                is VoiceEngineState.BargeInInterrupted -> "Barge-in triggered: speech playback halted!"
                is VoiceEngineState.Muted -> "Microphone muted."
                is VoiceEngineState.PermissionRequired -> "Microphone permission required."
                is VoiceEngineState.Error -> "Voice error: ${engineState.message}"
            }

            Text(text = stateText, color = AuraTextSecondary, fontSize = 11.sp)

            Spacer(modifier = Modifier.height(8.dp))

            val isListening = engineState is VoiceEngineState.Listening
            val isSpeaking = engineState is VoiceEngineState.Speaking
            val currentAmp = when (engineState) {
                is VoiceEngineState.Listening -> engineState.amplitude
                is VoiceEngineState.SpeechDetected -> engineState.energy
                else -> 0f
            }

            com.example.aura.ui.components.VoiceActivityVisualizer(
                amplitude = currentAmp,
                isSpeaking = isSpeaking,
                isListening = isListening,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (voiceMode == VoiceMode.PUSH_TO_TALK) {
                    Button(
                        onClick = {
                            if (engineState is VoiceEngineState.Listening) {
                                onPushToTalkStop()
                            } else {
                                onPushToTalkStart()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (engineState is VoiceEngineState.Listening) AuraCriticalRed else AuraCyanPrimary,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.testTag("push_to_talk_button")
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (engineState is VoiceEngineState.Listening) "Release to Send" else "Tap & Speak")
                    }
                } else {
                    val isContinuousActive = engineState !is VoiceEngineState.Idle &&
                            engineState !is VoiceEngineState.Error &&
                            engineState !is VoiceEngineState.PermissionRequired

                    if (isContinuousActive) {
                        Button(
                            onClick = onStopVoice,
                            colors = ButtonDefaults.buttonColors(containerColor = AuraCriticalRed),
                            modifier = Modifier.testTag("stop_continuous_voice_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop Continuous Loop")
                        }
                    } else {
                        Button(
                            onClick = onStartContinuous,
                            colors = ButtonDefaults.buttonColors(containerColor = AuraSafeGreen, contentColor = Color.Black),
                            modifier = Modifier.testTag("start_continuous_voice_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Start Continuous Mode")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: MessageEntity) {
    val isUser = message.role.equals("USER", ignoreCase = true)
    val isTool = message.role.equals("TOOL", ignoreCase = true)

    val (bg, border, align) = when {
        isUser -> Triple(AuraBgCardElevated, AuraCyanPrimary.copy(alpha = 0.4f), Alignment.End)
        isTool -> Triple(Color(0xFF161028), AuraVioletSecondary.copy(alpha = 0.5f), Alignment.Start)
        else -> Triple(AuraBgCard, AuraBorder, Alignment.Start)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Text(
            text = when {
                isUser -> "You"
                isTool -> "⚡ Tool Execution"
                else -> "Aura Agent"
            },
            color = when {
                isUser -> AuraCyanPrimary
                isTool -> AuraVioletSecondary
                else -> AuraTextSecondary
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )

        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bg),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(border))
        ) {
            Text(
                text = message.content,
                color = AuraTextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontFamily = if (isTool) FontFamily.Monospace else FontFamily.Default,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}

@Composable
fun StreamingBubble(
    text: String,
    streamingState: StreamingState
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "Aura (Streaming)",
                color = AuraCyanPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            if (streamingState is StreamingState.ReceivingChunks) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${streamingState.tokenCount} tokens • ${streamingState.elapsedMillis}ms",
                    color = AuraTextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AuraBgCard),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AuraCyanPrimary))
        ) {
            Text(
                text = text + " ▍",
                color = AuraTextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}

@Composable
fun EmptyConsoleHero(
    onQuickPrompt: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        colors = CardDefaults.cardColors(containerColor = AuraBgCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AuraBorder))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AURA AGENT CORE (STAGE 2)",
                color = AuraCyanPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Streaming conversation, voice state machine, and external tool execution boundaries are ready.",
                color = AuraTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { onQuickPrompt("Test web access tool") },
                    label = { Text("Web Access Tool", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = { onQuickPrompt("Test device notification") },
                    label = { Text("Notification Tool", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun orchestratorDetailText(phase: OrchestrationPhase): String? {
    return when (phase) {
        OrchestrationPhase.UNDERSTAND -> "Analyzing user prompt & intent"
        OrchestrationPhase.PLAN -> "Formulating execution plan & memory context"
        OrchestrationPhase.SELECT_TOOL -> "Selecting candidate tool"
        OrchestrationPhase.CHECK_PERMISSION -> "Verifying system permissions"
        OrchestrationPhase.REQUEST_APPROVAL_IF_REQUIRED -> "Awaiting user confirmation"
        OrchestrationPhase.EXECUTE -> "Invoking tool execution"
        OrchestrationPhase.OBSERVE_RESULT -> "Parsing execution observation"
        OrchestrationPhase.UPDATE_STATE -> "Writing session state"
        OrchestrationPhase.CONTINUE_OR_RESPOND -> "Synthesizing response"
        OrchestrationPhase.INTERRUPTED -> "Execution halted"
        OrchestrationPhase.ERROR -> "Execution error"
        else -> null
    }
}
