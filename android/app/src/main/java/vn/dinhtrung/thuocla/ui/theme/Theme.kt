package vn.dinhtrung.thuocla.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import vn.dinhtrung.thuocla.domain.ThemeMode

/**
 * The app's colour schemes (#19).
 * Deliberately *not* Material You dynamic colour: the coral/navy identity is the product's, and letting
 * the device wallpaper repaint the app would erase it.
 *
 * Every role the app's widgets actually read is set — not just the brief's headline roles. Material3's
 * built-in defaults for `surfaceContainer*` and `secondaryContainer` are *purple*, and they paint the
 * navigation bar and the selected tab; leaving them out produced an app whose screens were coral while
 * its chrome stayed Material purple.
 */
val LightColors: ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    tertiary = LightTertiary,
    // The light scheme has no separate onTertiary in the brief; white matches the other light pairs.
    onTertiary = LightOnPrimary,
    background = LightSurface,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = LightError,
    onError = LightOnError,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    surfaceTint = LightSurfaceTint,
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    background = DarkSurface,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = DarkError,
    onError = DarkOnError,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    surfaceTint = DarkSurfaceTint,
)

/**
 * The scheme for a resolved brightness. Exposed separately from [ThuoclaTheme] so a test can assert
 * the mapping without composing anything.
 */
fun thuoclaColorScheme(isDark: Boolean): ColorScheme = if (isDark) DarkColors else LightColors

/**
 * The scheme for a stored [ThemeMode] given whether the device is in dark mode. The whole point of
 * [ThemeMode.SYSTEM] is that this is the only place the two inputs are combined.
 */
fun thuoclaColorScheme(mode: ThemeMode, systemInDark: Boolean): ColorScheme =
    thuoclaColorScheme(mode.resolveIsDark(systemInDark))

/**
 * Wraps the app so every screen reads its colours from [MaterialTheme.colorScheme].
 *
 * [isDark] is already resolved by the caller (see [thuoclaColorScheme]) because only the caller knows
 * whether the user chose a mode or left it to the system.
 */
@Composable
fun ThuoclaTheme(
    isDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = thuoclaColorScheme(isDark),
        content = content,
    )
}
