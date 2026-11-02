package vn.dinhtrung.thuocla.domain

import java.time.Clock
import java.time.LocalDate
import vn.dinhtrung.thuocla.data.TrackerRepository

/**
 * The cigarette-log use cases (tasks.md 3.1 / 3.4): every write goes through the same two rules.
 *
 * 1. the time is rounded to the nearest 5 minutes first ([roundTo5]);
 * 2. the rounded (day, minute) may already exist, in which case the store refuses it and the caller
 *    shows the in-card warning instead of opening the reason sheet (spec `cigarette-log`).
 *
 * Nothing here reads the wall clock directly: every date/time fact comes from the injected [Clock]
 * (design decision: `AppContainer.clock`), which is what makes the acceptance scenarios testable.
 */
class CigaretteLog(
    private val repository: TrackerRepository,
    private val clock: Clock,
) {

    fun todayKey(): String = todayKey(clock)

    fun nowMinuteOfDay(): Int = nowMinuteOfDay(clock)

    /** The +1 button: current time rounded to 5 minutes, rolled into tomorrow at 23:58+. */
    suspend fun addNow(): WriteResult {
        val rounded = roundTo5(nowMinuteOfDay())
        val dateKey = rolloverDate(todayKey(), rounded.dayOffset) ?: return invalid()
        return repository.addRecord(dateKey, rounded.minuteOfDay)
    }

    /** "Thêm giờ khác": an explicit hour/minute on any day, still rounded and still duplicate-checked. */
    suspend fun addAt(dateKey: String, hour: Int, minute: Int): WriteResult {
        val minuteOfDay = minuteOfDayOf(hour, minute) ?: return invalid()
        return addAtMinute(dateKey, minuteOfDay)
    }

    suspend fun addAtMinute(dateKey: String, minuteOfDay: Int): WriteResult {
        if (minuteOfDay !in 0 until MINUTES_PER_DAY) return invalid()
        val rounded = roundTo5(minuteOfDay)
        val targetDate = rolloverDate(dateKey, rounded.dayOffset) ?: return invalid()
        return repository.addRecord(targetDate, rounded.minuteOfDay)
    }

    /**
     * Moves a cigarette's time. The new time is rounded like any other write, so 08:27 becomes 08:25.
     *
     * A target that rounds past midnight (23:58 / 23:59) is refused as INVALID: the store's
     * `updateTime` moves a row inside one day, and silently moving it to the next date would be a
     * different edit than the user asked for.
     */
    suspend fun editTime(dateKey: String, fromMinute: Int, toMinute: Int): WriteResult {
        if (toMinute !in 0 until MINUTES_PER_DAY) return invalid()
        val rounded = roundTo5(toMinute)
        if (rounded.dayOffset != 0) return invalid()
        return repository.updateTime(dateKey, fromMinute, rounded.minuteOfDay)
    }

    /** The undo button: removes the newest cigarette of today, or null when today is empty. */
    suspend fun undoLastToday(): CigRecord? = repository.deleteLast(todayKey())

    /**
     * Tapping a reason on the sheet: sets it, or clears it when it is already the record's reason
     * (spec `cigarette-log`, scenario "Bỏ lý do đã gắn").
     */
    suspend fun toggleTrigger(dateKey: String, minuteOfDay: Int, triggerIdx: Int): Boolean {
        if (triggerIdx !in Triggers.ALL.indices) return false
        val current = repository.day(dateKey).firstOrNull { it.minuteOfDay == minuteOfDay }
            ?: return false
        val next = if (current.triggerIdx == triggerIdx) null else triggerIdx
        return repository.setTrigger(dateKey, minuteOfDay, next)
    }

    /** Local date of [dateKey] shifted by days; used by the "add to a past day" dialog. */
    fun shiftDays(dateKey: String, days: Long): String? =
        runCatching { LocalDate.parse(dateKey).plusDays(days).toString() }.getOrNull()

    private fun invalid() = WriteResult(applied = false, block = WriteBlock.INVALID)
}
