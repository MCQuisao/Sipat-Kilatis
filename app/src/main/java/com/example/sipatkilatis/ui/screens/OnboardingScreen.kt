package com.example.sipatkilatis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sipatkilatis.R
import com.example.sipatkilatis.ui.AppLanguage
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3

/** 3 pages: what the app does, privacy promise, language + (placeholder) permissions. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pager = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val placeholderMsg = stringResource(R.string.perm_placeholder)
    val isLast = pager.currentPage == PAGE_COUNT - 1

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                if (!isLast) TextButton(onClick = onFinish) { Text(stringResource(R.string.onb_skip)) }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> InfoPage(Icons.Filled.Shield, stringResource(R.string.onb_title_1), stringResource(R.string.onb_body_1))
                    1 -> InfoPage(Icons.Filled.Lock, stringResource(R.string.onb_title_2), stringResource(R.string.onb_body_2))
                    else -> LanguagePage(onPermissionClick = { scope.launch { snackbar.showSnackbar(placeholderMsg) } })
                }
            }
            PageDots(current = pager.currentPage)
            Button(
                onClick = { if (isLast) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth().padding(24.dp).height(56.dp),
            ) {
                Text(stringResource(if (isLast) R.string.onb_start else R.string.onb_next))
            }
        }
    }
}

@Composable
private fun InfoPage(icon: ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        }
        Spacer(Modifier.height(32.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LanguagePage(onPermissionClick: () -> Unit) {
    val current = AppLanguage.current()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.onb_title_3), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.onb_body_3), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        LanguageOption(stringResource(R.string.lang_filipino), current == AppLanguage.FILIPINO) { AppLanguage.set(AppLanguage.FILIPINO) }
        LanguageOption(stringResource(R.string.lang_english), current == AppLanguage.ENGLISH) { AppLanguage.set(AppLanguage.ENGLISH) }

        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.onb_perm_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        // Placeholders: real runtime permission requests are added in phase 5.
        PermissionButton(Icons.Filled.Sms, stringResource(R.string.onb_perm_sms), onPermissionClick)
        PermissionButton(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.onb_perm_notif), onPermissionClick)
        Text(stringResource(R.string.onb_perm_later), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PermissionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PageDots(current: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(PAGE_COUNT) { i ->
            Box(
                Modifier
                    .padding(4.dp)
                    .size(if (i == current) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .align(Alignment.CenterVertically)
            )
        }
    }
}

