package vn.dinhtrung.thuocla

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The tab shell must keep every feature reachable. Rebasing this slice onto #14's three-tab activity
 * once produced a build where one resolution dropped `StatsRoute` and the other dropped `DataScreen`;
 * both compiled and both passed the unit tests, because no test looked at the shell itself. This is
 * that test: each tab is clicked and its own screen is asserted present. Task 7 replaced the Cài đặt
 * placeholder with a real settings screen, so that assertion moved from the placeholder tag to the
 * settings field tags — the same tab, a different screen behind it.
 *
 * Scope is reachability, not pixels — a tab that renders its screen's own tag proves the route is wired.
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
    fun shell_keepsSettingsScreenFromTask7Reachable() {
        composeRule.onNodeWithTag("tabSettings").performClick()
        // The placeholder became task 7's real settings screen; assert its own field tags.
        composeRule.onNodeWithTag("settingGoal").assertIsDisplayed()
        // The screen scrolls, so the save button sits below the fold in the test window.
        composeRule.onNodeWithTag("settingSave").performScrollTo().assertIsDisplayed()
    }

    /**
     * The spec's "Ngày đầu tiên dùng app" sentence end-to-end: with five cigarettes today and nothing
     * before, tap the "ít nhất" card on Hôm nay and the day detail must appear on the Thống kê tab.
     * The previous review round could only verify this at the screen-callback boundary and in the
     * ViewModel, not through the real activity — this closes that gap.
     *
     * The records are seeded into the activity's own database (the fallback needs today to be non-empty,
     * otherwise the card is not clickable at all), then the activity is launched on top of them.
     */
    @Test
    fun tappingTheLowestCard_onToday_opensTheDayDetailOnTheStatsTab() {
        val activity = composeRule.activity
        val container = (activity.application as ThuoclaApp).container
        val today = LocalDate.now(container.clock).toString()
        runBlocking {
            listOf(505, 590, 705, 755, 845).forEach { container.repository.addRecord(today, it) }
        }

        composeRule.onNodeWithTag("tabToday").performClick()
        composeRule.waitUntil(10_000) { counterHasFive() }
        composeRule.onNodeWithTag("todayScroll").performScrollToNode(hasTestTag("statLowest"))
        composeRule.onNodeWithTag("statLowest").performClick()

        // The tap switches to Thống kê and opens today's detail.
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("dayDetail").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("dayDetail").assertIsDisplayed()
    }

    /** The counter reads "5 / N điếu" once the seeded records reach the screen. */
    private fun counterHasFive(): Boolean =
        composeRule.onAllNodesWithText("5 /", substring = true).fetchSemanticsNodes().isNotEmpty()
}
