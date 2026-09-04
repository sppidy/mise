// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox.ui.design

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mise.recipebox.R

data class MiseColors(
    val deep: Color,
    val cream: Color,
    val paper: Color,
    val ink: Color,
    val ink2: Color,
    val orange: Color,
    val yellow: Color,
    val paleGreen: Color,
    val onPaleGreen: Color,
    val line: Color,
    val muted: Color,
    val card: Color,
    val scrim: Color,
)

val LightMiseColors = MiseColors(
    deep = Color(0xFF17382F), cream = Color(0xFFF6F4ED), paper = Color(0xFFFFFEFA),
    ink = Color(0xFF17382F), ink2 = Color(0xFF596A64), orange = Color(0xFFE97243),
    yellow = Color(0xFFF1C95C), paleGreen = Color(0xFFD8E6D6), onPaleGreen = Color(0xFF17382F), line = Color(0xFFE5E2D8),
    muted = Color(0xFF89948F), card = Color(0xFFFFFEFA), scrim = Color(0x660D1713),
)
val DarkMiseColors = MiseColors(
    deep = Color(0xFF1C483B), cream = Color(0xFF101815), paper = Color(0xFF17221E),
    ink = Color(0xFFEDF4F0), ink2 = Color(0xFFC5D0CB), orange = Color(0xFFE97243),
    yellow = Color(0xFFF1C95C), paleGreen = Color(0xFF263D34), onPaleGreen = Color(0xFFEDF4F0), line = Color(0xFF303B37),
    muted = Color(0xFF9BAAA4), card = Color(0xFF17221E), scrim = Color(0x99000000),
)

val DmSans = FontFamily(
    Font(R.font.dm_sans, FontWeight.Normal),
    Font(R.font.dm_sans, FontWeight.Medium),
    Font(R.font.dm_sans, FontWeight.SemiBold),
    Font(R.font.dm_sans, FontWeight.Bold),
)
val DmSerif = FontFamily(Font(R.font.dm_serif_display, FontWeight.Normal))
val DmSerifItalic = FontFamily(Font(R.font.dm_serif_display_italic, FontWeight.Normal, FontStyle.Italic))

data class MiseTypography(
    val body: TextStyle = TextStyle(fontFamily = DmSans, fontSize = 13.sp, lineHeight = 20.sp),
    val bodySmall: TextStyle = TextStyle(fontFamily = DmSans, fontSize = 11.sp, lineHeight = 16.sp),
    val label: TextStyle = TextStyle(fontFamily = DmSans, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold),
    val eyebrow: TextStyle = TextStyle(fontFamily = DmSans, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    val title: TextStyle = TextStyle(fontFamily = DmSerif, fontSize = 38.sp, lineHeight = 40.sp),
    val section: TextStyle = TextStyle(fontFamily = DmSerif, fontSize = 25.sp, lineHeight = 29.sp),
    val cardTitle: TextStyle = TextStyle(fontFamily = DmSans, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
)

val LocalMiseColors = staticCompositionLocalOf { LightMiseColors }
val LocalMiseTypography = staticCompositionLocalOf { MiseTypography() }

@Composable
fun MiseTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalMiseColors provides if (dark) DarkMiseColors else LightMiseColors,
        LocalMiseTypography provides MiseTypography(),
        content = content,
    )
}

object MiseShapes {
    val card = RoundedCornerShape(16.dp)
    val button = RoundedCornerShape(10.dp)
    val sheet = RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp)
}

fun Modifier.contentPadding(phone: Boolean): Modifier = padding(horizontal = if (phone) 18.dp else 36.dp)
