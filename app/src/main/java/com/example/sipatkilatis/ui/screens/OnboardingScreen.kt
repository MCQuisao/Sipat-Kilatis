package com.example.sipatkilatis.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.sipatkilatis.R
import com.example.sipatkilatis.ui.AppLanguage
import com.example.sipatkilatis.ui.components.PrimaryButton
import com.example.sipatkilatis.ui.components.TextAction
import com.example.sipatkilatis.ui.theme.BodyL
import com.example.sipatkilatis.ui.theme.Headline
import com.example.sipatkilatis.ui.theme.Radius
import com.example.sipatkilatis.ui.theme.Sipat
import com.example.sipatkilatis.ui.theme.Space
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3

/** 3 pages: what it does, the privacy promise, language. Permissions are asked later, in context (Home). */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pager = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val isLast = pager.currentPage == PAGE_COUNT - 1

    Scaffold(containerColor = Sipat.colors.paper) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.s), horizontalArrangement = Arrangement.End) {
                if (!isLast) TextAction(stringResource(R.string.onb_skip), onClick = onFinish)
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen),
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (page) {
                        0 -> PageInspect()
                        1 -> PagePrivacy()
                        else -> PageLanguage()
                    }
                }
            }
            StepDots(pager.currentPage)
            Row(Modifier.fillMaxWidth().padding(Space.screen), verticalAlignment = Alignment.CenterVertically) {
                if (pager.currentPage > 0) {
                    TextAction(stringResource(R.string.onb_back),
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } })
                }
                Spacer(Modifier.weight(1f))
                PrimaryButton(
                    stringResource(if (isLast) R.string.onb_start else R.string.onb_next),
                    onClick = { if (isLast) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    modifier = Modifier.width(180.dp),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.PageInspect() {
    LoupeIllustration(Modifier.size(180.dp).align(Alignment.CenterHorizontally))
    Spacer(Modifier.size(Space.xxl))
    Text(stringResource(R.string.onb_title_1), style = Headline, color = Sipat.colors.ink)
    Spacer(Modifier.size(Space.m))
    Text(stringResource(R.string.onb_body_1), style = BodyL, color = Sipat.colors.subtle)
}

@Composable
private fun PagePrivacy() {
    val c = Sipat.colors
    Icon(Icons.Filled.AirplanemodeActive, contentDescription = null, tint = c.ink, modifier = Modifier.size(40.dp))
    Spacer(Modifier.size(Space.xl))
    Text(stringResource(R.string.onb_title_2), style = Headline, color = c.ink)
    Spacer(Modifier.size(Space.xl))
    for ((icon, text) in listOf(Icons.Filled.CloudOff to R.string.onb_offline_1, Icons.Filled.Check to R.string.onb_offline_2)) {
        Row(Modifier.padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = c.ink)
            Spacer(Modifier.width(Space.m))
            Text(stringResource(text), style = BodyL, color = c.ink)
        }
    }
}

@Composable
private fun PageLanguage() {
    val context = LocalContext.current
    val current = AppLanguage.current()
    Text(stringResource(R.string.onb_title_3), style = Headline, color = Sipat.colors.ink)
    Spacer(Modifier.size(Space.s))
    Text(stringResource(R.string.onb_body_3), style = BodyL, color = Sipat.colors.subtle)
    Spacer(Modifier.size(Space.xl))
    LanguageOption(stringResource(R.string.lang_filipino), current == AppLanguage.FILIPINO) { AppLanguage.set(context, AppLanguage.FILIPINO) }
    Spacer(Modifier.size(Space.m))
    LanguageOption(stringResource(R.string.lang_english), current == AppLanguage.ENGLISH) { AppLanguage.set(context, AppLanguage.ENGLISH) }
}

/** Large selectable row: selected = 2dp Ink outline + check (not color). Applies instantly. */
@Composable
fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Sipat.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = Space.button)
            .clip(Radius.button)
            .background(if (selected) c.card else c.paper, Radius.button)
            .border(if (selected) Space.outline else 1.dp, if (selected) c.ink else c.subtle, Radius.button)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = BodyL, color = c.ink, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = c.ink)
    }
}

@Composable
private fun StepDots(current: Int) {
    val c = Sipat.colors
    Row(Modifier.fillMaxWidth().semantics { contentDescription = "${current + 1} / $PAGE_COUNT" },
        horizontalArrangement = Arrangement.Center) {
        repeat(PAGE_COUNT) { i ->
            Box(
                Modifier.padding(4.dp).size(if (i == current) 10.dp else 8.dp).clip(CircleShape)
                    .background(if (i == current) c.ink else c.line)
                    .align(Alignment.CenterVertically),
            )
        }
    }
}

/** Line illustration: a loupe inspecting a message bubble. Drawn in code (no image files). */
@Composable
fun LoupeIllustration(modifier: Modifier = Modifier) {
    val c = Sipat.colors
    Canvas(modifier) {
        val w = size.width
        val stroke = Stroke(width = w * 0.025f, cap = StrokeCap.Round)
        // Message bubble with a tail
        drawRoundRect(c.ink, Offset(w * 0.08f, w * 0.12f), Size(w * 0.62f, w * 0.42f), CornerRadius(w * 0.06f), style = stroke)
        drawLine(c.ink, Offset(w * 0.18f, w * 0.54f), Offset(w * 0.14f, w * 0.66f), stroke.width, StrokeCap.Round)
        drawLine(c.ink, Offset(w * 0.14f, w * 0.66f), Offset(w * 0.30f, w * 0.54f), stroke.width, StrokeCap.Round)
        // Text lines inside the bubble
        for ((i, len) in listOf(0.42f, 0.30f, 0.36f).withIndex()) {
            val y = w * (0.22f + i * 0.09f)
            drawLine(c.subtle, Offset(w * 0.16f, y), Offset(w * (0.16f + len), y), stroke.width * 0.8f, StrokeCap.Round)
        }
        // Loupe over the bubble
        val center = Offset(w * 0.62f, w * 0.52f)
        drawCircle(c.paper, w * 0.17f, center)
        drawCircle(c.ink, w * 0.17f, center, style = Stroke(width = w * 0.035f))
        drawLine(c.ink, Offset(w * 0.74f, w * 0.64f), Offset(w * 0.90f, w * 0.80f), w * 0.06f, StrokeCap.Round)
        // A check mark seen through the loupe
        drawLine(c.ink, Offset(w * 0.56f, w * 0.52f), Offset(w * 0.60f, w * 0.56f), stroke.width, StrokeCap.Round)
        drawLine(c.ink, Offset(w * 0.60f, w * 0.56f), Offset(w * 0.68f, w * 0.47f), stroke.width, StrokeCap.Round)
    }
}
