package vn.dinhtrung.thuocla.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import vn.dinhtrung.thuocla.domain.history.CHAIN_MAX_GAP_MINUTES
import vn.dinhtrung.thuocla.domain.history.COMPARISON_WINDOW_DAYS

/**
 * The "Điếu nên cắt" note (tasks.md group 5, spec `daily-summary` "Note \"điếu nên cắt\""), ported
 * from the PWA's `computeSttStats` / `getAvgCountByNow` / `getAvgLastCigTime` / `getDailyTarget` /
 * `renderCutReminder` (app.js v1.5.2).
 *
 * The PWA reads localStorage and the wall clock directly; every function here takes its inputs as
 * arguments instead. That is what makes the four golden verdicts of tasks.md 5.3 assertable in plain
 * JUnit tests — the numbers the PWA prints at 08:00, 08:40, 17:40 and 22:10 are reproduced without a
 * database, a device or a clock.
 *
 * Two deliberate differences from the PWA, both of which the native spec asks for:
 *
 * 1. The PWA caches the day's cut target in `cutDailyTarget` (design D2b) so a target whose time has
 *    already passed stays on screen until it is smoked. The port re-picks on every call, so the
 *    suggestion never points at a cigarette whose time has gone — which is exactly the spec's
 *    "Encourages when below goal with nothing cuttable" scenario ("mọi STT trong hạn mức đã qua giờ
 *    dự kiến → note vẫn hiển thị 'Còn X điếu…'"). The four golden verdicts are unaffected: the goal
 *    state outranks the target in all of them.
 * 2. The PWA's `renderCutReminder` writes text straight into the DOM. Here the note is a value
 *    ([CutNote]) whose strings the screen renders, so a test can assert the exact wording.
 */

/** "Hút theo" gap threshold in minutes — the same 40 the PWA's `renderCutWindows` uses. */
private val MAX_GAP_MINUTES = CHAIN_MAX_GAP_MINUTES

/** "Kép" gap threshold in minutes (tasks.md 5.1: "kép 20"). */
const val CUT_MAX_KEP_MINUTES = 20

/** Share of days at an STT that must be "hút theo" before the note warns about it. */
const val CUT_CHAIN_FREQ_THRESHOLD = 0.25

/** Share of days at an STT that must be "kép" for the target's frequency label to say "kép". */
const val CUT_KEP_FREQ_THRESHOLD = 0.15

/** Below this many logged days in the window the note asks for more data instead of guessing. */
const val CUT_MIN_DAYS = 7

/** The PWA's `CUT_STT_CAP`: only the first 20 cigarettes of a day are scored. */
const val CUT_STT_CAP = 20

/** An STT must appear on this share of same-kind days to be a stable candidate (tasks.md 5.1). */
const val CUT_MIN_PRESENCE = 0.3

/** An STT present this often, this steady and this rarely chained is a rigid anchor, not a target. */
const val CUT_ANCHOR_MIN_PRESENCE = 0.6
const val CUT_ANCHOR_MAX_STDEV = 45.0
const val CUT_ANCHOR_MAX_CHAIN_FREQ = 0.2

/** A candidate counts as "still upcoming" while its average time is at most this far behind now. */
const val CUT_UPCOMING_LOOKAHEAD_MINUTES = 15

/** `slack = max(30, stdevTime)` — the PWA's tolerance before the next-chain warning expires. */
const val CUT_SLACK_MIN_MINUTES = 30

/** Goal reached this long before the usual last cigarette means "quota gone early", not a win. */
const val CUT_EARLY_GOAL_MARGIN_MINUTES = 30

/** Fallback "end of day" minute when the history has no last-cigarette time at all. */
const val CUT_FALLBACK_END_OF_DAY_MINUTES = 22 * 60

/** Weekend or weekday — the two separate statistics the spec requires (tasks.md 5.1). */
enum class DayKind {
    WEEKDAY,
    WEEKEND;

