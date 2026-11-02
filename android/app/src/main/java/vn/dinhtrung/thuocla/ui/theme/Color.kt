package vn.dinhtrung.thuocla.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's palette (#19), taken from the PWA's own identity in `styles.css`.
 *
 * Every value here is a hex literal, and this is the only file in `ui/` allowed to contain one: the
 * screens read roles from `MaterialTheme.colorScheme`, so a theme change never needs a screen edit.
 * The job is not to invent colours — coral `#e94560` / `#ff6b81`, the navy `#1a1a2e`, the yellow
 * `#f1c40f` and the light surface `#f2f4fa` are the brand — but to pick shades that carry enough
 * contrast in both schemes.
 *
 * Contrast (WCAG, lowest in each group, measured on these pairs):
 *
 * | pair                          | light  | dark   |
 * |-------------------------------|--------|--------|
 * | primary / onPrimary           | 6.90:1 | 6.28:1 |
 * | surface / onSurface           | 15.51:1| 14.16:1|
 * | surfaceVariant / onSurfaceVariant | 6.25:1 | 6.72:1 |
 * | primary / surface             | 6.28:1 | —      |
 * | tertiary / onTertiary         | 5.54:1 | 9.21:1 |
 *
 * All at or above 4.5:1 for text and 3:1 for UI components.
 */

// ------------------------------------------------------------------ light

/** Coral darkened until it reads on a light surface: the same `#b3122e` the PWA's light block uses. */
internal val LightPrimary = Color(0xFFB3122E)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFFFDAD9)
internal val LightOnPrimaryContainer = Color(0xFF3F0013)
internal val LightSecondary = Color(0xFFA03A2A)
internal val LightTertiary = Color(0xFF8A6100)
internal val LightSurface = Color(0xFFF2F4FA)
internal val LightOnSurface = Color(0xFF1A1A2E)
internal val LightSurfaceVariant = Color(0xFFE6EAF3)
internal val LightOnSurfaceVariant = Color(0xFF4A5568)
internal val LightOutline = Color(0xFF7B8494)
internal val LightError = Color(0xFFB3261E)
internal val LightOnError = Color(0xFFFFFFFF)

/**
 * The "container" roles. Material3's default schemes paint the bottom navigation bar, selected
 * navigation items, chips and dividers with `surfaceContainer*` / `secondaryContainer`, and those
 * defaults are purple. If only the brief's roles are set, the app's chrome stays purple while the
 * screens turn coral — so these are derived from the brief's own surface and secondary tokens, and
 * every one of them is still a brief colour mixed toward the surface, not a new invention.
 */
internal val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val LightSurfaceContainerLow = Color(0xFFFAFBFE)
internal val LightSurfaceContainer = Color(0xFFF2F4FA)
internal val LightSurfaceContainerHigh = Color(0xFFECEFF7)
internal val LightSurfaceContainerHighest = Color(0xFFE6EAF3)
internal val LightSecondaryContainer = Color(0xFFFFDAD9)
internal val LightOnSecondaryContainer = Color(0xFF3F0013)
internal val LightSurfaceTint = Color(0xFFB3122E)

// ------------------------------------------------------------------- dark

/** The PWA's own coral, unchanged: dark is where the brand colour already lived. */
internal val DarkPrimary = Color(0xFFFF6B81)
internal val DarkOnPrimary = Color(0xFF3F0013)
internal val DarkPrimaryContainer = Color(0xFF7D1128)
internal val DarkOnPrimaryContainer = Color(0xFFFFDAD9)
internal val DarkSecondary = Color(0xFFFFB4A2)
internal val DarkTertiary = Color(0xFFF1C40F)
internal val DarkOnTertiary = Color(0xFF2F2400)
internal val DarkSurface = Color(0xFF1A1A2E)
internal val DarkOnSurface = Color(0xFFE6EAF3)
internal val DarkSurfaceVariant = Color(0xFF232A42)
internal val DarkOnSurfaceVariant = Color(0xFFA8B3C9)
internal val DarkOutline = Color(0xFF6B7794)
internal val DarkError = Color(0xFFFFB4AB)
internal val DarkOnError = Color(0xFF690005)

internal val DarkSurfaceContainerLowest = Color(0xFF12121F)
internal val DarkSurfaceContainerLow = Color(0xFF16162A)
internal val DarkSurfaceContainer = Color(0xFF1A1A2E)
internal val DarkSurfaceContainerHigh = Color(0xFF1E2038)
internal val DarkSurfaceContainerHighest = Color(0xFF232A42)
internal val DarkSecondaryContainer = Color(0xFF7D1128)
internal val DarkOnSecondaryContainer = Color(0xFFFFDAD9)
internal val DarkSurfaceTint = Color(0xFFFF6B81)
/** The scrim behind the reason sheet. Kept as a role rather than a raw `Color.Black`. */
internal val ScrimBlack = Color(0xFF000000)
