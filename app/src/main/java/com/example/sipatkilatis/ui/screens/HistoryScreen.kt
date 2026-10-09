package com.example.sipatkilatis.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sipatkilatis.R
import com.example.sipatkilatis.model.MessageSource
import com.example.sipatkilatis.model.ScanRecord
import com.example.sipatkilatis.model.Verdict
import com.example.sipatkilatis.ui.components.AppTopBar
import com.example.sipatkilatis.ui.components.SectionCard
import com.example.sipatkilatis.ui.components.VerdictChip
import com.example.sipatkilatis.ui.components.verdictLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Past scans with verdict, date, and sender, filterable by verdict. */
@Composable
fun HistoryScreen(history: List<ScanRecord>, onBack: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf<Verdict?>(null) }   // null = all
    val shown = if (filter == null) history else history.filter { it.verdict == filter }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.history_title), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = filter == null, onClick = { filter = null },
                    label = { Text(stringResource(R.string.history_all)) })
                Verdict.entries.forEach { v ->
                    FilterChip(selected = filter == v, onClick = { filter = v }, label = { Text(verdictLabel(v)) })
                }
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(shown, key = { it.id }) { HistoryItem(it) }
                }
            }
        }
    }
}

private val dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

@Composable
private fun HistoryItem(record: ScanRecord) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(record.sender, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            VerdictChip(record.verdict)
        }
        Text(record.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth()) {
            Text(
                Instant.ofEpochMilli(record.timestamp).atZone(ZoneId.systemDefault()).format(dateFormat),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(sourceLabel(record.source), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun sourceLabel(source: MessageSource) = stringResource(
    when (source) {
        MessageSource.SMS -> R.string.source_sms
        MessageSource.NOTIFICATION -> R.string.source_notification
        MessageSource.MANUAL -> R.string.source_manual
    }
)