    companion object {
        fun of(date: LocalDate): DayKind =
            if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
                WEEKEND
            } else {
                WEEKDAY
            }

        /** Null when [dateKey] is not a real "YYYY-MM-DD" date. */
        fun of(dateKey: String): DayKind? =
            runCatching { LocalDate.parse(dateKey) }.getOrNull()?.let { of(it) }
    }
}

/**
 * One STT's statistics over the window: on how many days it existed, its mean and spread in minutes,
 * and how often it followed the previous cigarette within [MAX_GAP_MINUTES] / [CUT_MAX_KEP_MINUTES].
 *
 * [present] is never zero: an STT exists in the list only once some day reached it.
 */
data class SttStat(
    val stt: Int,
    val present: Int,
    val avgMinute: Double,
    val stdevMinutes: Double,
    val chain: Int,
    val kep: Int,
) {
    /** Share of [present] days where this STT came ≤ 40 minutes after the previous cigarette. */
    val chainFreq: Double get() = chain.toDouble() / present

    /** Share of [present] days where this STT came ≤ 20 minutes after the previous cigarette. */
    val kepFreq: Double get() = kep.toDouble() / present
}

/**
 * [computeSttStats]' result: the two per-STT series plus how much data they rest on.
 *
 * [dayCount] counts every logged day in the window; [weekdayDays] / [weekendDays] count the days of
 * each kind. Presence is measured against the *same kind* count — the PWA's v1.4.3 fix: dividing by
 * [dayCount] made weekend candidates look rarer than they are and dropped them.
 */
data class CutStats(
    val weekday: List<SttStat> = emptyList(),
    val weekend: List<SttStat> = emptyList(),
    val dayCount: Int = 0,
    val weekdayDays: Int = 0,
    val weekendDays: Int = 0,
) {
    fun seriesOf(kind: DayKind): List<SttStat> =
        if (kind == DayKind.WEEKEND) weekend else weekday

    fun daysOf(kind: DayKind): Int =
        if (kind == DayKind.WEEKEND) weekendDays else weekdayDays
}

/** Why [CutTarget] was chosen: a "hút theo" STT, or the last cigarette still inside the goal. */
enum class CutTargetReason { CHAIN, TAIL }

/** One cigarette the note suggests skipping, with the frequency that makes it worth skipping. */
data class CutTarget(
    val stt: Int,
    val avgMinute: Double,
    val stdevMinutes: Double,
    val chain: Int,
    val kep: Int,
    val present: Int,
    val reason: CutTargetReason,
) {
    val chainFreq: Double get() = chain.toDouble() / present
    val kepFreq: Double get() = kep.toDouble() / present
}

/** Colour bucket of the note card; the PWA's `.cut-note.warn` / `.praise` / `.target` / neutral. */
enum class CutTone { PRAISE, WARN, TARGET, NEUTRAL }

/**
 * The note's state. Exactly one of these is on screen at a time; the order in [cutNote] is the
 * priority order tasks.md 5.2 asks for.
 */
sealed interface CutNote {
    val icon: String
    val title: String
    val subtitle: String
    val tone: CutTone

    /** Fewer than [CUT_MIN_DAYS] logged days in the window: ask for data, never invent a target. */
    data class InsufficientData(
        val daysPresent: Int,
        val minDays: Int = CUT_MIN_DAYS,
    ) : CutNote {
        override val icon: String = "📊"
        override val title: String = "Cần thêm dữ liệu để tính điếu nên cắt"
        override val subtitle: String =
            "Còn ${minDays - daysPresent} ngày nữa (tối thiểu $minDays ngày trong 30 ngày qua)"
        override val tone: CutTone = CutTone.NEUTRAL
    }

