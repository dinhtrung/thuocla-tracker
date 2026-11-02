package vn.dinhtrung.thuocla

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The tab shell must keep every feature reachable. Rebasing this slice onto #14's three-tab activity
 * once produced a build where one resolution dropped `StatsRoute` and the other dropped `DataScreen`;
 * both compiled and both passed the unit tests, because no test looked at the shell itself. This is
 * that test: each tab is clicked and its own screen is asserted present.
 *
 * Scope is reachability, not pixels — a tab that renders its screen's header proves the route is wired.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityShellTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun shell_offersAllFourTabs() {
        composeRule.onNodeWithTag("tabToday").assertIsDisplayed()
        composeRule.onNodeWithTag("tabStats").assertIsDisplayed()
        composeRule.onNodeWithTag("tabData").assertIsDisplayed()
        composeRule.onNodeWithTag("tabSettings").assertIsDisplayed()
    }

    @Test
    fun shell_keepsTodayScreenFrom13Reachable() {
        composeRule.onNodeWithTag("tabToday").performClick()
        composeRule.onNodeWithTag("addCig").assertIsDisplayed()
    }

    @Test
    fun shell_keepsStatsScreenFrom14Reachable() {
        composeRule.onNodeWithTag("tabStats").performClick()
        // "Thống kê" is both the tab label and the screen header, so assert the screen's own tag.
        composeRule.onNodeWithTag("statsChart").assertIsDisplayed()
    }

    @Test
    fun shell_keepsDataScreenFromThisSliceReachable() {
        composeRule.onNodeWithTag("tabData").performClick()
        // "Dữ liệu" is both the tab label and the screen header, so assert the screen's own tag.
        composeRule.onNodeWithTag("exportCsv").assertIsDisplayed()
    }

    @Test
    fun shell_keepsSettingsPlaceholderReachable() {
        composeRule.onNodeWithTag("tabSettings").performClick()
        composeRule.onNodeWithTag("settingsPlaceholder").assertIsDisplayed()
    }
}
