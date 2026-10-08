package com.example.aura.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.aura.data.dao.ActivityLogDao
import com.example.aura.data.dao.GoalDao
import com.example.aura.data.dao.MemoryDao
import com.example.aura.data.dao.MessageDao
import com.example.aura.data.dao.PreferenceDao
import com.example.aura.data.dao.ProjectDao
import com.example.aura.data.dao.SessionDao
import com.example.aura.data.dao.TaskDao
import com.example.aura.data.entities.ActivityLogEntity
import com.example.aura.data.entities.GoalEntity
import com.example.aura.data.entities.MemoryEntity
import com.example.aura.data.entities.MessageEntity
import com.example.aura.data.entities.ProjectEntity
import com.example.aura.data.entities.SessionEntity
import com.example.aura.data.entities.TaskEntity
import com.example.aura.data.entities.UserPreferenceEntity

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        ProjectEntity::class,
        GoalEntity::class,
        TaskEntity::class,
        ActivityLogEntity::class,
        UserPreferenceEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun projectDao(): ProjectDao
    abstract fun goalDao(): GoalDao
    abstract fun taskDao(): TaskDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun preferenceDao(): PreferenceDao

    companion object {
        @Volatile
        private var INSTANCE: AuraDatabase? = null

        fun getInstance(context: Context): AuraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AuraDatabase::class.java,
                    "aura_local.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
