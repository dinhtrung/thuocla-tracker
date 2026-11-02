package vn.dinhtrung.thuocla.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** tasks.md 3.1 — the rounding rule ported from `app.js` (`Math.round(m / 5) * 5`). */
class TimeTest {

    @Test
    fun roundTo5_14h07_becomes_14h05() {
        // 14:07 -> the spec's first acceptance number.
        assertEquals(845, roundTo5(14 * 60 + 7).minuteOfDay)
        assertEquals(0, roundTo5(14 * 60 + 7).dayOffset)
    }

    @Test
    fun roundTo5_14h08_becomes_14h10() {
        assertEquals(850, roundTo5(14 * 60 + 8).minuteOfDay)
    }

    @Test
    fun roundTo5_boundaries_round_up_likeJavaScript() {
        assertEquals(0, roundTo5(0).minuteOfDay)
        assertEquals(0, roundTo5(2).minuteOfDay)
        assertEquals(5, roundTo5(3).minuteOfDay)
        assertEquals(845, roundTo5(845).minuteOfDay)
        assertEquals(845, roundTo5(846).minuteOfDay)
        assertEquals(845, roundTo5(847).minuteOfDay)
        assertEquals(850, roundTo5(848).minuteOfDay)
    }

    @Test
    fun roundTo5_2358_rollsIntoTheNextDay() {
        // setMinutes(round(58/5)*5) = setMinutes(60), which is midnight of the NEXT date in JS.
        val rounded = roundTo5(23 * 60 + 58)
        assertEquals(0, rounded.minuteOfDay)
        assertEquals(1, rounded.dayOffset)

        assertEquals(0, roundTo5(1439).minuteOfDay)
        assertEquals(1, roundTo5(1439).dayOffset)
    }

    @Test
    fun roundTo5_rejectsMinutesOutsideTheDay() {
        for (bad in listOf(-1, 1440, 5000)) {
            try {
                roundTo5(bad)
                throw AssertionError("roundTo5($bad) should have thrown")
            } catch (expected: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun rolloverDate_crossesMonthAndYear() {
        assertEquals("2026-11-02", rolloverDate("2026-11-02", 0))
        assertEquals("2026-11-03", rolloverDate("2026-11-02", 1))
        assertEquals("2026-12-01", rolloverDate("2026-11-30", 1))
        assertEquals("2027-01-01", rolloverDate("2026-12-31", 1))
        assertNull(rolloverDate("not-a-date", 1))
    }

    @Test
    fun timeKey_matchesThePwaShape() {
        // app.js: `${y}-${m}-${d}-${h}-${min}` — used only as identity.
        assertEquals("2026-11-02-14-5", timeKey("2026-11-02", 845))
        assertEquals(
            timeKey("2026-11-02", 845),
            timeKey(CigRecord("2026-11-02", 845, triggerIdx = 3)),
        )
    }

    @Test
    fun formatHhMm_isZeroPadded() {
        assertEquals("14:05", formatHhMm(845))
        assertEquals("00:00", formatHhMm(0))
        assertEquals("23:59", formatHhMm(1439))
        assertEquals("", formatHhMm(1440))
    }

    @Test
    fun minuteOfDayOf_rejectsImpossibleTimes() {
        assertEquals(615, minuteOfDayOf(10, 15))
        assertNull(minuteOfDayOf(25, 99))
        assertNull(minuteOfDayOf(10, 60))
        assertNull(minuteOfDayOf(-1, 0))
    }
}
