package com.calcquest.kids.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcquest.kids.domain.quests.Topic

/** Soft paper-map palette. */
object MapColors {
    val Cream = Color(0xFFF6EBD3)
    val CreamDark = Color(0xFFEADBB8)
    val Paper = Color(0xFFFFFBF2)
    val Slate = Color(0xFF2F3A45)
    val SlateMuted = Color(0xFF5B6773)
    val Path = Color(0xFF8C7B5E)

    val Trail = Color(0xFF7FA77A)
    val TrailSoft = Color(0xFFDCEAD6)
    val Cave = Color(0xFF8FA9D6)
    val CaveSoft = Color(0xFFDDE7F6)
    val Tower = Color(0xFFB9A7DA)
    val TowerSoft = Color(0xFFEAE3F5)
    val Gate = Color(0xFFD9BE8C)
    val GateSoft = Color(0xFFF4E8D0)

    val Success = Color(0xFF3F7A45)
    val SuccessSoft = Color(0xFFDDEFD9)
    val Gentle = Color(0xFF9A6B2F)
    val GentleSoft = Color(0xFFF7E6CC)
    val Disabled = Color(0xFFB8B2A6)
}

fun Topic.accent(): Color = when (this) {
    Topic.ADDITION -> MapColors.Trail
    Topic.SUBTRACTION -> MapColors.Cave
    Topic.MULTIPLICATION -> MapColors.Tower
    Topic.DIVISION -> MapColors.Gate
}

fun Topic.soft(): Color = when (this) {
    Topic.ADDITION -> MapColors.TrailSoft
    Topic.SUBTRACTION -> MapColors.CaveSoft
    Topic.MULTIPLICATION -> MapColors.TowerSoft
    Topic.DIVISION -> MapColors.GateSoft
}

private val colors = lightColorScheme(
    primary = Color(0xFF4F6F52),
    onPrimary = Color.White,
    primaryContainer = MapColors.TrailSoft,
    onPrimaryContainer = MapColors.Slate,
    secondary = Color(0xFF52688F),
    onSecondary = Color.White,
    secondaryContainer = MapColors.CaveSoft,
    onSecondaryContainer = MapColors.Slate,
    tertiary = Color(0xFF6E5A93),
    tertiaryContainer = MapColors.TowerSoft,
    background = MapColors.Cream,
    onBackground = MapColors.Slate,
    surface = MapColors.Paper,
    onSurface = MapColors.Slate,
    surfaceVariant = MapColors.CreamDark,
    onSurfaceVariant = MapColors.SlateMuted,
    outline = MapColors.Path,
    error = Color(0xFF9A3B2F),
)

private val typography = Typography(
    displayLarge = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.SemiBold, lineHeight = 60.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
)

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun CalcQuestTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
