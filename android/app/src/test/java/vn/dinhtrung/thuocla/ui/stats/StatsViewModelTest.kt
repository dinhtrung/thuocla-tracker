package vn.dinhtrung.thuocla.ui.stats

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.dinhtrung.thuocla.data.TrackerRepository
import vn.dinhtrung.thuocla.data.db.AppDatabase
import vn.dinhtrung.thuocla.domain.formatHhMm

/**
 * tasks.md 6.1 ("seed 14 ngày → bấm lùi 1 tuần, dữ liệu đổi đúng khoảng") and 6.2 (edit time /
 * attach reason from the day detail), exercised through the real Room store.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repository: TrackerRepository
    private lateinit var viewModel: StatsViewModel

    /** 2026-11-02 10:00 in Ho Chi Minh City. */
    private val clock: Clock = Clock.fixed(
        Instant.parse("2026-11-02T03:00:00Z"),
        ZoneId.of("Asia/Ho_Chi_Minh"),
    )
    private val today: LocalDate = LocalDate.of(2026, 11, 2)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TrackerRepository(db)
        viewModel = StatsViewModel(repository, clock)
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun awaitState(predicate: (StatsUiState) -> Boolean): StatsUiState =
        viewModel.uiState.first(predicate)

    private suspend fun seedDay(dateKey: String, count: Int) {
        repeat(count) { i ->
            repository.addRecord(dateKey, minuteOfDay = 360 + i * 60, triggerIdx = null)
        }
    }

    @Test
    fun previousWeek_shiftsTheChartByExactlySevenDays_andChangesTheData() = runTest {
        // Previous week: 2026-10-20 .. 2026-10-26, five cigarettes per day.
        repeat(7) { i -> seedDay(today.minusDays(13L - i).toString(), count = 5) }
        // Current window: 2026-10-27 .. 2026-11-02, two cigarettes per day.
        repeat(7) { i -> seedDay(today.minusDays(6L - i).toString(), count = 2) }

        val first = awaitState { state -> state.days.size == 7 && state.days.all { it.count == 2 } }
        assertEquals("7 ngày qua", first.window.label)
        assertFalse(first.canGoForward)

        viewModel.previousWeek()
        val second = awaitState { state -> state.window.pagesBack == 1 && state.days.all { it.count == 5 } }

        assertEquals("10-20 – 10-26", second.window.label)
        assertTrue(second.canGoForward)
        assertEquals(
            listOf(
                "2026-10-20", "2026-10-21", "2026-10-22", "2026-10-23",
                "2026-10-24", "2026-10-25", "2026-10-26",
            ),
            second.days.map { it.dateKey },
        )
        assertEquals(35, second.days.sumOf { it.count }) // data really changed, not just the label

        viewModel.nextWeek()
        val back = awaitState { state -> state.window.pagesBack == 0 && state.days.all { it.count == 2 } }
        assertEquals("7 ngày qua", back.window.label)
        assertFalse(back.canGoForward)
    }

    @Test
    fun dayDetail_listsCigarettesWithGapsAndEditsTheReasonInPlace() = runTest {
        repository.addRecord(today.toString(), 600)   // 10:00
        repository.addRecord(today.toString(), 630)   // 10:30
        repository.addRecord(today.toString(), 660)   // 11:00

        viewModel.selectDay(today.toString())
        val opened = awaitState { state -> state.detail.size == 3 }

        assertEquals("Hôm nay", opened.detailTitle)
        assertEquals(listOf(1, 2, 3), opened.detail.map { it.index })
        assertNull(opened.detail[0].gapMinutes)
        assertEquals(30, opened.detail[1].gapMinutes)
        assertEquals(30, opened.detail[2].gapMinutes)
        assertNull(opened.detail[0].record.triggerIdx)

        viewModel.setTrigger(opened.detail[0], 1) // Cà phê
        val withReason = awaitState { state -> state.detail.firstOrNull()?.record?.triggerIdx == 1 }
        assertEquals(1, withReason.detail[0].record.triggerIdx)

        val prompt = withReason.detail[0]
        viewModel.setTrigger(prompt, 1) // tapping the same reason removes it
        val cleared = awaitState { state -> state.detail.firstOrNull()?.record?.triggerIdx == null }
        assertNull(cleared.detail[0].record.triggerIdx)
    }

    /**
     * The "ít nhất" / "Hôm qua" cards call [StatsViewModel.openDay], which must *open* the day even
     * when it is already selected — unlike a chart bar tap, which toggles. A second tap from the Today
     * screen means "show me this day again", never "close it".
     */
    @Test
    fun openDay_alwaysOpensTheDay_andNeverTogglesItClosed() = runTest {
        repository.addRecord(today.toString(), 600)

        viewModel.openDay(today.toString())
        val opened = awaitState { state -> state.detail.isNotEmpty() }
        assertEquals(today.toString(), opened.selectedDateKey)

        viewModel.openDay(today.toString())
        val reopened = awaitState { state -> state.selectedDateKey == today.toString() }
        assertEquals("a repeated open must not close the detail", today.toString(), reopened.selectedDateKey)
    }

    @Test
    fun moveTime_roundsToFiveMinutesAndRefusesAnOccupiedMinute() = runTest {
        repository.addRecord(today.toString(), 600) // 10:00
        repository.addRecord(today.toString(), 630) // 10:30

        viewModel.selectDay(today.toString())
        val opened = awaitState { state -> state.detail.size == 2 }

        viewModel.moveTime(opened.detail[0], 607) // 10:07 -> 10:05
        val moved = awaitState { state -> state.detail.firstOrNull()?.record?.minuteOfDay == 605 }
        assertEquals(605, moved.detail[0].record.minuteOfDay)

        val target = moved.detail[1]
        viewModel.moveTime(target, 606) // rounds to 10:05, already taken
        val blocked = awaitState { state -> state.message != null }

        assertEquals(
            "Đã có điếu lúc ${formatHhMm(605)} rồi",
            blocked.message,
        )
        assertEquals(listOf(605, 630), blocked.detail.map { it.record.minuteOfDay })

        viewModel.clearMessage()
        assertNull(awaitState { state -> state.message == null }.message)
    }

    @Test
    fun weekendComparison_usesTheLastThirtyDaysAndShowsDashWhenWeekendIsMissing() = runTest {
        // Only Monday-to-Friday days: the weekend column must stay null, not zero.
        val weekdays = listOf("2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29", "2026-10-30")
        weekdays.forEach { day -> seedDay(day, count = 4) }

        val state = awaitState { it.weekday != null }

        assertNotNull(state.weekday)
        assertNull(state.weekend)
        assertEquals(5, state.weekday!!.dayCount)
        assertEquals(4, state.weekday!!.avgPerDay)
    }

    @Test
    fun moveTime_refusesAChangeThatRoundsIntoTomorrow() = runTest {
        repository.addRecord(today.toString(), 600)

        viewModel.selectDay(today.toString())
        val opened = awaitState { state -> state.detail.size == 1 }

        viewModel.moveTime(opened.detail[0], 1438) // 23:58 -> the PWA rounds to 00:00 next day
        val refused = awaitState { state -> state.message != null }

        assertEquals("Giờ này làm tròn sang ngày khác — chọn giờ khác", refused.message)
        assertEquals(600, refused.detail[0].record.minuteOfDay) // nothing moved
    }
}
