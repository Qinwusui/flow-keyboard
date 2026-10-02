package com.flowkeyboard.android.data.local.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.flowkeyboard.android.model.DictionaryItem

@Entity(tableName = "dictionary", indices = [Index("pinyin"), Index("initials"), Index(value = ["word", "pinyin"], unique = true)])
data class DictionaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val pinyin: String,
    val initials: String,
    val baseWeight: Long = 0,
    val frequency: Long = 0,
) {
    fun toModel() = DictionaryItem(id, word, pinyin, baseWeight + frequency * 100)
}
