package vn.dinhtrung.thuocla.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * tasks.md 4.1 / 4.2 / 4.3 as arithmetic: the two mandatory numbers of the first-day case, the clock
 * card under and over its interval goal, and the comparison table's always-drawn "Hôm nay" column.
 *
 * These are pure functions, so this test needs neither Robolectric nor a database — the numbers in
 * the spec are checked directly.
 */
class DailySummaryTest {

    private val today = "2026-11-02" // a Monday
    private val yesterday = "2026-11-01"
    private val twoDaysAgo = "2026-10-31"

    private fun record(dateKey: String, minuteOfDay: Int, triggerIdx: Int? = null) =
        CigRecord(dateKey = dateKey, minuteOfDay = minuteOfDay, triggerIdx = triggerIdx)

    private fun fiveToday(): List<CigRecord> = listOf(
        record(today, 505), // 08:25
        record(today, 590), // 09:50
        record(today, 705), // 11:45
        record(today, 755), // 12:35
        record(today, 845), // 14:05
    )

    // --------------------------------------------------- 4.1 mandatory: 5.0 not 0.7

    @Test
    fun avg7Days_withOnlyTodayFive_isFivePointZero_notZeroPointSeven() {
        val summary = buildDailySummary(fiveToday(), today)

        // Divided by the one day that has data, not by 7: 5/7 = 0.714 would look like a broken app.
        assertEquals(5.0f, summary.avg7Days, 0.0001f)
        assertEquals("5.0", formatAverage(summary.avg7Days))
    }

    @Test
    fun lowestDayCard_onTheFirstDay_readsFiveToday() {
        val summary = buildDailySummary(fiveToday(), today)
        val lowest = summary.lowestDay

        assertEquals(5, lowest?.count)
        assertEquals(today, lowest?.dateKey)
        assertTrue("first day must be flagged as the fallback", lowest?.isTodayFallback == true)
        assertEquals("Ít nhất • Hôm nay", lowestDayLabel(lowest))
        // The fallback is a display value only: the comparison table must not treat it as a reference.
        assertFalse(lowest!!.isReference)
    }

    @Test
    fun yesterdayAndMonth_followTheSameRecords() {
        val records = fiveToday() + listOf(record(yesterday, 480), record(yesterday, 540)) +
            listOf(record(twoDaysAgo, 600))
        val summary = buildDailySummary(records, today)

        assertEquals(2, summary.yesterdayCount)
        assertEquals(7, summary.monthCount) // 5 today + 2 yesterday; 10-31 belongs to October
    }

    @Test
    fun avg7Days_ignoresDaysWithoutData_butCountsDaysWithData() {
        // Today 5, yesterday 3, two days ago 1 => mean of 3 active days = 3.0, not 9/7 = 1.3.
        val records = fiveToday() +
            listOf(record(yesterday, 480), record(yesterday, 540), record(yesterday, 600)) +
            listOf(record(twoDaysAgo, 615))
        val summary = buildDailySummary(records, today)

        assertEquals(3.0f, summary.avg7Days, 0.0001f)
    }

    @Test
    fun lowestDay_withRealHistory_picksTheFewestDay_oldestOnTies() {
        // 10-31 has 1, yesterday has 2 -> 10-31 wins.
        val records = fiveToday() + listOf(record(yesterday, 480), record(yesterday, 540)) +
            listOf(record(twoDaysAgo, 615))
        val lowest = buildDailySummary(records, today).lowestDay

        assertEquals(twoDaysAgo, lowest?.dateKey)
        assertEquals(1, lowest?.count)
        assertTrue(lowest!!.isReference)
        assertEquals("Ít nhất • T7 10-31", lowestDayLabel(lowest))
    }

    @Test
    fun lowestDay_noRecordsAtAll_isNull() {
        assertNull(buildDailySummary(emptyList(), today).lowestDay)
        assertEquals("Ít nhất", lowestDayLabel(null))
    }

    // ---------------------------------------------- 4.2 clock card: 13:35 / 45 / over 15

