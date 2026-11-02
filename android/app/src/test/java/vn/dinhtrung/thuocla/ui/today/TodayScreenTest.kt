package vn.dinhtrung.thuocla.ui.today

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
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

/**
 * tasks.md 3.2 / 3.3 on the real Compose surface: the +1 button exists under `testTag("addCig")`,
 * two taps make the counter read 2, a duplicate renders the in-card banner, and the reason sheet's
 * eight choices are present in order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodayScreenTest {

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

    private fun setContentWithViewModel(): TodayViewModel {
        val vm = TodayViewModel(
            log = CigaretteLog(repo, clock),
            repository = repo,
            triggerPromptDelayMillis = 0L,
        )
        composeRule.setContent {
            val state by vm.state.collectAsState()
            MaterialTheme {
                Surface {
                    TodayScreen(
                        state = state,
                        onAdd = vm::addCig,
                        onUndo = vm::undo,
                        onSelectTrigger = vm::selectTrigger,
                        onDismissTriggerSheet = vm::dismissTriggerSheet,
                        onAddAt = vm::addAt,
                        onEditTime = vm::editTime,
                    )
                }
            }
        }
        return vm
    }

    @Test
    fun addCig_twice_counterReadsTwo() {
        setContentWithViewModel()

        composeRule.onNodeWithTag("addCig").assertIsDisplayed()
        composeRule.onNodeWithTag("addCig").performClick()
        composeRule.waitUntil(5_000) { counterIs("1 / 10 điếu") }

        composeRule.runOnIdle { clock.advance(Duration.ofMinutes(60)) }
        composeRule.onNodeWithTag("addCig").performClick()
        composeRule.waitUntil(5_000) { counterIs("2 / 10 điếu") }

        assertEquals(listOf(845, 905), runBlockingRecords().map { it.minuteOfDay })
    }

    @Test
    fun duplicateTap_showsBannerInsideTheCounterCard() {
        setContentWithViewModel()

        composeRule.onNodeWithTag("addCig").performClick()
        composeRule.waitUntil(5_000) { counterIs("1 / 10 điếu") }

        composeRule.onNodeWithTag("addCig").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Đã có điếu lúc 14:05", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("counterWarning").assertIsDisplayed()
        assertEquals(1, runBlockingRecords().size)
    }

    @Test
    fun triggerSheet_listsEightReasons_andWritesTheChosenOne() {
        val vm = setContentWithViewModel()

        composeRule.onNodeWithTag("addCig").performClick()
        waitForCondition("the reason sheet to target the new cigarette") {
            vm.state.value.triggerTarget != null
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("triggerSheet").assertIsDisplayed()

        // Eight chips, in the spec's order, all present at once.
        listOf("Sau ăn", "Cà phê", "Stress", "Nhậu", "Buồn", "Thói quen", "Thèm", "Khác")
            .forEach { name ->
                composeRule.onNodeWithText(name, substring = true).assertIsDisplayed()
            }

        // Robolectric's touch injection does not dispatch into this overlaid sheet; invoking the
        // node's click action is the same code path without the coordinate mapping.
        composeRule.onNodeWithTag("trigger_1").performSemanticsAction(SemanticsActions.OnClick) // Cà phê
        waitForCondition("the sheet to close after the tap") { vm.state.value.triggerTarget == null }
        waitForCondition("the chosen reason to reach the store") {
            runBlocking { repo.day(today) }.singleOrNull()?.triggerIdx == 1
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("☕ Cà phê", substring = true).assertIsDisplayed()
    }

    @Test
    fun undoButton_removesTheCigarette() {
        val vm = setContentWithViewModel()

        composeRule.onNodeWithTag("addCig").performClick()
        composeRule.waitUntil(5_000) { counterIs("1 / 10 điếu") }
        composeRule.runOnIdle { vm.dismissTriggerSheet() }

        composeRule.onNodeWithTag("undoCig").performClick()
        composeRule.waitUntil(5_000) { counterIs("0 / 10 điếu") }
    }

    @Test
    fun tappingARecord_opensTheTimeEditor_andSavesTheRoundedTime() {
        runBlocking { repo.addRecord(today, 615) } // 10:15
        setContentWithViewModel()
        composeRule.waitUntil(5_000) { counterIs("1 / 10 điếu") }

        composeRule.onNodeWithTag("record_615").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("editTimeInput").assertIsDisplayed()

        composeRule.onNodeWithTag("editTimeInput").performTextClearance()
        composeRule.onNodeWithTag("editTimeInput").performTextInput("08:27")
        composeRule.onNodeWithTag("editTimeConfirm").performClick()

        waitForCondition("the edit to land at 08:25") {
            runBlocking { repo.day(today) }.singleOrNull()?.minuteOfDay == 505
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("record_505").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Robolectric runs the main looper in PAUSED mode, so coroutines that resume on `Dispatchers.Main`
     * (the reason-sheet delay, the store write after a tap) only run when the looper is idled. Compose's
     * `waitUntil` pumps the frame clock, not that queue.
     */
    private fun waitForCondition(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out waiting for $what")
            composeRule.runOnIdle { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle() }
            Thread.sleep(20)
        }
        composeRule.waitForIdle()
    }

    private fun counterIs(text: String): Boolean =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun runBlockingRecords() = runBlocking { repo.day(today) }
}
