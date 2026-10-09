package com.example.sipatkilatis.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.sipatkilatis.R
import com.example.sipatkilatis.ui.components.AppTopBar
import com.example.sipatkilatis.ui.components.SectionCard
import com.example.sipatkilatis.ui.theme.ScamTint

/** One offline guide card. Text comes from string resources, so it follows the app language. */
private data class GuideCard(
    val icon: ImageVector,
    @param:StringRes val title: Int,
    @param:StringRes val example: Int,
    @param:StringRes val tips: Int,
)

private val cards = listOf(
    GuideCard(Icons.Filled.AccountBalance, R.string.guide_ewallet_title, R.string.guide_ewallet_example, R.string.guide_ewallet_tips),
    GuideCard(Icons.Filled.LocalShipping, R.string.guide_delivery_title, R.string.guide_delivery_example, R.string.guide_delivery_tips),
    GuideCard(Icons.Filled.Work, R.string.guide_job_title, R.string.guide_job_example, R.string.guide_job_tips),
    GuideCard(Icons.Filled.Payments, R.string.guide_loan_title, R.string.guide_loan_example, R.string.guide_loan_tips),
    GuideCard(Icons.Filled.CardGiftcard, R.string.guide_prize_title, R.string.guide_prize_example, R.string.guide_prize_tips),
    GuideCard(Icons.Filled.Password, R.string.guide_otp_title, R.string.guide_otp_example, R.string.guide_otp_tips),
)

/** Offline cards for common Philippine scams: an example and how to spot it. */
@Composable
fun ScamGuideScreen(onBack: () -> Unit) {
    Scaffold(topBar = { AppTopBar(stringResource(R.string.guide_title), onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.guide_intro), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(cards) { GuideCardView(it) }
        }
    }
}

@Composable
private fun GuideCardView(card: GuideCard) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(card.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(card.title), style = MaterialTheme.typography.titleMedium)
        }
        Text(stringResource(R.string.guide_example), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        // The example looks like a received text message
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ScamTint).padding(12.dp)
        ) {
            Text(stringResource(card.example), style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic, color = Color(0xFF15181E))
        }
        Text(stringResource(R.string.guide_spot), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(card.tips), style = MaterialTheme.typography.bodyMedium)
    }
}
