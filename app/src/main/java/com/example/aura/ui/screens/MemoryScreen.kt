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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aura.data.entities.MemoryEntity
import com.example.aura.ui.AuraMainViewModel
import com.example.aura.ui.theme.AuraBgCard
import com.example.aura.ui.theme.AuraBgCardElevated
import com.example.aura.ui.theme.AuraBgDeep
import com.example.aura.ui.theme.AuraBorder
import com.example.aura.ui.theme.AuraCriticalRed
import com.example.aura.ui.theme.AuraCyanPrimary
import com.example.aura.ui.theme.AuraTextMuted
import com.example.aura.ui.theme.AuraTextPrimary
import com.example.aura.ui.theme.AuraTextSecondary
import com.example.aura.ui.theme.AuraVioletSecondary

@Composable
fun MemoryScreen(
    viewModel: AuraMainViewModel,
    modifier: Modifier = Modifier
) {
    val memories by viewModel.memories.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var memoryKey by remember { mutableStateOf("") }
    var memoryContent by remember { mutableStateOf("") }
    var memoryCategory by remember { mutableStateOf("PREFERENCE") }

    val filteredMemories = memories.filter {
        searchQuery.isBlank() || it.key.contains(searchQuery, true) || it.content.contains(searchQuery, true)
    }

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
                    text = "LONG-TERM MEMORY VAULT",
                    color = AuraCyanPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Local-First Semantic Knowledge Store",
                    color = AuraTextSecondary,
                    fontSize = 13.sp
                )
            }

            Button(
                onClick = { showAddDialog = !showAddDialog },
                colors = ButtonDefaults.buttonColors(containerColor = AuraCyanPrimary),
                modifier = Modifier.testTag("add_memory_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                Text("Add Memory", color = Color.Black, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search memories by key or content...", color = AuraTextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = AuraTextMuted) },
            modifier = Modifier.fillMaxWidth().testTag("memory_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AuraCyanPrimary,
                unfocusedBorderColor = AuraBorder,
                focusedTextColor = AuraTextPrimary,
                unfocusedTextColor = AuraTextPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (showAddDialog) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AuraBgCardElevated),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AuraCyanPrimary))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Store New Long-Term Memory", color = AuraTextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = memoryKey,
                        onValueChange = { memoryKey = it },
                        placeholder = { Text("Key / Subject (e.g., Preferred Tone, Timezone, Project Focus)") },
                        modifier = Modifier.fillMaxWidth().testTag("memory_key_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AuraTextPrimary,
                            unfocusedTextColor = AuraTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = memoryContent,
                        onValueChange = { memoryContent = it },
                        placeholder = { Text("Content / Rule / Fact") },
                        modifier = Modifier.fillMaxWidth().testTag("memory_content_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AuraTextPrimary,
                            unfocusedTextColor = AuraTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                if (memoryKey.isNotBlank() && memoryContent.isNotBlank()) {
                                    viewModel.addMemory(memoryKey, memoryContent, memoryCategory)
                                    memoryKey = ""
                                    memoryContent = ""
                                    showAddDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AuraCyanPrimary)
                        ) {
                            Text("Save into Memory Vault", color = Color.Black)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filteredMemories.isEmpty()) {
                item {
                    Text(
                        text = "No memories match query. Aura automatically draws from these entries during conversation context formulation.",
                        color = AuraTextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }

            items(filteredMemories, key = { it.id }) { memory ->
                MemoryCard(memory = memory, onDelete = { viewModel.deleteMemory(memory.id) })
            }
        }
    }
}

@Composable
fun MemoryCard(memory: MemoryEntity, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AuraBgCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AuraBorder))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = memory.category,
                        color = AuraVioletSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(AuraVioletSecondary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .border(0.5.dp, AuraVioletSecondary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text(
                        text = memory.key,
                        color = AuraCyanPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = memory.content,
                    color = AuraTextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Memory", tint = AuraCriticalRed.copy(alpha = 0.8f))
            }
        }
    }
}
