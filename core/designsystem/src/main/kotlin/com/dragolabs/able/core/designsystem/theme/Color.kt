package com.dragolabs.able.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Pulse brand colors (BRAND-001, concept "D · Pulse", chosen 2026-11-23): ink neutrals with a
 * single mint accent. [Mint] is the reference accent; [MintBright] is its variant for dark
 * backgrounds (launcher icon, dark theme) and [MintDeep] the variant that keeps text contrast
 * on light surfaces. Brand asset reserved to Drago Labs (ADR-011).
 */
object AbleBrandColors {
    val Ink = Color(0xFF101418)
    val InkSoft = Color(0xFF2A3138)
    val Mint = Color(0xFF1FC98E)
    val MintBright = Color(0xFF3DF2B0)
    val MintDeep = Color(0xFF006C4C)
    val MintContainer = Color(0xFFD6F7EA)
    val MintIndicator = Color(0xFFC7EEDD)
    val OnMintContainer = Color(0xFF00281A)
    val Mist = Color(0xFFF2F4F5)
}

/*
 * Fallback palette used when Android dynamic color is off or unavailable (docs/ux/adaptive-ui.md),
 * built from the Pulse palette: ink neutral surfaces and mint primary. Tertiary stays a warm
 * coral so warnings, badges and secondary emphasis keep a clear contrast with the mint accent.
 */

internal val AbleLightColorScheme: ColorScheme = lightColorScheme(
    primary = AbleBrandColors.MintDeep,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = AbleBrandColors.MintContainer,
    onPrimaryContainer = AbleBrandColors.OnMintContainer,
    secondary = Color(0xFF45574F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = AbleBrandColors.MintIndicator,
    onSecondaryContainer = Color(0xFF0E2119),
    tertiary = Color(0xFF9A4524),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCF),
    onTertiaryContainer = Color(0xFF7B2E0F),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF6F8F9),
    onBackground = AbleBrandColors.Ink,
    surface = Color(0xFFF6F8F9),
    onSurface = AbleBrandColors.Ink,
    surfaceVariant = Color(0xFFDCE2E5),
    onSurfaceVariant = Color(0xFF41494F),
    outline = Color(0xFF71797F),
    outlineVariant = Color(0xFFC1C8CC),
    scrim = Color(0xFF000000),
    inverseSurface = AbleBrandColors.InkSoft,
    inverseOnSurface = Color(0xFFEEF1F3),
    inversePrimary = AbleBrandColors.MintBright,
    surfaceDim = Color(0xFFD6DADD),
    surfaceBright = Color(0xFFF6F8F9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = AbleBrandColors.Mist,
    surfaceContainer = Color(0xFFECEFF1),
    surfaceContainerHigh = Color(0xFFE6EAEC),
    surfaceContainerHighest = Color(0xFFE0E4E7),
)

internal val AbleDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF45E0A6),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFFB9F6DB),
    secondary = Color(0xFFADCBBE),
    onSecondary = Color(0xFF18352A),
    secondaryContainer = Color(0xFF2B4239),
    onSecondaryContainer = AbleBrandColors.MintIndicator,
    tertiary = Color(0xFFFFB59A),
    onTertiary = Color(0xFF5C1A00),
    tertiaryContainer = Color(0xFF7B2E0F),
    onTertiaryContainer = Color(0xFFFFDBCF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = AbleBrandColors.Ink,
    onBackground = Color(0xFFE1E5E8),
    surface = AbleBrandColors.Ink,
    onSurface = Color(0xFFE1E5E8),
    surfaceVariant = Color(0xFF3A4248),
    onSurfaceVariant = Color(0xFFBEC6CB),
    outline = Color(0xFF889095),
    outlineVariant = Color(0xFF3A4248),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE1E5E8),
    inverseOnSurface = AbleBrandColors.InkSoft,
    inversePrimary = AbleBrandColors.MintDeep,
    surfaceDim = AbleBrandColors.Ink,
    surfaceBright = Color(0xFF363C42),
    surfaceContainerLowest = Color(0xFF0B0E11),
    surfaceContainerLow = Color(0xFF181C20),
    surfaceContainer = Color(0xFF1C2125),
    surfaceContainerHigh = Color(0xFF262B30),
    surfaceContainerHighest = AbleBrandColors.InkSoft,
)

/**
 * Semantic colors that Material 3 does not define but Able needs for honest status reporting
 * (success, warning). Error uses the standard color scheme roles. Never use color alone:
 * every status also carries an icon and a label.
 */
@Immutable
data class AbleSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

internal val LightSemanticColors = AbleSemanticColors(
    success = Color(0xFF2E6B2F),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFB1F2A9),
    onSuccessContainer = Color(0xFF14511A),
    warning = Color(0xFF7A5900),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDEA6),
    onWarningContainer = Color(0xFF5C4200),
)

internal val DarkSemanticColors = AbleSemanticColors(
    success = Color(0xFF96D68F),
    onSuccess = Color(0xFF003909),
    successContainer = Color(0xFF14511A),
    onSuccessContainer = Color(0xFFB1F2A9),
    warning = Color(0xFFF4BF48),
    onWarning = Color(0xFF402D00),
    warningContainer = Color(0xFF5C4200),
    onWarningContainer = Color(0xFFFFDEA6),
)

val LocalAbleSemanticColors = staticCompositionLocalOf { LightSemanticColors }
