package com.example.sipatkilatis.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sipatkilatis.R
import com.example.sipatkilatis.detection.MessageMarks
import com.example.sipatkilatis.detection.RiskSpan
import com.example.sipatkilatis.model.Verdict
import com.example.sipatkilatis.ui.AppLanguage
import com.example.sipatkilatis.ui.theme.Badge
import com.example.sipatkilatis.ui.theme.BodyL
import com.example.sipatkilatis.ui.theme.BodyM
import com.example.sipatkilatis.ui.theme.Display
import com.example.sipatkilatis.ui.theme.Headline
import com.example.sipatkilatis.ui.theme.Label
import com.example.sipatkilatis.ui.theme.MessageText
import com.example.sipatkilatis.ui.theme.Radius
import com.example.sipatkilatis.ui.theme.Sipat
import com.example.sipatkilatis.ui.theme.Space
import com.example.sipatkilatis.ui.theme.StatusColors
import com.example.sipatkilatis.ui.theme.Title

// ---------------------------------------------------------------- small helpers

/** True when the user turned animations off (Settings → Accessibility → Remove animations). */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) = HorizontalDivider(modifier, thickness = 1.dp, color = Sipat.colors.line)

/** Space to leave under scrolling content so the floating nav bar never covers it. */
@Composable
fun navBarClearance(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + FloatingNavHeight + 32.dp

// ---------------------------------------------------------------- verdict words

/** Short verdict word for badges, filters, history: Safe / Careful / Scam. */
@Composable
fun verdictLabel(verdict: Verdict): String = stringResource(
    when (verdict) {
        Verdict.SAFE -> R.string.verdict_safe
        Verdict.SUSPICIOUS -> R.string.verdict_suspicious
        Verdict.SCAM -> R.string.verdict_scam
    }
)

/** Full verdict phrase for the verdict card: Looks safe / Be careful / Likely a scam. */
@Composable
fun verdictWord(verdict: Verdict): String = stringResource(
    when (verdict) {
        Verdict.SAFE -> R.string.verdict_word_safe
        Verdict.SUSPICIOUS -> R.string.verdict_word_suspicious
        Verdict.SCAM -> R.string.verdict_word_scam
    }
)

// ---------------------------------------------------------------- headers

/** Small top bar with a back arrow, for detail screens (Check, Result). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(title, style = Title, modifier = Modifier.semantics { heading() }) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Sipat.colors.paper, scrolledContainerColor = Sipat.colors.paper),
    )
}

/** Large title for the tab screens (Home, History, Scam guide, Settings): no back arrow, the nav bar handles that. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().statusBarsPadding().padding(top = Space.l, bottom = Space.l)) {
        Text(title, style = Headline, color = Sipat.colors.ink, modifier = Modifier.semantics { heading() })
        if (subtitle != null) {
            Text(subtitle, style = BodyM, color = Sipat.colors.subtle, modifier = Modifier.padding(top = Space.xs))
        }
    }
}

// ---------------------------------------------------------------- cards

/** Standard content card: white surface, soft border, 20dp corners. */
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    padding: Dp = Space.l,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Sipat.colors
    Column(
        modifier.fillMaxWidth().background(c.card, Radius.card).border(1.dp, c.line, Radius.card).padding(padding),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        if (title != null) Text(title, style = Title, color = c.ink, modifier = Modifier.semantics { heading() })
        content()
    }
}

/** Rounded square with a tinted background holding one icon (cards, list rows, guide headers). */
@Composable
fun IconTile(icon: ImageVector, tint: Color, background: Color, size: Dp = 40.dp, iconSize: Dp = 22.dp) {
    Box(Modifier.size(size).background(background, Radius.small), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

// ---------------------------------------------------------------- verdict card with gauge

private const val GAUGE_START = 150f   // degrees, 0 = 3 o'clock
private const val GAUGE_SWEEP = 240f

/**
 * Hero of the Result screen: a soft card in the verdict's color with a semicircle gauge.
 * The arc fills up to the score; ticks show where Careful and Scam begin. Verdict = words + number + color.
 */
@Composable
fun VerdictCard(verdict: Verdict, score: Float, thresholds: Pair<Float, Float>, modifier: Modifier = Modifier) {
    val c = Sipat.colors
    val s = c.status(verdict)
    val reduceMotion = rememberReduceMotion()
    val points = (score * 100).toInt()
    val word = verdictWord(verdict)
    val a11y = stringResource(R.string.verdict_a11y, word, points)
    val fill = remember { Animatable(if (reduceMotion) score else 0f) }
    LaunchedEffect(score) { if (reduceMotion) fill.snapTo(score) else fill.animateTo(score, tween(700, easing = FastOutSlowInEasing)) }

    Column(
        modifier.fillMaxWidth().background(s.container, Radius.hero).padding(horizontal = Space.xl, vertical = Space.xl)
            .clearAndSetSemantics { contentDescription = a11y },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 220.dp, height = 170.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(200.dp)) {
                val stroke = 16.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)
                // Track, then the filled part in the verdict color
                drawArc(c.card.copy(alpha = 0.85f), GAUGE_START, GAUGE_SWEEP, false, topLeft, arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round))
                val sweep = GAUGE_SWEEP * fill.value.coerceIn(0.01f, 1f)
                drawArc(s.strong, GAUGE_START, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                // Threshold ticks (outside the arc)
                val r = size.width / 2
                val center = Offset(size.width / 2, size.height / 2)
                for (t in listOf(thresholds.first, thresholds.second)) {
                    val a = Math.toRadians((GAUGE_START + GAUGE_SWEEP * t).toDouble())
                    val dir = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
                    drawLine(s.onContainer.copy(alpha = 0.5f), center + dir * (r - stroke - 4.dp.toPx()),
                        center + dir * (r - stroke - 10.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                }
            }
            Column(Modifier.padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(points.toString(), style = Display.copy(fontSize = 52.sp, lineHeight = 56.sp), color = s.onContainer)
                Text(stringResource(R.string.gauge_of_100), style = Label, color = s.onContainer.copy(alpha = 0.75f))
            }
        }
        Text(word, style = Headline, color = s.onContainer)
        Text(stringResource(verdictSubtitle(verdict)), style = BodyM, color = s.onContainer.copy(alpha = 0.85f),
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.xs))
    }
}

