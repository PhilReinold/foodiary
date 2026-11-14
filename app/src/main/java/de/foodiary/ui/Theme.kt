package de.foodiary.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.foodiary.R
import de.foodiary.data.MealType

/** Farben aus dem Design-System „Ernährungstagebuch“, Version 3 (Hell-Theme). */
object Palette {
    val Surface100 = Color(0xFFFBF1EA)
    val Surface200 = Color(0xFFFFF8F1)
    val Ink = Color(0xFF2D2B2F)
    val InkMuted = Color(0xFF5B5862)
    val Lavender = Color(0xFFE6DCF6)
    val LavenderDeep = Color(0xFFBFB0DC)
    val Peach = Color(0xFFFFD9C2)
    val SoftRed = Color(0xFFF7B8B8)
    val Cream = Color(0xFFFFF8F1)
    val SlateSoft = Color(0xFFD6E0EA)
    val Outline = Color(0xFF9A8BB8)
}

/** Mahlzeit-Farben laut Designgrundlagen: Frühstück Pfirsich, Mittag Slate, Snack Lavendel, Abend Rot. */
fun MealType.color(): Color = when (this) {
    MealType.FRUEHSTUECK -> Palette.Peach
    MealType.MITTAG -> Palette.SlateSoft
    MealType.ABEND -> Palette.SoftRed
    MealType.SNACK -> Palette.Lavender
}

private val FoodiaryScheme = lightColorScheme(
    primary = Palette.Peach,
    onPrimary = Palette.Ink,
    primaryContainer = Palette.Peach,
    onPrimaryContainer = Palette.Ink,
    secondary = Palette.LavenderDeep,
    onSecondary = Palette.Ink,
    secondaryContainer = Palette.Lavender,
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.SoftRed,
    onTertiary = Palette.Ink,
    background = Palette.Surface100,
    onBackground = Palette.Ink,
    surface = Palette.Surface200,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Cream,
    onSurfaceVariant = Palette.InkMuted,
    outline = Palette.Outline
)

/** Radien laut Design-System: Chips 6 dp, Karten und Eingabefelder 16 dp. */
private val FoodiaryShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

/** Nunito, eingebunden aus res/font (Open Font License). */
private val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold)
)

/**
 * Textstile laut tokens.json: display 30/36 (800), title 22/28 (700), body 16/24 (400),
 * label 14/20 (700), caption 12/16 (600). Zwischenstufen sind daraus abgeleitet.
 */
private fun style(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = Nunito,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp
)

private val FoodiaryTypography = Typography(
    displayLarge = style(30, 36, FontWeight.ExtraBold),
    displayMedium = style(30, 36, FontWeight.ExtraBold),
    displaySmall = style(30, 36, FontWeight.ExtraBold),
    headlineLarge = style(30, 36, FontWeight.ExtraBold),
    headlineMedium = style(24, 32, FontWeight.ExtraBold),
    headlineSmall = style(22, 28, FontWeight.Bold),
    titleLarge = style(22, 28, FontWeight.Bold),
    titleMedium = style(16, 24, FontWeight.Bold),
    titleSmall = style(14, 20, FontWeight.Bold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(16, 24, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.SemiBold),
    labelLarge = style(14, 20, FontWeight.Bold),
    labelMedium = style(12, 16, FontWeight.SemiBold),
    labelSmall = style(12, 16, FontWeight.SemiBold)
)

@Composable
fun FoodiaryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FoodiaryScheme,
        shapes = FoodiaryShapes,
        typography = FoodiaryTypography,
        content = content
    )
}
