package com.trustMePro.podomoroapp.core

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow

private val Context.settingsData by preferencesDataStore("preferences")
interface SettingsProvider {
    val settings: Flow<AppSettings>
    suspend fun save(value: AppSettings)
}
class SettingsStore(context: Context) : SettingsProvider {
    private val store = context.settingsData
    private val focus = intPreferencesKey("focus")
    private val short = intPreferencesKey("short")
    private val long = intPreferencesKey("long")
    private val target = intPreferencesKey("target")
    private val dnd = booleanPreferencesKey("dnd")
    override val settings = store.data.map { AppSettings(it[focus] ?: 25, it[short] ?: 5, it[long] ?: 15, it[target] ?: 8, it[dnd] ?: true) }
    override suspend fun save(value: AppSettings) {
        require(listOf(value.focus, value.shortBreak, value.longBreak).all { it in 1..180 })
        require(value.dailyTarget in 1..100)
        store.edit { it[focus] = value.focus; it[short] = value.shortBreak; it[long] = value.longBreak; it[target] = value.dailyTarget; it[dnd] = value.useDnd }
    }
}
