package vn.dinhtrung.thuocla.domain

import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/** Wall-clock minutes in one local day. Kept explicit so the rollover arithmetic is readable. */
const val MINUTES_PER_DAY = 1440

/** The PWA rounds every logged time to a 5-minute boundary (`Math.round(m / 5) * 5`). */
const val ROUND_STEP_MINUTES = 5

/**
 * A minute-of-day after rounding, plus how many calendar days the rounding moved it.
 *
 * [dayOffset] is 1 only for the 23:58 / 23:59 case: the PWA does `setMinutes(round(58/5)*5)` which
 * rolls the Date into the next day. Dropping that fact would silently log the cigarette on the
 * wrong date, so callers must apply it with [rolloverDate].
 */
data class RoundedMinute(val minuteOfDay: Int, val dayOffset: Int = 0)

/**
 * Rounds [minuteOfDay] to the nearest 5 minutes, rounding up on the boundary — the same rounded-up
 * half as JavaScript's `Math.round` (14:07 -> 14:05, 14:08 -> 14:10, 14:58 -> 15:00).
 */
fun roundTo5(minuteOfDay: Int): RoundedMinute {
    require(minuteOfDay in 0 until MINUTES_PER_DAY) {
        "minuteOfDay must be 0..1439, was $minuteOfDay"
    }
    val rounded = ((minuteOfDay + 2) / ROUND_STEP_MINUTES) * ROUND_STEP_MINUTES
    return if (rounded >= MINUTES_PER_DAY) {
        RoundedMinute(rounded - MINUTES_PER_DAY, dayOffset = 1)
    } else {
        RoundedMinute(rounded)
    }
}

/** Applies a rounding day offset to [dateKey]; null when [dateKey] is not a real date. */
fun rolloverDate(dateKey: String, dayOffset: Int): String? {
    if (dayOffset == 0) return dateKey
    val date = runCatching { LocalDate.parse(dateKey) }.getOrNull() ?: return null
    return date.plusDays(dayOffset.toLong()).toString()
}

/**
 * Identity of one cigarette, mirroring the PWA's `timeKey` (day + hour + minute). Two records with
 * the same key are the duplicate the store refuses.
 */
fun timeKey(record: CigRecord): String = timeKey(record.dateKey, record.minuteOfDay)

fun timeKey(dateKey: String, minuteOfDay: Int): String =
    "$dateKey-${minuteOfDay / 60}-${minuteOfDay % 60}"

/** "HH:mm" for display; invalid values render empty rather than throwing in a UI path. */
fun formatHhMm(minuteOfDay: Int): String =
    if (minuteOfDay in 0 until MINUTES_PER_DAY) {
        "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
    } else {
        ""
    }

/** Null when hour/minute are out of range, so callers cannot build a 25:99 record. */
fun minuteOfDayOf(hour: Int, minute: Int): Int? =
    if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null

fun todayKey(clock: Clock): String = LocalDate.now(clock).toString()

fun nowMinuteOfDay(clock: Clock): Int = LocalTime.now(clock).let { it.hour * 60 + it.minute }
