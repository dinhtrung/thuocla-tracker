package vn.dinhtrung.thuocla.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.dinhtrung.thuocla.data.db.AppDatabase
import vn.dinhtrung.thuocla.data.db.RecordEntity
import vn.dinhtrung.thuocla.domain.CigRecord
import vn.dinhtrung.thuocla.domain.TrackerConfig
import vn.dinhtrung.thuocla.domain.WriteBlock

/** tasks.md 2.1 / 2.2 / 2.3 — Room store, duplicate-minute rule, defaults. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackerRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: TrackerRepository

    private val day = "2026-11-02"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TrackerRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ------------------------------------------------------- 2.1 unique index

    @Test
    fun duplicateMinute_isRejectedByTheDatabaseItself() = runTest {
        recordDao().insert(RecordEntity(day, 615))
        assertEquals(1, repo.day(day).size)

        // Bypass the DAO on purpose: the constraint must live in the schema, not in code.
        try {
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO records(dateKey, minuteOfDay) VALUES('$day', 615)"
            )
            fail("second row for the same (dateKey, minuteOfDay) was accepted")
        } catch (expected: SQLiteConstraintException) {
            // expected
        }
        assertEquals(1, repo.day(day).size)
    }

    @Test
    fun addRecord_duplicateMinuteIsBlockedAndKeepsTheOriginalRow() = runTest {
        val first = repo.addRecord(day, 615, triggerIdx = 2, note = "original")
        assertTrue(first.applied)
        assertEquals(WriteBlock.NONE, first.block)

        val second = repo.addRecord(day, 615, triggerIdx = 5, note = "overwrite attempt")

        assertFalse(second.applied)
        assertEquals(WriteBlock.DUPLICATE_MINUTE, second.block)
        assertEquals(CigRecord(day, 615, triggerIdx = 2, note = "original"), second.record)
        val stored = repo.day(day)
        assertEquals(listOf(CigRecord(day, 615, triggerIdx = 2, note = "original")), stored)
    }

    // ------------------------------------------------------------ 2.2 records

    @Test
    fun deleteLast_removesOnlyTheNewestRecordOfThatDay() = runTest {
        repo.addRecord(day, 600, triggerIdx = 1)
        repo.addRecord(day, 720, triggerIdx = 2)
        repo.addRecord(day, 900, triggerIdx = 3)

        val deleted = repo.deleteLast(day)

        assertEquals(CigRecord(day, 900, triggerIdx = 3), deleted)
        assertEquals(listOf(600, 720), repo.day(day).map { it.minuteOfDay })
        assertTrue(repo.day("2026-11-03").isEmpty())
        assertNull(repo.deleteLast("2026-11-03"))
    }

    @Test
    fun updateTime_movesTheRecordAndKeepsReasonAndNote() = runTest {
        repo.addRecord(day, 615, triggerIdx = 1, note = "after lunch")

        val result = repo.updateTime(day, fromMinute = 615, toMinute = 720)

        assertTrue(result.applied)
        assertEquals(CigRecord(day, 720, triggerIdx = 1, note = "after lunch"), result.record)
        assertEquals(listOf(CigRecord(day, 720, triggerIdx = 1, note = "after lunch")), repo.day(day))
    }

    @Test
    fun updateTime_rejectsOccupiedTargetAndLeavesTheOriginalInPlace() = runTest {
        repo.addRecord(day, 615, triggerIdx = 1)
        repo.addRecord(day, 720, triggerIdx = 2)

        val result = repo.updateTime(day, fromMinute = 615, toMinute = 720)

        assertFalse(result.applied)
        assertEquals(WriteBlock.DUPLICATE_MINUTE, result.block)
        assertEquals(
            listOf(615 to 1, 720 to 2),
            repo.day(day).map { it.minuteOfDay to it.triggerIdx },
        )
    }

    @Test
    fun updateTime_missingSourceIsInvalid() = runTest {
        val result = repo.updateTime(day, fromMinute = 615, toMinute = 720)
        assertFalse(result.applied)
        assertEquals(WriteBlock.INVALID, result.block)
        assertTrue(repo.day(day).isEmpty())
    }

    @Test
    fun setTrigger_attachesAndClearsTheReason() = runTest {
        repo.addRecord(day, 615)

        assertTrue(repo.setTrigger(day, 615, 1))
        assertEquals(1, repo.day(day).single().triggerIdx)

        assertTrue(repo.setTrigger(day, 615, null))
        assertNull(repo.day(day).single().triggerIdx)
    }

    @Test
    fun setTrigger_onMissingRowReportsFalse() = runTest {
        assertFalse(repo.setTrigger(day, 615, 1))
    }

    @Test
    fun dayAndRange_returnOrderedRecordsAndRangeIsInclusive() = runTest {
        repo.addRecord("2026-10-31", 900)
        repo.addRecord(day, 720)
        repo.addRecord(day, 600)
        repo.addRecord("2026-11-03", 60)

        assertEquals(listOf(600, 720), repo.day(day).map { it.minuteOfDay })
        assertEquals(
            listOf("2026-10-31" to 900, day to 600, day to 720),
            repo.range("2026-10-31", day).map { it.dateKey to it.minuteOfDay },
        )
        assertTrue(repo.range(day, "2026-10-31").isEmpty())
        assertTrue(repo.range("not-a-date", day).isEmpty())
    }

    @Test
    fun monthCount_countsOnlyTheGivenMonth() = runTest {
        repo.addRecord("2026-10-31", 900)
        repo.addRecord("2026-11-01", 600)
        repo.addRecord(day, 615)
        repo.addRecord(day, 720)

        assertEquals(3, repo.monthCount("2026-11"))
        assertEquals(1, repo.monthCount("2026-10"))
        assertEquals(0, repo.monthCount("2026-12"))
        assertEquals(0, repo.monthCount("2026-13"))
    }

    @Test
    fun distinctDayCount_countsDaysNotRecords() = runTest {
        repo.addRecord(day, 600)
        repo.addRecord(day, 720)
        repo.addRecord("2026-11-03", 60)

        assertEquals(2, repo.distinctDayCount())
    }

    @Test
    fun invalidMinuteOrTriggerIsRejected() = runTest {
        assertEquals(WriteBlock.INVALID, repo.addRecord(day, 1440).block)
        assertEquals(WriteBlock.INVALID, repo.addRecord(day, -1).block)
        assertEquals(WriteBlock.INVALID, repo.addRecord("2026-02-30", 600).block)
        assertEquals(WriteBlock.INVALID, repo.addRecord(day, 600, triggerIdx = 8).block)
        assertTrue(repo.day(day).isEmpty())
    }

    @Test
    fun dayFlow_emitsWhatTheRepositoryStored() = runTest {
        repo.addRecord(day, 615, triggerIdx = 1)

        assertEquals(listOf(615), repo.dayFlow(day).first().map { it.minuteOfDay })
    }

    // ------------------------------------------------------------- 2.2 config

    @Test
    fun getConfig_seedsThePwaDefaults() = runTest {
        assertEquals(TrackerConfig.DEFAULT, repo.getConfig())
        assertEquals(10, repo.getConfig().goal)
        assertEquals(60, repo.getConfig().intervalGoal)
        assertEquals(25_000L, repo.getConfig().pricePerPack)
        assertEquals(20, repo.getConfig().cigsPerPack)
    }

    @Test
    fun saveConfig_persistsAndNormalizesBlankFieldsToDefaults() = runTest {
        val saved = repo.saveConfig(TrackerConfig(goal = 8, intervalGoal = 45, pricePerPack = 30_000L, cigsPerPack = 20))
        assertEquals(TrackerConfig(8, 45, 30_000L, 20), saved)
        assertEquals(TrackerConfig(8, 45, 30_000L, 20), repo.getConfig())
        assertEquals(8, repo.configFlow().first().goal)

        val normalized = repo.saveConfig(TrackerConfig(goal = 0, intervalGoal = -5, pricePerPack = 0L, cigsPerPack = 0))
        assertEquals(TrackerConfig.DEFAULT, normalized)
        assertEquals(TrackerConfig.DEFAULT, repo.getConfig())
    }

    @Test
    fun configRowIsNotDuplicatedByRepeatedWrites() = runTest {
        repo.saveConfig(TrackerConfig(goal = 8))
        repo.saveConfig(TrackerConfig(goal = 9))

        val rows = db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM config")
            .use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

        assertEquals(1, rows)
        assertEquals(9, repo.getConfig().goal)
    }

    // ------------------------------------------------ 2.4 persistence (proxy)

    @Test
    fun recordsAndConfigSurviveClosingAndReopeningTheDatabase() = runTest {
        val name = "reopen-test.db"
        context.deleteDatabase(name)

        var fileDb: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val firstRepo = TrackerRepository(fileDb)
            firstRepo.addRecord(day, 600, triggerIdx = 1)
            firstRepo.addRecord(day, 615)
            firstRepo.addRecord(day, 720, triggerIdx = 2)
            firstRepo.saveConfig(TrackerConfig(goal = 12))
        } finally {
            fileDb.close()
        }

        fileDb = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val reopened = TrackerRepository(fileDb)
            assertEquals(
                listOf(600 to 1, 615 to null, 720 to 2),
                reopened.day(day).map { it.minuteOfDay to it.triggerIdx },
            )
            assertEquals(12, reopened.getConfig().goal)
        } finally {
            fileDb.close()
        }
    }

    private fun recordDao() = db.recordDao()
}
