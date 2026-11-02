package vn.dinhtrung.thuocla.ui.today

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /** Days the screen asked to open; the "ít nhất" and "Hôm qua" cards write here. */
    private val openedDays = mutableListOf<String>()

    private fun setContentWithViewModel(): TodayViewModel {
        openedDays.clear()
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
                        onOpenDay = { openedDays += it },
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
        composeRule.onNodeWithTag("todayScroll").performScrollToNode(hasTestTag("record_845"))
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

        composeRule.onNodeWithTag("todayScroll").performScrollToNode(hasTestTag("record_615"))
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
        composeRule.onNodeWithTag("todayScroll").performScrollToNode(hasTestTag("record_505"))
        composeRule.onNodeWithTag("record_505").assertIsDisplayed()
    }

    // ------------------------------------------------------------ tasks.md 4.1 / 4.2 / 4.3

    @Test
    fun firstDay_fiveCigarettes_avg7ReadsFivePointZero_andLowestCardSaysToday() {
        runBlocking {
            // 08:25, 09:50, 11:45, 12:35, 14:05 — the five cigarettes of the first day.
            listOf(505, 590, 705, 755, 845).forEach { repo.addRecord(today, it) }
        }
        setContentWithViewModel()
        composeRule.waitUntil(5_000) { counterIs("5 / 10 điếu") }

        composeRule.onNodeWithTag("todayScroll").performScrollToNode(hasTestTag("statAvg7"))
        // The spec's two mandatory numbers: TB 7 ngày = 5.0 (not 0.7), thẻ ít nhất = "5 • Hôm nay".
        composeRule.onNodeWithTag("statAvg7").assertIsDisplayed()
        composeRule.onNodeWithText("5.0", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("statLowest").assertIsDisplayed()
        composeRule.onNodeWithText("Ít nhất • Hôm nay", substring = true).assertIsDisplayed()

        // "bấm vào mở được chi tiết ngày hôm nay" — the tap must reach the day detail. On the first day
        // the fallback is today itself, so the card opens today's key.
        composeRule.onNodeWithTag("statLowest").performClick()
        assertEquals(listOf(today), openedDays)
    }

    @Test
    fun comparisonTable_firstDay_stillDrawsFiveTodayRows_withDashesInReferenceColumns() {
        runBlocking {
            listOf(505, 590, 705, 755, 845).forEach { repo.addRecord(today, it) }
        }
        setContentWithViewModel()
        composeRule.waitUntil(5_000) { counterIs("5 / 10 điếu") }

        composeRule.onNodeWithTag("todayScroll").performScrollToNode(hasTestTag("comparisonTable"))
        // Row 5 exists even though there is no reference day: the Today column is never cut short.
        composeRule.onNodeWithTag("comparisonRow_5").assertExists()
        composeRule.onNodeWithTag("comparisonTable").assertIsDisplayed()
        composeRule.onNodeWithText("(5)", substring = true).assertIsDisplayed()

        // "hai cột còn lại hiện dấu —": row 5's two reference cells each render time and gap, so the
        // row must carry exactly 4 dashes. Asserting the dashes (not just the row's existence) is what
        // catches a regression that prints 0 in a column with no reference day.
        val dashesInRow5 = composeRule.onAllNodes(
            hasText("—") and hasAnyAncestor(hasTestTag("comparisonRow_5")),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals("row 5 has 2 reference columns x 2 lines of dashes", 4, dashesInRow5.size)
        val zerosInRow5 = composeRule.onAllNodes(
            hasText("0") and hasAnyAncestor(hasTestTag("comparisonRow_5")),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals("no reference column may print 0", 0, zerosInRow5.size)
    }

    /**
     * Spec `daily-summary`, "Đếm tăng sau mỗi lần ghi": with goal 10 the progress bar reads 10%, 20%,
     * 30% after 1, 2, 3 cigarettes. The percentages are asserted as values on the rendered screen, not
     * only as `state.progress`, so a broken label cannot pass.
     */
    @Test
    fun threeCigarettes_progressReadsTenThenTwentyThenThirtyPercent() {
        setContentWithViewModel()

        composeRule.onNodeWithText("0%", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("addCig").performClick()
        composeRule.waitUntil(5_000) { counterIs("1 / 10 điếu") }
        composeRule.onNodeWithText("10%", substring = true).assertIsDisplayed()

        composeRule.runOnIdle { clock.advance(Duration.ofMinutes(60)) }
        composeRule.onNodeWithTag("addCig").performClick()
        composeRule.waitUntil(5_000) { counterIs("2 / 10 điếu") }
        composeRule.onNodeWithText("20%", substring = true).assertIsDisplayed()

        composeRule.runOnIdle { clock.advance(Duration.ofMinutes(60)) }
        composeRule.onNodeWithTag("addCig").performClick()
        composeRule.waitUntil(5_000) { counterIs("3 / 10 điếu") }
        composeRule.onNodeWithText("30%", substring = true).assertIsDisplayed()
    }

    @Test
    fun intervalCard_overGoal_showsMinutesOver() {
        runBlocking { repo.addRecord(today, 755) } // 12:35, goal 60, clock frozen at 14:07
        setContentWithViewModel()
        composeRule.waitUntil(5_000) { counterIs("1 / 10 điếu") }

        // 14:07 - 12:35 = 92 minutes, so 32 minutes past the 60-minute goal.
        composeRule.onNodeWithTag("intervalElapsed").assertIsDisplayed()
        composeRule.onNodeWithText("01:32", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("intervalOver").assertIsDisplayed()
        composeRule.onNodeWithText("+32 phút", substring = true).assertIsDisplayed()
    }

    /** Spec scenario "Chưa tới mục tiêu khoảng cách": the card shows the minutes left to the goal. */
    @Test
    fun intervalCard_underGoal_showsNextTimeAndMinutesRemaining() {
        runBlocking { repo.addRecord(today, 755) } // 12:35
        composeRule.runOnIdle { clock.advance(Duration.ofMinutes(-77)) } // clock at 12:50
        setContentWithViewModel()
        composeRule.waitUntil(5_000) { counterIs("1 / 10 điếu") }

        // Last cigarette 12:35, goal 60, now 12:50 -> next 13:35 and 45 minutes left.
        composeRule.onNodeWithTag("intervalElapsed").assertIsDisplayed()
        composeRule.onNodeWithText("00:15", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("intervalNext").assertIsDisplayed()
        composeRule.onNodeWithText("13:35", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("intervalRemaining").assertIsDisplayed()
        composeRule.onNodeWithText("còn 45 phút", substring = true).assertIsDisplayed()
    }

    // ------------------------------------------- 5.2 the note on the rendered screen

    /**
     * Seeds 29 days of history (weekday 12-cigarette days plus three weekend shapes) so the note has
     * enough data to leave its "cần thêm dữ liệu" state. 2026-11-02 is a Monday, so the weekday series
     * supplies both baselines.
     */
    private fun seedCutHistory() {
        val weekdayTail = listOf(420, 460, 500, 540, 660, 780, 900, 1080, 1200, 1260, 1290, 1320)
        val weekendB = listOf(420, 460, 500, 540, 720)
        val weekendC = listOf(420, 460, 480, 540, 720)
        val weekendD = listOf(420, 460, 500, 515, 540, 720)
        var b = 0
        var c = 0
        var d = 0
        runBlocking {
            for (back in 1..29) {
                val day = java.time.LocalDate.parse(today).minusDays(back.toLong())
                val minutes = if (day.dayOfWeek.value >= 6) {
                    when {
                        b < 3 -> weekendB.also { b++ }
                        c < 3 -> weekendC.also { c++ }
                        d < 2 -> weekendD.also { d++ }
                        else -> weekendB.also { b++ }
                    }
                } else {
                    weekdayTail
                }
                minutes.forEach { repo.addRecord(day.toString(), it) }
            }
        }
    }

    /**
     * Tasks.md 5.2 on the real Compose surface: the note card renders with its icon, its one-line
     * verdict and its explanation, all three carrying the note's own strings.
     *
     * At 14:07 with one cigarette the weekday habit baseline is 6.0 cigarettes and the goal pace is 5
     * (goal 10, span 900 min → 90 min/cigarette), so the note praises — the PWA's "behind pace" state,
     * not the cut target.
     */
    @Test
    fun cutNoteCard_rendersTheVerdictForThisMinute() {
        seedCutHistory()
        setContentWithViewModel()
        composeRule.waitUntil(5_000) { counterIs("0 / 10 điếu") }
        composeRule.onNodeWithTag("addCig").performClick()
        waitForCondition("the note to move off the insufficient-data state") {
            composeRule.onAllNodesWithTag("cutNote").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("todayScroll").performScrollToNode(hasTestTag("cutNote"))
        composeRule.onNodeWithTag("cutNote").assertIsDisplayed()
        composeRule.onNodeWithTag("cutNoteIcon").assertIsDisplayed()

        // The verdict at 14:07 with one cigarette is a praise: the weekday habit baseline is 6.0 and
        // the goal pace is 5, so "behind both baselines" holds. Asserted as rendered text, not state.
        composeRule.onNodeWithTag("cutNoteTitle").assertIsDisplayed()
        composeRule.onNodeWithText("Hút ít hơn nhịp mục tiêu", substring = true).assertIsDisplayed()
        // The subtitle sits below the fold in this viewport, so existence (not display) is asserted.
        composeRule.onNodeWithTag("cutNoteSubtitle").assertExists()
        assertTrue(
            "the subtitle must quote the goal pace",
            composeRule.onAllNodesWithText("nhịp mục tiêu ~5", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /** The same screen after +1 and undo: the note recomputes without a manual refresh. */
    @Test
    fun cutNoteCard_recomputesAfterAddAndUndo() {
        seedCutHistory()
        val vm = setContentWithViewModel()
        composeRule.waitUntil(5_000) { counterIs("0 / 10 điếu") }

        val before = vm.state.value.cutNote
        composeRule.onNodeWithTag("addCig").performClick()
        composeRule.waitUntil(5_000) { counterIs("1 / 10 điếu") }
        waitForCondition("the note to change after +1") { vm.state.value.cutNote != before }

        val afterAdd = vm.state.value.cutNote
        composeRule.runOnIdle { vm.dismissTriggerSheet() }
        composeRule.onNodeWithTag("undoCig").performClick()
        composeRule.waitUntil(5_000) { counterIs("0 / 10 điếu") }
        waitForCondition("the note to change back after undo") { vm.state.value.cutNote != afterAdd }
    }

    /** With fewer than 7 days of history the note asks for data and invents no target. */
    @Test
    fun cutNoteCard_insufficientData_asksForMoreDays() {
        setContentWithViewModel()
        composeRule.waitUntil(5_000) { counterIs("0 / 10 điếu") }
        composeRule.onNodeWithTag("addCig").performClick()
        waitForCondition("the insufficient-data note") {
            composeRule.onAllNodesWithTag("cutNote").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("todayScroll").performScrollToNode(hasTestTag("cutNote"))
        composeRule.onNodeWithText("Cần thêm dữ liệu để tính điếu nên cắt", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("tối thiểu 7 ngày", substring = true).assertIsDisplayed()
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
