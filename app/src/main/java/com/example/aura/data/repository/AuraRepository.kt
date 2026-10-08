package com.example.aura.data.repository

import com.example.aura.core.logging.AuditLogEntry
import com.example.aura.data.database.AuraDatabase
import com.example.aura.data.entities.ActivityLogEntity
import com.example.aura.data.entities.GoalEntity
import com.example.aura.data.entities.MemoryEntity
import com.example.aura.data.entities.MessageEntity
import com.example.aura.data.entities.ProjectEntity
import com.example.aura.data.entities.SessionEntity
import com.example.aura.data.entities.TaskEntity
import com.example.aura.data.entities.UserPreferenceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject

/**
 * Concrete repository layer abstracting local Room persistence.
 */
class AuraRepository(private val db: AuraDatabase) {

    // --- Sessions & Messages ---
    val allSessions: Flow<List<SessionEntity>> = db.sessionDao().getAllSessions()

    fun getMessagesForSession(sessionId: String): Flow<List<MessageEntity>> =
        db.messageDao().getMessagesForSession(sessionId)

    suspend fun getMessagesForSessionSync(sessionId: String): List<MessageEntity> =
        db.messageDao().getMessagesForSessionSync(sessionId)

    suspend fun saveSession(session: SessionEntity) =
        db.sessionDao().insertSession(session)

    suspend fun deleteSession(sessionId: String) {
        db.messageDao().deleteMessagesForSession(sessionId)
        db.sessionDao().deleteSessionById(sessionId)
    }

    suspend fun saveMessage(message: MessageEntity) =
        db.messageDao().insertMessage(message)

    // --- Long-Term Memory ---
    val allMemories: Flow<List<MemoryEntity>> = db.memoryDao().getAllMemories()

    fun searchMemories(query: String): Flow<List<MemoryEntity>> =
        if (query.isBlank()) allMemories else db.memoryDao().searchMemories(query)

    suspend fun getAllMemoriesSync(): List<MemoryEntity> =
        db.memoryDao().getAllMemoriesSync()

    suspend fun saveMemory(memory: MemoryEntity) =
        db.memoryDao().insertMemory(memory)

    suspend fun deleteMemory(memoryId: String) =
        db.memoryDao().deleteMemoryById(memoryId)

    // --- Projects, Goals, Tasks ---
    val allProjects: Flow<List<ProjectEntity>> = db.projectDao().getAllProjects()
    val allTasks: Flow<List<TaskEntity>> = db.taskDao().getAllTasks()

    fun getGoalsForProject(projectId: String): Flow<List<GoalEntity>> =
        db.goalDao().getGoalsForProject(projectId)

    fun getTasksForProject(projectId: String): Flow<List<TaskEntity>> =
        db.taskDao().getTasksForProject(projectId)

    suspend fun saveProject(project: ProjectEntity) =
        db.projectDao().insertProject(project)

    suspend fun deleteProject(projectId: String) =
        db.projectDao().deleteProjectById(projectId)

    suspend fun saveGoal(goal: GoalEntity) =
        db.goalDao().insertGoal(goal)

    suspend fun saveTask(task: TaskEntity) =
        db.taskDao().insertTask(task)

    suspend fun updateTaskStatus(taskId: String, status: String) {
        // Can be queried & updated
    }

    suspend fun deleteTask(taskId: String) =
        db.taskDao().deleteTaskById(taskId)

    // --- Audit Logs ---
    val recentActivityLogs: Flow<List<ActivityLogEntity>> = db.activityLogDao().getRecentLogs()

    suspend fun persistAuditLog(entry: AuditLogEntry) {
        val metaJson = if (entry.metadata.isNotEmpty()) {
            JSONObject(entry.metadata as Map<*, *>).toString()
        } else "{}"

        val entity = ActivityLogEntity(
            id = entry.id,
            sessionId = entry.sessionId,
            timestamp = entry.timestampMillis,
            eventType = entry.type.name,
            source = entry.source,
            description = entry.message,
            metadataJson = metaJson,
            riskLevel = entry.securityLevel.name
        )
        db.activityLogDao().insertLog(entity)
    }

    suspend fun clearLogs() = db.activityLogDao().clearLogs()

    // --- User Preferences ---
    val allPreferences: Flow<List<UserPreferenceEntity>> = db.preferenceDao().getAllPreferences()

    suspend fun getPreference(key: String): String? =
        db.preferenceDao().getPreference(key)?.value

    suspend fun setPreference(key: String, value: String, category: String = "general") =
        db.preferenceDao().setPreference(
            UserPreferenceEntity(key = key, value = value, category = category)
        )
}