private fun verdictSubtitle(verdict: Verdict) = when (verdict) {
    Verdict.SAFE -> R.string.verdict_sub_safe
    Verdict.SUSPICIOUS -> R.string.verdict_sub_suspicious
    Verdict.SCAM -> R.string.verdict_sub_scam
}

/** Small colored badge for lists and filters: icon + short word on the verdict's soft color. */
@Composable
fun StatusBadge(verdict: Verdict, modifier: Modifier = Modifier) {
    val s = Sipat.colors.status(verdict)
    Row(modifier.background(s.container, Radius.pill).padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(ShieldIcons.of(verdict), contentDescription = null, tint = s.strong, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(verdictLabel(verdict), style = Badge, color = s.onContainer)
    }
}

// ---------------------------------------------------------------- highlighted message

/**
 * The inspected message. Strong signs (OTP, bad links): red marker with white text.
 * Weaker signs: amber band + underline. Look-alike characters get a red box.
 * Tapping a highlight calls [onSpanClick] (opens "Why is this risky?").
 */
@Composable
fun HighlightedMessage(text: String, marks: MessageMarks, onSpanClick: (RiskSpan) -> Unit, modifier: Modifier = Modifier) {
    val c = Sipat.colors
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val spans = marks.spans.filter { it.range.first >= 0 && it.range.last < text.length }
    val filipino = AppLanguage.current() == AppLanguage.FILIPINO
    val annotated = buildAnnotatedString {
        append(text)
        spans.filter { it.strong }.forEach {
            addStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium), it.range.first, it.range.last + 1)
        }
    }
    Text(
        annotated, style = MessageText, color = c.ink,
        onTextLayout = { layout = it },
        modifier = modifier
            .drawBehind {
                val l = layout ?: return@drawBehind
                fun band(range: IntRange, draw: (Float, Float, Float, Float) -> Unit) {
                    val startLine = l.getLineForOffset(range.first)
                    val endLine = l.getLineForOffset(range.last)
                    for (line in startLine..endLine) {
                        val s = if (line == startLine) l.getHorizontalPosition(range.first, true) else l.getLineLeft(line)
                        val e = if (line == endLine) l.getHorizontalPosition(range.last + 1, true) else l.getLineRight(line)
                        draw(minOf(s, e), l.getLineTop(line), maxOf(s, e), l.getLineBottom(line))
                    }
                }
                val r = CornerRadius(4.dp.toPx())
                // Weak first, strong on top
                spans.filter { !it.strong }.forEach { span ->
                    band(span.range) { left, top, right, bottom ->
                        drawRoundRect(c.careful.container, Offset(left - 2, top + 2), Size(right - left + 4, bottom - top - 4), r)
                        drawRect(c.careful.strong, Offset(left, bottom - 4.dp.toPx()), Size(right - left, 2.dp.toPx()))
                    }
                }
                spans.filter { it.strong }.forEach { span ->
                    band(span.range) { left, top, right, bottom ->
                        drawRoundRect(c.scam.strong, Offset(left - 3, top + 2), Size(right - left + 6, bottom - top - 4), r)
                    }
                }
                // Boxed look-alike characters
                marks.lookalikes.filter { it < text.length }.forEach { i ->
                    val b = l.getBoundingBox(i)
                    drawRoundRect(c.scam.strong, Offset(b.left - 1, b.top + 3), Size(b.width + 2, b.height - 6),
                        CornerRadius(2.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                }
            }
            .pointerInput(spans) {
                detectTapGestures { pos ->
                    val l = layout ?: return@detectTapGestures
                    val i = l.getOffsetForPosition(pos)
                    spans.filter { i in it.range }.maxByOrNull { if (it.strong) 1 else 0 }?.let(onSpanClick)
                }
            }
            // TalkBack: each highlight is reachable as an action ("Why: …")
            .semantics {
                customActions = spans.distinctBy { it.flag.id }.map { s ->
                    CustomAccessibilityAction(if (filipino) s.flag.reasonFil else s.flag.reasonEn) { onSpanClick(s); true }
                }
            },
    )
}

/** Soft grey block for a message (Demo screen, Check screen previews). */
@Composable
fun MessageBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(Sipat.colors.wash, Radius.small).padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.s), content = content,
    )
}

