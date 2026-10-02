package com.flowkeyboard.android.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import com.flowkeyboard.android.data.local.database.AppDatabase
import com.flowkeyboard.android.data.local.datastore.FlowPreferencesDataSource
import com.flowkeyboard.android.data.repository.*
import com.flowkeyboard.android.engine.FeedbackEngine
import com.flowkeyboard.android.engine.PinyinEngine
import com.flowkeyboard.android.ui.keyboard.FlowKeyboardViewModel
import com.flowkeyboard.android.ui.main.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

private val Context.flowDataStore by preferencesDataStore(name = "flow_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })

val appModule = module {
    single { AppDatabase.create(androidContext()) }
    single { get<AppDatabase>().dictionaryDao() }
    single { get<AppDatabase>().statDao() }
    single { FlowPreferencesDataSource(androidContext().flowDataStore) }
    single<SettingsRepository> { DataStoreSettingsRepository(get()) }
    single<DictionaryRepository> { RoomDictionaryRepository(get(), get()) }
    single { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    factory { PinyinEngine() }
    factory { FeedbackEngine(androidContext()) }
    viewModel { FlowKeyboardViewModel(get(), get(), get(), get(), get()) }
    viewModel { MainViewModel(get(), get()) }
}
