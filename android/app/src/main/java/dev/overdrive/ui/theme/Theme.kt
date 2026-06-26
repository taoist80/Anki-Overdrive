package dev.overdrive.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.util.concurrent.ConcurrentHashMap

/**
 * Overdrive palette — calibrated to the DDL 4.0.4 look (reference/screenshots/ddl404): a violet
 * nebula ground, one glowing-blue "holo" accent, a rose reserved for the wordmark, and violet-biased
 * neutrals. Gold is kept solely as the "commit" CTA color. `blue` carries the holo accent so the
 * many existing `colors.blue` call sites pick up the new accent for free. See restyle design spec.
 */
@Immutable
data class OverdriveColors(
    val background: Color = Color(0xFF140A22),       // void — page ground / vignette
    val surface: Color = Color(0xFF1B1030),          // card gradient bottom
    val panel: Color = Color(0xFF241A3A),            // card gradient top
    val panelBorder: Color = Color(0x24FFFFFF),      // soft light border (white @ 14%)
    val gold: Color = Color(0xFFE6B800),             // kept — commit CTA only
    val blue: Color = Color(0xFF4FB0FF),             // holo accent — selection / glow
    val orange: Color = Color(0xFFF26A21),
    val rose: Color = Color(0xFFECA9CE),             // wordmark only
    val textPrimary: Color = Color(0xFFF4ECFA),      // violet-white
    val textDim: Color = Color(0xFFAE95C8),          // lavender (chosen neutral)
    val barEmpty: Color = Color(0xFF2E2440),         // violet-biased empty track
    val danger: Color = Color(0xFFE5484D),
    val success: Color = Color(0xFF49C56A),
)

val LocalOverdriveColors: ProvidableCompositionLocal<OverdriveColors> =
    staticCompositionLocalOf { OverdriveColors() }

val LocalOverdriveFont: ProvidableCompositionLocal<FontFamily> =
    staticCompositionLocalOf { FontFamily.Default }

/** Convenience accessor: `OverdriveTheme.colors` / `OverdriveTheme.font` inside a composable. */
object OverdriveTheme {
    val colors: OverdriveColors
        @Composable get() = LocalOverdriveColors.current
    val font: FontFamily
        @Composable get() = LocalOverdriveFont.current
}

/**
 * In-memory asset bitmap cache. Bundled assets are immutable, so keying on path and caching across
 * screens/recompositions is safe and keeps the garage carousel etc. smooth.
 */
object OverdriveAssets {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun load(ctx: Context, path: String): ImageBitmap? {
        cache[path]?.let { return it }
        if (path in missing) return null
        val bmp = runCatching {
            ctx.assets.open(path).use { BitmapFactory.decodeStream(it) }.asImageBitmap()
        }.getOrNull()
        if (bmp == null) missing.add(path) else cache[path] = bmp
        return bmp
    }
}

@Composable
fun rememberAsset(path: String?): ImageBitmap? {
    val ctx = LocalContext.current
    return remember(path) { path?.let { OverdriveAssets.load(ctx, it) } }
}

@Composable
private fun overdriveTypography(font: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.f() = copy(fontFamily = font)
    return Typography(
        displayLarge = base.displayLarge.f(),
        displayMedium = base.displayMedium.f(),
        displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f(),
        headlineMedium = base.headlineMedium.f(),
        headlineSmall = base.headlineSmall.f(),
        titleLarge = base.titleLarge.f(),
        titleMedium = base.titleMedium.f(),
        titleSmall = base.titleSmall.f(),
        bodyLarge = base.bodyLarge.f(),
        bodyMedium = base.bodyMedium.f(),
        bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f().copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
        labelMedium = base.labelMedium.f(),
        labelSmall = base.labelSmall.f(),
    )
}

@Composable
fun OverdriveTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val font = remember {
        runCatching { FontFamily(Font("fonts/UniversLTStd.otf", ctx.assets)) }
            .getOrDefault(FontFamily.Default)
    }
    val colors = remember { OverdriveColors() }
    val scheme = darkColorScheme(
        primary = colors.blue,
        secondary = colors.gold,
        tertiary = colors.orange,
        background = colors.background,
        surface = colors.surface,
        onPrimary = Color.Black,
        onBackground = colors.textPrimary,
        onSurface = colors.textPrimary,
        error = colors.danger,
    )
    // isSystemInDarkTheme referenced so lint is happy about an always-dark game UI being intentional.
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalOverdriveColors provides colors,
        LocalOverdriveFont provides font,
    ) {
        MaterialTheme(colorScheme = scheme, typography = overdriveTypography(font), content = content)
    }
}
