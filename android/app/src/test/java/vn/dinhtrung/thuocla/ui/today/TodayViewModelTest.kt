package vn.dinhtrung.thuocla.ui.today

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import vn.dinhtrung.thuocla.testutil.MainDispatcherRule
import vn.dinhtrung.thuocla.testutil.MutableClock

/**
 * tasks.md 3.2 / 3.3 at the state level: +1 increments the counter, a duplicate does not, the reason
 * sheet opens on the cigarette that was just created, and two cigarettes keep two reasons.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodayViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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

    private fun viewModel(triggerPromptDelayMillis: Long = 0L) = TodayViewModel(
        log = CigaretteLog(repo, clock),
        repository = repo,
        triggerPromptDelayMillis = triggerPromptDelayMillis,
    )

    @Test
    fun addCig_twice_incrementsTheCounterToTwo() = runBlocking {
        val vm = viewModel()

        vm.addCig()
        awaitUntil("first cigarette in state") { vm.state.value.count == 1 }

        clock.advance(Duration.ofMinutes(60)) // 15:07 -> 15:05, a different minute
        vm.addCig()
        awaitUntil("second cigarette in state") { vm.state.value.count == 2 }

        assertEquals(listOf(845, 905), vm.state.value.records.map { it.minuteOfDay })
        assertEquals(10, vm.state.value.goal)
        assertEquals(0.2f, vm.state.value.progress, 0.001f)
    }

    @Test
    fun addCig_sameMinute_showsInCardWarning_andDoesNotOpenTheSheet() = runBlocking {
        val vm = viewModel()

        vm.addCig()
        awaitUntil("first cigarette") { vm.state.value.count == 1 }
        awaitUntil("first sheet") { vm.state.value.triggerTarget != null }
        vm.dismissTriggerSheet()
        awaitUntil("sheet closed") { vm.state.value.triggerTarget == null }

        vm.addCig()
        awaitUntil("duplicate warning") { vm.state.value.warning != null }

        val warning = vm.state.value.warning.orEmpty()
        assertTrue("warning was: $warning", warning.contains("Đã có điếu lúc 14:05"))
        assertEquals(1, vm.state.value.count)
        assertNull("the reason sheet must not open for a blocked duplicate", vm.state.value.triggerTarget)
    }

    @Test
    fun addCig_opensSheetTargetedAtTheCreatedCigarette() = runBlocking {
        val vm = viewModel()

        vm.addCig()
        awaitUntil("sheet target") { vm.state.value.triggerTarget != null }

        val target = vm.state.value.triggerTarget
        assertNotNull(target)
        assertEquals(CigRecordTarget(today, 845), target?.let { CigRecordTarget(it.dateKey, it.minuteOfDay) })
    }

    @Test
    fun twoCigarettes_keepTwoDifferentReasons() = runBlocking {
        // tasks.md 3.3: +1 -> Cà phê, +1 -> Stress means cigarette 1 = Cà phê and cigarette 2 = Stress.
        val vm = viewModel()

        vm.addCig()
        awaitUntil("first sheet") { vm.state.value.triggerTarget != null }
        vm.selectTrigger(1) // Cà phê
        awaitUntil("first reason stored") { repo.day(today).firstOrNull()?.triggerIdx == 1 }

        clock.advance(Duration.ofMinutes(60))
        vm.addCig()
        awaitUntil("second cigarette") { vm.state.value.count == 2 }
        awaitUntil("second sheet") { vm.state.value.triggerTarget?.minuteOfDay == 905 }
        vm.selectTrigger(2) // Stress
        awaitUntil("second reason stored") { repo.day(today).lastOrNull()?.triggerIdx == 2 }

        val stored = repo.day(today)
        assertEquals(2, stored.size)
        assertEquals(845 to 1, stored[0].minuteOfDay to stored[0].triggerIdx)
        assertEquals(905 to 2, stored[1].minuteOfDay to stored[1].triggerIdx)
    }

    @Test
    fun undo_removesTheNewestCigarette() = runBlocking {
        val vm = viewModel()

        vm.addCig()
        awaitUntil("first cigarette") { vm.state.value.count == 1 }
        clock.advance(Duration.ofMinutes(60))
        vm.addCig()
        awaitUntil("second cigarette") { vm.state.value.count == 2 }

        vm.undo()
        awaitUntil("undo applied") { vm.state.value.count == 1 }

        assertEquals(listOf(845), repo.day(today).map { it.minuteOfDay })
    }

    @Test
    fun addAt_duplicateInThePast_showsTheSameWarning() = runBlocking {
        val vm = viewModel()
        repo.addRecord(today, 615) // 10:15 already logged

        vm.addAt(today, 10, 15)
        awaitUntil("duplicate warning for 10:15") {
            vm.state.value.warning?.contains("Đã có điếu lúc 10:15") == true
        }

        assertEquals(1, repo.day(today).size)
    }

    // ------------------------------------------------------------------ helpers

    private data class CigRecordTarget(val dateKey: String, val minuteOfDay: Int)

    /** Room delivers flow emissions from its own executor, so poll instead of assuming a tick. */
    private suspend fun awaitUntil(what: String, condition: suspend () -> Boolean) {
        val reached = withTimeoutOrNull(5_000) {
            while (!condition()) delay(10)
            true
        }
        assertTrue("timed out waiting for: $what", reached == true)
    }
}
