package com.flowkeyboard.android.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionary WHERE pinyin LIKE :prefix || '%' OR initials LIKE :prefix || '%' ORDER BY (baseWeight + frequency * 100) DESC, LENGTH(pinyin), id LIMIT :limit")
    fun searchPrefix(prefix: String, limit: Int = 20): Flow<List<DictionaryEntity>>

    @Query("UPDATE dictionary SET frequency = MIN(frequency + 1, 1000000000) WHERE id = :id")
    suspend fun incrementFrequency(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<DictionaryEntity>)

    @Query("SELECT COUNT(*) FROM dictionary")
    suspend fun count(): Int
}
