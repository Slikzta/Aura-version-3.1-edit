package com.example.aura.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aura.core.provider.ProviderConfig
import com.example.aura.core.security.ApprovalPolicy
import com.example.aura.core.tools.AuraTool
import com.example.aura.core.voice.VoiceEngineState
import com.example.aura.core.voice.VoiceMode
import com.example.aura.ui.AuraMainViewModel
import com.example.aura.ui.components.SecurityLevelBadge
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
import kotlin.math.roundToInt

/**
 * Complete, production-grade Settings screen for Aura:
 * - General Preferences
 * - AI Provider Configuration (Gemini, OpenAI, Anthropic Claude, Ollama, Custom OpenAI-Compatible)
 * - Voice & Speech Parameters (Rate, Pitch, Natural Voice, Language Locale, Preview)
 * - Autonomous Mode & Hands-Free Conversation Controls (Mute, Pause, Resume, Indicators)
 * - Android Permissions Matrix (Status, Explanations, Dynamic Requests)
 * - Tools & Skills Management (Visibility, Toggles, Real Execution Testing)
 */
@Composable
fun SettingsScreen(
    viewModel: AuraMainViewModel,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableIntStateOf(0) }
    val sections = listOf(
        "Providers" to Icons.Default.SmartToy,
        "Voice & Speech" to Icons.Default.RecordVoiceOver,
        "Autonomous" to Icons.Default.AccountTree,
        "Permissions" to Icons.Default.Security,
        "Tools" to Icons.Default.Build
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuraBgDeep)
            .padding(16.dp)
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedSection,
            containerColor = AuraBgCard,
            contentColor = AuraCyanPrimary,
            edgePadding = 8.dp,
            modifier = Modifier.fillMaxWidth().testTag("settings_tab_row")
        ) {
            sections.forEachIndexed { index, (title, icon) ->
                Tab(
                    selected = selectedSection == index,
                    onClick = { selectedSection = index },
                    icon = { Icon(icon, contentDescription = title, modifier = Modifier.size(18.dp)) },
                    text = { Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("settings_tab_$index")
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (selectedSection) {
            0 -> SettingsProvidersSection(viewModel = viewModel)
            1 -> SettingsVoiceSection(viewModel = viewModel)
            2 -> SettingsAutonomousSection(viewModel = viewModel)
            3 -> SettingsPermissionsSection(viewModel = viewModel)
            else -> SettingsToolsSection(viewModel = viewModel)
        }
    }
}

// ==========================================
// 1. AI MODEL PROVIDERS SECTION
// ==========================================
@Composable
fun SettingsProvidersSection(viewModel: AuraMainViewModel) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val activeProviderId by viewModel.activeProviderId.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "AI MODEL BACKENDS & SECURE CREDENTIALS",
            color = AuraCyanPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = "Configure and activate your desired reasoning backend. Credentials are encrypted in private storage and never logged.",
            color = AuraTextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(providers, key = { it.id }) { provider ->
                SettingsProviderCard(
                    provider = provider,
                    isActive = provider.id == activeProviderId,
                    onSelect = { viewModel.selectProvider(provider.id) },
                    onSave = { updated -> viewModel.updateProviderConfig(updated) }
                )
            }
        }
    }
}

