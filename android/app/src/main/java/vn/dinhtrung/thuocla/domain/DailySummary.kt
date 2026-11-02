package vn.dinhtrung.thuocla.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The "Hôm nay" screen's derived numbers (tasks.md 4.1 / 4.2 / 4.3), ported from the PWA's
 * `updateDisplay()` and `renderComparisonTimeline()` (app.js v1.5.2).
 *
 * Everything here is a pure function over records that are already in the store: no clock, no
 * database, no coroutines. That is what makes the two mandatory numbers of task 4.1 — "TB 7 ngày
 * = 5.0" and "thẻ ít nhất = 5 • Hôm nay" — assertable in a plain JUnit test.
 */

/** The PWA's `getLastNDays(31)` window: today plus the 30 days before it. */
const val LOWEST_DAY_WINDOW_DAYS = 31

/** The PWA's "TB 7 ngày" window: today and the six days before it. */
const val AVG7_WINDOW_DAYS = 7

/** "TB 30 ngày": the thirty days before today (today excluded, like the PWA's comparison series). */
const val AVG_SERIES_WINDOW_DAYS = 30

/** One point of the "TB 30 ngày" column: the average minute and average gap at one STT. */
data class AvgPoint(val avgMinute: Int, val avgGapMinutes: Int?)

/** One row of the three-column comparison table: the same STT in all three columns. */
data class ComparisonRow(
    val stt: Int,
    val today: CigRecord?,
    val lowest: CigRecord?,
    val avg: AvgPoint?,
)

/**
 * The day with the fewest cigarettes in the 31-day window, or — on the first day using the app —
 * today itself with [isTodayFallback] set. The fallback is why the "ít nhất" card still shows a
 * number and the label "Hôm nay" instead of an empty card.
 */
data class LowestDay(
    val dateKey: String,
    val count: Int,
    val records: List<CigRecord>,
    val isTodayFallback: Boolean = false,
) {
    /** False for the first-day fallback: today is not a reference day for the comparison table. */
    val isReference: Boolean get() = !isTodayFallback && count > 0
}

/** The four quick stats plus the comparison series. See [dailySummary]. */
data class DailySummary(
    val today: String,
    val lowestDay: LowestDay? = null,
    val yesterdayCount: Int = 0,
    /** "TB 7 ngày": total over the window divided by the number of days that have data. */
    val avg7Days: Float = 0f,
    val monthCount: Int = 0,
    /** Index i is STT i+1; null when the window has no record at that STT. */
    val avgSeries: List<AvgPoint?> = emptyList(),
    /** Mean cigarettes per active day in the comparison window, for the "TB 30 ngày" header. */
    val avgCount: Float = 0f,
) {
    companion object {
        fun empty(today: String) = DailySummary(today)
    }
}

/** Timer facts for the clock card (tasks.md 4.2). */
data class IntervalStatus(
    /** Whole minutes since the last cigarette today. */
    val elapsedMinutes: Int = 0,
    val intervalGoal: Int = 0,
    /** "HH:mm" of the next cigarette while under the goal; null when over or with no record. */
    val expectedNext: String? = null,
    /** Positive while under the goal, null once it is reached. */
    val minutesRemaining: Int? = null,
    /** Positive once the goal is exceeded, null while under it. */
    val minutesOver: Int? = null,
) {
    val reachedGoal: Boolean get() = minutesOver != null

    companion object {
        val EMPTY = IntervalStatus()
    }
}

/**
 * Builds the summary from [records] — which may span any window; only the rows the PWA looks at are
 * used — for the local date [today] ("YYYY-MM-DD").
 */
