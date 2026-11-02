package vn.dinhtrung.thuocla.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.dinhtrung.thuocla.data.TrackerRepository
import vn.dinhtrung.thuocla.data.db.AppDatabase
import vn.dinhtrung.thuocla.domain.CigaretteLog
import vn.dinhtrung.thuocla.testutil.MutableClock
import vn.dinhtrung.thuocla.ui.today.TodayScreen
import vn.dinhtrung.thuocla.ui.today.TodayViewModel

/**
 * tasks.md 7.1 as the user experiences it: change the goal 10 -> 8 in Cài đặt, come back to Hôm nay,
 * and both the goal and the progress are recomputed. Also checks the 7.2 footer on the real screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: TrackerRepository
    private lateinit var clock: MutableClock

    private val today = "2026-11-02"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TrackerRepository(db)
        clock = MutableClock.at("2026-11-02T14:07:30+07:00")
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun changingTheGoalToEight_updatesTodayCounterAndProgress() {
        runBlocking {
            repo.addRecord(today, 845) // 14:05
            repo.addRecord(today, 905) // 15:05
        }

        val todayVm = TodayViewModel(CigaretteLog(repo, clock), repo, triggerPromptDelayMillis = 0L)
        val settingsVm = SettingsViewModel(repo, versionName = "1.0.0")

        composeRule.setContent {
            // A two-tab strip local to this test. The production shell lives in MainActivity's TAB_
            // constants after #15 merged, so this test does not borrow it; what matters here is the
            // settings -> today round trip, and MainActivityShellTest guards real navigation.
            var selected by remember { mutableStateOf(0) }
            MaterialTheme {
                Surface {
                    Column {
                        TabRow(selectedTabIndex = selected) {
                            Tab(
                                selected = selected == 0,
                                onClick = { selected = 0 },
                                text = { Text("Hôm nay") },
                                modifier = Modifier.testTag("tabToday"),
                            )
                            Tab(
                                selected = selected == 1,
                                onClick = { selected = 1 },
                                text = { Text("Cài đặt") },
                                modifier = Modifier.testTag("tabSettings"),
                            )
                        }
                        when (selected) {
                            0 -> {
                                val state by todayVm.state.collectAsState()
                                TodayScreen(
                                    state = state,
                                    onAdd = todayVm::addCig,
                                    onUndo = todayVm::undo,
                                    onSelectTrigger = todayVm::selectTrigger,
                                    onDismissTriggerSheet = todayVm::dismissTriggerSheet,
                                    onAddAt = todayVm::addAt,
                                    onEditTime = todayVm::editTime,
                                )
                            }

                            else -> {
                                val state by settingsVm.state.collectAsState()
                                SettingsScreen(
                                    state = state,
                                    onGoalChange = settingsVm::onGoalChange,
                                    onIntervalGoalChange = settingsVm::onIntervalGoalChange,
                                    onPriceChange = settingsVm::onPricePerPackChange,
                                    onCigsPerPackChange = settingsVm::onCigsPerPackChange,
                                    onSave = settingsVm::save,
                                )
                            }
                        }
                    }
                }
            }
        }

        composeRule.waitUntil(10_000) { counterIs("2 / 10 điếu") }

        composeRule.onNodeWithTag("tabSettings").performClick()
        composeRule.waitUntil(10_000) { settingsVm.state.value.fields.goal == "10" }

        composeRule.onNodeWithTag("settingGoal").performTextClearance()
        composeRule.onNodeWithTag("settingGoal").performTextInput("8")
        composeRule.onNodeWithTag("settingSave").performScrollTo().performClick()

        // Read the ViewModel's view of the store: runBlocking on the main thread would deadlock
        // against Room's transaction dispatcher.
        waitForCondition("the goal to be stored") { settingsVm.state.value.storedConfig.goal == 8 }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settingFooter").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settingFooter").assertTextContains("v1.0.0 · 1 ngày")

        composeRule.onNodeWithTag("tabToday").performClick()
        composeRule.waitUntil(10_000) { counterIs("2 / 8 điếu") }
        composeRule.onNodeWithText("25%", substring = true).assertIsDisplayed()

        assertEquals(8, settingsVm.state.value.storedConfig.goal)
    }

    // ------------------------------------------------------------------ helpers

    private fun counterIs(text: String): Boolean =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun waitForCondition(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out waiting for $what")
            composeRule.runOnIdle { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle() }
            Thread.sleep(20)
        }
        composeRule.waitForIdle()
    }
}
