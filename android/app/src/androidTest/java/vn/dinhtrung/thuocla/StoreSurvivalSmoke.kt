package vn.dinhtrung.thuocla

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import vn.dinhtrung.thuocla.data.TrackerRepository
import vn.dinhtrung.thuocla.data.db.AppDatabase

/**
 * tasks.md 2.4, executed as two separate instrumentation runs with `am force-stop` in between:
 *
 *   adb shell am instrument -w -e class vn.dinhtrung.thuocla.StoreSeedSmoke#seedThreeRecords ...
 *   adb shell am force-stop vn.dinhtrung.thuocla
 *   adb shell am instrument -w -e class vn.dinhtrung.thuocla.StoreVerifySmoke#threeRecordsSurvived ...
 *
 * Two classes on purpose: a single class keeps one process alive, which would not prove anything
 * about a killed app.
 */
@RunWith(AndroidJUnit4::class)
class StoreSeedSmoke {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun seedThreeRecords() = runBlocking {
        context.deleteDatabase(AppDatabase.NAME)
        val repo = TrackerRepository(AppDatabase.build(context))

        assertTrue(repo.addRecord(DAY, 600, triggerIdx = 1).applied)
        assertTrue(repo.addRecord(DAY, 615, triggerIdx = 2).applied)
        assertTrue(repo.addRecord(DAY, 720).applied)

        assertEquals(3, repo.day(DAY).size)
    }

    private companion object {
        const val DAY = "2026-11-02"
    }
}

@RunWith(AndroidJUnit4::class)
class StoreVerifySmoke {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun threeRecordsSurvivedTheKill() = runBlocking {
        val repo = TrackerRepository(AppDatabase.build(context))

        val records = repo.day(DAY)
        assertEquals(3, records.size)
        assertEquals(listOf(600, 615, 720), records.map { it.minuteOfDay })
        assertEquals(listOf(1, 2, null), records.map { it.triggerIdx })
    }

    private companion object {
        const val DAY = "2026-11-02"
    }
}
