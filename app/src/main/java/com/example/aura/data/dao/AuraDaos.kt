package com.example.aura.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.aura.data.entities.ActivityLogEntity
import com.example.aura.data.entities.GoalEntity
import com.example.aura.data.entities.MemoryEntity
import com.example.aura.data.entities.MessageEntity
import com.example.aura.data.entities.ProjectEntity
import com.example.aura.data.entities.SessionEntity
import com.example.aura.data.entities.TaskEntity
import com.example.aura.data.entities.UserPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSessionById(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSessionSync(sessionId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM long_term_memories ORDER BY lastAccessedAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM long_term_memories WHERE key LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%'")
    fun searchMemories(query: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM long_term_memories ORDER BY lastAccessedAt DESC")
    suspend fun getAllMemoriesSync(): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity)

    @Query("DELETE FROM long_term_memories WHERE id = :id")
    suspend fun deleteMemoryById(id: String)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE projectId = :projectId ORDER BY createdAt ASC")
    fun getGoalsForProject(projectId: String): Flow<List<GoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEntity)

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoalById(id: String)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun getTasksForProject(projectId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 200): Flow<List<ActivityLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActivityLogEntity)

    @Query("DELETE FROM activity_logs")
    suspend fun clearLogs()
}

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM user_preferences")
    fun getAllPreferences(): Flow<List<UserPreferenceEntity>>

    @Query("SELECT * FROM user_preferences WHERE key = :key")
    suspend fun getPreference(key: String): UserPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreference(pref: UserPreferenceEntity)
}
