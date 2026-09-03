package com.iykyk.facecollage.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Deliberately loud and high contrast: the audience is kids and teens.
val Ink = Color(0xFF16132A)
val InkSoft = Color(0xFF241F3F)
val Bubblegum = Color(0xFFFF4D8D)
val Mint = Color(0xFF35E0C8)
val Sunshine = Color(0xFFFFC94D)
val Cloud = Color(0xFFFFF6EE)

private val Dark = darkColorScheme(
    primary = Bubblegum,
    onPrimary = Cloud,
    secondary = Mint,
    onSecondary = Ink,
    tertiary = Sunshine,
    onTertiary = Ink,
    background = Ink,
    onBackground = Cloud,
    surface = InkSoft,
    onSurface = Cloud,
)

private val Light = lightColorScheme(
    primary = Bubblegum,
    onPrimary = Cloud,
    secondary = Mint,
    onSecondary = Ink,
    tertiary = Sunshine,
    onTertiary = Ink,
    background = Cloud,
    onBackground = Ink,
    surface = Color(0xFFFFFFFF),
    onSurface = Ink,
)

private val PlayfulType = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    labelLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun FaceCollageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = PlayfulType,
        content = content,
    )
}