    /** Goal reached, and the day is far enough along that stopping is a win rather than a warning. */
    data class GoalReached(val goal: Int) : CutNote {
        override val icon: String = "🎉"
        override val title: String = "Đạt mục tiêu $goal điếu — thắng hôm nay rồi!"
        override val subtitle: String = "Đừng hút thêm nữa nhé, giữ đà!"
        override val tone: CutTone = CutTone.PRAISE
    }

    /**
     * Goal reached before the usual last cigarette (mean last time − 30 min): the quota is gone while
     * the evening is not, so this warns instead of praising (user directive 2026-08-15).
     */
    data class GoalReachedEarly(
        val goal: Int,
        val nowMinute: Int,
        val avgLastMinute: Double?,
    ) : CutNote {
        override val icon: String = "⚠️"
        override val title: String = "Hết hạn mức $goal điếu rồi — tối nay đừng hút nữa!"
        override val subtitle: String =
            if (avgLastMinute != null && avgLastMinute > 0) {
                "Mới ${formatHhMm(nowMinute)} đã dùng hết quota — thường ngày bạn hút đến " +
                    "~${formatHhMm(avgLastMinute.roundToInt())}. Giữ kỷ luật!"
            } else {
                "Mới ${formatHhMm(nowMinute)} đã dùng hết quota — từ giờ đến hết ngày đừng hút thêm nữa!"
            }
        override val tone: CutTone = CutTone.WARN
    }

    /** More cigarettes than the goal allows. */
    data class GoalExceeded(val goal: Int, val todayCount: Int) : CutNote {
        override val icon: String = "⚠️"
        override val title: String = "Vượt mục tiêu $goal điếu (đang $todayCount) — dừng lại!"
        override val subtitle: String = "+${todayCount - goal} điếu ngoài kế hoạch hôm nay"
        override val tone: CutTone = CutTone.WARN
    }

    /**
     * Fewer cigarettes than *both* baselines (habit and goal pace). [targetCount] decides which
     * baseline the wording quotes — the PWA quotes the goal pace when it exists, the habit otherwise.
     */
    data class Praise(
        val difference: Int,
        val targetCount: Int,
        val avgCount: Double,
        val todayCount: Int,
    ) : CutNote {
        override val icon: String = "✅"
        override val title: String =
            if (targetCount > 0) {
                "Hút ít hơn nhịp mục tiêu $difference điếu — giỏi lắm!"
            } else {
                "Hút ít hơn nhịp thường ngày $difference điếu — giỏi lắm!"
            }
        override val subtitle: String =
            if (targetCount > 0) {
                "Lúc này thường ~${oneDecimal(avgCount)} điếu, nhịp mục tiêu ~$targetCount — " +
                    "bạn mới $todayCount. Cứ đà này!"
            } else {
                "Lúc này thường đã hút ~${oneDecimal(avgCount)} điếu — bạn mới $todayCount. Cứ đà này!"
            }
        override val tone: CutTone = CutTone.PRAISE
    }

    /** More cigarettes than *both* baselines: smoke faster than habit and goal pace. */
    data class PaceWarning(
        val avgCount: Double,
        val targetCount: Int,
        val todayCount: Int,
        val goal: Int,
        val minutesPerCigarette: Double,
    ) : CutNote {
        override val icon: String = "⚠️"
        override val title: String = "Hút nhanh hơn cả thói quen lẫn nhịp mục tiêu — giãn ra!"
        override val subtitle: String =
            "Thường giờ này ~${oneDecimal(avgCount)} điếu, nhịp mục tiêu ~$targetCount — bạn đã " +
                "$todayCount. Cần giãn ~${minutesPerCigarette.roundToInt()}ph/điếu để về đúng $goal điếu."
        override val tone: CutTone = CutTone.WARN
    }

