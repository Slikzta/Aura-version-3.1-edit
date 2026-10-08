package com.example.aura.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.core.agent.AgentState
import com.example.aura.core.security.ApprovalRequest
import com.example.aura.core.security.SecurityLevel
import com.example.aura.core.session.VoiceState
import com.example.aura.ui.theme.AuraBgCardElevated
import com.example.aura.ui.theme.AuraBorder
import com.example.aura.ui.theme.AuraBorderGlow
import com.example.aura.ui.theme.AuraCriticalRed
import com.example.aura.ui.theme.AuraCyanPrimary
import com.example.aura.ui.theme.AuraSafeGreen
import com.example.aura.ui.theme.AuraTextMuted
import com.example.aura.ui.theme.AuraTextPrimary
import com.example.aura.ui.theme.AuraTextSecondary
import com.example.aura.ui.theme.AuraVioletSecondary
import com.example.aura.ui.theme.AuraWarningAmber

@Composable
fun AgentStatusBadge(
    state: AgentState,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (state) {
        AgentState.IDLE -> Pair(AuraSafeGreen, "AURA READY")
        AgentState.THINKING -> Pair(AuraCyanPrimary, "THINKING")
        AgentState.PLANNING -> Pair(AuraCyanPrimary, "PLANNING")
        AgentState.EXECUTING_TOOL -> Pair(AuraVioletSecondary, "RUNNING TOOL")
        AgentState.AWAITING_APPROVAL -> Pair(AuraWarningAmber, "APPROVAL REQUIRED")
        AgentState.INTERRUPTED -> Pair(AuraCriticalRed, "INTERRUPTED")
        AgentState.ERROR -> Pair(AuraCriticalRed, "AGENT ERROR")
    }

    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
fun SecurityLevelBadge(
    level: SecurityLevel,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (level) {
        SecurityLevel.SAFE -> Pair(AuraSafeGreen, "SAFE")
        SecurityLevel.LOW_RISK -> Pair(AuraCyanPrimary, "LOW RISK")
        SecurityLevel.MEDIUM_RISK, SecurityLevel.SENSITIVE -> Pair(AuraWarningAmber, "MEDIUM RISK")
        SecurityLevel.HIGH_RISK, SecurityLevel.CRITICAL -> Pair(AuraCriticalRed, "HIGH RISK")
    }

    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .border(0.5.dp, color.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun VoiceStateIndicator(
    voiceState: VoiceState,
    modifier: Modifier = Modifier
) {
    val (color, label) = when (voiceState) {
        VoiceState.IDLE -> Pair(AuraTextMuted, "Voice Muted")
        VoiceState.LISTENING -> Pair(AuraCyanPrimary, "Listening...")
        VoiceState.PROCESSING -> Pair(AuraVioletSecondary, "Voice Processing...")
        VoiceState.SPEAKING -> Pair(AuraSafeGreen, "Aura Speaking")
        VoiceState.INTERRUPTED -> Pair(AuraCriticalRed, "Voice Interrupted")
        VoiceState.MUTED -> Pair(AuraTextMuted, "Muted")
    }

    Row(
        modifier = modifier
            .background(AuraBgCardElevated, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, color = AuraTextSecondary, fontSize = 11.sp)
    }
}

@Composable
fun PendingApprovalCard(
    request: ApprovalRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("approval_card_${request.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AuraBgCardElevated),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AuraWarningAmber.copy(alpha = 0.6f)))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Permission Alert",
                        tint = AuraWarningAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Approval Required",
                        color = AuraWarningAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                SecurityLevelBadge(level = request.riskLevel)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Tool: ${request.toolName}",
                color = AuraTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = request.actionSummary,
                color = AuraTextSecondary,
                fontSize = 13.sp
            )

            if (request.parametersSummary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(text = "Parameters:", color = AuraTextMuted, fontSize = 11.sp)
                    request.parametersSummary.forEach { (key, value) ->
                        Text(
                            text = "• $key: $value",
                            color = AuraTextPrimary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onReject,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraCriticalRed),
                    modifier = Modifier.testTag("reject_button_${request.id}")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Decline", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Decline")
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuraSafeGreen,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("approve_button_${request.id}")
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Approve", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Approve Action")
                }
            }
        }
    }
}
