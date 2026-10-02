package com.flowkeyboard.android.ui.keyboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowkeyboard.android.model.DictionaryItem

@Composable
fun CandidateBar(composing: String, candidates: List<DictionaryItem>, onCandidate: (DictionaryItem) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        if (composing.isNotEmpty()) {
            Text(composing, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            LazyRow(contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (candidates.isEmpty()) item { Text("No match · Space commits pinyin", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
                items(candidates, key = { it.id }) { candidate ->
                    SuggestionChip(onClick = { onCandidate(candidate) }, label = { Text(candidate.word, style = MaterialTheme.typography.titleMedium) })
                }
            }
        } else {
            Text("Tap the target line • Drag or fling a lane", Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