fun buildDailySummary(records: List<CigRecord>, today: String): DailySummary {
    val todayDate = runCatching { LocalDate.parse(today) }.getOrNull()
        ?: return DailySummary.empty(today)

    val byDay: Map<String, List<CigRecord>> = records
        .filter { runCatching { LocalDate.parse(it.dateKey) }.isSuccess }
        .groupBy { it.dateKey }
        .mapValues { (_, day) -> day.sortedBy { it.minuteOfDay } }

    // "TB 7 ngày" divides by the number of days that have data, not by 7. A first day with 5
    // cigarettes must read 5.0: 5/7 = 0.7 looks like a broken app (spec `daily-summary`).
    val activeDayCounts = dayKeys(todayDate, AVG7_WINDOW_DAYS).mapNotNull { byDay[it]?.size }
    val avg7 = if (activeDayCounts.isEmpty()) 0f else activeDayCounts.average().toFloat()

    val monthKey = today.substring(0, 7)
    val (series, avgCount) = comparisonSeries(byDay, todayDate)

    return DailySummary(
        today = today,
        lowestDay = lowestDay(byDay, todayDate),
        yesterdayCount = byDay[todayDate.minusDays(1).toString()]?.size ?: 0,
        avg7Days = avg7,
        monthCount = byDay.filterKeys { it.startsWith(monthKey) }.values.sumOf { it.size },
        avgSeries = series,
        avgCount = avgCount,
    )
}

/**
 * Rows for the three-column table. Row count is the widest of the three columns, but never narrower
 * than today's cigarettes: the "Hôm nay" column is always drawn in full even when there is no
 * reference day yet (spec scenario "Chưa có ngày tham chiếu").
 */
fun comparisonRows(todayRecords: List<CigRecord>, summary: DailySummary): List<ComparisonRow> {
    val sortedToday = todayRecords.sortedBy { it.minuteOfDay }
    val lowestRecords = summary.lowestDay?.takeIf { it.isReference }?.records.orEmpty()
    val rowCount = maxOf(sortedToday.size, lowestRecords.size, summary.avgSeries.size)
    return (1..rowCount).map { stt ->
        ComparisonRow(
            stt = stt,
            today = sortedToday.getOrNull(stt - 1),
            lowest = lowestRecords.getOrNull(stt - 1),
            avg = summary.avgSeries.getOrNull(stt - 1),
        )
    }
}

/**
 * The clock card's numbers. [lastMinute] is the newest cigarette of today (null when none).
 *
 * Under the goal the next cigarette is expected at last + goal; at or over the goal the PWA shows
 * how many minutes were exceeded, not a next time.
 */
