package com.example.sipatkilatis.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Soft, rounded shapes by role. */
object Radius {
    val input = RoundedCornerShape(16.dp)       // text fields, search
    val button = RoundedCornerShape(16.dp)
    val card = RoundedCornerShape(20.dp)        // content cards
    val hero = RoundedCornerShape(28.dp)        // verdict card, protection card
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val small = RoundedCornerShape(12.dp)       // icon tiles, inner boxes
    val pill = RoundedCornerShape(50)           // badges, filter tabs, nav bar
    /** Chat bubble: flat corner where the "tail" would be. */
    val bubble = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
}

val AppShapes = Shapes(
    extraSmall = Radius.small,
    small = Radius.small,
    medium = Radius.button,
    large = Radius.card,
    extraLarge = Radius.hero,
)
