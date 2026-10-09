package com.example.sipatkilatis.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sipatkilatis.R
import com.example.sipatkilatis.data.RoomScanRepository
import com.example.sipatkilatis.model.ScanRecord
import com.example.sipatkilatis.model.Verdict
import com.example.sipatkilatis.ui.components.EmptyState
import com.example.sipatkilatis.ui.components.ScreenHeader
import com.example.sipatkilatis.ui.components.StatusBadge
import com.example.sipatkilatis.ui.components.navBarClearance
import com.example.sipatkilatis.ui.components.verdictLabel
import com.example.sipatkilatis.ui.theme.BodyL
import com.example.sipatkilatis.ui.theme.BodyM
import com.example.sipatkilatis.ui.theme.Label
import com.example.sipatkilatis.ui.theme.Radius
import com.example.sipatkilatis.ui.theme.Sipat
import com.example.sipatkilatis.ui.theme.Space
import com.example.sipatkilatis.ui.theme.Title
import kotlinx.coroutines.launch

/** Search + filter tabs + one card per checked message. Swipe a card to delete, with Undo. */
@Composable
fun HistoryScreen(
    history: List<ScanRecord>,
    onOpen: (Long) -> Unit,
    onDelete: (ScanRecord) -> Unit,
    onRestore: (ScanRecord) -> Unit,
    snackbar: SnackbarHostState,   // shown by AppNavHost on top of every layer, so Undo always gets the tap
) {
    val c = Sipat.colors
    var filter by rememberSaveable { mutableStateOf<Verdict?>(null) }   // null = all
    var query by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    // How many times each scan was restored with Undo. Part of the list key: the swipe card saves its
    // "swiped away" state per key, so a restored scan with the old key would instantly delete itself again.
    var restores by remember { mutableStateOf(mapOf<Long, Int>()) }
    val deletedMsg = stringResource(R.string.history_deleted)
    val undo = stringResource(R.string.undo)
    val clearance = navBarClearance()
    val shown = history.filter { r ->
        (filter == null || r.verdict == filter) &&
            (query.isBlank() || r.text.contains(query, ignoreCase = true) || r.sender.contains(query, ignoreCase = true))
    }

    Scaffold(
        containerColor = c.paper,
    ) { _ ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.screen, end = Space.screen, bottom = clearance),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            item { ScreenHeader(stringResource(R.string.history_title)) }
            item { SearchField(query) { query = it } }
            item {
                // Filter tabs: All / Safe / Careful / Scam, each verdict with its color dot
                Row(Modifier.fillMaxWidth().padding(bottom = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    FilterTab(stringResource(R.string.history_all), null, filter == null, Modifier.weight(0.8f)) { filter = null }
                    Verdict.entries.forEach { v ->
                        FilterTab(verdictLabel(v), v, filter == v, Modifier.weight(1f)) { filter = v }
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    EmptyState(Icons.Outlined.Inbox,
                        stringResource(if (history.isEmpty()) R.string.history_empty else R.string.history_empty_filter))
                }
            } else {
                items(shown, key = { "${it.id}:${restores[it.id] ?: 0}" }) { record ->
                    val state = rememberSwipeToDismissBoxState()
                    SwipeToDismissBox(
                        state = state,
                        onDismiss = {
                            onDelete(record)
                            scope.launch {
                                snackbar.currentSnackbarData?.dismiss()   // one Undo at a time
                                // With an action, Compose defaults to an Indefinite snackbar that never goes away:
                                // give it a timeout and a close button
                                val result = snackbar.showSnackbar(deletedMsg, undo, withDismissAction = true,
                                    duration = SnackbarDuration.Long)
                                if (result == SnackbarResult.ActionPerformed) {
                                    restores = restores + (record.id to (restores[record.id] ?: 0) + 1)
                                    onRestore(record)
                                }
                            }
                        },
                        backgroundContent = {
                            Row(Modifier.fillMaxSize().background(c.scam.container, Radius.card).padding(horizontal = Space.xl),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = c.scam.strong)
                            }
                        },
                    ) { HistoryCard(record, onClick = { onOpen(record.id) }) }
                }
            }
        }
    }
}

/** Rounded, softly shaded search field. */
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val c = Sipat.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).background(c.wash, Radius.input).padding(start = Space.l, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = c.subtle, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Space.m))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(stringResource(R.string.history_search), style = BodyL, color = c.mute)
            BasicTextField(query, onChange, singleLine = true, textStyle = BodyL.copy(color = c.ink),
                cursorBrush = SolidColor(c.ink), modifier = Modifier.fillMaxWidth())
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onChange("") }) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel), tint = c.subtle)
            }
        } else {
            Spacer(Modifier.width(Space.m))
        }
    }
}

/** Rounded filter tab. Selected = ink fill; verdict tabs show their color dot. */
@Composable
private fun FilterTab(label: String, verdict: Verdict?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Sipat.colors
    Row(
        modifier.heightIn(min = 40.dp)
            .background(if (selected) c.ink else c.card, Radius.pill)
            .border(1.dp, if (selected) c.ink else c.line, Radius.pill)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = Space.s),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (verdict != null) {
            Box(Modifier.size(8.dp).background(c.status(verdict).strong, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = Label.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (selected) c.paper else c.ink, maxLines = 1)
    }
}

/** One checked message as a card: badge + time, sender, two-line preview. */
@Composable
private fun HistoryCard(record: ScanRecord, onClick: () -> Unit) {
    val c = Sipat.colors
    val sender = record.sender.takeIf { it != RoomScanRepository.NO_SENDER } ?: stringResource(R.string.history_unknown_sender)
    val ago = DateUtils.getRelativeTimeSpanString(record.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    Column(
        Modifier.fillMaxWidth().background(c.card, Radius.card).border(1.dp, c.line, Radius.card)
            .clickable(onClick = onClick).padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(record.verdict)
            Spacer(Modifier.weight(1f))
            Text(ago, style = BodyM, color = c.mute)
        }
        Text(sender, style = Title.copy(fontWeight = FontWeight.SemiBold), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(record.text, style = BodyM, color = c.subtle, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