    /** The next cigarette is a "hút theo" and its predicted time has not passed yet. */
    data class NextChainWarning(
        val nextStt: Int,
        val stat: SttStat,
    ) : CutNote {
        override val icon: String = "⚠️"
        override val title: String =
            "Điếu #$nextStt ≈ ${formatHhMm(stat.avgMinute.roundToInt())} — đây là điếu hút theo, thử cắt!"
        override val subtitle: String =
            "${stat.chain}/${stat.present} ngày là \"điếu theo\" (gap ≤${MAX_GAP_MINUTES}ph) trong 30 ngày qua"
        override val tone: CutTone = CutTone.WARN
    }

    /** The day's suggested cigarette to skip. */
    data class TargetCut(val target: CutTarget, val goal: Int) : CutNote {
        override val icon: String = "🎯"
        override val title: String =
            "Hôm nay cắt điếu #${target.stt} ≈ ${formatHhMm(target.avgMinute.roundToInt())}"
        override val subtitle: String =
            when {
                target.reason == CutTargetReason.TAIL && goal > 0 ->
                    "Điếu cuối trong hạn mức $goal — bỏ nó để về ${target.stt - 1} điếu!"
                target.reason == CutTargetReason.TAIL ->
                    "Điếu cuối ngày — bỏ nó để về sớm hơn!"
                target.kepFreq >= CUT_KEP_FREQ_THRESHOLD ->
                    "kép ${target.kep}/${target.present} ngày trong 30 ngày qua — thử bỏ nó nhé!"
                else ->
                    "hút theo ${target.chain}/${target.present} ngày trong 30 ngày qua — thử bỏ nó nhé!"
            }
        override val tone: CutTone = CutTone.TARGET
    }

    /** Below goal with nothing worth cutting in sight: keep the note up and count the remaining quota. */
    data class Encourage(val goal: Int, val todayCount: Int) : CutNote {
        override val icon: String = "🎯"
        override val title: String =
            "Còn ${goal - todayCount} điếu trong hạn mức $goal hôm nay — giữ nhịp nhé!"
        override val subtitle: String =
            if (goal - todayCount == 1) {
                "Điếu cuối — bỏ nó để về dưới mục tiêu!"
            } else {
                "Không có điếu nào đáng cắt lúc này — đừng hút theo!"
            }
        override val tone: CutTone = CutTone.TARGET
    }

    /** No goal set and nothing worth cutting. */
    data object Neutral : CutNote {
        override val icon: String = "💪"
        override val title: String = "Hôm nay không có điếu nào đáng cắt — khoảng cách hút đang ổn!"
        override val subtitle: String = "Giữ nhịp này nhé!"
        override val tone: CutTone = CutTone.NEUTRAL
    }
}

/**
 * "2.4" — one decimal, matching the PWA's `avgCount.toFixed(1)`.
 *
 * JavaScript's `toFixed` rounds the *exact binary value* of the double, not its decimal short form.
 * `String.format("%.1f")` also rounds the binary value but through a decimal conversion that can
 * disagree on halves; `new BigDecimal(value)` takes the exact binary expansion, and
 * `setScale(1, HALF_UP)` on that reproduces `toFixed(1)` for every value tested (2739 distinct
 * `s/d` ratios, 0 mismatches). The shortest-repr alternative (`BigDecimal.valueOf`) is *not*
 * equivalent: it rounds 3.55 up to 3.6 where the PWA prints 3.5.
 *
 * Reachable and user-visible: 20 weekday days with 71 cigarettes by 23:00 gives avgCount exactly
 * 3.55, and the note must read "~3.5 điếu" like the PWA, not "~3.6".
 */
private fun oneDecimal(value: Double): String =
    BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toPlainString()

/** The window's first day key, inclusive: 30 days ending at [today] (the PWA's `CUT_LOOKBACK`). */
private fun windowStartKey(today: LocalDate): String =
    today.minusDays((COMPARISON_WINDOW_DAYS - 1).toLong()).toString()

