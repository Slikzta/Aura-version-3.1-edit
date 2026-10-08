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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aura.core.tools.AuraTool
import com.example.aura.ui.AuraMainViewModel
import com.example.aura.ui.components.SecurityLevelBadge
import com.example.aura.ui.theme.AuraBgCard
import com.example.aura.ui.theme.AuraBgCardElevated
import com.example.aura.ui.theme.AuraBgDeep
import com.example.aura.ui.theme.AuraBorder
import com.example.aura.ui.theme.AuraCyanPrimary
import com.example.aura.ui.theme.AuraTextMuted
import com.example.aura.ui.theme.AuraTextPrimary
import com.example.aura.ui.theme.AuraTextSecondary
import com.example.aura.ui.theme.AuraVioletSecondary
import com.example.aura.ui.theme.AuraWarningAmber

@Composable
fun ToolsScreen(
    viewModel: AuraMainViewModel,
    modifier: Modifier = Modifier
) {
    val tools by viewModel.tools.collectAsStateWithLifecycle()
    val enabledIds by viewModel.enabledToolIds.collectAsStateWithLifecycle()
    val executionOutput by viewModel.toolExecutionOutput.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuraBgDeep)
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "SKILL & TOOL ARCHITECTURE (STAGE 2)",
                color = AuraCyanPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "Modular Capability Registry, Schemas & System Permissions",
                color = AuraTextSecondary,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tool test execution result banner
        executionOutput?.let { output ->
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
                        Text("Live Tool Output", color = AuraCyanPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Button(
                            onClick = { viewModel.clearToolExecutionOutput() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = AuraTextMuted)
                        ) {
                            Text("Dismiss", fontSize = 11.sp)
                        }
                    }
                    Text(
                        text = output,
                        color = AuraTextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(tools, key = { it.id }) { tool ->
                val isEnabled = enabledIds.contains(tool.id)
                ToolCard(
                    tool = tool,
                    isEnabled = isEnabled,
                    onToggle = { viewModel.toggleToolEnabled(tool.id, it) },
                    onRunDirect = {
                        val testArgs = when (tool.id) {
                            "web_access" -> mapOf("url" to "https://api.github.com", "max_characters" to 1000)
                            "sandboxed_file" -> mapOf("action" to "write", "file_name" to "aura_test.txt", "content" to "Stage 2 sandbox file boundary.")
                            "notifications" -> mapOf("title" to "Aura Alert", "message" to "Real Stage 2 Android notification verification.")
                            "device_actions" -> mapOf("action" to "copy_to_clipboard", "text" to "Aura local-first autonomous agent")
                            "calendar" -> mapOf("action" to "query_upcoming", "days_ahead" to 7)
                            "messaging" -> mapOf("recipient_phone" to "+15551234567", "message_body" to "Hello from Aura Agent!")
                            "browser_automation" -> mapOf("url" to "https://www.android.com")
                            else -> emptyMap()
                        }
                        viewModel.executeToolDirectly(tool.id, testArgs)
                    }
                )
            }
        }
    }
}

@Composable
fun ToolCard(
    tool: AuraTool,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onRunDirect: () -> Unit
) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tool.name,
                            color = AuraTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        SecurityLevelBadge(level = tool.securityLevel)
                    }
                    Text(
                        text = "ID: ${tool.id} • Category: ${tool.category.name}",
                        color = AuraTextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AuraCyanPrimary,
                        checkedTrackColor = AuraCyanPrimary.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.testTag("tool_toggle_${tool.id}")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = tool.description,
                color = AuraTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            // Runtime Permissions badge if required
            if (tool.permissionRequirements.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Permissions Required:",
                        color = AuraWarningAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    tool.permissionRequirements.forEach { perm ->
                        Text(
                            text = perm.substringAfterLast('.'),
                            color = AuraWarningAmber,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .background(AuraWarningAmber.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .border(0.5.dp, AuraWarningAmber.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }

            // Input Schema
            if (tool.inputSchema.parameters.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(text = "Input Schema Parameters:", color = AuraTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    tool.inputSchema.parameters.forEach { param ->
                        Text(
                            text = "• ${param.name} (${param.type}${if (param.required) ", required" else ""}): ${param.description}",
                            color = AuraTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onRunDirect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuraVioletSecondary.copy(alpha = 0.2f),
                        contentColor = AuraVioletSecondary
                    ),
                    modifier = Modifier.testTag("run_tool_button_${tool.id}")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Test Run Tool", fontSize = 11.sp)
                }
            }
        }
    }
}
