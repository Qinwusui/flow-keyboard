package com.flowkeyboard.android.model

enum class KeyType { CHARACTER, SPACE, BACKSPACE, ENTER, SHIFT, MODE, EMOJI, SYMBOL }

data class KeyItem(val id: String, val label: String, val value: String = label, val type: KeyType = KeyType.CHARACTER)

