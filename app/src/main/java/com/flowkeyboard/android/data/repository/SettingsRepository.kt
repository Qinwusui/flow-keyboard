package com.flowkeyboard.android.data.repository

import com.flowkeyboard.android.data.local.datastore.FlowPreferencesDataSource
import com.flowkeyboard.android.model.KeyboardSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<KeyboardSettings>
    suspend fun update(transform: (KeyboardSettings) -> KeyboardSettings)
}

class DataStoreSettingsRepository(private val source: FlowPreferencesDataSource) : SettingsRepository {
    override val settings = source.settings
    override suspend fun update(transform: (KeyboardSettings) -> KeyboardSettings) = source.update(transform)
}
