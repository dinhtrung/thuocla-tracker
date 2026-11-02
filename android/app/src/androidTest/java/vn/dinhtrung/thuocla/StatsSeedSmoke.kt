package vn.dinhtrung.thuocla

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import vn.dinhtrung.thuocla.data.TrackerRepository
import vn.dinhtrung.thuocla.data.db.AppDatabase

/**
 * Seeds two deterministic weeks so the stats screen can be exercised by hand (or by a UI script)
 * on a device: the current 7 days have 2 cigarettes each, the previous 7 days have 5 each.
 *
 * Run with:
 *   adb shell am instrument -w -e class vn.dinhtrung.thuocla.StatsSeedSmoke#seedFourteenDays \
 *     vn.dinhtrung.thuocla.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class StatsSeedSmoke {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun seedFourteenDays() = runBlocking {
        context.deleteDatabase(AppDatabase.NAME)
        val repository = TrackerRepository(AppDatabase.build(context))
        val today = LocalDate.now()

        for (offset in 0..13) {
            val dateKey = today.minusDays(offset.toLong()).toString()
            val count = if (offset < 7) 2 else 5
            repeat(count) { i ->
                repository.addRecord(dateKey, minuteOfDay = 360 + i * 60)
            }
        }

        assertEquals(2, repository.day(today.toString()).size)
        assertEquals(5, repository.day(today.minusDays(7).toString()).size)
    }
}
