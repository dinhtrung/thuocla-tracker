package vn.dinhtrung.thuocla.domain.history

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.dinhtrung.thuocla.domain.CigRecord

class HistoryStatsTest {

    private val today = LocalDate.of(2026, 11, 2) // Monday

    private fun record(date: String, minuteOfDay: Int, trigger: Int? = null) =
        CigRecord(dateKey = date, minuteOfDay = minuteOfDay, triggerIdx = trigger)

    // ------------------------------------------------------------ 6.1 chart

    @Test
    fun weekWindow_pageZero_isTheLastSevenDaysEndingToday() {
        val window = weekWindow(today, pagesBack = 0)

        assertEquals(LocalDate.of(2026, 10, 27), window.from)
        assertEquals(today, window.to)
        assertEquals("7 ngày qua", window.label)
        assertEquals(
            listOf(
                "2026-10-27", "2026-10-28", "2026-10-29", "2026-10-30",
                "2026-10-31", "2026-11-01", "2026-11-02",
            ),
            window.dateKeys,
        )
    }

    @Test
    fun weekWindow_previousPage_shiftsExactlyOneWeek() {
        val window = weekWindow(today, pagesBack = 1)

        assertEquals(LocalDate.of(2026, 10, 20), window.from)
        assertEquals(LocalDate.of(2026, 10, 26), window.to)
        assertEquals("10-20 – 10-26", window.label)
        assertTrue(window.dateKeys.none { it in weekWindow(today, 0).dateKeys })
    }

    @Test
    fun chartDays_countsEachDayIncludingEmptyOnes_andAveragesGaps() {
        val records = listOf(
            record("2026-10-31", 400),
            record("2026-10-31", 460),
            record("2026-10-31", 500),
            record("2026-11-01", 600),
        )

        val days = chartDays(records, weekWindow(today, 0))

        assertEquals(7, days.size)
        val saturday = days.single { it.dateKey == "2026-10-31" }
        assertEquals("T7", saturday.dayName)
        assertEquals(3, saturday.count)
        assertEquals(50, saturday.avgGapMinutes) // (60 + 40) / 2

        val sunday = days.single { it.dateKey == "2026-11-01" }
        assertEquals(1, sunday.count)
        assertNull(sunday.avgGapMinutes) // a single cigarette has no gap

        val empty = days.single { it.dateKey == "2026-11-02" }
        assertEquals(0, empty.count)
        assertNull(empty.avgGapMinutes)
    }

    @Test
    fun chartDays_ignoresRecordsOutsideTheWindow() {
        val records = listOf(record("2026-10-26", 400), record("2026-11-02", 500))

        val days = chartDays(records, weekWindow(today, 0))

        assertEquals(1, days.sumOf { it.count })
        assertTrue(days.none { it.dateKey == "2026-10-26" })
    }

    // ------------------------------------------------------- 6.2 day detail

    @Test
    fun dayDetail_numbersRowsAndMeasuresGapsAgainstThePreviousCigarette() {
        val rows = dayDetail(
            records = listOf(
                record("2026-11-02", 655, trigger = 1),
                record("2026-11-02", 500),
                record("2026-11-02", 600),
            ),
            intervalGoal = 60,
        )

        assertEquals(listOf(1, 2, 3), rows.map { it.index })
        assertEquals(listOf(500, 600, 655), rows.map { it.record.minuteOfDay })
        assertNull(rows[0].gapMinutes)
        assertNull(rows[0].gapDeltaToGoal)
        assertEquals(100, rows[1].gapMinutes)
        assertEquals(40, rows[1].gapDeltaToGoal)   // 100 - 60, later than the goal
        assertEquals(55, rows[2].gapMinutes)
        assertEquals(-5, rows[2].gapDeltaToGoal)   // 55 - 60, earlier than the goal
        assertEquals(1, rows[2].record.triggerIdx)
    }

    @Test
    fun dayDetail_emptyDayIsEmpty() {
        assertTrue(dayDetail(emptyList(), intervalGoal = 60).isEmpty())
    }

    // ------------------------------------------- 6.3 weekend vs weekday

    @Test
    fun weekendComparison_missingWeekendDays_yieldsNullNotZero() {
        val records = (0..5).map { record("2026-10-27", 400 + it * 60) } // Tuesday only

        val comparison = weekendComparison(records, today)

        assertNull(comparison.weekend) // spec: "—", never 0
        assertEquals(1, comparison.weekday!!.dayCount)
    }

    @Test
    fun weekendComparison_computesPerDayAveragesGapsChainsAndFirstLast() {
        val records = listOf(
            // Saturday 2026-10-31: gaps 60 and 40 -> chain 2, avg gap 50
            record("2026-10-31", 400),
            record("2026-10-31", 460),
            record("2026-10-31", 500),
            // Sunday 2026-11-01: gap 100 -> chain 0, avg gap 100
            record("2026-11-01", 600),
            record("2026-11-01", 700),
            // Monday 2026-10-26: gaps 60, 60, 60 -> chain 3
            record("2026-10-26", 360),
            record("2026-10-26", 420),
            record("2026-10-26", 480),
            record("2026-10-26", 540),
        )

        val comparison = weekendComparison(records, today)
        val weekend = comparison.weekend!!
        val weekday = comparison.weekday!!

        assertEquals(2, weekend.dayCount)
        assertEquals(5, weekend.totalCigarettes)
        assertEquals(3, weekend.avgPerDay)              // round(5 / 2)
        assertEquals(67, weekend.avgGapMinutes)         // (60 + 40 + 100) / 3 gaps, like the PWA
        assertEquals(0.5, weekend.chainPerDay, 0.001)   // only the 40ph gap is a chain -> 1 / 2 days
        assertEquals(500, weekend.avgFirstMinute)       // (400 + 600) / 2 -> 08:20
        assertEquals(600, weekend.avgLastMinute)        // (500 + 700) / 2 -> 10:00

        assertEquals(1, weekday.dayCount)
        assertEquals(4, weekday.avgPerDay)
        assertEquals(60, weekday.avgGapMinutes)
        assertEquals(0.0, weekday.chainPerDay, 0.001)  // all gaps are 60ph > 40ph
        assertEquals(360, weekday.avgFirstMinute)
        assertEquals(540, weekday.avgLastMinute)
    }

    @Test
    fun weekendComparison_stopsAtThirtyDaysAndIncludesToday() {
        val old = LocalDate.of(2026, 10, 1) // 32 days before today -> outside
        val edge = today.minusDays(29)      // first day inside
        val records = listOf(
            record(old.toString(), 600),
            record(edge.toString(), 600),
            record(today.toString(), 700),
        )

        val comparison = weekendComparison(records, today)

        // Oct 1 is outside the 30-day window. Oct 4 (Sunday) and today (Monday) are inside.
        assertEquals(1, comparison.weekday!!.dayCount)
        assertEquals(1, comparison.weekday!!.totalCigarettes)
        assertEquals(1, comparison.weekend!!.dayCount)
        assertEquals(1, comparison.weekend!!.totalCigarettes)
    }
}