fun intervalStatus(lastMinute: Int?, nowMinute: Int, intervalGoal: Int): IntervalStatus {
    if (lastMinute == null) return IntervalStatus(intervalGoal = intervalGoal)
    val elapsed = ((nowMinute - lastMinute) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY
    if (intervalGoal <= 0) return IntervalStatus(elapsed, intervalGoal)
    return if (elapsed >= intervalGoal) {
        IntervalStatus(
            elapsedMinutes = elapsed,
            intervalGoal = intervalGoal,
            minutesOver = elapsed - intervalGoal,
        )
    } else {
        IntervalStatus(
            elapsedMinutes = elapsed,
            intervalGoal = intervalGoal,
            expectedNext = formatHhMm((lastMinute + intervalGoal) % MINUTES_PER_DAY),
            minutesRemaining = intervalGoal - elapsed,
        )
    }
}

/** "5.0" — one decimal, always a dot: the spec's own notation for "TB 7 ngày". */
fun formatAverage(value: Float): String = String.format(Locale.US, "%.1f", value)

/**
 * Label under the "ít nhất" card: "Ít nhất • Hôm nay" on the first day (the PWA's fallback), or
 * "Ít nhất • T2 10-27" for a real reference day. "Ít nhất" alone when there is nothing to show.
 */
fun lowestDayLabel(lowest: LowestDay?): String {
    if (lowest == null || lowest.count <= 0) return "Ít nhất"
    if (lowest.isTodayFallback) return "Ít nhất • Hôm nay"
    val date = runCatching { LocalDate.parse(lowest.dateKey) }.getOrNull() ?: return "Ít nhất"
    return "Ít nhất • ${shortDayName(date)} ${lowest.dateKey.substring(5)}"
}

/** "T2".."CN" — the PWA's `dayNamesShort`, indexed by weekday starting Monday. */
private fun shortDayName(date: LocalDate): String = when (date.dayOfWeek) {
    DayOfWeek.MONDAY -> "T2"
    DayOfWeek.TUESDAY -> "T3"
    DayOfWeek.WEDNESDAY -> "T4"
    DayOfWeek.THURSDAY -> "T5"
    DayOfWeek.FRIDAY -> "T6"
    DayOfWeek.SATURDAY -> "T7"
    DayOfWeek.SUNDAY -> "CN"
    else -> "—"
}

/** [count] consecutive date keys ending at [endDate], oldest first. */
private fun dayKeys(endDate: LocalDate, count: Int): List<String> =
    (count - 1 downTo 0).map { endDate.minusDays(it.toLong()).toString() }

/**
 * The PWA's `getLowestDay()`: the smallest non-empty day in the 31-day window other than today,
 * ties going to the oldest day. With no other day, today is the fallback — and null when today is
 * empty too.
 */
private fun lowestDay(byDay: Map<String, List<CigRecord>>, todayDate: LocalDate): LowestDay? {
    var best: LowestDay? = null
    // Oldest first and strict `<`, exactly like the PWA's walk over getLastNDays(31).
    for (offset in LOWEST_DAY_WINDOW_DAYS - 1 downTo 1) {
        val key = todayDate.minusDays(offset.toLong()).toString()
        val day = byDay[key] ?: continue
        if (day.isEmpty()) continue
        if (best == null || day.size < best.count) {
            best = LowestDay(key, day.size, day)
        }
    }
    if (best != null) return best
    val todayRecords = byDay[todayDate.toString()].orEmpty()
    return if (todayRecords.isEmpty()) {
        null
    } else {
        LowestDay(todayDate.toString(), todayRecords.size, todayRecords, isTodayFallback = true)
    }
}

/**
 * The "TB 30 ngày" column: for every STT in the 30 days before today, the mean minute and the mean
 * gap to the previous cigarette. A minute repeated inside one day (a mis-tap the PWA drops) counts
 * once. Returns the series and the mean cigarettes per active day.
 */
private fun comparisonSeries(
    byDay: Map<String, List<CigRecord>>,
    todayDate: LocalDate,
): Pair<List<AvgPoint?>, Float> {
    val times = mutableListOf<MutableList<Int>>()
    val gaps = mutableListOf<MutableList<Int>>()
    var total = 0
    var activeDays = 0
    for (key in dayKeys(todayDate.minusDays(1), AVG_SERIES_WINDOW_DAYS)) {
        val day = byDay[key]?.let(::withoutDuplicateMinutes).orEmpty()
        if (day.isEmpty()) continue
        activeDays++
        total += day.size
        for (i in day.indices) {
            while (times.size <= i) {
                times.add(mutableListOf())
                gaps.add(mutableListOf())
            }
            times[i].add(day[i].minuteOfDay)
            if (i > 0) {
                val gap = day[i].minuteOfDay - day[i - 1].minuteOfDay
                if (gap > 0) gaps[i].add(gap)
            }
        }
    }
    val points = times.mapIndexed { i, minutes ->
        AvgPoint(
            avgMinute = minutes.average().roundToInt(),
            avgGapMinutes = gaps[i].takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        )
    }
    return points to (if (activeDays == 0) 0f else total.toFloat() / activeDays)
}

/** Drops repeated minutes inside one day, keeping the first — the PWA's "bỏ record trùng phút". */
private fun withoutDuplicateMinutes(day: List<CigRecord>): List<CigRecord> =
    day.sortedBy { it.minuteOfDay }.distinctBy { it.minuteOfDay }
