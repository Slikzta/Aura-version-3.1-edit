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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.aura.core.agent.StepStatus
import com.example.aura.data.entities.ProjectEntity
import com.example.aura.data.entities.TaskEntity
import com.example.aura.ui.AuraMainViewModel
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
fun AutonomousScreen(
    viewModel: AuraMainViewModel,
    modifier: Modifier = Modifier
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val activePlan by viewModel.activePlan.collectAsStateWithLifecycle()

    var showNewProjectDialog by remember { mutableStateOf(false) }
    var projectTitle by remember { mutableStateOf("") }
    var projectDesc by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuraBgDeep)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AUTONOMOUS AGENT ORCHESTRATION",
                    color = AuraCyanPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Projects, Goals & Multi-Step Task Execution",
                    color = AuraTextSecondary,
                    fontSize = 13.sp
                )
            }

            Button(
                onClick = { showNewProjectDialog = !showNewProjectDialog },
                colors = ButtonDefaults.buttonColors(containerColor = AuraVioletSecondary),
                modifier = Modifier.testTag("new_project_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Project", modifier = Modifier.padding(end = 4.dp))
                Text("New Project", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (showNewProjectDialog) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AuraBgCardElevated),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AuraVioletSecondary))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Define Autonomous Project", color = AuraTextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = projectTitle,
                        onValueChange = { projectTitle = it },
                        placeholder = { Text("Project Title (e.g., Weekly Market Research)") },
                        modifier = Modifier.fillMaxWidth().testTag("project_title_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AuraTextPrimary,
                            unfocusedTextColor = AuraTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = projectDesc,
                        onValueChange = { projectDesc = it },
                        placeholder = { Text("Objective / High-level goal") },
                        modifier = Modifier.fillMaxWidth().testTag("project_desc_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AuraTextPrimary,
                            unfocusedTextColor = AuraTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                if (projectTitle.isNotBlank()) {
                                    viewModel.createProjectAndPlan(projectTitle, projectDesc, autoExecute = true)
                                    projectTitle = ""
                                    projectDesc = ""
                                    showNewProjectDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AuraCyanPrimary),
                            modifier = Modifier.testTag("launch_project_button")
                        ) {
                            Text("Launch Project & Plan", color = androidx.compose.ui.graphics.Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Active Autonomous Plan Card
        activePlan?.let { plan ->
            val hasPendingOrFailed = plan.steps.any { it.status == StepStatus.PENDING || it.status == StepStatus.FAILED }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("active_plan_card"),
                colors = CardDefaults.cardColors(containerColor = AuraBgCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AuraCyanPrimary.copy(alpha = 0.5f)))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AuraCyanPrimary)
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            Column {
                                Text(
                                    text = "Active Plan: ${plan.goalTitle}",
                                    color = AuraCyanPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (plan.isRunning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .padding(end = 4.dp),
                                            color = AuraCyanPrimary,
                                            strokeWidth = 1.5.dp
                                        )
                                        Text(
                                            text = "RUNNING",
                                            color = AuraCyanPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.testTag("plan_status_running")
                                        )
                                    } else if (plan.steps.isNotEmpty() && plan.steps.all { it.status == StepStatus.COMPLETED }) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AuraSafeGreen, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "ALL STEPS COMPLETED",
                                            color = AuraSafeGreen,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.testTag("plan_status_completed")
                                        )
                                    } else if (plan.failureReason != null) {
                                        Icon(Icons.Default.Cancel, contentDescription = null, tint = AuraCriticalRed, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "HALTED ON ERROR",
                                            color = AuraCriticalRed,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.testTag("plan_status_failed")
                                        )
                                    } else {
                                        Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = AuraWarningAmber, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "READY",
                                            color = AuraWarningAmber,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.testTag("plan_status_ready")
                                        )
                                    }
                                }
                            }
                        }

                        // Plan Execution Control Buttons
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (plan.isRunning) {
                                Button(
                                    onClick = { viewModel.cancelActivePlan() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AuraCriticalRed),
                                    modifier = Modifier.testTag("cancel_plan_button")
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = "Cancel", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Stop", fontSize = 11.sp)
                                }
                            } else if (hasPendingOrFailed) {
                                Button(
                                    onClick = { viewModel.executeActivePlan() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AuraCyanPrimary),
                                    modifier = Modifier.testTag("execute_plan_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Execute", tint = Color.Black, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Execute", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    if (plan.failureReason != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AuraCriticalRed.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                .border(1.dp, AuraCriticalRed.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, tint = AuraCriticalRed, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = plan.failureReason,
                                color = AuraCriticalRed,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    plan.steps.forEach { step ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    when (step.status) {
                                        StepStatus.EXECUTING -> AuraCyanPrimary.copy(alpha = 0.08f)
                                        StepStatus.FAILED -> AuraCriticalRed.copy(alpha = 0.08f)
                                        StepStatus.AWAITING_APPROVAL -> AuraWarningAmber.copy(alpha = 0.08f)
                                        else -> Color.Transparent
                                    },
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("plan_step_${step.stepIndex}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${step.stepIndex}.",
                                    color = AuraTextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = step.description,
                                        color = AuraTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    step.toolId?.let { toolName ->
                                        Text(
                                            text = "tool: $toolName",
                                            color = AuraTextMuted,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    when (step.status) {
                                        StepStatus.EXECUTING -> {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                color = AuraCyanPrimary,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("RUNNING", color = AuraCyanPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        StepStatus.COMPLETED -> {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AuraSafeGreen, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("DONE", color = AuraSafeGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        StepStatus.FAILED -> {
                                            Icon(Icons.Default.Cancel, contentDescription = null, tint = AuraCriticalRed, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("FAILED", color = AuraCriticalRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        StepStatus.AWAITING_APPROVAL -> {
                                            Text("APPROVAL REQUIRED", color = AuraWarningAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        else -> {
                                            Text("PENDING", color = AuraTextMuted, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }

                            if (step.resultSummary != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "↳ ${step.resultSummary.take(140)}",
                                    color = AuraSafeGreen,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(start = 18.dp)
                                )
                            }

                            if (step.error != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 18.dp)
                                ) {
                                    Text(
                                        text = "↳ Error: ${step.error}",
                                        color = AuraCriticalRed,
                                        fontSize = 10.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(
                                        onClick = { viewModel.retryActivePlanStep(step.stepIndex) },
                                        modifier = Modifier
                                            .height(24.dp)
                                            .testTag("retry_step_button_${step.stepIndex}"),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraCyanPrimary)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(10.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Retry", fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Projects & Tasks list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "PERSISTED PROJECTS (${projects.size})",
                    color = AuraTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (projects.isEmpty()) {
                item {
                    Text(
                        text = "No autonomous projects created yet. Tap '+ New Project' to define an autonomous objective.",
                        color = AuraTextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }

            items(projects, key = { it.id }) { project ->
                ProjectCard(project = project, tasks = tasks.filter { it.projectId == project.id })
            }
        }
    }
}

@Composable
fun ProjectCard(project: ProjectEntity, tasks: List<TaskEntity>) {
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
                Text(
                    text = project.title,
                    color = AuraTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = project.status,
                    color = AuraCyanPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (project.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = project.description, color = AuraTextSecondary, fontSize = 12.sp)
            }

            if (tasks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "Task Breakdown (${tasks.size}):", color = AuraTextMuted, fontSize = 11.sp)
                tasks.forEach { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (task.status == "COMPLETED") Icons.Default.CheckCircle else Icons.Default.Pending,
                            contentDescription = null,
                            tint = if (task.status == "COMPLETED") AuraSafeGreen else AuraWarningAmber,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(text = task.title, color = AuraTextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(text = task.status, color = AuraTextMuted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
