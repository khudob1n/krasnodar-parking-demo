package ru.krasnodar.parking.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.krasnodar.parking.R

/** Направление «Чистый». */
object Brand {
    val Blue = Color(0xFF0066FF)
    val Orange = Color(0xFFFF8A00)
    val Background = Color(0xFFF8FAFC)
    val Text = Color(0xFF1E293B)
    val Muted = Color(0xFF64748B)
    val Divider = Color(0xFFE2E8F0)
    val SegmentInactive = Color(0xFF9DBEF5)
    val Success = Color(0xFF16A34A)
    val Error = Color(0xFFDC2626)
    val Graphite = Color(0xFF1B2028)
    val GraphiteCard = Color(0xFF242B35)
}

/** Оригинальный Onest (SIL OFL 1.1), кириллица и латиница. */
val Onest = FontFamily(
    Font(R.font.onest_regular, FontWeight.Normal),
    Font(R.font.onest_medium, FontWeight.Medium),
    Font(R.font.onest_semibold, FontWeight.SemiBold),
    Font(R.font.onest_bold, FontWeight.Bold),
)

// Размеры из дизайн-системы: экран 32, раздел 24, карточка 20, важное 18,
// текст 16, подписи 14, 12 — только при необходимости.
private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Onest, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Onest, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
    bodyLarge = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Onest, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    labelMedium = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

private val Light = lightColorScheme(
    primary = Brand.Blue,
    onPrimary = Color.White,
    secondary = Brand.Orange,
    background = Brand.Background,
    onBackground = Brand.Text,
    surface = Color.White,
    onSurface = Brand.Text,
    surfaceVariant = Color(0xFFEFF4FF),
    onSurfaceVariant = Brand.Muted,
    outline = Brand.Divider,
    error = Brand.Error,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF4D8DFF),
    onPrimary = Color.White,
    secondary = Brand.Orange,
    background = Brand.Graphite,
    onBackground = Color(0xFFE2E8F0),
    surface = Brand.GraphiteCard,
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF2D3642),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    error = Color(0xFFF87171),
)

/** Отступы: шаг 8, поля экрана 24. */
object Spacing {
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
}

@Composable
fun KrasnodarParkingTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
        content = content,
    )
}
