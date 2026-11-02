package vn.dinhtrung.thuocla.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.dinhtrung.thuocla.data.TrackerRepository
import vn.dinhtrung.thuocla.data.db.AppDatabase
import vn.dinhtrung.thuocla.testutil.MutableClock

/**
 * tasks.md 3.1 / 3.4 — the log rules on top of the store: rounding, duplicate-minute guard, undo,
 * time edits and trigger toggling. The clock is frozen at 2026-11-02 14:07 (+07).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CigaretteLogTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: TrackerRepository
    private lateinit var clock: MutableClock
    private lateinit var log: CigaretteLog

    private val today = "2026-11-02"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TrackerRepository(db)
        clock = MutableClock.at("2026-11-02T14:07:30+07:00")
        log = CigaretteLog(repo, clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ------------------------------------------------------------- addNow (3.1)

    @Test
    fun addNow_at1407_stores1405() = runTest {
        val result = log.addNow()

        assertTrue(result.applied)
        assertEquals(CigRecord(today, 845), result.record)
        assertEquals(listOf(CigRecord(today, 845)), repo.day(today))
    }

    @Test
    fun addNow_at1408_stores1410() = runTest {
        clock.advance(Duration.ofMinutes(1)) // 14:08
        val result = log.addNow()

        assertTrue(result.applied)
        assertEquals(850, result.record?.minuteOfDay)
    }

    @Test
    fun addNow_twiceInTheSameFiveMinutes_isBlocked() = runTest {
        assertTrue(log.addNow().applied)
        clock.advance(Duration.ofSeconds(20)) // 14:07:50 — still 14:07, so still 14:05 after rounding

        val second = log.addNow()

        assertFalse(second.applied)
        assertEquals(WriteBlock.DUPLICATE_MINUTE, second.block)
        assertEquals(845, second.record?.minuteOfDay)
        assertEquals(1, repo.day(today).size)
    }

    @Test
    fun addNow_at2358_isLoggedOnTheNextDayAtMidnight() = runTest {
        clock = MutableClock.at("2026-11-02T23:58:10+07:00")
        log = CigaretteLog(repo, clock)

        val result = log.addNow()

        assertTrue(result.applied)
        assertEquals(CigRecord("2026-11-03", 0), result.record)
        assertTrue(repo.day(today).isEmpty())
    }

    // --------------------------------------------------------------- addAt (3.4)

    @Test
    fun addAt_existingMinute_isBlocked() = runTest {
        assertTrue(log.addAt(today, 10, 15).applied)

        val duplicate = log.addAt(today, 10, 15)

        assertFalse(duplicate.applied)
        assertEquals(WriteBlock.DUPLICATE_MINUTE, duplicate.block)
        assertEquals(1, repo.day(today).size)
    }

    @Test
    fun addAt_roundsTheChosenTime() = runTest {
        val result = log.addAt(today, 14, 8)
        assertEquals(850, result.record?.minuteOfDay)
    }

    @Test
    fun addAt_pastDay_isStoredOnThatDay() = runTest {
        val past = "2026-10-31"

        val result = log.addAt(past, 9, 2)

        assertTrue(result.applied)
        assertEquals(CigRecord(past, 540), result.record)
        assertTrue(repo.day(today).isEmpty())
        assertEquals(listOf(CigRecord(past, 540)), repo.range(past, today))
    }

    @Test
    fun addAt_rejectsImpossibleTime() = runTest {
        val result = log.addAt(today, 25, 99)

        assertFalse(result.applied)
        assertEquals(WriteBlock.INVALID, result.block)
        assertTrue(repo.day(today).isEmpty())
    }

    // ------------------------------------------------------------ editTime (3.4)

    @Test
    fun editTime_0820_to_0827_stores0825() = runTest {
        log.addAt(today, 8, 20) // 500

        val result = log.editTime(today, fromMinute = 500, toMinute = 8 * 60 + 27)

        assertTrue(result.applied)
        assertEquals(505, result.record?.minuteOfDay) // 08:27 rounds down to 08:25
        assertEquals(listOf(CigRecord(today, 505)), repo.day(today))
    }

    @Test
    fun editTime_ontoOccupiedMinute_isBlockedAndKeepsBothRows() = runTest {
        log.addAt(today, 8, 20) // 500
        log.addAt(today, 8, 30) // 510

        val result = log.editTime(today, fromMinute = 500, toMinute = 510)

        assertFalse(result.applied)
        assertEquals(WriteBlock.DUPLICATE_MINUTE, result.block)
        assertEquals(listOf(500, 510), repo.day(today).map { it.minuteOfDay })
    }

    @Test
    fun editTime_pastMidnight_isRefusedInsteadOfMovingTheDay() = runTest {
        log.addAt(today, 8, 20)

        val result = log.editTime(today, fromMinute = 500, toMinute = 23 * 60 + 58)

        assertFalse(result.applied)
        assertEquals(WriteBlock.INVALID, result.block)
        assertEquals(listOf(500), repo.day(today).map { it.minuteOfDay })
    }

    // ----------------------------------------------------------------- undo (3.1)

    @Test
    fun undoLastToday_removesOnlyTheNewestCigarette() = runTest {
        log.addAt(today, 10, 15)
        log.addAt(today, 14, 5)

        val removed = log.undoLastToday()

        assertEquals(CigRecord(today, 845), removed)
        assertEquals(listOf(615), repo.day(today).map { it.minuteOfDay })
    }

    @Test
    fun undoLastToday_onEmptyDay_returnsNull() = runTest {
        assertNull(log.undoLastToday())
    }

    // ------------------------------------------------------------- triggers (3.3)

    @Test
    fun toggleTrigger_setsThenClearsTheSameReason() = runTest {
        log.addAt(today, 10, 15)

        assertTrue(log.toggleTrigger(today, 615, 2)) // Stress
        assertEquals(2, repo.day(today).single().triggerIdx)

        assertTrue(log.toggleTrigger(today, 615, 2)) // tapping Stress again clears it
        assertNull(repo.day(today).single().triggerIdx)
    }

    @Test
    fun toggleTrigger_rejectsUnknownReasonAndMissingRow() = runTest {
        log.addAt(today, 10, 15)

        assertFalse(log.toggleTrigger(today, 615, 8))
        assertFalse(log.toggleTrigger(today, 700, 1))
        assertNull(repo.day(today).single().triggerIdx)
    }
}
