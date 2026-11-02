package vn.dinhtrung.thuocla.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.dinhtrung.thuocla.domain.ThemeMode

/**
 * #19 acceptance: the stored mode decides the scheme. "Theo hệ thống" follows the device, "Tối" and
 * "Sáng" override it, and neither scheme leaves a literal colour in a screen.
 */
class ThemeTest {

    @Test
    fun systemMode_followsTheDevice() {
        assertEquals(DarkColors, thuoclaColorScheme(ThemeMode.SYSTEM, systemInDark = true))
        assertEquals(LightColors, thuoclaColorScheme(ThemeMode.SYSTEM, systemInDark = false))
    }

    @Test
    fun lightMode_staysLightEvenWhenTheDeviceIsDark() {
        assertEquals(LightColors, thuoclaColorScheme(ThemeMode.LIGHT, systemInDark = true))
        assertEquals(LightColors, thuoclaColorScheme(ThemeMode.LIGHT, systemInDark = false))
    }

    @Test
    fun darkMode_staysDarkEvenWhenTheDeviceIsLight() {
        assertEquals(DarkColors, thuoclaColorScheme(ThemeMode.DARK, systemInDark = true))
        assertEquals(DarkColors, thuoclaColorScheme(ThemeMode.DARK, systemInDark = false))
    }

    @Test
    fun unknownStoredMode_fallsBackToFollowingTheDevice() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("purple"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorage("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage("Dark"))
    }

    /** The two schemes are the ones the brief names, not Material's purple defaults. */
    @Test
    fun schemes_carryTheBrandRoles() {
        assertEquals(Color(0xFFB3122E), LightColors.primary)
        assertEquals(Color(0xFFF2F4FA), LightColors.surface)
        assertEquals(Color(0xFFFF6B81), DarkColors.primary)
        assertEquals(Color(0xFF1A1A2E), DarkColors.surface)
        assertEquals(Color(0xFFF1C40F), DarkColors.tertiary)
    }

    /**
     * "Đổi máy sang dark mode: app tự đổi, không viền trắng, không chữ đen trên nền navy." The scheme
     * itself must not hand a dark surface light text or vice versa.
     */
    @Test
    fun eachScheme_keepsTextReadableOnItsOwnSurface() {
        assertTrue("light surface must be light", LightColors.surface.luminance() > 0.9f)
        assertTrue("light text must be dark", LightColors.onSurface.luminance() < 0.1f)
        assertTrue("dark surface must be dark", DarkColors.surface.luminance() < 0.05f)
        assertTrue("dark text must be light", DarkColors.onSurface.luminance() > 0.7f)
        assertFalse(
            "the dark scheme must not be the light one",
            DarkColors.surface == LightColors.surface,
        )
    }

    /**
     * Regression for the bug found on device: the first build set only the brief's headline roles, so
     * the navigation bar (`surfaceContainer`) and the selected tab (`secondaryContainer`) kept
     * Material3's *purple* defaults while the screens turned coral. No chrome role may remain a
     * default, and none may be a purple tone.
     */
    @Test
    fun bothSchemes_paintTheChromeFromTheBrief_notMaterialPurple() {
        val purple = setOf(
            Color(0xFFFEF7FF), // M3 default surface
            Color(0xFFF3EDF7), // M3 default surfaceContainer
            Color(0xFFE8DEF8), // M3 default secondaryContainer
            Color(0xFFE6E0E9), // M3 default surfaceContainerHighest
        )

        for ((name, scheme) in listOf("light" to LightColors, "dark" to DarkColors)) {
            for ((role, value) in listOf(
                "surfaceContainer" to scheme.surfaceContainer,
                "surfaceContainerHigh" to scheme.surfaceContainerHigh,
                "surfaceContainerHighest" to scheme.surfaceContainerHighest,
                "secondaryContainer" to scheme.secondaryContainer,
                "onSecondaryContainer" to scheme.onSecondaryContainer,
            )) {
                assertFalse("$name.$role is a Material3 default: $value", value in purple)
            }
        }

        // The nav bar in each scheme is the brief's own surface shade, not a tinted invention.
        assertEquals(LightSurfaceContainer, LightColors.surfaceContainer)
        assertEquals(DarkSurfaceContainer, DarkColors.surfaceContainer)
        assertEquals(DarkSurface, DarkColors.surfaceContainer)
    }
}
