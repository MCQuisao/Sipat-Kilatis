package com.example.sipatkilatis.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sipatkilatis.R
import com.example.sipatkilatis.detection.RiskSpan
import com.example.sipatkilatis.graph
import com.example.sipatkilatis.ui.AppLanguage
import com.example.sipatkilatis.ui.components.CheckRow
import com.example.sipatkilatis.ui.components.HighlightedMessage
import com.example.sipatkilatis.ui.components.InfoSheet
import com.example.sipatkilatis.ui.components.ScreenHeader
import com.example.sipatkilatis.ui.components.navBarClearance
import com.example.sipatkilatis.ui.theme.Badge
import com.example.sipatkilatis.ui.theme.BodyL
import com.example.sipatkilatis.ui.theme.BodyM
import com.example.sipatkilatis.ui.theme.Label
import com.example.sipatkilatis.ui.theme.Radius
import com.example.sipatkilatis.ui.theme.Sipat
import com.example.sipatkilatis.ui.theme.Space
import com.example.sipatkilatis.ui.theme.Title

/** One offline guide entry. Text comes from string resources, so it follows the app language. */
private data class GuideItem(
    val icon: ImageVector,
    @param:StringRes val title: Int,
    @param:StringRes val example: Int,
    @param:StringRes val tips: Int,
    @param:StringRes val todo: Int,
)

private val items = listOf(
    GuideItem(Icons.Rounded.AccountBalance, R.string.guide_ewallet_title, R.string.guide_ewallet_example, R.string.guide_ewallet_tips, R.string.guide_ewallet_todo),
    GuideItem(Icons.Rounded.LocalShipping, R.string.guide_delivery_title, R.string.guide_delivery_example, R.string.guide_delivery_tips, R.string.guide_delivery_todo),
    GuideItem(Icons.Rounded.Work, R.string.guide_job_title, R.string.guide_job_example, R.string.guide_job_tips, R.string.guide_job_todo),
    GuideItem(Icons.Rounded.Payments, R.string.guide_loan_title, R.string.guide_loan_example, R.string.guide_loan_tips, R.string.guide_loan_todo),
    GuideItem(Icons.Rounded.CardGiftcard, R.string.guide_prize_title, R.string.guide_prize_example, R.string.guide_prize_tips, R.string.guide_prize_todo),
    GuideItem(Icons.Rounded.Password, R.string.guide_otp_title, R.string.guide_otp_example, R.string.guide_otp_tips, R.string.guide_otp_todo),
)

/** Expandable cards: what it looks like (as a text bubble, highlighted like Result), how to spot it, what to do. */
@Composable
fun ScamGuideScreen() {
    val c = Sipat.colors
    var expanded by rememberSaveable { mutableStateOf<Int?>(null) }
    var openSpan by remember { mutableStateOf<RiskSpan?>(null) }
    val filipino = AppLanguage.current() == AppLanguage.FILIPINO

    LazyColumn(
        Modifier.fillMaxSize().background(c.paper),
        contentPadding = PaddingValues(start = Space.screen, end = Space.screen, bottom = navBarClearance()),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item { ScreenHeader(stringResource(R.string.guide_title), stringResource(R.string.guide_intro)) }
        items(items) { item ->
            val open = expanded == item.title
            val arrow by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
            Column(
                Modifier.fillMaxWidth()
                    .shadow(if (open) 6.dp else 1.dp, Radius.card, ambientColor = Color.Black.copy(alpha = 0.2f),
                        spotColor = Color.Black.copy(alpha = 0.2f))
                    .background(c.card, Radius.card)
                    .border(1.dp, c.line, Radius.card),
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Space.touch + 12.dp)
                        .clickable { expanded = if (open) null else item.title }
                        .padding(horizontal = Space.l, vertical = Space.m)
                        .semantics { stateDescription = if (open) "expanded" else "collapsed" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(item.icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(Space.m))
                    Text(stringResource(item.title), style = Title.copy(fontWeight = FontWeight.SemiBold), color = c.ink,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = c.subtle, modifier = Modifier.rotate(arrow))
                }
                AnimatedVisibility(open) { GuideDetails(item, onSpanClick = { openSpan = it }) }
            }
        }
    }

    openSpan?.let { span ->
        InfoSheet(stringResource(R.string.highlight_why_title), if (filipino) span.flag.reasonFil else span.flag.reasonEn,
            onDismiss = { openSpan = null })
    }
}

@Composable
private fun GuideDetails(item: GuideItem, onSpanClick: (RiskSpan) -> Unit) {
    val c = Sipat.colors
    val context = LocalContext.current
    val example = stringResource(item.example).trim('"', '“', '”')
    val marks = remember(example) { context.graph.detector.marks(example) }
    Column(Modifier.padding(start = Space.l, end = Space.l, bottom = Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        SubLabel(stringResource(R.string.guide_example))
        SmsBubble { HighlightedMessage(example, marks, onSpanClick) }
        SubLabel(stringResource(R.string.guide_spot))
        stringResource(item.tips).lines().map { it.removePrefix("•").trim() }.filter { it.isNotEmpty() }
            .forEach { CheckRow(Icons.Rounded.Check, it, tint = c.ink, tileColor = c.wash) }
        // What to do, in a soft green box
        Row(Modifier.fillMaxWidth().background(c.safe.container, Radius.small).padding(Space.l), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = c.safe.strong, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Space.m))
            Column {
                Text(stringResource(R.string.guide_todo), style = Label.copy(fontWeight = FontWeight.SemiBold), color = c.safe.onContainer)
                Text(stringResource(item.todo), style = BodyL, color = c.safe.onContainer)
            }
        }
    }
}

/** Small uppercase-free section label inside a card. */
@Composable
private fun SubLabel(text: String) = Text(text, style = Badge, color = Sipat.colors.subtle)

/**
 * The example drawn like a received text message: grey avatar + "Unknown sender", then an incoming bubble.
 * Risky parts are highlighted inside (red = bad link / OTP, amber = pressure words).
 */
@Composable
private fun SmsBubble(content: @Composable () -> Unit) {
    val c = Sipat.colors
    Column(Modifier.fillMaxWidth().background(c.paper, Radius.small).padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(c.wash, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = c.subtle, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(Space.s))
            Text(stringResource(R.string.history_unknown_sender), style = BodyM.copy(fontWeight = FontWeight.Medium), color = c.subtle)
        }
        Box(
            Modifier.padding(start = 36.dp).widthIn(max = 320.dp)
                .background(c.card, Radius.bubble).border(1.dp, c.line, Radius.bubble)
                .padding(horizontal = Space.l, vertical = Space.m),
        ) { content() }
    }
}
