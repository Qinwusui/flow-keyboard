package com.flowkeyboard.android.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryDao {
    @Query("""
        SELECT * FROM dictionary 
        WHERE pinyin LIKE :prefix || '%' OR initials LIKE :prefix || '%' 
        ORDER BY 
            CASE 
                WHEN pinyin = :prefix THEN 1 
                WHEN initials = :prefix THEN 2 
                WHEN pinyin LIKE :prefix || '%' THEN 3 
                ELSE 4 
            END ASC, 
            (baseWeight + frequency * 100) DESC, 
            LENGTH(pinyin) ASC, 
            id ASC 
        LIMIT :limit
    """)
    fun searchPrefix(prefix: String, limit: Int = 100): Flow<List<DictionaryEntity>>

    @Query("""
        SELECT * FROM dictionary 
        WHERE initials LIKE :initialsPrefix || '%' AND pinyin LIKE :pinyinPattern 
        ORDER BY 
            CASE WHEN initials = :initialsPrefix THEN 1 ELSE 2 END ASC,
            (baseWeight + frequency * 100) DESC, 
            LENGTH(pinyin) ASC, 
            id ASC 
        LIMIT :limit
    """)
    fun searchHybrid(initialsPrefix: String, pinyinPattern: String, limit: Int = 50): Flow<List<DictionaryEntity>>

    @Query("SELECT * FROM dictionary WHERE word LIKE :prefix || '%' AND LENGTH(word) > LENGTH(:prefix) ORDER BY (baseWeight + frequency * 100) DESC, LENGTH(word), id LIMIT :limit")
    fun searchWordPrefix(prefix: String, limit: Int = 30): Flow<List<DictionaryEntity>>

    @Query("UPDATE dictionary SET frequency = MIN(frequency + 1, 1000000000) WHERE id = :id")
    suspend fun incrementFrequency(id: Long)

    @Query("UPDATE dictionary SET frequency = MIN(frequency + 1, 1000000000) WHERE word = :word")
    suspend fun incrementWordFrequency(word: String)

    @Query("SELECT * FROM dictionary WHERE word = :word LIMIT 1")
    suspend fun findByWord(word: String): DictionaryEntity?

    @Query("INSERT INTO dictionary (word, pinyin, initials, baseWeight, frequency) VALUES (:word, :pinyin, :initials, :baseWeight, :frequency) ON CONFLICT(word, pinyin) DO UPDATE SET frequency = frequency + 1")
    suspend fun insertOrUpdateWord(word: String, pinyin: String, initials: String, baseWeight: Long = 50, frequency: Long = 1)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<DictionaryEntity>)

    @Query("SELECT COUNT(*) FROM dictionary")
    suspend fun count(): Int

    @Query("SELECT toWord FROM user_transitions WHERE fromWord = :fromWord ORDER BY count DESC, lastUsedTime DESC LIMIT :limit")
    fun getTransitions(fromWord: String, limit: Int = 15): Flow<List<String>>

    @Query("INSERT INTO user_transitions (fromWord, toWord, count, lastUsedTime) VALUES (:fromWord, :toWord, 1, :now) ON CONFLICT(fromWord, toWord) DO UPDATE SET count = count + 1, lastUsedTime = :now")
    suspend fun recordTransition(fromWord: String, toWord: String, now: Long = System.currentTimeMillis())

    @Query("SELECT toWord FROM bigram_transitions WHERE fromWord = :fromWord ORDER BY weight DESC LIMIT :limit")
    fun getBigramTransitions(fromWord: String, limit: Int = 15): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBigrams(entries: List<BigramTransitionEntity>)
}
