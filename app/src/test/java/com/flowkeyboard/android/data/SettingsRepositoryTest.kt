package com.flowkeyboard.android.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.flowkeyboard.android.data.local.datastore.FlowPreferencesDataSource
import com.flowkeyboard.android.data.repository.DataStoreSettingsRepository
import com.flowkeyboard.android.data.repository.SettingsRepository
import com.flowkeyboard.android.model.BeltOrientation
import com.flowkeyboard.android.model.InputMode
import com.flowkeyboard.android.model.KeyOrder
import com.flowkeyboard.android.model.KeyboardSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun settings_emitsDefaultsForEmptyPreferences() = runTest {
        val repository = createRepository()

        assertEquals(KeyboardSettings(), repository.settings.first())
    }

    @Test
    fun update_persistsSpeedAndPreservesOtherSettings() = runTest {
        val repository = createRepository()

        repository.update { it.copy(speed = 125f) }

        assertEquals(KeyboardSettings(speed = 125f), repository.settings.first())
    }

    @Test
    fun update_normalizesSpeedOutsideSupportedRange() = runTest {
        val repository = createRepository()

        repository.update { it.copy(speed = -10f) }
        assertEquals(0f, repository.settings.first().speed, 0f)
        repository.update { it.copy(speed = 1_000f) }
        assertEquals(240f, repository.settings.first().speed, 0f)
    }

    @Test
    fun update_replacesNonFiniteSpeedWithDefault() = runTest {
        val repository = createRepository()

        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { speed ->
            repository.update { it.copy(speed = speed) }
            assertEquals(64f, repository.settings.first().speed, 0f)
        }
    }

    @Test
    fun update_persistsBothOrientations() = runTest {
        val repository = createRepository()

        repository.update { it.copy(orientation = BeltOrientation.VERTICAL) }
        assertEquals(KeyboardSettings(orientation = BeltOrientation.VERTICAL), repository.settings.first())
        repository.update { it.copy(orientation = BeltOrientation.HORIZONTAL) }
        assertEquals(KeyboardSettings(), repository.settings.first())
    }

    @Test
    fun update_togglesSound() = runTest {
        val repository = createRepository()

        repository.update { it.copy(soundEnabled = true) }
        assertEquals(KeyboardSettings(soundEnabled = true), repository.settings.first())
        repository.update { it.copy(soundEnabled = false) }
        assertEquals(KeyboardSettings(), repository.settings.first())
    }

    @Test
    fun update_togglesHaptics() = runTest {
        val repository = createRepository()

        repository.update { it.copy(hapticsEnabled = false) }
        assertEquals(KeyboardSettings(hapticsEnabled = false), repository.settings.first())
        repository.update { it.copy(hapticsEnabled = true) }
        assertEquals(KeyboardSettings(), repository.settings.first())
    }

    @Test
    fun update_persistsCruisingAndNormalizesDirection() = runTest {
        val repository = createRepository()

        repository.update { it.copy(cruising = false, direction = -10) }
        assertEquals(KeyboardSettings(cruising = false, direction = -1), repository.settings.first())
        repository.update { it.copy(cruising = true, direction = 0) }
        assertEquals(KeyboardSettings(), repository.settings.first())
    }

    @Test
    fun update_persistsKeyOrderAndInputMode() = runTest {
        val repository = createRepository()

        repository.update { it.copy(keyOrder = KeyOrder.ALPHABETICAL, inputMode = InputMode.PINYIN) }

        assertEquals(
            KeyboardSettings(keyOrder = KeyOrder.ALPHABETICAL, inputMode = InputMode.PINYIN),
            repository.settings.first(),
        )
    }

    @Test
    fun settings_activeCollectorReceivesUpdates() = runTest {
        val repository = createRepository()
        val emissions = Channel<KeyboardSettings>(Channel.UNLIMITED)
        backgroundScope.launch { repository.settings.collect { emissions.send(it) } }

        assertEquals(KeyboardSettings(), emissions.receive())
        repository.update { it.copy(speed = 120f) }
        assertEquals(KeyboardSettings(speed = 120f), emissions.receive())
        repository.update { it.copy(soundEnabled = true) }
        assertEquals(KeyboardSettings(speed = 120f, soundEnabled = true), emissions.receive())
    }

    @Test
    fun update_usesLatestSettingsForSequentialEdits() = runTest {
        val repository = createRepository()

        repository.update { it.copy(speed = 100f) }
        repository.update { it.copy(speed = it.speed + 25f, orientation = BeltOrientation.VERTICAL) }
        repository.update { it.copy(soundEnabled = true, hapticsEnabled = false) }

        assertEquals(
            KeyboardSettings(speed = 125f, orientation = BeltOrientation.VERTICAL, soundEnabled = true, hapticsEnabled = false),
            repository.settings.first(),
        )
    }

    @Test
    fun update_concurrentEditsPreserveEachChangedSetting() = runTest {
        val repository = createRepository()

        coroutineScope {
            launch { repository.update { it.copy(speed = 150f) } }
            launch { repository.update { it.copy(orientation = BeltOrientation.VERTICAL) } }
            launch { repository.update { it.copy(soundEnabled = true) } }
            launch { repository.update { it.copy(hapticsEnabled = false) } }
        }

        assertEquals(
            KeyboardSettings(speed = 150f, orientation = BeltOrientation.VERTICAL, soundEnabled = true, hapticsEnabled = false),
            repository.settings.first(),
        )
    }

    @Test
    fun settings_surviveClosingAndReopeningDataStore() = runTest {
        val file = temporaryFolder.newFile("persisted.preferences_pb")
        val firstStoreJob = SupervisorJob(backgroundScope.coroutineContext[Job])
        val firstStoreScope = CoroutineScope(backgroundScope.coroutineContext + firstStoreJob)
        val firstRepository = createRepository(file, firstStoreScope)
        val expected = KeyboardSettings(
            speed = 180f,
            direction = -1,
            cruising = false,
            orientation = BeltOrientation.VERTICAL,
            keyOrder = KeyOrder.ALPHABETICAL,
            inputMode = InputMode.PINYIN,
            soundEnabled = true,
            hapticsEnabled = false,
        )
        firstRepository.update { expected }
        assertEquals(expected, firstRepository.settings.first())

        firstStoreJob.cancelAndJoin()
        val reopenedRepository = createRepository(file)

        assertEquals(expected, reopenedRepository.settings.first())
    }

    private fun TestScope.createRepository(
        file: File = temporaryFolder.newFile("settings.preferences_pb"),
        scope: CoroutineScope = backgroundScope,
    ): SettingsRepository {
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        return DataStoreSettingsRepository(FlowPreferencesDataSource(store))
    }
}
