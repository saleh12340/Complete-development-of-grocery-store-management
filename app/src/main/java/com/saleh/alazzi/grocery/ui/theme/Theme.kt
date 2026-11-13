package com.saleh.alazzi.grocery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saleh.alazzi.grocery.ui.ThemeMode

@Immutable
data class AlAzziSemanticColors(
    val paidGreen: Color,
    val paidGreenContainer: Color,
    val debtRed: Color,
    val debtRedContainer: Color,
    val warningAmber: Color,
    val warningAmberContainer: Color,
    val borderSubtle: Color,
    val headerNavy: Color,
    val onHeaderNavy: Color
)

val LocalAlAzziColors = staticCompositionLocalOf {
    AlAzziSemanticColors(
        paidGreen = Color(0xFF15803D),
        paidGreenContainer = Color(0xFFDCFCE7),
        debtRed = Color(0xFFB91C1C),
        debtRedContainer = Color(0xFFFEE2E2),
        warningAmber = Color(0xFFB45309),
        warningAmberContainer = Color(0xFFFEF3C7),
        borderSubtle = Color(0xFFDCE4EE),
        headerNavy = Color(0xFF0F3D6E),
        onHeaderNavy = Color.White
    )
}

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0F3D6E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9F7),
    onPrimaryContainer = Color(0xFF0B2A4D),
    secondary = Color(0xFF15803D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCFCE7),
    onSecondaryContainer = Color(0xFF0C4A23),
    tertiary = Color(0xFF1E5B9A),
    error = Color(0xFFB91C1C),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFF3F6FA),
    onBackground = Color(0xFF101828),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101828),
    surfaceVariant = Color(0xFFE9EFF6),
    onSurfaceVariant = Color(0xFF475467),
    outline = Color(0xFFC8D3E0)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8CC8FF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF144272),
    onPrimaryContainer = Color(0xFFD5EAFF),
    secondary = Color(0xFF6EE7A0),
    onSecondary = Color(0xFF003919),
    secondaryContainer = Color(0xFF0F512B),
    onSecondaryContainer = Color(0xFFBBF7D0),
    tertiary = Color(0xFFA3D2FF),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Color(0xFF0D1520),
    onBackground = Color(0xFFEAF0F6),
    surface = Color(0xFF15202E),
    onSurface = Color(0xFFEAF0F6),
    surfaceVariant = Color(0xFF1E2D3F),
    onSurfaceVariant = Color(0xFFB4C0CD),
    outline = Color(0xFF314459)
)

private val AlAzziShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp)
)

private val AlAzziTypography = Typography(
    headlineSmall = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp)
)

@Composable
fun AlAzziTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val semantic = if (dark) {
        AlAzziSemanticColors(
            paidGreen = Color(0xFF6EE7A0),
            paidGreenContainer = Color(0xFF114627),
            debtRed = Color(0xFFFF9B9B),
            debtRedContainer = Color(0xFF5C1818),
            warningAmber = Color(0xFFFCD34D),
            warningAmberContainer = Color(0xFF593B0B),
            borderSubtle = Color(0xFF2B3C50),
            headerNavy = Color(0xFF133356),
            onHeaderNavy = Color(0xFFEAF0F6)
        )
    } else {
        AlAzziSemanticColors(
            paidGreen = Color(0xFF15803D),
            paidGreenContainer = Color(0xFFDCFCE7),
            debtRed = Color(0xFFB91C1C),
            debtRedContainer = Color(0xFFFEE2E2),
            warningAmber = Color(0xFFB45309),
            warningAmberContainer = Color(0xFFFEF3C7),
            borderSubtle = Color(0xFFDCE4EE),
            headerNavy = Color(0xFF0F3D6E),
            onHeaderNavy = Color.White
        )
    }

    CompositionLocalProvider(LocalAlAzziColors provides semantic) {
        MaterialTheme(
            colorScheme = if (dark) DarkColorScheme else LightColorScheme,
            typography = AlAzziTypography,
            shapes = AlAzziShapes,
            content = content
        )
    }
}
