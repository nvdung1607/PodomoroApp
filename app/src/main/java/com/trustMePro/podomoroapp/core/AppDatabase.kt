package com.trustMePro.podomoroapp.core

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC") fun observeTasks(): Flow<List<TaskItem>>
    @Query("SELECT * FROM goals ORDER BY createdAt DESC") fun observeGoals(): Flow<List<GoalItem>>
    @Query("SELECT * FROM task_events") fun observeEvents(): Flow<List<TaskEvent>>
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC") fun observeSessions(): Flow<List<FocusSession>>
    @Query("SELECT * FROM intervals") fun observeIntervals(): Flow<List<FocusInterval>>
    @Query("SELECT * FROM checklists ORDER BY `order` ASC, createdAt ASC") fun observeChecklists(): Flow<List<ChecklistItem>>
    @Query("SELECT * FROM timer WHERE id = 1") fun observeTimer(): Flow<TimerState?>
    @Query("SELECT * FROM tasks") suspend fun tasks(): List<TaskItem>
    @Query("SELECT * FROM goals") suspend fun goals(): List<GoalItem>
    @Query("SELECT * FROM task_events") suspend fun events(): List<TaskEvent>
    @Query("SELECT * FROM sessions") suspend fun sessions(): List<FocusSession>
    @Query("SELECT * FROM intervals") suspend fun intervals(): List<FocusInterval>
    @Query("SELECT * FROM checklists") suspend fun allChecklists(): List<ChecklistItem>
    @Query("SELECT * FROM checklists WHERE taskId = :taskId ORDER BY `order` ASC, createdAt ASC") suspend fun checklists(taskId: String): List<ChecklistItem>
    @Query("SELECT * FROM timer WHERE id = 1") suspend fun timer(): TimerState?
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun task(id: String): TaskItem?
    @Query("SELECT * FROM goals WHERE id = :id") suspend fun goal(id: String): GoalItem?
    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun session(id: String): FocusSession?
    @Query("SELECT * FROM checklists WHERE id = :id") suspend fun checklist(id: String): ChecklistItem?
    @Upsert suspend fun save(value: TaskItem)
    @Upsert suspend fun save(value: GoalItem)
    @Upsert suspend fun save(value: TaskEvent)
    @Upsert suspend fun save(value: FocusSession)
    @Upsert suspend fun save(value: FocusInterval)
    @Upsert suspend fun save(value: ChecklistItem)
    @Upsert suspend fun save(value: TimerState)
    @Query("UPDATE tasks SET goalId = NULL WHERE goalId = :id") suspend fun detachGoal(id: String)
    @Query("DELETE FROM checklists WHERE id = :id") suspend fun deleteChecklist(id: String)
    @Query("DELETE FROM checklists WHERE taskId = :taskId") suspend fun deleteChecklistsForTask(taskId: String)
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM task_events") suspend fun clearEvents()
    @Query("DELETE FROM sessions") suspend fun clearSessions()
    @Query("DELETE FROM intervals") suspend fun clearIntervals()
    @Query("DELETE FROM checklists") suspend fun clearChecklists()
    @Query("DELETE FROM timer") suspend fun clearTimer()
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `checklists` (" +
            "`id` TEXT NOT NULL, " +
            "`taskId` TEXT NOT NULL, " +
            "`title` TEXT NOT NULL, " +
            "`isDone` INTEGER NOT NULL, " +
            "`order` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, " +
            "PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklists_taskId` ON `checklists` (`taskId`)")
    }
}

@Database(
    entities = [TaskItem::class, GoalItem::class, TaskEvent::class, FocusSession::class, FocusInterval::class, TimerState::class, ChecklistItem::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() { abstract fun dao(): AppDao }
