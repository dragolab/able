package com.dragolabs.able.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.dragolabs.able.core.designsystem.R
import com.dragolabs.able.core.designsystem.component.LocalAbleHapticsEnabled
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.model.ThemeMode

/**
 * Expressive shape scale: generous rounding for cards and containers, fully rounded
 * buttons come from the component defaults.
 */
internal val AbleShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val BaseTypography = Typography()

/**
 * Google Sans Flex (SIL OFL 1.1, bundled unmodified; license in assets/licenses). Display and
 * headline styles use the ROND axis for the softer, expressive Able look (BRAND-001).
 */
@OptIn(ExperimentalTextApi::class)
private fun flex(weight: Int, round: Float = 0f): FontFamily = FontFamily(
    Font(
        R.font.google_sans_flex,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.Setting("ROND", round),
        ),
    ),
)

private val DisplayFlex = flex(weight = 600, round = 100f)
private val HeadlineFlex = flex(weight = 600, round = 60f)
private val TitleFlex = flex(weight = 600)
private val BodyFlex = flex(weight = 400)
private val LabelFlex = flex(weight = 500)

/** Material 3 type scale on Google Sans Flex; emphasized variants are derived by Material. */
internal val AbleTypography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = DisplayFlex),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = DisplayFlex),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = DisplayFlex),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = HeadlineFlex),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = HeadlineFlex),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = HeadlineFlex),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = TitleFlex),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = TitleFlex),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = TitleFlex),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = BodyFlex),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = BodyFlex),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = BodyFlex),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = LabelFlex),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = LabelFlex),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = LabelFlex),
)

/**
 * Able theme (ADR-004): Material 3 Expressive with Android dynamic color, System/Light/Dark
 * selection and a coherent fallback palette when dynamic color is unavailable.
 */
@Composable
fun AbleTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> AbleDarkColorScheme
        else -> AbleLightColorScheme
    }
    val semantic = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(
        LocalAbleSemanticColors provides semantic,
        LocalAbleHapticsEnabled provides hapticsEnabled,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = AbleShapes,
            typography = AbleTypography,
            content = content,
        )
    }
}

/** Accessors for Able-specific theme values. */
object AbleThemeExtras {
    val semantic: AbleSemanticColors
        @Composable @ReadOnlyComposable get() = LocalAbleSemanticColors.current
}

/** Standard spacing tokens (4dp grid). */
object AbleSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Horizontal gutter for screen content on compact windows. */
    val screenGutter = 16.dp

    /** Maximum readable width for single-column content on wide windows. */
    val maxContentWidth = 840.dp
}