/**
 * The PWA's `computeSttStats()`: per-STT statistics over the last 30 days, split weekend/weekday.
 *
 * Rules carried over verbatim:
 * - only the first [CUT_STT_CAP] cigarettes of a day are scored;
 * - a gap of zero (a duplicate minute) never counts as "hút theo" or "kép" (`gap > 0`);
 * - [SttStat.avgMinute] is the mean wall-clock minute, [SttStat.stdevMinutes] the population standard
 *   deviation (`sqrt(E[x²] − E[x]²)`), exactly the PWA's arithmetic.
 *
 * Today's own records are included in the statistics (the PWA includes them too); only the two
 * baselines below exclude today.
 */
fun computeSttStats(records: List<CigRecord>, today: LocalDate): CutStats {
    val fromKey = windowStartKey(today)
    val toKey = today.toString()

    val accumulators = mapOf(
        DayKind.WEEKDAY to mutableListOf<SttAccumulator>(),
        DayKind.WEEKEND to mutableListOf<SttAccumulator>(),
    )
    var dayCount = 0
    val daysPerKind = mutableMapOf(DayKind.WEEKDAY to 0, DayKind.WEEKEND to 0)

    records
        .filter { it.dateKey in fromKey..toKey }
        .groupBy { it.dateKey }
        .forEach { (dateKey, rows) ->
            if (rows.isEmpty()) return@forEach
            val kind = DayKind.of(dateKey) ?: return@forEach
            dayCount++
            daysPerKind[kind] = (daysPerKind[kind] ?: 0) + 1

            val series = accumulators.getValue(kind)
            val sorted = rows.sortedBy { it.minuteOfDay }
            for (index in 0 until minOf(sorted.size, CUT_STT_CAP)) {
                while (series.size <= index) series.add(SttAccumulator())
                val stat = series[index]
                stat.present++
                val minute = sorted[index].minuteOfDay
                stat.minuteSum += minute.toLong()
                stat.minuteSquareSum += minute.toLong() * minute
                if (index > 0) {
                    val gap = minute - sorted[index - 1].minuteOfDay
                    if (gap > 0 && gap <= MAX_GAP_MINUTES) {
                        stat.chain++
                        if (gap <= CUT_MAX_KEP_MINUTES) stat.kep++
                    }
                }
            }
        }

    fun toSeries(kind: DayKind): List<SttStat> =
        accumulators.getValue(kind).mapIndexed { index, stat ->
            val mean = stat.minuteSum.toDouble() / stat.present
            val variance = max(0.0, stat.minuteSquareSum.toDouble() / stat.present - mean * mean)
            SttStat(
                stt = index + 1,
                present = stat.present,
                avgMinute = mean,
                stdevMinutes = sqrt(variance),
                chain = stat.chain,
                kep = stat.kep,
            )
        }

    return CutStats(
        weekday = toSeries(DayKind.WEEKDAY),
        weekend = toSeries(DayKind.WEEKEND),
        dayCount = dayCount,
        weekdayDays = daysPerKind.getValue(DayKind.WEEKDAY),
        weekendDays = daysPerKind.getValue(DayKind.WEEKEND),
    )
}

private class SttAccumulator {
    var present = 0
    var minuteSum = 0L
    var minuteSquareSum = 0L
    var chain = 0
    var kep = 0
}

/**
 * The PWA's `getAvgCountByNow()` — baseline A, the "nhịp quen thuộc": the mean number of cigarettes
 * already smoked by [nowMinute] on the same-kind days **before today** (today is excluded, user
 * directive 2026-08-15). Zero when there is no such day.
 */
fun avgCountByNow(
    records: List<CigRecord>,
    today: LocalDate,
    kind: DayKind,
    nowMinute: Int,
): Double {
    val fromKey = windowStartKey(today)
    val toKey = today.minusDays(1).toString()
    var sum = 0
    var days = 0
    records
        .filter { it.dateKey in fromKey..toKey }
        .groupBy { it.dateKey }
        .forEach { (dateKey, rows) ->
            if (rows.isEmpty() || DayKind.of(dateKey) != kind) return@forEach
            sum += rows.count { it.minuteOfDay <= nowMinute }
            days++
        }
    return if (days == 0) 0.0 else sum.toDouble() / days
}

