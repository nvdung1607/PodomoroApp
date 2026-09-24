package com.trustMePro.podomoroapp.core

import android.app.Application
import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    val clock = AndroidTime(context)
    val database = Room.databaseBuilder(context, AppDatabase::class.java, "focus.db")
        .addMigrations(MIGRATION_1_2)
        .build()
    val settings = SettingsStore(context)
    val scheduler = AndroidScheduler(context)
    val dnd = FocusDnd(context)
    val authService = AuthService()
    val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val syncEngine = SyncEngine(authService, database, syncScope, settings)
    val repository = Repository(database, settings, clock, scheduler, dnd::apply, syncEngine)
}
class FocusApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val container by lazy { AppContainer(this) }
}
