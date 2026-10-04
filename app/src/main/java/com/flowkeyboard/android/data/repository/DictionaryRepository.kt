package com.flowkeyboard.android.data.repository

import com.flowkeyboard.android.data.local.database.DictionaryDao
import com.flowkeyboard.android.data.local.database.DictionaryEntity
import com.flowkeyboard.android.data.local.database.StatDao
import com.flowkeyboard.android.data.local.database.TypingStatEntity
import com.flowkeyboard.android.engine.PinyinEngine
import com.flowkeyboard.android.model.DictionaryItem
import com.flowkeyboard.android.model.TypingStat
import com.flowkeyboard.android.model.TypingSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface DictionaryRepository {
    fun search(prefix: String): Flow<List<DictionaryItem>>
    fun predictNext(previousWord: String): Flow<List<DictionaryItem>>
    suspend fun learn(item: DictionaryItem)
    suspend fun record(stat: TypingStat)
    suspend fun recordTransition(fromWord: String, toWord: String)
    suspend fun learnCompound(word1: String, word2: String)
    val summary: Flow<TypingSummary>
}

class RoomDictionaryRepository(private val dictionary: DictionaryDao, private val stats: StatDao) : DictionaryRepository {

    private fun getTranspositions(input: String): List<String> {
        if (input.length in 2..5) {
            val list = mutableListOf<String>()
            for (i in 0 until input.length - 1) {
                val chars = input.toCharArray()
                val tmp = chars[i]
                chars[i] = chars[i + 1]
                chars[i + 1] = tmp
                val variant = String(chars)
                if (variant != input && variant !in list) {
                    list.add(variant)
                }
            }
            return list
        }
        return emptyList()
    }

    override fun search(prefix: String): Flow<List<DictionaryItem>> {
        val clean = prefix.lowercase(java.util.Locale.ROOT)
        val normalized = clean.replace("'", "")
        if (normalized.isEmpty() || normalized.length > 64 || normalized.any { it !in 'a'..'z' }) return flowOf(emptyList())
        val trimmed = clean.trimEnd('\'')
        val syllables = PinyinEngine.splitSyllables(trimmed)
        if (syllables != null) {
            val hasExplicitSeparator = clean.contains('\'')
            if (hasExplicitSeparator && syllables.size > 1) {
                val initialsPrefix = syllables.map { it[0] }.joinToString("")
                val pinyinPattern = normalized + "%"
                val firstSyllable = syllables[0]
                return combine(
                    dictionary.searchHybrid(initialsPrefix, pinyinPattern, 60),
                    dictionary.searchPrefix(firstSyllable, 40)
                ) { fullMatches, prefixMatches ->
                    val fullModels = fullMatches.map { it.toModel() }
                    val fullIds = fullModels.map { it.id }.toSet()
                    val prefixModels = prefixMatches.map { it.toModel() }.filter {
                        !fullIds.contains(it.id) && it.pinyin.lowercase(java.util.Locale.ROOT).replace("'", "") == firstSyllable
                    }
                    fullModels + prefixModels
                }
            }
            if (syllables.size > 1) {
                val firstSyllable = syllables[0]
                return combine(
                    dictionary.searchPrefix(normalized, 60),
                    dictionary.searchPrefix(firstSyllable, 40)
                ) { fullMatches, prefixMatches ->
                    val fullModels = fullMatches.map { it.toModel() }
                    val fullIds = fullModels.map { it.id }.toSet()
                    val prefixModels = prefixMatches.map { it.toModel() }.filter {
                        !fullIds.contains(it.id) && it.pinyin.lowercase(java.util.Locale.ROOT).replace("'", "") == firstSyllable
                    }
                    fullModels + prefixModels
                }
            }
            return dictionary.searchPrefix(normalized, 100).map { entries -> entries.map { it.toModel() } }
        }

        val hybridSplits = PinyinEngine.parseHybrid(normalized)
        val hybridFlows = hybridSplits.map { dictionary.searchHybrid(it.initialsPrefix, it.pinyinPattern, 40) }
        val transpositions = getTranspositions(normalized)
        val primaryFlow = dictionary.searchPrefix(normalized, 60)
        val secondaryFlows = transpositions.map { dictionary.searchPrefix(it, 40) }
        val allFlows = listOf(primaryFlow) + hybridFlows + secondaryFlows

        return combine(allFlows) { matchArrays ->
            val seenIds = mutableSetOf<Long>()
            val seenWords = mutableSetOf<String>()
            val entityList = mutableListOf<DictionaryEntity>()
            for (list in matchArrays) {
                for (entity in list) {
                    if (seenIds.add(entity.id) && seenWords.add(entity.word)) {
                        entityList.add(entity)
                    }
                }
            }
            entityList.sortWith(
                compareBy<DictionaryEntity> { entity ->
                    val ep = entity.pinyin.lowercase(java.util.Locale.ROOT).replace("'", "")
                    val ei = entity.initials.lowercase(java.util.Locale.ROOT)
                    when {
                        ep == normalized -> 1
                        ei == normalized -> 2
                        ep.startsWith(normalized) -> 3
                        ei.startsWith(normalized) -> 4
                        else -> 5
                    }
                }.thenByDescending { it.baseWeight + it.frequency * 100 }
                 .thenBy { it.pinyin.length }
                 .thenBy { it.id }
            )
            entityList.map { it.toModel() }
        }
    }

