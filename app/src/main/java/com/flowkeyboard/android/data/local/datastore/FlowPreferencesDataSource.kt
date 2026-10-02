package com.flowkeyboard.android.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flowkeyboard.android.model.*
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class FlowPreferencesDataSource(private val store: DataStore<Preferences>) {
    private object Keys {
        val speed = floatPreferencesKey("speed")
        val direction = intPreferencesKey("direction")
        val cruising = booleanPreferencesKey("cruising")
        val orientation = stringPreferencesKey("orientation")
        val order = stringPreferencesKey("key_order")
        val mode = stringPreferencesKey("input_mode")
        val sound = booleanPreferencesKey("sound")
        val haptics = booleanPreferencesKey("haptics")
    }
    val settings: Flow<KeyboardSettings> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { p ->
        KeyboardSettings(
            speed = p[Keys.speed] ?: 64f, direction = p[Keys.direction] ?: 1,
            cruising = p[Keys.cruising] ?: true,
            orientation = enumValue(p[Keys.orientation], BeltOrientation.HORIZONTAL),
            keyOrder = enumValue(p[Keys.order], KeyOrder.QWERTY),
            inputMode = enumValue(p[Keys.mode], InputMode.ENGLISH),
            soundEnabled = p[Keys.sound] ?: false, hapticsEnabled = p[Keys.haptics] ?: true,
        ).normalized()
    }

    // Read-modify-write occurs in one DataStore transaction, preserving concurrent setting edits.
    suspend fun update(transform: (KeyboardSettings) -> KeyboardSettings) {
        store.edit { p ->
            val current = KeyboardSettings(p[Keys.speed] ?: 64f, p[Keys.direction] ?: 1,
                p[Keys.cruising] ?: true, enumValue(p[Keys.orientation], BeltOrientation.HORIZONTAL),
                enumValue(p[Keys.order], KeyOrder.QWERTY), enumValue(p[Keys.mode], InputMode.ENGLISH),
                p[Keys.sound] ?: false, p[Keys.haptics] ?: true)
            val value = transform(current).normalized()
            p[Keys.speed] = value.speed; p[Keys.direction] = value.direction
            p[Keys.cruising] = value.cruising; p[Keys.orientation] = value.orientation.name
            p[Keys.order] = value.keyOrder.name; p[Keys.mode] = value.inputMode.name
            p[Keys.sound] = value.soundEnabled; p[Keys.haptics] = value.hapticsEnabled
        }
    }
    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
