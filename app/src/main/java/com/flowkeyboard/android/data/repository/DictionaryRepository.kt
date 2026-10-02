package com.flowkeyboard.android.data.repository

import com.flowkeyboard.android.data.local.database.DictionaryDao
import com.flowkeyboard.android.data.local.database.StatDao
import com.flowkeyboard.android.data.local.database.TypingStatEntity
import com.flowkeyboard.android.model.DictionaryItem
import com.flowkeyboard.android.model.TypingStat
import com.flowkeyboard.android.model.TypingSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface DictionaryRepository {
    fun search(prefix: String): Flow<List<DictionaryItem>>
    suspend fun learn(item: DictionaryItem)
    suspend fun record(stat: TypingStat)
    val summary: Flow<TypingSummary>
}

class RoomDictionaryRepository(private val dictionary: DictionaryDao, private val stats: StatDao) : DictionaryRepository {
    override fun search(prefix: String): Flow<List<DictionaryItem>> {
        val normalized = prefix.lowercase(java.util.Locale.ROOT).replace("'", "")
        if (normalized.isEmpty() || normalized.length > 64 || normalized.any { it !in 'a'..'z' }) return flowOf(emptyList())
        return dictionary.searchPrefix(normalized).map { entries -> entries.map { it.toModel() } }
    }
    override suspend fun learn(item: DictionaryItem) = dictionary.incrementFrequency(item.id)
    override suspend fun record(stat: TypingStat) = stats.insert(TypingStatEntity.from(stat))
    override val summary = stats.observeSummary()
}
