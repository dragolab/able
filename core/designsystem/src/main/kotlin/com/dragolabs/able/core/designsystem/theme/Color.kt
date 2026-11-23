package com.dragolabs.able.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Fallback palette used when Android dynamic color is unavailable (docs/ux/adaptive-ui.md).
 * Brand palette (BRAND-001): Able teal with a coral accent. Tones follow the Material 3 tonal palette roles.
 */

internal val AbleLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF00677D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4EBFF),
    onPrimaryContainer = Color(0xFF004E5F),
    secondary = Color(0xFF4B6269),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE7EF),
    onSecondaryContainer = Color(0xFF344A51),
    tertiary = Color(0xFF9A4524),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCF),
    onTertiaryContainer = Color(0xFF7B2E0F),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF5FAFC),
    onBackground = Color(0xFF171C1F),
    surface = Color(0xFFF5FAFC),
    onSurface = Color(0xFF171C1F),
    surfaceVariant = Color(0xFFDBE4E8),
    onSurfaceVariant = Color(0xFF3F484C),
    outline = Color(0xFF6F797C),
    outlineVariant = Color(0xFFBFC8CC),
    inverseSurface = Color(0xFF2C3134),
    inverseOnSurface = Color(0xFFEDF1F4),
    inversePrimary = Color(0xFF5DD5FC),
    surfaceDim = Color(0xFFD6DBDE),
    surfaceBright = Color(0xFFF5FAFC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF4F7),
    surfaceContainer = Color(0xFFE9EFF1),
    surfaceContainerHigh = Color(0xFFE4E9EC),
    surfaceContainerHighest = Color(0xFFDEE3E6),
)

internal val AbleDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF5DD5FC),
    onPrimary = Color(0xFF003542),
    primaryContainer = Color(0xFF004E5F),
    onPrimaryContainer = Color(0xFFB4EBFF),
    secondary = Color(0xFFB2CBD3),
    onSecondary = Color(0xFF1D343A),
    secondaryContainer = Color(0xFF344A51),
    onSecondaryContainer = Color(0xFFCEE7EF),
    tertiary = Color(0xFFFFB59A),
    onTertiary = Color(0xFF5C1A00),
    tertiaryContainer = Color(0xFF7B2E0F),
    onTertiaryContainer = Color(0xFFFFDBCF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1417),
    onBackground = Color(0xFFDEE3E6),
    surface = Color(0xFF0F1417),
    onSurface = Color(0xFFDEE3E6),
    surfaceVariant = Color(0xFF3F484C),
    onSurfaceVariant = Color(0xFFBFC8CC),
    outline = Color(0xFF899296),
    outlineVariant = Color(0xFF3F484C),
    inverseSurface = Color(0xFFDEE3E6),
    inverseOnSurface = Color(0xFF2C3134),
    inversePrimary = Color(0xFF00677D),
    surfaceDim = Color(0xFF0F1417),
    surfaceBright = Color(0xFF353A3D),
    surfaceContainerLowest = Color(0xFF0A0F12),
    surfaceContainerLow = Color(0xFF171C1F),
    surfaceContainer = Color(0xFF1B2023),
    surfaceContainerHigh = Color(0xFF252B2D),
    surfaceContainerHighest = Color(0xFF303638),
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