    @Test
    fun intervalStatus_underGoal_showsNextTimeAndRemainingMinutes() {
        // Last cigarette 12:35, goal 60, now 12:50 -> 15 elapsed, next 13:35, 45 minutes to go.
        val status = intervalStatus(lastMinute = 755, nowMinute = 770, intervalGoal = 60)

        assertEquals(15, status.elapsedMinutes)
        assertEquals("13:35", status.expectedNext)
        assertEquals(45, status.minutesRemaining)
        assertNull(status.minutesOver)
        assertFalse(status.reachedGoal)
    }

    @Test
    fun intervalStatus_overGoal_showsMinutesOver() {
        // Same last cigarette and goal, now 13:50 -> 75 elapsed, 15 minutes over.
        val status = intervalStatus(lastMinute = 755, nowMinute = 830, intervalGoal = 60)

        assertEquals(75, status.elapsedMinutes)
        assertEquals(15, status.minutesOver)
        assertNull("an exceeded goal has no next time to show", status.expectedNext)
        assertNull(status.minutesRemaining)
        assertTrue(status.reachedGoal)
    }

    @Test
    fun intervalStatus_noCigaretteToday_hasNoElapsedTime() {
        val status = intervalStatus(lastMinute = null, nowMinute = 830, intervalGoal = 60)

        assertEquals(0, status.elapsedMinutes)
        assertNull(status.expectedNext)
        assertNull(status.minutesOver)
    }

    // ------------------------------- 4.3 comparison table always draws the "Hôm nay" column

    @Test
    fun comparisonRows_firstDay_stillDrawsAllFiveTodayRows_andBlankReferenceColumns() {
        val summary = buildDailySummary(fiveToday(), today)
        val rows = comparisonRows(fiveToday(), summary)

        assertEquals("the Today column must never be cut short", 5, rows.size)
        assertEquals(listOf(505, 590, 705, 755, 845), rows.map { it.today?.minuteOfDay })
        assertTrue("no reference day means no lowest cell", rows.all { it.lowest == null })
        assertTrue("no history means no average cell", rows.all { it.avg == null })
    }

    @Test
    fun comparisonRows_withHistory_fillsAllThreeColumnsByStt() {
        // Yesterday: 08:00 and 09:00. Two days ago: 08:10 and 09:10.
        // STT1 average = 485, STT2 average = 545. Lowest day = 10-31 (2 cigarettes, tie -> oldest).
        val history = listOf(
            record(yesterday, 480), record(yesterday, 540),
            record(twoDaysAgo, 490), record(twoDaysAgo, 550),
        )
        val summary = buildDailySummary(fiveToday() + history, today)
        val rows = comparisonRows(fiveToday(), summary)

        assertEquals(5, rows.size)
        assertEquals(485, rows[0].avg?.avgMinute)
        assertEquals(545, rows[1].avg?.avgMinute)
        assertEquals(60, rows[1].avg?.avgGapMinutes) // 545 - 485
        // Lowest reference day is 10-31 with 2 cigarettes, so rows 1-2 have a lowest cell.
        assertEquals(twoDaysAgo, summary.lowestDay?.dateKey)
        assertEquals(490, rows[0].lowest?.minuteOfDay)
        assertEquals(550, rows[1].lowest?.minuteOfDay)
        assertNull(rows[2].lowest)
        assertEquals(2.0f, summary.avgCount, 0.0001f) // 4 cigarettes over 2 active days
    }

    @Test
    fun comparisonRows_emptyToday_hasNoRows() {
        assertTrue(comparisonRows(emptyList(), buildDailySummary(emptyList(), today)).isEmpty())
    }

    @Test
    fun comparisonSeries_dropsRepeatedMinutesInsideOneDay() {
        // The PWA drops a repeated minute (a mis-tap) so a 0-minute gap never enters the average.
        val history = listOf(record(yesterday, 480), record(yesterday, 480), record(yesterday, 540))
        val summary = buildDailySummary(fiveToday() + history, today)

        assertEquals(480, summary.avgSeries[0]?.avgMinute)
        // The gap lives on STT 2: 09:00 - 08:00 = 60 minutes.
        assertEquals(60, summary.avgSeries[1]?.avgGapMinutes)
        assertEquals(2.0f, summary.avgCount, 0.0001f) // 480 twice collapses to 2 distinct times in 1 day
    }
}