@Composable
fun SettingsProviderCard(
    provider: ProviderConfig,
    isActive: Boolean,
    onSelect: () -> Unit,
    onSave: (ProviderConfig) -> Result<Unit>
) {
    var endpoint by remember(provider.endpointUrl) { mutableStateOf(provider.endpointUrl) }
    var apiKey by remember(provider.apiKey) { mutableStateOf(provider.apiKey) }
    var modelName by remember(provider.defaultModel) { mutableStateOf(provider.defaultModel) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_provider_card_${provider.id}"),
        colors = CardDefaults.cardColors(containerColor = if (isActive) AuraBgCardElevated else AuraBgCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(if (isActive) AuraCyanPrimary else AuraBorder)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = if (isActive) AuraCyanPrimary else AuraVioletSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = provider.displayName,
                            color = AuraTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Backend: ${provider.type.name}",
                            color = AuraTextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (isActive) {
                    AssistChip(
                        onClick = {},
                        label = { Text("ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = AuraCyanPrimary.copy(alpha = 0.2f),
                            labelColor = AuraCyanPrimary
                        ),
                        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = AuraCyanPrimary)
                    )
                } else {
                    OutlinedButton(
                        onClick = onSelect,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraCyanPrimary),
                        modifier = Modifier.testTag("select_provider_${provider.id}")
                    ) {
                        Text("Activate", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (provider.type != com.example.aura.core.provider.ModelProviderType.DIAGNOSTIC_OFFLINE) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = {
                        endpoint = it
                        statusMessage = null
                    },
                    label = { Text("API Endpoint URL") },
                    modifier = Modifier.fillMaxWidth().testTag("endpoint_input_${provider.id}"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AuraTextPrimary,
                        unfocusedTextColor = AuraTextPrimary,
                        focusedBorderColor = AuraCyanPrimary,
                        unfocusedBorderColor = AuraBorder
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (provider.type != com.example.aura.core.provider.ModelProviderType.OLLAMA_LOCAL) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            statusMessage = null
                        },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth().testTag("api_key_input_${provider.id}"),
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isKeyVisible) "Hide Key" else "Show Key",
                                    tint = AuraTextMuted
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AuraTextPrimary,
                            unfocusedTextColor = AuraTextPrimary,
                            focusedBorderColor = AuraCyanPrimary,
                            unfocusedBorderColor = AuraBorder
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = modelName,
                    onValueChange = {
                        modelName = it
                        statusMessage = null
                    },
                    label = { Text("Default Model Name") },
                    modifier = Modifier.fillMaxWidth().testTag("model_input_${provider.id}"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AuraTextPrimary,
                        unfocusedTextColor = AuraTextPrimary,
                        focusedBorderColor = AuraCyanPrimary,
                        unfocusedBorderColor = AuraBorder
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    statusMessage?.let { msg ->
                        Text(
                            text = msg,
                            color = if (isError) AuraCriticalRed else AuraSafeGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    } ?: Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val updated = provider.copy(
                                endpointUrl = endpoint.trim(),
                                apiKey = apiKey.trim(),
                                defaultModel = modelName.trim()
                            )
                            val res = onSave(updated)
                            if (res.isSuccess) {
                                isError = false
                                statusMessage = "Configuration saved securely."
                            } else {
                                isError = true
                                statusMessage = res.exceptionOrNull()?.message ?: "Failed to save."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AuraVioletSecondary),
                        modifier = Modifier.testTag("save_provider_${provider.id}")
                    ) {
                        Text("Save Config", fontSize = 12.sp)
                    }
                }
            } else {
                Text(
                    text = "Foundation diagnostic offline engine for validating tool execution and local orchestration without external network keys.",
                    color = AuraTextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ==========================================
// 2. VOICE & SPEECH SETTINGS SECTION
// ==========================================
@Composable
fun SettingsVoiceSection(viewModel: AuraMainViewModel) {
    val speechRate by viewModel.speechRate.collectAsStateWithLifecycle()
    val speechPitch by viewModel.speechPitch.collectAsStateWithLifecycle()
    val selectedVoice by viewModel.selectedVoiceName.collectAsStateWithLifecycle()
    val selectedLang by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val engineState by viewModel.voiceEngineState.collectAsStateWithLifecycle()

    val availableVoices = remember { viewModel.getAvailableVoices() }
    val availableLanguages = remember { viewModel.getAvailableLanguages() }

    var previewText by remember { mutableStateOf("Aura autonomous agent online and ready.") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "VOICE ENGINE & NATURAL SPEECH SYNTHESIS",
                color = AuraCyanPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "Customize speech rate, pitch, and voice profile to prevent robotic cadence and enhance conversation clarity.",
                color = AuraTextSecondary,
                fontSize = 12.sp
            )
        }

        // Live Voice Engine Status Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AuraBgCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AuraBorder))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Engine State:", color = AuraTextMuted, fontSize = 12.sp)
                        Text(
                            text = engineState::class.simpleName ?: "Idle",
                            color = when (engineState) {
                                is VoiceEngineState.Speaking -> AuraSafeGreen
                                is VoiceEngineState.Listening -> AuraCyanPrimary
                                is VoiceEngineState.Muted -> AuraWarningAmber
                                is VoiceEngineState.Error -> AuraCriticalRed
                                else -> AuraTextSecondary
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.setMuted(!isMuted) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMuted) AuraWarningAmber else AuraBgCardElevated,
                                contentColor = AuraTextPrimary
                            ),
                            modifier = Modifier.weight(1f).testTag("toggle_mute_button")
                        ) {
                            Icon(if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isMuted) "Unmute" else "Mute", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                if (isPaused) viewModel.resumeVoice() else viewModel.pauseVoice()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPaused) AuraCyanPrimary else AuraBgCardElevated,
                                contentColor = if (isPaused) Color.Black else AuraTextPrimary
                            ),
                            modifier = Modifier.weight(1f).testTag("toggle_pause_button")
                        ) {
                            Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isPaused) "Resume" else "Pause", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Speech Rate Slider Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AuraBgCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AuraBorder))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Speech Rate (Speed)", color = AuraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(String.format("%.2fx", speechRate), color = AuraCyanPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Slider(
                        value = speechRate,
                        onValueChange = { viewModel.setSpeechRate(it) },
                        valueRange = 0.5f..2.0f,
                        steps = 15,
                        colors = SliderDefaults.colors(
                            thumbColor = AuraCyanPrimary,
                            activeTrackColor = AuraCyanPrimary,
                            inactiveTrackColor = AuraBorder
                        ),
                        modifier = Modifier.testTag("speech_rate_slider")
                    )
                    Text("Standard: 1.0x. Lower values provide more natural, deliberate articulation.", color = AuraTextMuted, fontSize = 11.sp)
                }
            }
        }

        // Speech Pitch Slider Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AuraBgCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AuraBorder))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Voice Pitch", color = AuraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(String.format("%.2fx", speechPitch), color = AuraVioletSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Slider(
                        value = speechPitch,
                        onValueChange = { viewModel.setSpeechPitch(it) },
                        valueRange = 0.5f..2.0f,
                        steps = 15,
                        colors = SliderDefaults.colors(
                            thumbColor = AuraVioletSecondary,
                            activeTrackColor = AuraVioletSecondary,
                            inactiveTrackColor = AuraBorder
                        ),
                        modifier = Modifier.testTag("speech_pitch_slider")
                    )
                    Text("Adjusts the tone of speech synthesis for more natural vocal presence.", color = AuraTextMuted, fontSize = 11.sp)
                }
            }
        }

        // Voice Profile & Language Selection Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AuraBgCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AuraBorder))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Installed Voices & Language Locales", color = AuraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (availableVoices.isNotEmpty()) {
                        Text("Active Voice: ${selectedVoice.ifBlank { "System Default" }}", color = AuraTextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableVoices.take(3).forEach { voiceName ->
                                val isChosen = selectedVoice.equals(voiceName, ignoreCase = true)
                                AssistChip(
                                    onClick = { viewModel.setSelectedVoice(voiceName) },
                                    label = { Text(voiceName.takeLast(12), fontSize = 10.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (isChosen) AuraCyanPrimary.copy(alpha = 0.2f) else Color.Transparent,
                                        labelColor = if (isChosen) AuraCyanPrimary else AuraTextSecondary
                                    ),
                                    border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = if (isChosen) AuraCyanPrimary else AuraBorder)
                                )
                            }
                        }
                    } else {
                        Text("System voice engine uses device-optimized neural synthesis.", color = AuraTextMuted, fontSize = 12.sp)
                    }
                }
            }
        }

        // Voice Preview Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AuraBgCardElevated),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AuraCyanPrimary))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Instant Voice Preview", color = AuraCyanPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = previewText,
                        onValueChange = { previewText = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AuraTextPrimary,
                            unfocusedTextColor = AuraTextPrimary,
                            focusedBorderColor = AuraCyanPrimary,
                            unfocusedBorderColor = AuraBorder
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.previewVoice(previewText) },
                        colors = ButtonDefaults.buttonColors(containerColor = AuraCyanPrimary),
                        modifier = Modifier.fillMaxWidth().testTag("preview_voice_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Audition Voice", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. AUTONOMOUS MODE SETTINGS SECTION
// ==========================================
@Composable
fun SettingsAutonomousSection(viewModel: AuraMainViewModel) {
    val voiceMode by viewModel.voiceMode.collectAsStateWithLifecycle()
    val approvalPolicy by viewModel.approvalPolicy.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val handsFreeEnabled by viewModel.continuousHandsFreeEnabled.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "AUTONOMOUS MODE & HANDS-FREE INTERACTION",
                color = AuraCyanPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "Configure hands-free continuous conversations, execution approval gates, and multi-turn audio interactions.",
                color = AuraTextSecondary,
                fontSize = 12.sp
            )
        }

        // Continuous Conversation Mode Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AuraBgCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AuraBorder))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continuous Hands-Free Conversation", color = AuraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "Automatically listens after speaking finishes without holding push-to-talk.",
                                color = AuraTextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = voiceMode == VoiceMode.CONTINUOUS_CONVERSATION,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    viewModel.startContinuousConversation()
                                } else {
                                    viewModel.setVoiceMode(VoiceMode.PUSH_TO_TALK)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AuraCyanPrimary,
                                checkedTrackColor = AuraCyanPrimary.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.testTag("hands_free_switch")
                        )
                    }
                }
            }
        }

        // Security Approval Policy Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AuraBgCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AuraBorder))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Security Approval Gate Policy", color = AuraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Controls which autonomous tool actions require your manual sign-off before executing.", color = AuraTextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    ApprovalPolicy.entries.forEach { policy ->
                        val isSelected = approvalPolicy == policy
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setSecurityPolicy(policy) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Security,
                                contentDescription = null,
                                tint = if (isSelected) AuraCyanPrimary else AuraTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = policy.name,
                                    color = if (isSelected) AuraCyanPrimary else AuraTextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = when (policy) {
                                        ApprovalPolicy.STANDARD -> "Medium and high impact tools require confirmation."
                                        ApprovalPolicy.STRICT -> "Any action with device effects requires confirmation."
                                        ApprovalPolicy.ZERO_TRUST -> "Every autonomous tool invocation requires confirmation."
                                    },
                                    color = AuraTextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. ANDROID PERMISSIONS SECTION
