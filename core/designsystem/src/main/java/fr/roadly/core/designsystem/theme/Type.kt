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

fun roadlyTypography(scale: Float = 1f) = Typography(
    headlineMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 28.sp * scale, lineHeight = 34.sp * scale),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 22.sp * scale, lineHeight = 28.sp * scale),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 18.sp * scale, lineHeight = 24.sp * scale),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp * scale, lineHeight = 24.sp * scale),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp * scale, lineHeight = 20.sp * scale),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp * scale, lineHeight = 20.sp * scale),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp * scale, lineHeight = 16.sp * scale),
)

/** Chiffres de navigation (vitesse, distance) : grands, tabulaires. */
val NavDigits = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 52.sp,
    fontFeatureSettings = "tnum", textAlign = TextAlign.Center,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
