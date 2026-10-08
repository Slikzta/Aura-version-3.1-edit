package com.example.aura.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.aura.ui.components.AgentStatusBadge
import com.example.aura.ui.screens.AuditAndProvidersScreen
import com.example.aura.ui.screens.AutonomousScreen
import com.example.aura.ui.screens.ConsoleScreen
import com.example.aura.ui.screens.MemoryScreen
import com.example.aura.ui.screens.ToolsScreen
import com.example.aura.ui.theme.AuraBgCard
import com.example.aura.ui.theme.AuraBgDeep
import com.example.aura.ui.theme.AuraCyanPrimary
import com.example.aura.ui.theme.AuraTextMuted
import com.example.aura.ui.theme.AuraTextPrimary
import com.example.aura.ui.theme.AuraVioletSecondary

enum class AuraNavDestination(val label: String, val icon: ImageVector) {
    CONSOLE("Console", Icons.Default.Chat),
    AUTONOMOUS("Autonomous", Icons.Default.AccountTree),
    MEMORY("Memory", Icons.Default.Psychology),
    TOOLS("Tools", Icons.Default.Build),
    AUDIT("Audit & Config", Icons.Default.Shield)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraApp(
    viewModel: AuraMainViewModel,
    modifier: Modifier = Modifier
) {
    var currentDestination by rememberSaveable { mutableStateOf(AuraNavDestination.CONSOLE) }
    val agentState by viewModel.agentState.collectAsStateWithLifecycle()

    BackHandler(enabled = currentDestination != AuraNavDestination.CONSOLE) {
        currentDestination = AuraNavDestination.CONSOLE
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AuraBgDeep,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_aura_logo),
                            contentDescription = "Aura Agent Logo",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "AURA",
                                color = AuraTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "Autonomous AI Agent Core",
                                color = AuraCyanPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    AgentStatusBadge(state = agentState, modifier = Modifier.padding(end = 12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraBgCard)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = AuraBgCard,
                contentColor = AuraTextPrimary
            ) {
                AuraNavDestination.entries.forEach { dest ->
                    val isSelected = currentDestination == dest
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentDestination = dest },
                        icon = {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = dest.label,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = dest.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AuraCyanPrimary,
                            selectedTextColor = AuraCyanPrimary,
                            unselectedIconColor = AuraTextMuted,
                            unselectedTextColor = AuraTextMuted,
                            indicatorColor = AuraCyanPrimary.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_tab_${dest.name}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentDestination) {
                AuraNavDestination.CONSOLE -> ConsoleScreen(viewModel = viewModel)
                AuraNavDestination.AUTONOMOUS -> AutonomousScreen(viewModel = viewModel)
                AuraNavDestination.MEMORY -> MemoryScreen(viewModel = viewModel)
                AuraNavDestination.TOOLS -> ToolsScreen(viewModel = viewModel)
                AuraNavDestination.AUDIT -> AuditAndProvidersScreen(viewModel = viewModel)
            }
        }
    }
}