    override fun predictNext(previousWord: String): Flow<List<DictionaryItem>> {
        val trimmed = previousWord.trim()
        if (trimmed.isEmpty()) return flowOf(emptyList())
        val defaultFollowUps = listOf("的", "了", "在", "是", "我", "你", "他", "们", "好", "也", "很", "都", "吗", "吧", "呢", "，", "！")
        return combine(
            dictionary.getTransitions(trimmed, 15),
            dictionary.getBigramTransitions(trimmed, 15),
            dictionary.searchWordPrefix(trimmed, 30)
        ) { userTransitions, bigramTransitions, entities ->
            val result = mutableListOf<DictionaryItem>()
            val seen = mutableSetOf<String>()
            var transitionId = 500_000L

            for (word in userTransitions) {
                if (word.isNotEmpty() && seen.add(word)) {
                    result.add(DictionaryItem(transitionId++, word, "", 9999))
                }
            }

            for (word in bigramTransitions) {
                if (word.isNotEmpty() && seen.add(word)) {
                    result.add(DictionaryItem(transitionId++, word, "", 5000))
                }
            }

            for (entity in entities) {
                val suffix = entity.word.removePrefix(trimmed)
                if (suffix.isNotEmpty() && seen.add(suffix)) {
                    result.add(DictionaryItem(entity.id, suffix, entity.pinyin, entity.baseWeight))
                }
            }

            var fakeId = 1_000_000L
            for (w in defaultFollowUps) {
                if (seen.add(w)) {
                    result.add(DictionaryItem(fakeId++, w, "", 10))
                }
            }
            result
        }
    }

    override suspend fun learn(item: DictionaryItem) {
        if (item.id < 500_000L) {
            dictionary.incrementFrequency(item.id)
        } else {
            dictionary.incrementWordFrequency(item.word)
        }
    }

    override suspend fun recordTransition(fromWord: String, toWord: String) {
        val f = fromWord.trim()
        val t = toWord.trim()
        if (f.isNotEmpty() && t.isNotEmpty()) {
            dictionary.recordTransition(f, t)
        }
    }

    override suspend fun learnCompound(word1: String, word2: String) {
        val w1 = word1.trim()
        val w2 = word2.trim()
        if (w1.isEmpty() || w2.isEmpty() || (w1.length + w2.length) > 8) return
        val compound = w1 + w2
        val e1 = dictionary.findByWord(w1)
        val e2 = dictionary.findByWord(w2)
        val py = if (e1 != null && e2 != null) (e1.pinyin + e2.pinyin) else ""
        val init = if (e1 != null && e2 != null) (e1.initials + e2.initials) else ""
        if (py.isNotEmpty() && init.isNotEmpty()) {
            dictionary.insertOrUpdateWord(compound, py, init, 50, 1)
        }
    }

    override suspend fun record(stat: TypingStat) = stats.insert(TypingStatEntity.from(stat))
    override val summary = stats.observeSummary()
}
