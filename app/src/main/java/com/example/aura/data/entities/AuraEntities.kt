package com.example.aura.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val mode: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: String,
    val content: String,
    val toolCallsJson: String? = null,
    val toolResultJson: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "long_term_memories")
data class MemoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val key: String,
    val content: String,
    val category: String, // FACT, PREFERENCE, CONTEXT, INSTRUCTION
    val confidence: Float = 1.0f,
    val source: String = "user_input",
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val status: String = "PLANNING", // PLANNING, IN_PROGRESS, COMPLETED, PAUSED
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val title: String,
    val description: String,
    val isAchieved: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val goalId: String? = null,
    val title: String,
    val description: String,
    val status: String = "PENDING", // PENDING, IN_PROGRESS, COMPLETED, FAILED, BLOCKED
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val source: String,
    val description: String,
    val metadataJson: String = "{}",
    val riskLevel: String = "SAFE"
)

@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
    val category: String = "general",
    val updatedAt: Long = System.currentTimeMillis()
)
