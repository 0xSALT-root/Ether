package com.example.ether.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

enum class AppTheme(val displayName: String, val primaryColor: Color) {
    DEFAULT("Ether Classic", White),
    OCEAN("Ocean Blue", Color(0xFF2196F3)),
    FOREST("Forest Green", Color(0xFF4CAF50)),
    SUNSET("Sunset Orange", Color(0xFFFF5722)),
    LAVENDER("Lavender Purple", Color(0xFF9C27B0)),
    ROSE("Rose Pink", Color(0xFFE91E63)),
    TOMATO("Tomato Red", Color(0xFFF44336)),
    AMBER("Amber Glow", Color(0xFFFFC107)),
    TEAL("Teal Calm", Color(0xFF009688)),
    INDIGO("Indigo Night", Color(0xFF3F51B5)),
    SLATE("Slate Gray", Color(0xFF607D8B)),
    MINT("Mint Fresh", Color(0xFF00BFA5)),
    GOLD("Royal Gold", Color(0xFFFFD700)),
    CORAL("Coral Reef", Color(0xFFFF7F50)),
    SKY("Sky Blue", Color(0xFF87CEEB)),
    OLIVE("Olive Drab", Color(0xFF6B8E23)),
    CHOCOLATE("Chocolate", Color(0xFFD2691E)),
    ORCHID("Orchid", Color(0xFFDA70D6)),
    SAND("Desert Sand", Color(0xFFEDC9AF)),
    LEMON("Lemon Zest", Color(0xFFFFF700)),
    MIDNIGHT("Midnight Dark", Color(0xFF1A237E)),
    BURGUNDY("Deep Burgundy", Color(0xFF880E4F)),
    COFFEE("Coffee Brown", Color(0xFF4E342E)),
    NEON("Neon Cyber", Color(0xFF00FF41)),
    CYBERPUNK("Cyberpunk Pink", Color(0xFFFF00FF)),
    BERRY("Berry Purple", Color(0xFF6A1B9A)),
    STEEL("Steel Gray", Color(0xFF455A64)),
    CANDY("Candy Cotton", Color(0xFFFF80AB)),
    EMERALD("Emerald Green", Color(0xFF2E7D32)),
    COBALT("Cobalt Blue", Color(0xFF1976D2))
}

private fun Color.lerp(other: Color, fraction: Float): Color {
    return Color(
        red = red + (other.red - red) * fraction,
        green = green + (other.green - green) * fraction,
        blue = blue + (other.blue - blue) * fraction,
        alpha = alpha + (other.alpha - alpha) * fraction
    )
}

// Stronger tint for white text in dark mode
private fun getTintedWhite(primary: Color): Color {
    return Color.White.copy(alpha = 0.80f).compositeOver(primary.copy(alpha = 0.20f))
}

// SIGNIFICANTLY stronger tinted black for light mode (75% black, 25% primary)
private fun getTintedBlack(primary: Color): Color {
    return Color.Black.copy(alpha = 0.75f).compositeOver(primary.copy(alpha = 0.25f))
}

private fun getDarkColorScheme(primary: Color): ColorScheme {
    val tintedWhite = getTintedWhite(primary)
    return darkColorScheme(
        primary = primary,
        onPrimary = if (primary == Color.White) Color.Black else tintedWhite,
        primaryContainer = if (primary == Color.White) LightGray else primary.copy(alpha = 0.5f),
        onPrimaryContainer = tintedWhite,
        secondary = primary.copy(alpha = 0.85f),
        onSecondary = Color.Black,
        secondaryContainer = primary.copy(alpha = 0.3f),
        onSecondaryContainer = tintedWhite,
        tertiary = AccentGray,
        background = if (primary == Color.White) DarkGray else primary.lerp(Color.Black, 0.92f),
        // Darkened surface for dialogs/popups to be clearly themed
        surface = if (primary == Color.White) DarkGray else primary.lerp(Color.Black, 0.85f),
        surfaceVariant = if (primary == Color.White) LightGray else primary.lerp(Color.Black, 0.78f),
        onBackground = tintedWhite,
        onSurface = tintedWhite,
        onSurfaceVariant = tintedWhite.copy(alpha = 0.85f)
    )
}

private fun getAmoledColorScheme(primary: Color): ColorScheme {
    val tintedWhite = getTintedWhite(primary)
    return darkColorScheme(
        primary = primary,
        onPrimary = if (primary == Color.White) Color.Black else tintedWhite,
        primaryContainer = if (primary == Color.White) LightGray else primary.copy(alpha = 0.5f),
        onPrimaryContainer = tintedWhite,
        secondary = primary.copy(alpha = 0.85f),
        onSecondary = Color.Black,
        secondaryContainer = primary.copy(alpha = 0.3f),
        onSecondaryContainer = tintedWhite,
        tertiary = AccentGray,
        background = Color.Black,
        surface = Color.Black,
        surfaceVariant = if (primary == Color.White) DarkGray else primary.lerp(Color.Black, 0.82f),
        onBackground = tintedWhite,
        onSurface = tintedWhite,
        onSurfaceVariant = tintedWhite.copy(alpha = 0.85f)
    )
}

private fun getLightColorScheme(primary: Color): ColorScheme {
    val tintedBlack = getTintedBlack(primary)
    // Stronger background tint for light mode
    val lightBg = if (primary == Color.White) Color.White else primary.lerp(Color.White, 0.94f)
    
    return lightColorScheme(
        primary = if (primary == Color.White) Color.Black else primary,
        onPrimary = Color.White,
        primaryContainer = if (primary == Color.White) SubtleAccent else primary.copy(alpha = 0.4f),
        onPrimaryContainer = tintedBlack,
        secondary = if (primary == Color.White) AccentGray else primary.copy(alpha = 0.9f),
        onSecondary = Color.White,
        secondaryContainer = if (primary == Color.White) MutedWhite else primary.copy(alpha = 0.2f),
        onSecondaryContainer = tintedBlack,
        tertiary = LightGray,
        background = lightBg,
        surface = lightBg,
        // Stronger card/dialog colors in light mode
        surfaceVariant = if (primary == Color.White) MutedWhite else primary.lerp(Color.White, 0.80f),
        onBackground = tintedBlack,
        onSurface = tintedBlack,
        onSurfaceVariant = tintedBlack.copy(alpha = 0.8f)
    )
}

@Composable
fun EtherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoledMode: Boolean = false,
    selectedTheme: String = "DEFAULT",
    content: @Composable () -> Unit
) {
    val theme = try { AppTheme.valueOf(selectedTheme) } catch (_: Exception) { AppTheme.DEFAULT }
    val primaryColor = theme.primaryColor

    val colorScheme = when {
        amoledMode -> getAmoledColorScheme(primaryColor)
        darkTheme -> getDarkColorScheme(primaryColor)
        else -> getLightColorScheme(primaryColor)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
