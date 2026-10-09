package com.example.sipatkilatis.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.sipatkilatis.R

/** Inter, bundled in res/font (works offline). License: assets/licenses/OFL-Inter.txt */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) =
    TextStyle(fontFamily = Inter, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.sp)

// Hierarchy by weight: Bold for big numbers / titles, SemiBold for headings, Medium for labels and badges.
val Display = style(32, 38, FontWeight.Bold, -0.6)       // gauge number, stat numbers
val Headline = style(26, 32, FontWeight.Bold, -0.4)      // screen titles, verdict word
val Title = style(18, 24, FontWeight.SemiBold, -0.2)     // card titles
val BodyL = style(16, 24, FontWeight.Normal)
val BodyM = style(14, 20, FontWeight.Normal)
val Label = style(14, 20, FontWeight.Medium)
val Badge = style(12, 16, FontWeight.SemiBold, 0.2)      // status badges, nav labels

/** The message being inspected: a little extra letter spacing makes look-alike characters easier to tell apart. */
val MessageText = BodyL.copy(letterSpacing = 0.15.sp)

val Typography = Typography(
    displayLarge = Display, displayMedium = Display, displaySmall = Display,
    headlineLarge = Headline, headlineMedium = Headline, headlineSmall = Headline,
    titleLarge = Title, titleMedium = Title.copy(fontSize = 16.sp, lineHeight = 22.sp), titleSmall = Label,
    bodyLarge = BodyL, bodyMedium = BodyM, bodySmall = BodyM,
    labelLarge = Label, labelMedium = Label, labelSmall = Badge,
)
