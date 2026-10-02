package com.flowkeyboard.android.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.flowkeyboard.android.model.TypingSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface StatDao {
    @Insert suspend fun insert(stat: TypingStatEntity)
    @Query("SELECT COALESCE(SUM(keystrokes), 0) AS keystrokes, COALESCE(SUM(committedCharacters), 0) AS characters, COALESCE(SUM(durationMillis), 0) AS durationMillis FROM typing_stats")
    fun observeSummary(): Flow<TypingSummary>
    @Query("SELECT * FROM typing_stats ORDER BY startedAtMillis DESC LIMIT 100")
    fun observeRecent(): Flow<List<TypingStatEntity>>
}
