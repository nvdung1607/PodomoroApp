package com.trustMePro.podomoroapp.core

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC") fun observeTasks(): Flow<List<TaskItem>>
    @Query("SELECT * FROM goals ORDER BY createdAt DESC") fun observeGoals(): Flow<List<GoalItem>>
    @Query("SELECT * FROM task_events") fun observeEvents(): Flow<List<TaskEvent>>
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC") fun observeSessions(): Flow<List<FocusSession>>
    @Query("SELECT * FROM intervals") fun observeIntervals(): Flow<List<FocusInterval>>
    @Query("SELECT * FROM timer WHERE id = 1") fun observeTimer(): Flow<TimerState?>
    @Query("SELECT * FROM tasks") suspend fun tasks(): List<TaskItem>
    @Query("SELECT * FROM goals") suspend fun goals(): List<GoalItem>
    @Query("SELECT * FROM task_events") suspend fun events(): List<TaskEvent>
    @Query("SELECT * FROM sessions") suspend fun sessions(): List<FocusSession>
    @Query("SELECT * FROM intervals") suspend fun intervals(): List<FocusInterval>
    @Query("SELECT * FROM timer WHERE id = 1") suspend fun timer(): TimerState?
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun task(id: String): TaskItem?
    @Query("SELECT * FROM goals WHERE id = :id") suspend fun goal(id: String): GoalItem?
    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun session(id: String): FocusSession?
    @Upsert suspend fun save(value: TaskItem)
    @Upsert suspend fun save(value: GoalItem)
    @Upsert suspend fun save(value: TaskEvent)
    @Upsert suspend fun save(value: FocusSession)
    @Upsert suspend fun save(value: FocusInterval)
    @Upsert suspend fun save(value: TimerState)
    @Query("UPDATE tasks SET goalId = NULL WHERE goalId = :id") suspend fun detachGoal(id: String)
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM task_events") suspend fun clearEvents()
    @Query("DELETE FROM sessions") suspend fun clearSessions()
    @Query("DELETE FROM intervals") suspend fun clearIntervals()
    @Query("DELETE FROM timer") suspend fun clearTimer()
}

@Database(entities = [TaskItem::class, GoalItem::class, TaskEvent::class, FocusSession::class, FocusInterval::class, TimerState::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() { abstract fun dao(): AppDao }
