package com.example.sipatkilatis.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.sipatkilatis.model.Appearance
import com.example.sipatkilatis.model.Verdict
import com.example.sipatkilatis.ui.theme.Sipat
import com.example.sipatkilatis.ui.theme.SipatKilatisTheme
import com.example.sipatkilatis.ui.theme.Space

// Design check: the three verdicts (color + icon + words), dark mode, Filipino and 200% font.

@Composable
private fun VerdictTrio() {
    Column(Modifier.background(Sipat.colors.paper).padding(bottom = Space.l), verticalArrangement = Arrangement.spacedBy(Space.l)) {
        VerdictCard(Verdict.SAFE, 0.12f, 0.4f to 0.7f, Modifier.padding(horizontal = Space.screen))
        VerdictCard(Verdict.SUSPICIOUS, 0.55f, 0.4f to 0.7f, Modifier.padding(horizontal = Space.screen))
        VerdictCard(Verdict.SCAM, 0.93f, 0.4f to 0.7f, Modifier.padding(horizontal = Space.screen))
        Row(Modifier.padding(horizontal = Space.screen), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Verdict.entries.forEach { StatusBadge(it) }
        }
        FloatingNavBar(current = "home", onSelect = {})
    }
}

@Preview(name = "Verdicts - light", widthDp = 360)
@Composable
private fun VerdictsLight() = SipatKilatisTheme(Appearance.LIGHT) { VerdictTrio() }

@Preview(name = "Verdicts - dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VerdictsDark() = SipatKilatisTheme(Appearance.DARK) { VerdictTrio() }

@Preview(name = "Verdicts - Filipino", widthDp = 360, locale = "fil")
@Composable
private fun VerdictsFilipino() = SipatKilatisTheme(Appearance.LIGHT) { VerdictTrio() }

@Preview(name = "Verdicts - 200% font", widthDp = 360, fontScale = 2f)
@Composable
private fun VerdictsLargeFont() = SipatKilatisTheme(Appearance.LIGHT) { VerdictTrio() }