// ==========================================
@Composable
fun SettingsPermissionsSection(viewModel: AuraMainViewModel) {
    val context = LocalContext.current

    data class PermissionItem(
        val permission: String,
        val label: String,
        val description: String,
        val isRequiredFor: String
    )

    val permissionsList = listOf(
        PermissionItem(
            permission = android.Manifest.permission.RECORD_AUDIO,
            label = "Record Audio (Microphone)",
            description = "Enables real-time speech-to-text recognition for push-to-talk and continuous hands-free dialogue.",
            isRequiredFor = "Voice Controls & Conversation"
        ),
        PermissionItem(
            permission = android.Manifest.permission.POST_NOTIFICATIONS,
            label = "Post Notifications",
            description = "Allows Aura to notify you when long-running autonomous project steps complete.",
            isRequiredFor = "Notification Tool & Autonomous Alerts"
        ),
        PermissionItem(
            permission = android.Manifest.permission.READ_CALENDAR,
            label = "Read Calendar",
            description = "Enables the calendar manager tool to query upcoming events and agenda details.",
            isRequiredFor = "Calendar Tool"
        )
    )

    var triggerRefresh by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        triggerRefresh++
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "DEVICE PERMISSIONS & PRIVACY SAFEGUARDS",
                color = AuraCyanPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "Aura adheres strictly to Android least-privilege standards. Permissions are only utilized when explicitly initiated.",
                color = AuraTextSecondary,
                fontSize = 12.sp
            )
        }

        items(permissionsList) { item ->
            val isGranted = ContextCompat.checkSelfPermission(context, item.permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

            Card(
                modifier = Modifier.fillMaxWidth().testTag("permission_card_${item.permission}"),
                colors = CardDefaults.cardColors(containerColor = AuraBgCard),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(if (isGranted) AuraSafeGreen.copy(alpha = 0.5f) else AuraWarningAmber)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.label, color = AuraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        AssistChip(
                            onClick = {},
                            label = { Text(if (isGranted) "GRANTED" else "MISSING", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isGranted) AuraSafeGreen.copy(alpha = 0.2f) else AuraWarningAmber.copy(alpha = 0.2f),
                                labelColor = if (isGranted) AuraSafeGreen else AuraWarningAmber
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(item.description, color = AuraTextSecondary, fontSize = 12.sp)

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Powers: ${item.isRequiredFor}", color = AuraCyanPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)

                    if (!isGranted) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { permissionLauncher.launch(item.permission) },
                                colors = ButtonDefaults.buttonColors(containerColor = AuraCyanPrimary),
                                modifier = Modifier.testTag("request_permission_button")
                            ) {
                                Text("Grant Permission", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().testTag("open_system_settings_button"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraTextSecondary)
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Android App System Settings", fontSize = 12.sp)
            }
        }
    }
}

// ==========================================
// 5. TOOLS & CAPABILITIES SECTION
// ==========================================
@Composable
fun SettingsToolsSection(viewModel: AuraMainViewModel) {
    val tools by viewModel.tools.collectAsStateWithLifecycle()
    val enabledIds by viewModel.enabledToolIds.collectAsStateWithLifecycle()
    val output by viewModel.toolExecutionOutput.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "EXTENSIBLE TOOLS & RUNTIME SKILLS",
                color = AuraCyanPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "Registered tools available to the autonomous orchestration engine. Toggle capabilities or trigger safe execution audits.",
                color = AuraTextSecondary,
                fontSize = 12.sp
            )
        }

        output?.let { text ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AuraBgCardElevated),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AuraCyanPrimary))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Execution Audit Result", color = AuraCyanPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Button(
                                onClick = { viewModel.clearToolExecutionOutput() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = AuraTextMuted)
                            ) {
                                Text("Clear", fontSize = 11.sp)
                            }
                        }
                        Text(text = text, color = AuraTextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        items(tools, key = { it.id }) { tool ->
            val isEnabled = enabledIds.contains(tool.id)
            Card(
                modifier = Modifier.fillMaxWidth().testTag("tool_card_${tool.id}"),
                colors = CardDefaults.cardColors(containerColor = AuraBgCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AuraBorder))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tool.name, color = AuraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            SecurityLevelBadge(level = tool.securityLevel)
                        }

                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { viewModel.toggleToolEnabled(tool.id, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AuraCyanPrimary,
                                checkedTrackColor = AuraCyanPrimary.copy(alpha = 0.3f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(tool.description, color = AuraTextSecondary, fontSize = 11.sp)

                    if (tool.permissionRequirements.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Requires: ${tool.permissionRequirements.joinToString()}",
                            color = AuraWarningAmber,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val args = when (tool.id) {
                                    "network_check" -> emptyMap()
                                    "system_diagnostics" -> mapOf("include_memory" to true)
                                    "sandboxed_file" -> mapOf("action" to "list")
                                    "propose_notification" -> mapOf("title" to "Aura Test", "message" to "Diagnostic test notice")
                                    "device_actions" -> mapOf("action" to "vibrate", "duration_ms" to 150)
                                    else -> emptyMap<String, Any?>()
                                }
                                viewModel.testExecuteTool(tool.id, args)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraCyanPrimary),
                            modifier = Modifier.testTag("test_tool_${tool.id}")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Tool", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