/**
 * The PWA's `getAvgLastCigTime()` — the mean minute of each same-kind day's **last** cigarette,
 * yesterday and before. Zero when there is no such day.
 *
 * The mean of each day's last record, not the mean of the last STT slot: the last slot belongs to the
 * rare 20-cigarette day and would drag the estimate late.
 */
fun avgLastCigMinute(records: List<CigRecord>, today: LocalDate, kind: DayKind): Double {
    val fromKey = windowStartKey(today)
    val toKey = today.minusDays(1).toString()
    var sum = 0
    var days = 0
    records
        .filter { it.dateKey in fromKey..toKey }
        .groupBy { it.dateKey }
        .forEach { (dateKey, rows) ->
            if (rows.isEmpty() || DayKind.of(dateKey) != kind) return@forEach
            sum += rows.maxOf { it.minuteOfDay }
            days++
        }
    return if (days == 0) 0.0 else sum.toDouble() / days
}

/**
 * The PWA's `getDailyTarget()`: the cigarette worth skipping today.
 *
 * Candidates are "hút theo" or "kép" STTs that are stable enough ([CUT_MIN_PRESENCE] of same-kind
 * days), not rigid anchors, still ahead of [todayCount] and inside the goal. The earliest upcoming
 * one wins; when none is upcoming, the last cigarette still inside the goal is the tail fallback.
 *
 * Unlike the PWA there is no per-day cache — see the file header for why the spec prefers re-picking.
 */
fun dailyTarget(
    stats: CutStats,
    kind: DayKind,
    goal: Int,
    todayCount: Int,
    nowMinute: Int,
): CutTarget? {
    val withinGoal = stats.seriesOf(kind).filter { goal <= 0 || it.stt <= goal }
    if (withinGoal.isEmpty()) return null

    val daysOfKind = stats.daysOf(kind).coerceAtLeast(1)
    val candidates = withinGoal.filter { stat ->
        if (stat.stt <= todayCount) return@filter false
        if (stat.chainFreq < CUT_CHAIN_FREQ_THRESHOLD && stat.kepFreq < CUT_KEP_FREQ_THRESHOLD) {
            return@filter false
        }
        val presence = stat.present.toDouble() / daysOfKind
        if (presence < CUT_MIN_PRESENCE) return@filter false
        val isAnchor = presence >= CUT_ANCHOR_MIN_PRESENCE &&
            stat.stdevMinutes <= CUT_ANCHOR_MAX_STDEV &&
            stat.chainFreq < CUT_ANCHOR_MAX_CHAIN_FREQ
        !isAnchor
    }

    val upcoming = candidates
        .filter { it.avgMinute > nowMinute - CUT_UPCOMING_LOOKAHEAD_MINUTES }
        .minByOrNull { it.avgMinute }
    if (upcoming != null) return upcoming.toTarget(CutTargetReason.CHAIN)

    val tail = withinGoal
        .filter {
            it.present > 0 &&
                it.stt > todayCount &&
                it.avgMinute > nowMinute - CUT_UPCOMING_LOOKAHEAD_MINUTES
        }
        .lastOrNull()
    return tail?.toTarget(CutTargetReason.TAIL)
}

private fun SttStat.toTarget(reason: CutTargetReason): CutTarget =
    CutTarget(
        stt = stt,
        avgMinute = avgMinute,
        stdevMinutes = stdevMinutes,
        chain = chain,
        kep = kep,
        present = present,
        reason = reason,
    )

/**
 * The note for [today] at [nowMinute], in the PWA's priority order (tasks.md 5.2):
 *
 * 1. too little data → ask for more;
 * 2. goal reached / exceeded → win or warning, judged by whether the day is actually over;
 * 3. praise → fewer cigarettes than both baselines;
 * 4. pace warning → more than both baselines;
 * 5. next-chain warning → the next cigarette is a "hút theo" whose time has not passed;
 * 6. cut target → the day's suggested cigarette;
 * 7. fallback → count the remaining quota (never hide the note).
 */
