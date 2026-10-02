package com.flowkeyboard.android.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.flowkeyboard.android.model.TypingStat

@Entity(tableName = "typing_stats")
data class TypingStatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMillis: Long,
    val durationMillis: Long,
    val keystrokes: Int,
    val committedCharacters: Int,
) {
    fun toModel() = TypingStat(id, startedAtMillis, durationMillis, keystrokes, committedCharacters)
    companion object {
        fun from(stat: TypingStat) = TypingStatEntity(stat.id, stat.startedAtMillis, stat.durationMillis.coerceAtLeast(0),
            stat.keystrokes.coerceAtLeast(0), stat.committedCharacters.coerceAtLeast(0))
    }
}
