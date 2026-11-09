package com.znfdev.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palette. Surfaces step up in lightness: Panel < PanelRaised < Cell < CellHigh < Pill. */
object ZevColors {
    val Background = Color(0xFF141218)
    val Scrim = Color(0x99000000)

    val Panel = Color(0xFF1D1B20)
    val PanelRaised = Color(0xFF2B2930)
    val Cell = Color(0xFF34313A)
    val CellHigh = Color(0xFF3A3740)
    val Pill = Color(0xFF5C5960)
    val Editor = Color(0xFF17151A)

    val Text = Color(0xFFE6E0E9)
    val TextDim = Color(0xFF9C96A0)
    val Outline = Color(0x33FFFFFF)

    val Primary = Color(0xFFD0BCFF)
    val OnPrimary = Color(0xFF381E72)
    val PrimaryContainer = Color(0xFF4F378B)
    val OnPrimaryContainer = Color(0xFFEADDFF)

    val Green = Color(0xFF4FA383)
    val Gold = Color(0xFFE0B84A)
    val Danger = Color(0xFFF2B8B5)
    val LuaBlue = Color(0xFF000080)

    // code editor (One Dark flavoured)
    val CodeText = Color(0xFFABB2BF)
    val CodeKeyword = Color(0xFFC678DD)
    val CodeString = Color(0xFF98C379)
    val CodeComment = Color(0xFF5C6370)
    val CodeNumber = Color(0xFFD19A66)
    val CodeCall = Color(0xFF61AFEF)
    val CodeGlobal = Color(0xFFE5C07B)
    val CodeSelf = Color(0xFFE06C75)
    val CodeGutter = Color(0xFF4B4F5A)
}

object ZevShapes {
    val Small = RoundedCornerShape(12.dp)
    val Medium = RoundedCornerShape(16.dp)
    val Card = RoundedCornerShape(18.dp)
    val Large = RoundedCornerShape(24.dp)
    val Panel = RoundedCornerShape(26.dp)
    val Pill = RoundedCornerShape(50)
}

/** Shared motion curves so every screen moves the same way. */
object ZevMotion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    fun <T> enter(ms: Int = 260, delay: Int = 0): TweenSpec<T> = tween(ms, delay, Decelerate)
    fun <T> exit(ms: Int = 160): TweenSpec<T> = tween(ms, 0, Accelerate)
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.72f, stiffness = 420f)
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 700f)
}

/** The bundled Material Symbols font (assets/znfdev). Null if it failed to load. */
val LocalIconFont = compositionLocalOf<FontFamily?> { null }

private fun loadIconFont(context: Context): FontFamily? = try {
    FontFamily(Typeface.createFromAsset(context.applicationContext.assets, "znfdev"))
} catch (t: Throwable) {
    null
}

private val ZevTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun ZevTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val iconFont = remember(context) { loadIconFont(context) }
    val scheme = darkColorScheme(
        primary = ZevColors.Primary,
        onPrimary = ZevColors.OnPrimary,
        primaryContainer = ZevColors.PrimaryContainer,
        onPrimaryContainer = ZevColors.OnPrimaryContainer,
        secondary = ZevColors.Green,
        background = ZevColors.Background,
        onBackground = ZevColors.Text,
        surface = ZevColors.Panel,
        onSurface = ZevColors.Text,
        surfaceVariant = ZevColors.Cell,
        onSurfaceVariant = ZevColors.TextDim,
        outline = ZevColors.Outline,
        error = ZevColors.Danger
    )
    CompositionLocalProvider(LocalIconFont provides iconFont) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ZevTypography,
            shapes = Shapes(
                small = ZevShapes.Small,
                medium = ZevShapes.Medium,
                large = ZevShapes.Large
            ),
            content = content
        )
    }
}
