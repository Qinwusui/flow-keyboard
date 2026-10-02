package com.flowkeyboard.android.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.flowkeyboard.android.data.local.database.AppDatabase
import com.flowkeyboard.android.data.local.database.DictionaryDao
import com.flowkeyboard.android.data.local.database.DictionaryEntity
import com.flowkeyboard.android.data.repository.DictionaryRepository
import com.flowkeyboard.android.data.repository.RoomDictionaryRepository
import com.flowkeyboard.android.model.DictionaryItem
import com.flowkeyboard.android.model.TypingStat
import com.flowkeyboard.android.model.TypingSummary
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RoomDictionaryRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: DictionaryDao
    private lateinit var repository: DictionaryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).setDriver(BundledSQLiteDriver()).build()
        dao = database.dictionaryDao()
        repository = RoomDictionaryRepository(dao, database.statDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAll_persistsEntriesAndRepositoryMapsTheirWeights() = runTest {
        val entry = DictionaryEntity(
            id = 1L, word = "你好", pinyin = "nihao", initials = "nh", baseWeight = 250L, frequency = 3L,
        )
        dao.insertAll(listOf(entry))

        assertEquals(1, dao.count())
        assertEquals(listOf(entry), dao.searchPrefix("ni").first())
        assertEquals(listOf(DictionaryItem(1L, "你好", "nihao", 550L)), repository.search("ni").first())
    }

    @Test
    fun insertAll_ignoresDuplicateWordAndPinyinWithoutReplacingLearnedFrequency() = runTest {
        val original = DictionaryEntity(
            id = 1L, word = "你好", pinyin = "nihao", initials = "nh", baseWeight = 250L, frequency = 3L,
        )
        dao.insertAll(listOf(original))
        dao.insertAll(listOf(original.copy(id = 2L, frequency = 0L)))

        assertEquals(1, dao.count())
        assertEquals(listOf(original), dao.searchPrefix("ni").first())
    }

    @Test
    fun searchPrefix_matchesPinyinFromTheBeginning() = runTest {
        dao.insertAll(entries())

        assertEquals(listOf("你好", "你们"), dao.searchPrefix("ni").first().map { it.word })
        assertEquals(listOf("你好"), repository.search("niha").first().map { it.word })
        assertTrue(repository.search("ihao").first().isEmpty())
        assertTrue(repository.search("zzz").first().isEmpty())
    }

    @Test
    fun searchPrefix_matchesInitialsAsWellAsFullPinyin() = runTest {
        dao.insertAll(entries())

        assertEquals(listOf("你好"), dao.searchPrefix("nh").first().map { it.word })
        assertEquals(listOf("你们"), repository.search("nm").first().map { it.word })
    }

    @Test
    fun search_normalizesUppercaseAndApostrophes() = runTest {
        dao.insertAll(entries())

        assertEquals(listOf("你好"), repository.search("NI'HA").first().map { it.word })
    }

    @Test
    fun search_returnsEmptyForBlankInvalidOrExcessivelyLongPrefixes() = runTest {
        dao.insertAll(entries())

        listOf("", "'", " ", "ni1", "%", "_", "你", "a".repeat(65)).forEach { prefix ->
            assertTrue("Unexpected results for $prefix", repository.search(prefix).first().isEmpty())
        }
    }

    @Test
    fun searchPrefix_ranksByCombinedBaseWeightAndUsageFrequency() = runTest {
        dao.insertAll(
            listOf(
                DictionaryEntity(1L, "你好", "nihao", "nh", baseWeight = 250L),
                DictionaryEntity(2L, "你们", "nimen", "nm", baseWeight = 100L, frequency = 2L),
                DictionaryEntity(3L, "你", "ni", "n", baseWeight = 200L),
            ),
        )

        val suggestions = repository.search("ni").first()
        assertEquals(listOf("你们", "你好", "你"), suggestions.map { it.word })
        assertEquals(listOf(300L, 250L, 200L), suggestions.map { it.weight })
    }

    @Test
    fun searchPrefix_breaksWeightTiesByPinyinLengthThenId() = runTest {
        dao.insertAll(
            listOf(
                DictionaryEntity(3L, "你们", "nimen", "nm", baseWeight = 100L),
                DictionaryEntity(2L, "你好", "nihao", "nh", baseWeight = 100L),
                DictionaryEntity(1L, "你", "ni", "n", baseWeight = 100L),
            ),
        )

        assertEquals(listOf(1L, 2L, 3L), dao.searchPrefix("ni").first().map { it.id })
    }

    @Test
    fun searchPrefix_respectsCustomAndDefaultResultLimits() = runTest {
        dao.insertAll(
            (1L..25L).map { id ->
                DictionaryEntity(id, "词$id", "ni", "n", baseWeight = id)
            },
        )

        assertEquals(20, repository.search("ni").first().size)
        assertEquals(listOf(25L, 24L), dao.searchPrefix("ni", limit = 2).first().map { it.id })
        assertTrue(dao.searchPrefix("ni", limit = 0).first().isEmpty())
    }

    @Test
    fun learn_repeatedUsagePromotesSecondRankedWordToTopSuggestion() = runTest {
        dao.insertAll(entries())
        val before = repository.search("ni").first()
        assertEquals(listOf("你好", "你们"), before.map { it.word })
        val second = before[1]

        // learn is this repository's API for recording a selected candidate's usage.
        repository.learn(second)
        assertEquals(listOf("你好", "你们"), repository.search("ni").first().map { it.word })
        repeat(3) { repository.learn(second) }

        val persisted = dao.searchPrefix("ni").first()
        val after = repository.search("ni").first()
        assertEquals(4L, persisted.first { it.id == second.id }.frequency)
        assertEquals(0L, persisted.first { it.id == before[0].id }.frequency)
        assertEquals(second.id, after.first().id)
        assertEquals(second.weight + 400L, after.first().weight)
        assertEquals(before[0].weight, after[1].weight)
    }

    @Test
    fun learn_updatesAnExistingSearchFlow() = runTest {
        dao.insertAll(entries())
        val emissions = Channel<List<DictionaryItem>>(Channel.UNLIMITED)
        backgroundScope.launch { repository.search("ni").collect { emissions.send(it) } }
        val initial = emissions.receive()

        repeat(4) { repository.learn(initial[1]) }

        val updated = emissions.receiveAsFlow().first { suggestions ->
            suggestions.any { it.id == initial[1].id && it.weight == initial[1].weight + 400L }
        }
        assertEquals(initial[1].id, updated.first().id)
        assertTrue(updated.first().weight > initial.first().weight)
    }

    @Test
    fun learn_unknownIdLeavesExistingEntriesUnchanged() = runTest {
        dao.insertAll(entries())
        val before = dao.searchPrefix("ni").first()

        repository.learn(DictionaryItem(999L, "未知", "weizhi"))

        assertEquals(before, dao.searchPrefix("ni").first())
    }

    @Test
    fun learn_capsFrequencyAtDatabaseLimit() = runTest {
        dao.insertAll(
            listOf(DictionaryEntity(1L, "你", "ni", "n", frequency = 999_999_999L)),
        )
        val item = repository.search("ni").first().single()

        repeat(3) { repository.learn(item) }

        assertEquals(1_000_000_000L, dao.searchPrefix("ni").first().single().frequency)
    }

    @Test
    fun summary_isEmptyBeforeAnyTypingIsRecorded() = runTest {
        assertEquals(TypingSummary(), repository.summary.first())
        assertEquals(0.0, repository.summary.first().wordsPerMinute, 0.001)
    }

    @Test
    fun record_persistsTypingStatsAndUpdatesAggregateSummary() = runTest {
        repository.record(TypingStat(startedAtMillis = 1_000L, durationMillis = 30_000L, keystrokes = 20, committedCharacters = 15))
        repository.record(TypingStat(startedAtMillis = 2_000L, durationMillis = 30_000L, keystrokes = 30, committedCharacters = 25))

        val summary = repository.summary.first()
        assertEquals(TypingSummary(keystrokes = 50L, characters = 40L, durationMillis = 60_000L), summary)
        assertEquals(8.0, summary.wordsPerMinute, 0.001)
        assertEquals(listOf(2_000L, 1_000L), database.statDao().observeRecent().first().map { it.startedAtMillis })
    }

    @Test
    fun firstCreation_seedsAThousandUsableEntries() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val seeded = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setDriver(BundledSQLiteDriver()).addCallback(AppDatabase.seedCallback(context)).build()
        try {
            assertEquals(1000, seeded.dictionaryDao().count())
            assertTrue(seeded.dictionaryDao().searchPrefix("nihao").first().any { it.word == "你好" })
        } finally { seeded.close() }
    }

    private fun entries() = listOf(
        DictionaryEntity(1L, "你好", "nihao", "nh", baseWeight = 350L),
        DictionaryEntity(2L, "你们", "nimen", "nm", baseWeight = 100L),
        DictionaryEntity(3L, "好", "hao", "h", baseWeight = 500L),
    )
}
