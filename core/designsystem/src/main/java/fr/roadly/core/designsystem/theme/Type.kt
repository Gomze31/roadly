package fr.roadly.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import fr.roadly.core.designsystem.R

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val noPadding = PlatformTextStyle(includeFontPadding = false)

fun roadlyTypography(scale: Float = 1f) = Typography(
    displayLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 40.sp * scale, lineHeight = 44.sp * scale, letterSpacing = (-0.5).sp, platformStyle = noPadding),
    displayMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 32.sp * scale, lineHeight = 36.sp * scale, letterSpacing = (-0.4).sp, platformStyle = noPadding),
    headlineLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 30.sp * scale, lineHeight = 36.sp * scale, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 26.sp * scale, lineHeight = 32.sp * scale, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 22.sp * scale, lineHeight = 28.sp * scale),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp * scale, lineHeight = 26.sp * scale),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp * scale, lineHeight = 22.sp * scale),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp * scale, lineHeight = 20.sp * scale),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp * scale, lineHeight = 23.sp * scale),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp * scale, lineHeight = 20.sp * scale),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp * scale, lineHeight = 16.sp * scale),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp * scale, lineHeight = 20.sp * scale),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp * scale, lineHeight = 16.sp * scale),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp * scale, lineHeight = 14.sp * scale, letterSpacing = 1.2.sp),
)

/** Chiffres de navigation (vitesse, distance) : grands, tabulaires. */
val NavDigits = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 52.sp,
    fontFeatureSettings = "tnum", textAlign = TextAlign.Center,
    platformStyle = noPadding,
)

/** Petit titre en capitales espacées (« VOTRE CHOIX », « DENSITÉ À VENIR »). */
val Overline = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp,
    platformStyle = noPadding,
)

/** Valeur de statistique (ETA, minutes, km) : tabulaire, sans marge interne. */
val StatValue = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp,
    fontFeatureSettings = "tnum", platformStyle = noPadding,
)
