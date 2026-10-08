package com.example.aura.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.aura.core.logging.AuditLogEntry
import com.example.aura.core.provider.ProviderConfig
import com.example.aura.core.security.ApprovalPolicy
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AuditAndProvidersScreen(
    viewModel: AuraMainViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuraBgDeep)
            .padding(16.dp)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = AuraBgCard,
            contentColor = AuraCyanPrimary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Activity Audit Log", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Model Providers", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedTab == 0) {
            AuditLogSubView(viewModel = viewModel)
        } else {
            ModelProvidersSubView(viewModel = viewModel)
        }
    }
}

@Composable
fun AuditLogSubView(viewModel: AuraMainViewModel) {
    val logs by viewModel.activityLogs.collectAsStateWithLifecycle()
    val policy by viewModel.approvalPolicy.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Policy control bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AuraBgCard),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AuraBorder))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = AuraCyanPrimary)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("Security Approval Policy:", color = AuraTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ApprovalPolicy.entries.forEach { p ->
                        val isSelected = policy == p
                        AssistChip(
                            onClick = { viewModel.setSecurityPolicy(p) },
                            label = { Text(p.name, fontSize = 10.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isSelected) AuraCyanPrimary.copy(alpha = 0.2f) else Color.Transparent
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = if (isSelected) AuraCyanPrimary else AuraBorder
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AUDIT TIMELINE (${logs.size} EVENTS)",
                color = AuraTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { viewModel.clearAuditLogs() }) {
                Icon(Icons.Default.Delete, contentDescription = "Clear logs", tint = AuraTextMuted)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (logs.isEmpty()) {
                item {
                    Text(
                        text = "No audit logs recorded yet. Events appear here as Aura reasons, plans, executes tools, and checks security.",
                        color = AuraTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
            items(logs, key = { it.id }) { log ->
                AuditLogCard(log = log)
            }
        }
    }
}

@Composable
fun AuditLogCard(log: AuditLogEntry) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val formattedTime = timeFormat.format(Date(log.timestampMillis))

    val typeColor = when (log.type.name) {
        "REASONING_STEP" -> AuraCyanPrimary
        "TOOL_PROPOSED", "TOOL_EXECUTED" -> AuraVioletSecondary
        "APPROVAL_GRANTED" -> AuraSafeGreen
        "APPROVAL_REJECTED", "TOOL_FAILED" -> AuraCriticalRed
        "SESSION_INTERRUPTED" -> AuraWarningAmber
        else -> AuraTextSecondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AuraBgCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AuraBorder))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = log.type.name,
                        color = typeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text(text = "[$formattedTime]", color = AuraTextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                SecurityLevelBadge(level = log.securityLevel)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = log.message, color = AuraTextPrimary, fontSize = 12.sp, lineHeight = 16.sp)

            if (log.metadata.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.metadata.entries.joinToString(" • ") { "${it.key}: ${it.value}" },
                    color = AuraTextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun ModelProvidersSubView(viewModel: AuraMainViewModel) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val activeProviderId by viewModel.activeProviderId.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "PLUGGABLE MODEL BACKENDS",
            color = AuraCyanPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = "Aura is provider-agnostic. Select or configure any reasoning engine:",
            color = AuraTextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(providers, key = { it.id }) { provider ->
                ProviderConfigCard(
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
fun ProviderConfigCard(
    provider: ProviderConfig,
    isActive: Boolean,
    onSelect: () -> Unit,
    onSave: (ProviderConfig) -> Unit
) {
    var endpoint by remember(provider.endpointUrl) { androidx.compose.runtime.mutableStateOf(provider.endpointUrl) }
    var apiKey by remember(provider.apiKey) { androidx.compose.runtime.mutableStateOf(provider.apiKey) }
    var modelName by remember(provider.defaultModel) { androidx.compose.runtime.mutableStateOf(provider.defaultModel) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_card_${provider.id}"),
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
                    RadioButton(
                        selected = isActive,
                        onClick = onSelect,
                        colors = RadioButtonDefaults.colors(selectedColor = AuraCyanPrimary),
                        modifier = Modifier.testTag("provider_radio_${provider.id}")
                    )
                    Column {
                        Text(
                            text = provider.displayName,
                            color = AuraTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Type: ${provider.type.name}",
                            color = AuraTextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (isActive) {
                    Text(
                        text = "ACTIVE",
                        color = AuraCyanPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(AuraCyanPrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                label = { Text("Endpoint URL", fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AuraTextPrimary,
                    unfocusedTextColor = AuraTextPrimary
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("Model Identifier", fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AuraTextPrimary,
                    unfocusedTextColor = AuraTextPrimary
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key / Bearer Token", fontSize = 11.sp) },
                placeholder = { Text("Configure in Secrets panel or enter here") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AuraTextPrimary,
                    unfocusedTextColor = AuraTextPrimary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = {
                        onSave(
                            provider.copy(
                                endpointUrl = endpoint,
                                apiKey = apiKey,
                                defaultModel = modelName
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuraVioletSecondary)
                ) {
                    Text("Save Provider Config", fontSize = 11.sp)
                }
            }
        }
    }
}
