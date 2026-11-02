package vn.dinhtrung.thuocla.domain.history

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt
import vn.dinhtrung.thuocla.domain.CigRecord
import vn.dinhtrung.thuocla.domain.dayName

/** Minutes in a displayed week (tasks.md 6.1: "biểu đồ cột số điếu 7 ngày"). */
const val WEEK_DAYS = 7

/** tasks.md 6.3: a cigarette within this many minutes of the previous one is "hút theo". */
const val CHAIN_MAX_GAP_MINUTES = 40

/** tasks.md 6.3 / spec `history-stats`: the comparison covers the last 30 days. */
const val COMPARISON_WINDOW_DAYS = 30

/**
 * A sliding 7-day window ending on [to]. Page 0 is the last seven days (today included); each
 * further page moves back exactly one week, matching the PWA's `chartPage`.
 */
data class WeekWindow(val from: LocalDate, val to: LocalDate, val pagesBack: Int) {

    val dateKeys: List<String> = List(WEEK_DAYS) { from.plusDays(it.toLong()).toString() }

    /** "7 ngày qua" on page 0, otherwise "MM-DD – MM-DD" (the PWA's `chartDateRange`). */
    val label: String =
        if (pagesBack == 0) "7 ngày qua" else "${shortMonthDay(from)} – ${shortMonthDay(to)}"
}

fun weekWindow(today: LocalDate, pagesBack: Int): WeekWindow {
    require(pagesBack >= 0) { "pagesBack must be >= 0, was $pagesBack" }
    val to = today.minusDays(WEEK_DAYS.toLong() * pagesBack)
    return WeekWindow(from = to.minusDays((WEEK_DAYS - 1).toLong()), to = to, pagesBack = pagesBack)
}

/** One bar of the chart. */
data class DayPoint(
    val dateKey: String,
    val dayName: String,
    val count: Int,
    /** Average gap between cigarettes of that day, null when fewer than two. */
    val avgGapMinutes: Int?,
)

/** Chart data: one point per day of [window], zeros included (an empty day is a real data point). */
fun chartDays(records: List<CigRecord>, window: WeekWindow): List<DayPoint> {
    val byDay = records.filter { it.dateKey in window.dateKeys }.groupBy { it.dateKey }
    return window.dateKeys.map { key ->
        val rows = byDay[key].orEmpty().sortedBy { it.minuteOfDay }
        DayPoint(
            dateKey = key,
            dayName = dayName(LocalDate.parse(key)),
            count = rows.size,
            avgGapMinutes = averageGap(rows),
        )
    }
}

/** One line of the day detail (tasks.md 6.2). */
data class DayDetailRow(
    /** 1-based position in the day, as the PWA labels "Điếu #n". */
    val index: Int,
    val record: CigRecord,
    /** Minutes since the previous cigarette; null for the first one. */
    val gapMinutes: Int?,
    /** Gap minus the configured interval goal: negative means earlier than the goal. */
    val gapDeltaToGoal: Int?,
)

/** Day detail rows in time order, with gaps measured against the *previous* row. */
fun dayDetail(records: List<CigRecord>, intervalGoal: Int): List<DayDetailRow> {
    val sorted = records.sortedBy { it.minuteOfDay }
    return sorted.mapIndexed { i, record ->
        val gap = if (i == 0) null else record.minuteOfDay - sorted[i - 1].minuteOfDay
        DayDetailRow(
            index = i + 1,
            record = record,
            gapMinutes = gap,
            gapDeltaToGoal = gap?.let { it - intervalGoal },
        )
    }
}

/** Aggregate for one day type (weekend or weekday) over the comparison window (tasks.md 6.3). */
data class TypeStats(
    val dayCount: Int,
    val totalCigarettes: Int,
    val avgPerDay: Int,
    val avgGapMinutes: Int?,
    /** Cigarettes within [CHAIN_MAX_GAP_MINUTES] of the previous one, per logged day. */
    val chainPerDay: Double,
    val avgFirstMinute: Int?,
    val avgLastMinute: Int?,
)

/**
 * Weekend vs weekday. A type that has no logged day in the window is **null**, never zeros — the
 * spec's "thiếu ngày cuối tuần ⇒ hiện '—' không hiện 0".
 */
data class WeekendComparison(val weekend: TypeStats?, val weekday: TypeStats?)

/**
 * Compares weekend days (Sat/Sun) with weekdays over the last [COMPARISON_WINDOW_DAYS] calendar days
 * ending at [today]. Only days that actually have cigarettes count towards the averages, which is
 * how the PWA computes them (a day without records is not a zero-cigarette day, it is unknown).
 */
fun weekendComparison(records: List<CigRecord>, today: LocalDate): WeekendComparison {
    val from = today.minusDays((COMPARISON_WINDOW_DAYS - 1).toLong()).toString()
    val to = today.toString()
    val weekend = mutableListOf<List<CigRecord>>()
    val weekday = mutableListOf<List<CigRecord>>()

    records
        .filter { it.dateKey in from..to }
        .groupBy { it.dateKey }
        .forEach { (dateKey, rows) ->
            val day = LocalDate.parse(dateKey).dayOfWeek
            val sorted = rows.sortedBy { it.minuteOfDay }
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                weekend += sorted
            } else {
                weekday += sorted
            }
        }

    return WeekendComparison(weekend = statsOf(weekend), weekday = statsOf(weekday))
}

private fun statsOf(days: List<List<CigRecord>>): TypeStats? {
    if (days.isEmpty()) return null
    val minutesPerDay = days.map { rows -> rows.map { it.minuteOfDay }.sorted() }
    val total = minutesPerDay.sumOf { it.size }
    val gaps = minutesPerDay.flatMap { minutes ->
        minutes.zipWithNext { previous, current -> current - previous }.filter { it > 0 }
    }
    return TypeStats(
        dayCount = days.size,
        totalCigarettes = total,
        avgPerDay = (total.toDouble() / days.size).roundToInt(),
        avgGapMinutes = gaps.takeIf { it.isNotEmpty() }
            ?.let { (it.sum().toDouble() / it.size).roundToInt() },
        chainPerDay = gaps.count { it <= CHAIN_MAX_GAP_MINUTES }.toDouble() / days.size,
        avgFirstMinute = minutesPerDay.mapNotNull { it.firstOrNull() }.meanRounded(),
        avgLastMinute = minutesPerDay.mapNotNull { it.lastOrNull() }.meanRounded(),
    )
}

private fun averageGap(rows: List<CigRecord>): Int? {
    if (rows.size < 2) return null
    val gaps = rows.map { it.minuteOfDay }.zipWithNext { previous, current -> current - previous }
    return (gaps.sum().toDouble() / gaps.size).roundToInt()
}

private fun List<Int>.meanRounded(): Int? = takeIf { it.isNotEmpty() }?.average()?.roundToInt()

private fun shortMonthDay(date: LocalDate): String =
    "${date.monthValue.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
