package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Operations light theme - strictly light surfaces, slate text, muted blue primary
private val OperationsColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = PureWhite,
    primaryContainer = PrimaryBlueLight,
    onPrimaryContainer = PrimaryBlueDark,
    secondary = Slate700,
    onSecondary = PureWhite,
    secondaryContainer = Slate100,
    onSecondaryContainer = Slate900,
    tertiary = Slate600,
    onTertiary = PureWhite,
    background = Slate50,
    onBackground = Slate900,
    surface = PureWhite,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,
    outline = Slate300,
    outlineVariant = Slate200,
    error = EStopRed,
    onError = PureWhite,
    errorContainer = StatusRedBg,
    onErrorContainer = StatusRedText
)

// 6px / 6dp radius everywhere per spec
val OperationsShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Light theme only per spec
    dynamicColor: Boolean = false, // Keep consistent operations styling
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OperationsColorScheme,
        typography = Typography,
        shapes = OperationsShapes,
        content = content
    )
}
