package com.banana.shotrecord.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** 品牌视觉令牌:渐变、玻璃卡片描边等,与 Material 配色解耦 */
data class BrandColors(
    val brandGradient: Brush,
    val recordGradient: Brush,
    val glowColor: Color,
    val cardBorder: Color,
)

private val DarkBrand = BrandColors(
    brandGradient = Brush.linearGradient(listOf(Color(0xFF8B7CFF), Color(0xFF5AD7FF))),
    recordGradient = Brush.linearGradient(listOf(Color(0xFFFF4D67), Color(0xFFFF7A45))),
    glowColor = Color(0xFF8B7CFF),
    cardBorder = Color(0x14FFFFFF),
)

private val LightBrand = BrandColors(
    brandGradient = Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF00B8D4))),
    recordGradient = Brush.linearGradient(listOf(Color(0xFFFF4D67), Color(0xFFFF7A45))),
    glowColor = Color(0xFF6C5CE7),
    cardBorder = Color(0x14000000),
)

val LocalBrand = staticCompositionLocalOf { DarkBrand }

/**
 * 自定义视觉身份("Studio"暗色系):石墨底色 + 紫青品牌渐变。
 * 刻意不用动态取色——保持品牌一致性。
 */
@Composable
fun ShotRecordTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        // 仅在 Android 12+ 取系统配色作参考太"普通",直接用品牌色
        darkTheme -> darkColorScheme(
            primary = Color(0xFF9D8FFF),
            onPrimary = Color(0xFF0A0B0F),
            secondary = Color(0xFF5AD7FF),
            background = Color(0xFF0A0B0F),
            onBackground = Color(0xFFEDEEF2),
            surface = Color(0xFF12141B),
            onSurface = Color(0xFFEDEEF2),
            surfaceVariant = Color(0xFF1A1D26),
            onSurfaceVariant = Color(0xFF9CA3B2),
            error = Color(0xFFFF5A76),
            outline = Color(0xFF262B36),
        )
        else -> lightColorScheme(
            primary = Color(0xFF5B4EE0),
            onPrimary = Color(0xFFFFFFFF),
            secondary = Color(0xFF0097B2),
            background = Color(0xFFF4F5F9),
            onBackground = Color(0xFF171923),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF171923),
            surfaceVariant = Color(0xFFEBEDF4),
            onSurfaceVariant = Color(0xFF5C6270),
            error = Color(0xFFE5484D),
            outline = Color(0xFFD9DCE5),
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalBrand provides if (darkTheme) DarkBrand else LightBrand,
                content = content,
            )
        },
    )
}
