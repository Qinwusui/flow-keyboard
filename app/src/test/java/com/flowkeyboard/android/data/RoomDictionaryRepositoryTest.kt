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
import org.junit.Assert.assertFalse
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
                DictionaryEntity(3L, "你的", "nide", "nd", baseWeight = 200L),
            ),
        )

        val suggestions = repository.search("ni").first()
        assertEquals(listOf("你们", "你好", "你的"), suggestions.map { it.word })
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
            (1L..105L).map { id ->
                DictionaryEntity(id, "词$id", "ni", "n", baseWeight = id)
            },
        )

        assertEquals(100, repository.search("ni").first().size)
        assertEquals(listOf(105L, 104L), dao.searchPrefix("ni", limit = 2).first().map { it.id })
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
    fun search_multiSyllableCombinesFullWordAndFirstSyllableCandidates() = runTest {
        dao.insertAll(
            listOf(
                DictionaryEntity(10L, "键盘", "jianpan", "jp", baseWeight = 400L),
                DictionaryEntity(11L, "键", "jian", "j", baseWeight = 300L),
                DictionaryEntity(12L, "见", "jian", "j", baseWeight = 250L),
                DictionaryEntity(13L, "建议", "jianyi", "jy", baseWeight = 200L),
                DictionaryEntity(14L, "盘", "pan", "p", baseWeight = 300L),
            ),
        )

        val results = repository.search("jianpan").first().map { it.word }
        assertEquals(listOf("键盘", "键", "见"), results)
    }

    @Test
    fun predictNext_returnsSuffixMatchesAndDefaultFollowUps() = runTest {
        dao.insertAll(
            listOf(
                DictionaryEntity(100L, "键盘输入", "jianpansr", "jpsr", baseWeight = 400L),
                DictionaryEntity(101L, "键盘鼠标", "jianpansb", "jpsb", baseWeight = 300L),
                DictionaryEntity(102L, "键盘", "jianpan", "jp", baseWeight = 200L),
            ),
        )

        val predictions = repository.predictNext("键盘").first().map { it.word }
        assertTrue(predictions.contains("输入"))
        assertTrue(predictions.contains("鼠标"))
        assertFalse(predictions.contains("键盘"))
        assertTrue(predictions.contains("的"))
    }

    @Test
    fun search_matchesTranspositionInitials() = runTest {
        dao.insertAll(
            listOf(
                DictionaryEntity(10L, "我感觉", "woganjue", "wgj", baseWeight = 50L),
                DictionaryEntity(11L, "外交官", "waijiaoguan", "wjg", baseWeight = 10L),
            )
        )
        val results = repository.search("wjg").first().map { it.word }
        assertTrue(results.contains("外交官"))
        assertTrue(results.contains("我感觉"))
    }

    @Test
    fun recordTransition_prioritizesUserLearnedNextWordInPrediction() = runTest {
        repository.recordTransition("我", "感觉")
        val predictions = repository.predictNext("我").first()
        assertEquals("感觉", predictions.first().word)
    }

    @Test
    fun learnCompound_createsUserCompoundPhrase() = runTest {
        dao.insertAll(
            listOf(
                DictionaryEntity(1L, "我", "wo", "w", baseWeight = 50L),
                DictionaryEntity(2L, "感觉", "ganjue", "gj", baseWeight = 50L),
            )
        )
        repository.learnCompound("我", "感觉")
        val found = dao.findByWord("我感觉")
        assertTrue(found != null)
        assertEquals("woganjue", found?.pinyin)
        assertEquals("wgj", found?.initials)
    }

    @Test
    fun firstCreation_seedsAThousandUsableEntries() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val seeded = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setDriver(BundledSQLiteDriver()).addCallback(AppDatabase.seedCallback(context)).build()
        try {
            assertTrue(seeded.dictionaryDao().count() >= 50000)
            assertTrue(seeded.dictionaryDao().searchPrefix("nihao").first().any { it.word == "你好" })
            assertTrue(seeded.dictionaryDao().searchPrefix("jianpan").first().any { it.word == "键盘" })
            assertTrue(seeded.dictionaryDao().searchPrefix("pan").first().any { it.word == "盘" })
            assertTrue(seeded.dictionaryDao().searchPrefix("jian").first().any { it.word == "键" })
            assertTrue(seeded.dictionaryDao().searchPrefix("dyz").first().any { it.word == "多音字" })
            assertTrue(seeded.dictionaryDao().searchPrefix("hang").first().any { it.word == "行" })
            assertTrue(seeded.dictionaryDao().searchPrefix("chang").first().any { it.word == "长" })
            assertTrue(seeded.dictionaryDao().searchPrefix("smdx").first().any { it.word == "什么东西" })
        } finally { seeded.close() }
    }

    @Test
    fun search_smdx_returnsShenMeDongXi() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val seeded = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setDriver(BundledSQLiteDriver()).addCallback(AppDatabase.seedCallback(context)).build()
        val repo = RoomDictionaryRepository(seeded.dictionaryDao(), seeded.statDao())
        try {
            val results = repo.search("smdx").first().map { it.word }
            assertTrue("Expected '什么东西' in $results", results.contains("什么东西"))
            assertEquals("什么东西", results.first())
        } finally { seeded.close() }
    }

    @Test
    fun predictNext_usesPreSeededBigramTransitions() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val seeded = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setDriver(BundledSQLiteDriver()).addCallback(AppDatabase.seedCallback(context)).build()
        val repo = RoomDictionaryRepository(seeded.dictionaryDao(), seeded.statDao())
        try {
            val predictions = repo.predictNext("人工").first().map { it.word }
            assertTrue("Expected '智能' in $predictions", predictions.contains("智能"))

            val inputPredictions = repo.predictNext("输入").first().map { it.word }
            assertTrue("Expected '法' in $inputPredictions", inputPredictions.contains("法"))
        } finally { seeded.close() }
    }

    @Test
    fun search_idiomInitials_findsFourCharacterIdioms() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val seeded = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setDriver(BundledSQLiteDriver()).addCallback(AppDatabase.seedCallback(context)).build()
        val repo = RoomDictionaryRepository(seeded.dictionaryDao(), seeded.statDao())
        try {
            val results = repo.search("hjbf").first().map { it.word }
            assertTrue("Expected '厚积薄发' in $results", results.contains("厚积薄发"))
        } finally { seeded.close() }
    }

    @Test
    fun search_hybridAbbreviation_findsTargetWord() = runTest {
        dao.insertAll(
            listOf(
                DictionaryEntity(10L, "多音字", "duoyinzi", "dyz", baseWeight = 50L),
                DictionaryEntity(11L, "北京", "beijing", "bj", baseWeight = 50L),
            )
        )
        val dyziResults = repository.search("dyzi").first().map { it.word }
        assertTrue("Expected '多音字' in $dyziResults", dyziResults.contains("多音字"))

        val bjingResults = repository.search("bjing").first().map { it.word }
        assertTrue("Expected '北京' in $bjingResults", bjingResults.contains("北京"))
    }

    @Test
    fun search_polyphones_findsCharactersUnderMultiplePronunciations() = runTest {
        dao.insertAll(
            listOf(
                DictionaryEntity(20L, "行", "xing", "x", baseWeight = 30L),
                DictionaryEntity(21L, "行", "hang", "h", baseWeight = 35L),
                DictionaryEntity(22L, "长", "zhang", "z", baseWeight = 40L),
                DictionaryEntity(23L, "长", "chang", "c", baseWeight = 38L),
            )
        )
        val hangResults = repository.search("hang").first().map { it.word }
        assertTrue(hangResults.contains("行"))

        val xingResults = repository.search("xing").first().map { it.word }
        assertTrue(xingResults.contains("行"))

        val changResults = repository.search("chang").first().map { it.word }
        assertTrue(changResults.contains("长"))

        val zhangResults = repository.search("zhang").first().map { it.word }
        assertTrue(zhangResults.contains("长"))
    }

    @Test
    fun search_wo_prioritizesWoOverWomen() = runTest {
        dao.insertAll(
            listOf(
                DictionaryEntity(1L, "我们", "women", "wm", baseWeight = 50L),
                DictionaryEntity(2L, "我", "wo", "w", baseWeight = 48L),
            )
        )
        val results = repository.search("wo").first().map { it.word }
        assertEquals("我", results.first())
    }

    @Test
    fun search_seededDatabase_typingWoDirectlyShowsWoFirst() = runTest {
        val seeded = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).setDriver(BundledSQLiteDriver()).addCallback(AppDatabase.seedCallback(ApplicationProvider.getApplicationContext())).build()
        try {
            val repo = RoomDictionaryRepository(seeded.dictionaryDao(), seeded.statDao())
            val woResults = repo.search("wo").first().map { it.word }
            assertEquals("我", woResults.first())

            val womenResults = repo.search("women").first().map { it.word }
            assertEquals("我们", womenResults.first())

            val wmResults = repo.search("wm").first().map { it.word }
            assertEquals("我们", wmResults.first())
        } finally {
            seeded.close()
        }
    }

    @Test
    fun search_activeSegmentation_xian_prioritizesXiAnOverSingleSyllables() = runTest {
        dao.insertAll(
            listOf(
                DictionaryEntity(1L, "先", "xian", "x", baseWeight = 24L),
                DictionaryEntity(2L, "西安", "xian", "xa", baseWeight = 10L),
            )
        )
        val segmentedResults = repository.search("xi'an").first().map { it.word }
        assertEquals("西安", segmentedResults.first())

        val unsegmentedResults = repository.search("xian").first().map { it.word }
        assertEquals("先", unsegmentedResults.first())
    }

    @Test
    fun search_activeSegmentation_fangan_disambiguatesFangAnAndFanGan() = runTest {
        dao.insertAll(
            listOf(
                DictionaryEntity(1L, "方案", "fangan", "fa", baseWeight = 20L),
                DictionaryEntity(2L, "反感", "fangan", "fg", baseWeight = 25L),
            )
        )
        val fangAnResults = repository.search("fang'an").first().map { it.word }
        assertEquals("方案", fangAnResults.first())

        val fanGanResults = repository.search("fan'gan").first().map { it.word }
        assertEquals("反感", fanGanResults.first())
    }

    private fun entries() = listOf(
        DictionaryEntity(1L, "你好", "nihao", "nh", baseWeight = 350L),
        DictionaryEntity(2L, "你们", "nimen", "nm", baseWeight = 100L),
        DictionaryEntity(3L, "好", "hao", "h", baseWeight = 500L),
    )
}
