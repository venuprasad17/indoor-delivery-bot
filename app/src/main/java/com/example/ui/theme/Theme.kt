package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = ActionBlue,
    onPrimary = PureWhite,
    primaryContainer = ActionBlueSubtle,
    onPrimaryContainer = ActionBlueHover,
    secondary = Slate700,
    onSecondary = PureWhite,
    secondaryContainer = Slate100,
    onSecondaryContainer = Slate800,
    background = PureWhite,
    onBackground = Slate900,
    surface = PureWhite,
    onSurface = Slate900,
    surfaceVariant = SurfaceCanvas,
    onSurfaceVariant = Slate700,
    outline = BorderHairline,
    outlineVariant = Slate200,
    error = StatusRed,
    onError = PureWhite,
    errorContainer = StatusRedBg,
    onErrorContainer = StatusRed
)

val TechnicalShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(6.dp)
)

@Composable
fun RobotOperatorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        shapes = TechnicalShapes,
        content = content
    )
}