// ---------------------------------------------------------------- buttons

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                  icon: ImageVector? = null) {
    val c = Sipat.colors
    Button(
        onClick = onClick, enabled = enabled, shape = Radius.button,
        modifier = modifier.fillMaxWidth().heightIn(min = Space.button),
        colors = ButtonDefaults.buttonColors(containerColor = c.ink, contentColor = c.paper,
            disabledContainerColor = c.wash, disabledContentColor = c.mute),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        if (icon != null) { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(Space.s)) }
        Text(text, style = Label.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp))
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                    icon: ImageVector? = null) {
    val c = Sipat.colors
    OutlinedButton(
        onClick = onClick, enabled = enabled, shape = Radius.button,
        modifier = modifier.heightIn(min = Space.touch),
        border = BorderStroke(1.dp, c.line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = c.card, contentColor = c.ink, disabledContentColor = c.mute),
    ) {
        if (icon != null) { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(Space.s)) }
        Text(text, style = Label.copy(fontWeight = FontWeight.SemiBold))
    }
}

/** Soft colored button for verdict-related actions (Mark as safe = green, Report scam = red). */
@Composable
fun TonalButton(text: String, onClick: () -> Unit, colors: StatusColors, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick, shape = Radius.button, modifier = modifier.heightIn(min = Space.touch),
        colors = ButtonDefaults.buttonColors(containerColor = colors.container, contentColor = colors.onContainer),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Text(text, style = Label.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = Space.touch),
        colors = ButtonDefaults.textButtonColors(contentColor = Sipat.colors.ink)) {
        Text(text, style = Label.copy(fontWeight = FontWeight.SemiBold))
    }
}

// ---------------------------------------------------------------- rows and sections

/** Section title above a group of content. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Title, color = Sipat.colors.ink,
        modifier = modifier.padding(top = Space.xl, bottom = Space.s).semantics { heading() })
}

/** List row: icon tile + label (+ optional value) + chevron. */
@Composable
fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit, value: String? = null, showDivider: Boolean = true) {
    val c = Sipat.colors
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Space.touch + 4.dp).clickable(onClick = onClick).padding(vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(icon, c.ink, c.wash, size = 36.dp, iconSize = 20.dp)
            Spacer(Modifier.width(Space.m))
            Text(label, style = BodyL, color = c.ink, modifier = Modifier.weight(1f))
            if (value != null) Text(value, style = BodyM, color = c.subtle)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.mute)
        }
        if (showDivider) DividerLine()
    }
}

/** Single-select segmented tabs on a soft track; the selected tab is a raised white pill. */
@Composable
fun SegmentedChoice(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Sipat.colors
    Row(modifier.fillMaxWidth().background(c.wash, Radius.pill).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp)
                    .then(if (on) Modifier.background(c.card, Radius.pill).border(1.dp, c.line, Radius.pill) else Modifier)
                    .clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { onSelect(i) }
                    .semantics { this.selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Label.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (on) c.ink else c.subtle, maxLines = 1)
            }
        }
    }
}

/** A "what to do" line: small tinted icon + text. */
@Composable
fun CheckRow(icon: ImageVector, text: String, tint: Color = Sipat.colors.ink, tileColor: Color = Sipat.colors.wash) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        IconTile(icon, tint, tileColor, size = 32.dp, iconSize = 18.dp)
        Spacer(Modifier.width(Space.m))
        Text(text, style = BodyL, color = Sipat.colors.ink, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Friendly empty state. */
@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = Space.xxl), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.m)) {
        IconTile(icon, Sipat.colors.subtle, Sipat.colors.wash, size = 56.dp, iconSize = 28.dp)
        Text(text, style = BodyL, color = Sipat.colors.subtle, textAlign = TextAlign.Center)
    }
}

/** Bottom sheet that explains what an action will do before it happens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(title: String, body: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Sipat.colors.card, shape = Radius.sheet) {
        Column(Modifier.padding(horizontal = Space.screen).padding(bottom = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Text(title, style = Title, color = Sipat.colors.ink)
            Text(body, style = BodyL, color = Sipat.colors.subtle)
            Spacer(Modifier.height(Space.xs))
            PrimaryButton(confirmLabel, onClick = { onConfirm(); onDismiss() })
            TextAction(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/** Bottom sheet with one piece of information (e.g. "Why is this risky?"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(title: String, body: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Sipat.colors.card, shape = Radius.sheet) {
        Column(Modifier.padding(horizontal = Space.screen).padding(bottom = Space.xxl), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Text(title, style = Title, color = Sipat.colors.ink)
            Text(body, style = BodyL, color = Sipat.colors.ink)
        }
    }
}
