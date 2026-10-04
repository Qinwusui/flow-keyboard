package com.flowkeyboard.android.data.local.database

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "user_transitions",
    primaryKeys = ["fromWord", "toWord"],
    indices = [Index("fromWord")]
)
data class UserTransitionEntity(
    val fromWord: String,
    val toWord: String,
    val count: Long = 1,
    val lastUsedTime: Long = System.currentTimeMillis()
)
