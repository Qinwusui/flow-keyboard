package com.flowkeyboard.android.data.local.database

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "bigram_transitions",
    primaryKeys = ["fromWord", "toWord"],
    indices = [Index("fromWord")]
)
data class BigramTransitionEntity(
    val fromWord: String,
    val toWord: String,
    val weight: Int = 50,
)