fun cutNote(
    records: List<CigRecord>,
    today: LocalDate,
    nowMinute: Int,
    goal: Int,
): CutNote {
    val stats = computeSttStats(records, today)
    if (stats.dayCount < CUT_MIN_DAYS) {
        return CutNote.InsufficientData(daysPresent = stats.dayCount)
    }

    val kind = DayKind.of(today)
    val todayCount = records.count { it.dateKey == today.toString() }
    val series = stats.seriesOf(kind)

    if (goal > 0 && todayCount >= goal) {
        if (todayCount > goal) return CutNote.GoalExceeded(goal, todayCount)
        val avgLast = avgLastCigMinute(records, today, kind)
        val endOfDay = if (avgLast > 0) {
            avgLast - CUT_EARLY_GOAL_MARGIN_MINUTES
        } else {
            CUT_FALLBACK_END_OF_DAY_MINUTES.toDouble()
        }
        return if (nowMinute >= endOfDay) {
            CutNote.GoalReached(goal)
        } else {
            CutNote.GoalReachedEarly(goal, nowMinute, avgLast.takeIf { it > 0 })
        }
    }

    val firstAvg = series.firstOrNull()?.avgMinute ?: 0.0
    val avgCount = avgCountByNow(records, today, kind, nowMinute)
    val avgLast = avgLastCigMinute(records, today, kind)
    val span = if (avgLast > 0 && firstAvg > 0) avgLast - firstAvg else 0.0
    val minutesPerCigarette = if (goal > 0 && span > 0) span / goal else 0.0
    val targetCount = if (minutesPerCigarette > 0 && nowMinute > firstAvg) {
        minOf(goal, ceil((nowMinute - firstAvg) / minutesPerCigarette).toInt())
    } else {
        0
    }

    val belowTarget = targetCount == 0 || todayCount < targetCount
    if (belowTarget && todayCount < avgCount && nowMinute >= firstAvg) {
        val difference = if (targetCount > 0) {
            targetCount - todayCount
        } else {
            ceil(avgCount).toInt() - todayCount
        }
        return CutNote.Praise(
            difference = difference,
            targetCount = targetCount,
            avgCount = avgCount,
            todayCount = todayCount,
        )
    }

    if (targetCount > 0 && todayCount > avgCount && todayCount > targetCount) {
        return CutNote.PaceWarning(
            avgCount = avgCount,
            targetCount = targetCount,
            todayCount = todayCount,
            goal = goal,
            minutesPerCigarette = minutesPerCigarette,
        )
    }

    val nextStt = todayCount + 1
    val next = series.getOrNull(nextStt - 1)
    if (next != null) {
        val slack = max(CUT_SLACK_MIN_MINUTES.toDouble(), next.stdevMinutes)
        if (next.chainFreq >= CUT_CHAIN_FREQ_THRESHOLD && nowMinute < next.avgMinute + slack) {
            return CutNote.NextChainWarning(nextStt, next)
        }
    }

    val target = dailyTarget(stats, kind, goal, todayCount, nowMinute)
    if (target != null) return CutNote.TargetCut(target, goal)

    return if (goal > 0) CutNote.Encourage(goal, todayCount) else CutNote.Neutral
}

/** [cutNote] for a "YYYY-MM-DD" key; malformed keys fall back to the insufficient-data note. */
fun cutNote(
    records: List<CigRecord>,
    todayKey: String,
    nowMinute: Int,
    goal: Int,
): CutNote {
    val today = runCatching { LocalDate.parse(todayKey) }.getOrNull()
        ?: return CutNote.InsufficientData(daysPresent = 0)
    return cutNote(records, today, nowMinute, goal)
}
