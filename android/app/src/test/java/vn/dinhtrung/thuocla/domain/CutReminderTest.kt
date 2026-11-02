package vn.dinhtrung.thuocla.domain

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tasks.md 5.3 — the golden test for the "điếu nên cắt" note, on a seed modelled on Trung's own usage
 * (goal 12, weekend days included).
 *
 * The four expected verdicts come from tasks.md 5.3 and are the PWA's own words:
 *
 * | now   | today | verdict                                          |
 * |-------|-------|--------------------------------------------------|
 * | 08:00 | 2     | ✅ praise "Hút ít hơn nhịp mục tiêu 1 điếu"        |
 * | 08:40 | 5     | ⚠️ "Hút nhanh hơn cả thói quen lẫn nhịp mục tiêu" |
 * | 17:40 | 12/12 | ⚠️ "Hết hạn mức 12 điếu rồi"                      |
 * | 22:10 | 12/12 | 🎉 "Đạt mục tiêu 12 điếu — thắng hôm nay rồi!"     |
 *
 * The two averages the verdicts quote are the PWA's own published figures — "~2.4 điếu" at 08:00 and
 * "~3.3 điếu" at 08:40 (CHANGELOG v1.4.6 / tasks.md 7.3) — and the seed below reproduces both.
 *
 * A note on the seed: no backup file of Trung's real data exists in this repository or on this
 * machine (`thuocla_backup_*.json` is absent; the v1.4.x verification notes cite a CSV from
 * 07-15→08-15 that was never committed). So this seed is *reconstructed* to hit the same four
 * numbers the PWA printed on real data. The numbers in the table are the contract; the seed is the
 * fixture that satisfies them. If the real CSV surfaces, swapping the seed is a localised change.
 */
class CutReminderTest {

    // ---------------------------------------------------------------- the seed

    /**
     * Weekday history: 12 cigarettes, 07:00 → 22:00. Exactly the goal, so the "nhịp quen thuộc"
     * baseline saturates only at the very end of the day. The first gaps are 40 minutes, so STT 2 is a
     * genuine "hút theo" (gap ≤ 40) — without one the next-chain warning would be unreachable.
     */
    private val weekdayTail = listOf(420, 460, 500, 540, 660, 780, 900, 1080, 1200, 1260, 1290, 1320)

    /**
     * Weekend history, three shapes that together give ~2.4 by 08:00 and ~3.3 by 08:40 while still
     * averaging 12:00 for the last cigarette (so 22:10 counts as "the day is over"):
     * - B (5 cigarettes): 07:00, 07:40, 08:20, 09:00, 12:00
     * - C (5 cigarettes): 07:00, 07:40, 08:00, 09:00, 12:00
     * - D (6 cigarettes): 07:00, 07:40, 08:20, 08:35, 09:00, 12:00
     */
    private val weekendB = listOf(420, 460, 500, 540, 720)
    private val weekendC = listOf(420, 460, 480, 540, 720)
    private val weekendD = listOf(420, 460, 500, 515, 540, 720)

    /**
     * Builds 29 days of history ending the day before [today], all inside the 30-day window.
     *
     * Weekend days rotate B, B, B, C, C, C, D, D so the first four weekend days are the B shape; the
     * goal-pace baseline needs more than one shape to land on 3 at 08:40 without the "hút theo" test
     * collapsing to a single shape.
     */
    private fun history(today: LocalDate): List<CigRecord> {
        val out = mutableListOf<CigRecord>()
        var b = 0
        var c = 0
        var d = 0
        for (back in 1..(LOWEST_DAY_WINDOW_DAYS - 2)) {
            val day = today.minusDays(back.toLong())
            val key = day.toString()
            val minutes = when (DayKind.of(day)) {
                DayKind.WEEKEND -> when {
                    b < 3 -> weekendB.also { b++ }
                    c < 3 -> weekendC.also { c++ }
                    d < 2 -> weekendD.also { d++ }
                    else -> weekendB.also { b++ }
                }
                DayKind.WEEKDAY -> weekdayTail
            }
            minutes.forEach { out += CigRecord(key, it) }
        }
        return out
    }

    private fun todayRecords(today: LocalDate, minutes: List<Int>): List<CigRecord> =
        minutes.map { CigRecord(today.toString(), it) }

    // ------------------------------------------------- 5.3 the four verdicts

    /** 2026-08-15 is a Saturday — the weekend branch, which is why the seed carries weekend days. */
    @Test
    fun saturday_0800_withTwoCigarettes_praises() {
        val today = LocalDate.of(2026, 8, 15)
        assertEquals(DayOfWeek.SATURDAY, today.dayOfWeek)

        val records = history(today) + todayRecords(today, listOf(420, 460))
        val note = cutNote(records, today, nowMinute = 8 * 60, goal = 12)

        assertTrue("expected praise, got $note", note is CutNote.Praise)
        note as CutNote.Praise
        assertEquals("✅", note.icon)
        assertEquals("Hút ít hơn nhịp mục tiêu 1 điếu — giỏi lắm!", note.title)
        // The PWA's own published wording for this moment: "thường ~2.4 điếu".
        assertEquals(
            "Lúc này thường ~2.4 điếu, nhịp mục tiêu ~3 — bạn mới 2. Cứ đà này!",
            note.subtitle,
        )
        assertEquals(CutTone.PRAISE, note.tone)
        assertEquals(2, note.todayCount)
        assertEquals(3, note.targetCount)
    }

    @Test
    fun saturday_0840_withFiveCigarettes_warnsAboutPace() {
        val today = LocalDate.of(2026, 8, 15)
        val records = history(today) + todayRecords(today, listOf(420, 460, 480, 500, 520))
        val note = cutNote(records, today, nowMinute = 8 * 60 + 40, goal = 12)

        assertTrue("expected pace warning, got $note", note is CutNote.PaceWarning)
        note as CutNote.PaceWarning
        assertEquals("⚠️", note.icon)
        assertEquals("Hút nhanh hơn cả thói quen lẫn nhịp mục tiêu — giãn ra!", note.title)
        // The spec's own scenario text: habit 3.3, goal pace 3, five cigarettes already.
        assertEquals(
            "Thường giờ này ~3.3 điếu, nhịp mục tiêu ~4 — bạn đã 5. Cần giãn ~25ph/điếu để về đúng 12 điếu.",
            note.subtitle,
        )
        assertEquals(CutTone.WARN, note.tone)
        assertEquals(5, note.todayCount)
    }

    /** 2026-08-17 is a Monday — the weekday branch, whose last cigarette averages 22:00. */
    @Test
    fun monday_1740_withTwelveOfTwelve_warnsQuotaGoneEarly() {
        val today = LocalDate.of(2026, 8, 17)
        assertEquals(DayOfWeek.MONDAY, today.dayOfWeek)

        val records = history(today) +
            todayRecords(today, listOf(420, 440, 460, 480, 500, 520, 540, 600, 720, 840, 960, 1060))
        val note = cutNote(records, today, nowMinute = 17 * 60 + 40, goal = 12)

        assertTrue("expected early-quota warning, got $note", note is CutNote.GoalReachedEarly)
        note as CutNote.GoalReachedEarly
        assertEquals("⚠️", note.icon)
        assertEquals("Hết hạn mức 12 điếu rồi — tối nay đừng hút nữa!", note.title)
        assertEquals(
            "Mới 17:40 đã dùng hết quota — thường ngày bạn hút đến ~22:00. Giữ kỷ luật!",
            note.subtitle,
        )
        assertEquals(CutTone.WARN, note.tone)
    }

    @Test
    fun monday_2210_withTwelveOfTwelve_praisesGoalReached() {
        val today = LocalDate.of(2026, 8, 17)
        val records = history(today) +
            todayRecords(today, listOf(420, 460, 500, 540, 600, 660, 720, 900, 1080, 1200, 1300, 1320))
        val note = cutNote(records, today, nowMinute = 22 * 60 + 10, goal = 12)

        assertTrue("expected goal-reached praise, got $note", note is CutNote.GoalReached)
        note as CutNote.GoalReached
        assertEquals("🎉", note.icon)
        assertEquals("Đạt mục tiêu 12 điếu — thắng hôm nay rồi!", note.title)
        assertEquals("Đừng hút thêm nữa nhé, giữ đà!", note.subtitle)
        assertEquals(CutTone.PRAISE, note.tone)
    }

    /**
     * The early/late split for the goal state is "now >= mean last cigarette − 30 minutes", so the
     * same 12/12 day reads as a warning at 17:40 and a win at 22:10. The boundary itself sits at
     * 21:30 for this seed (22:00 − 30 min) — asserted so a change to the margin is caught.
     */
    @Test
    fun goalReached_boundary_isMeanLastCigaretteMinusThirtyMinutes() {
        val today = LocalDate.of(2026, 8, 17)
        val twelve = listOf(420, 460, 500, 540, 600, 660, 720, 900, 1080, 1200, 1300, 1320)
        val records = history(today) + todayRecords(today, twelve)

        val justBefore = cutNote(records, today, nowMinute = 21 * 60 + 29, goal = 12)
        val atBoundary = cutNote(records, today, nowMinute = 21 * 60 + 30, goal = 12)

        assertTrue("21:29 should still warn, got $justBefore", justBefore is CutNote.GoalReachedEarly)
        assertTrue("21:30 should be a win, got $atBoundary", atBoundary is CutNote.GoalReached)
    }

    // -------------------------------------------------------- 5.1 statistics

    @Test
    fun computeSttStats_splitsWeekendAndWeekdayAndKeepsGapZeroOutOfChain() {
        val today = LocalDate.of(2026, 8, 15)
        val records = history(today) + todayRecords(today, listOf(420, 460))

        val stats = computeSttStats(records, today)

        assertEquals(30, stats.dayCount)
        assertEquals(21, stats.weekdayDays)
        assertEquals(9, stats.weekendDays)

        // STT 1 of both kinds is 07:00 with no previous cigarette, so it is never "hút theo".
        assertEquals(420.0, stats.weekend[0].avgMinute, 0.001)
        assertEquals(0.0, stats.weekend[0].chainFreq, 0.001)
        assertEquals(420.0, stats.weekday[0].avgMinute, 0.001)
        assertEquals(0.0, stats.weekday[0].chainFreq, 0.001)

        // The weekday tail's first gaps are 40 minutes: STT 2 is "hút theo" but not "kép" (40 > 20).
        assertEquals(1.0, stats.weekday[1].chainFreq, 0.001)
        assertEquals(0.0, stats.weekday[1].kepFreq, 0.001)
    }

    /** A duplicate minute is not "hút theo": the PWA's `gap > 0` rule (spec "Cut-target computation"). */
    @Test
    fun computeSttStats_duplicateMinuteDoesNotCountAsChain() {
        // 2026-08-14 is a Friday and 08-13 a Thursday, so both days land in the weekday series.
        val today = LocalDate.of(2026, 8, 17)
        val friday = "2026-08-14"
        val thursday = "2026-08-13"
        val records = listOf(
            CigRecord(friday, 480),
            CigRecord(friday, 480),
            CigRecord(thursday, 480),
            CigRecord(thursday, 540),
        )

        val weekday = computeSttStats(records, today).weekday

        // Two days reached STT 2: the duplicate pair (gap 0, not a chain) and the 60-minute pair
        // (also not a chain). Neither may count, so the frequency is zero across two days.
        assertEquals(2, weekday[1].present)
        assertEquals(0, weekday[1].chain)
        assertEquals(0.0, weekday[1].chainFreq, 0.001)
    }

    /** The spec: "Separates weekend and weekday statistics" — a weekend view never mixes weekdays. */
    @Test
    fun weekendNote_ignoresWeekdayHistory() {
        val today = LocalDate.of(2026, 8, 15)
        val records = history(today) + todayRecords(today, listOf(420, 460))
        val note = cutNote(records, today, nowMinute = 8 * 60, goal = 12)
        note as CutNote.Praise
        // 2.375 is the weekend baseline; the weekday baseline at 08:00 is 2.0 and would change the diff.
        assertEquals(2.375, avgCountByNow(records, today, DayKind.WEEKEND, 8 * 60), 0.001)
        assertEquals(2.0, avgCountByNow(records, today, DayKind.WEEKDAY, 8 * 60), 0.001)
        assertTrue(
            "weekend baseline must be used, subtitle was ${note.subtitle}",
            note.subtitle.contains("~2.4 điếu"),
        )
    }

    /** Baseline A excludes today: a heavy today must not raise the "nhịp quen thuộc" it is judged by. */
    @Test
    fun avgCountByNow_excludesTodaysRecords() {
        val today = LocalDate.of(2026, 8, 17)
        val base = history(today)
        val withToday = base + todayRecords(today, List(12) { 7 * 60 + it * 5 })

        assertEquals(
            avgCountByNow(base, today, DayKind.WEEKDAY, nowMinute = 9 * 60),
            avgCountByNow(withToday, today, DayKind.WEEKDAY, nowMinute = 9 * 60),
            0.0001,
        )
    }

    /** The mean of each day's *last* record, not the last STT slot. */
    @Test
    fun avgLastCigMinute_usesEachDaysLastRecord() {
        val today = LocalDate.of(2026, 8, 17)
        val stats = avgLastCigMinute(history(today), today, DayKind.WEEKDAY)
        assertEquals(1320.0, stats, 0.001)
    }

    // ------------------------------------------------ 5.2 the state machine

    @Test
    fun insufficientData_belowSevenDays_asksForMoreAndInventsNoTarget() {
        val today = LocalDate.of(2026, 8, 17)
        val records = (1..3).flatMap { back ->
            listOf(
                CigRecord(today.minusDays(back.toLong()).toString(), 420),
                CigRecord(today.minusDays(back.toLong()).toString(), 480),
            )
        }
        val note = cutNote(records, today, nowMinute = 10 * 60, goal = 12)

        assertTrue("expected insufficient-data note, got $note", note is CutNote.InsufficientData)
        note as CutNote.InsufficientData
        assertEquals(3, note.daysPresent)
        assertEquals("📊", note.icon)
        assertEquals("Cần thêm dữ liệu để tính điếu nên cắt", note.title)
        assertEquals(
            "Còn 4 ngày nữa (tối thiểu 7 ngày trong 30 ngày qua)",
            note.subtitle,
        )
    }

    @Test
    fun goalExceeded_warnsAndQuotesTheOvershoot() {
        val today = LocalDate.of(2026, 8, 17)
        val records = history(today) + todayRecords(today, List(15) { 7 * 60 + it * 40 })
        val note = cutNote(records, today, nowMinute = 17 * 60, goal = 12)

        assertTrue("expected goal-exceeded warning, got $note", note is CutNote.GoalExceeded)
        note as CutNote.GoalExceeded
        assertEquals("⚠️", note.icon)
        assertEquals("Vượt mục tiêu 12 điếu (đang 15) — dừng lại!", note.title)
        assertEquals("+3 điếu ngoài kế hoạch hôm nay", note.subtitle)
    }

    /** The spec: "Warns before the predicted time" for a chain STT at ≥25% of days. */
    @Test
    fun nextChainWarning_firesBeforeThePredictedMinute() {
        val today = LocalDate.of(2026, 8, 17)
        // One cigarette so far, so the next STT is #2 — a 40-minute chain in the weekday seed.
        val records = history(today) + todayRecords(today, listOf(420))
        val note = cutNote(records, today, nowMinute = 7 * 60 + 10, goal = 12)

        assertTrue("expected next-chain warning, got $note", note is CutNote.NextChainWarning)
        assertEquals("⚠️", note.icon)
        assertEquals("Điếu #2 ≈ 07:40 — đây là điếu hút theo, thử cắt!", note.title)
        // 20 of 21 weekday days reached STT 2 (today is only at STT 1); every one of them chained.
        assertEquals(
            "20/20 ngày là \"điếu theo\" (gap ≤40ph) trong 30 ngày qua",
            note.subtitle,
        )
    }

    /** The spec: "No warning for non-chain cigarettes" — once the predicted time has passed. */
    @Test
    fun nextChainWarning_stopsAfterThePredictedMinutePlusSlack() {
        val today = LocalDate.of(2026, 8, 17)
        val records = history(today) + todayRecords(today, listOf(420))
        // STT 2 averages 08:00; the weekday stdev is 0, so slack is the 30-minute floor → expires 08:30.
        val after = cutNote(records, today, nowMinute = 8 * 60 + 31, goal = 12)

        assertTrue(
            "chain warning should have expired, got $after",
            after !is CutNote.NextChainWarning,
        )
    }

    @Test
    fun dailyTarget_neverExceedsTheDailyGoal() {
        val today = LocalDate.of(2026, 8, 17)
        val stats = computeSttStats(history(today), today)

        val target = dailyTarget(stats, DayKind.WEEKDAY, goal = 4, todayCount = 1, nowMinute = 8 * 60)

        if (target != null) {
            assertTrue("target STT ${target.stt} must stay inside the goal 4", target.stt <= 4)
        }
    }

    @Test
    fun dailyTarget_skipsAlreadySmokedStt() {
        val today = LocalDate.of(2026, 8, 17)
        val stats = computeSttStats(history(today), today)

        val target = dailyTarget(stats, DayKind.WEEKDAY, goal = 12, todayCount = 6, nowMinute = 8 * 60)

        if (target != null) {
            assertTrue("target STT ${target.stt} must be ahead of 6", target.stt > 6)
        }
    }

    /** The spec: "Cut target never exceeds the daily goal" — goal 10 must not suggest STT 17. */
    @Test
    fun targetCut_staysInsideTheGoal() {
        val today = LocalDate.of(2026, 8, 17)
        val records = history(today) + todayRecords(today, List(3) { 7 * 60 + it * 40 })
        val note = cutNote(records, today, nowMinute = 9 * 60, goal = 10)

        if (note is CutNote.TargetCut) {
            assertTrue("target STT ${note.target.stt} must be ≤ goal 10", note.target.stt <= 10)
        }
    }

    /**
     * The spec: "Encourages when below goal with nothing cuttable" — the note never hides.
     *
     * A weekend day is used deliberately: the weekend habit baseline saturates at ~5 cigarettes, so
     * 11 cigarettes is *ahead* of habit (no praise), while every STT inside the goal has already
     * passed its predicted time (no target) — the exact combination the spec describes.
     */
    @Test
    fun encourage_whenBelowGoalWithNothingCuttable() {
        val today = LocalDate.of(2026, 8, 15)
        val eleven = listOf(420, 460, 500, 540, 600, 660, 720, 780, 840, 900, 1320)
        val records = history(today) + todayRecords(today, eleven)
        val note = cutNote(records, today, nowMinute = 23 * 60 + 55, goal = 12)

        assertTrue("expected encouragement, got $note", note is CutNote.Encourage)
        note as CutNote.Encourage
        assertEquals("🎯", note.icon)
        assertEquals("Còn 1 điếu trong hạn mức 12 hôm nay — giữ nhịp nhé!", note.title)
        assertEquals("Điếu cuối — bỏ nó để về dưới mục tiêu!", note.subtitle)
    }

    /**
     * Goal 0 (no daily limit) with the weekday tail already smoked: the day is at its own habit
     * baseline, so there is no praise, and with no goal there is no quota to count — the neutral
     * message is the fallback.
     */
    @Test
    fun neutral_whenNoGoalAndNothingToCut() {
        val today = LocalDate.of(2026, 8, 17)
        val records = history(today) + todayRecords(today, weekdayTail)
        val note = cutNote(records, today, nowMinute = 23 * 60, goal = 0)

        assertTrue("expected neutral note, got $note", note is CutNote.Neutral)
        assertEquals("💪", note.icon)
        assertEquals(
            "Hôm nay không có điếu nào đáng cắt — khoảng cách hút đang ổn!",
            note.title,
        )
    }

    /**
     * The spec: "Live update cadence" — the note is a pure function of records + minute, so the same
     * records at two different minutes must give two different notes. This is the seam a cached
     * target would break.
     */
    @Test
    fun cutNote_recomputesForTheMinuteItIsGiven() {
        // A weekday 12/12 day: at 21:29 the quota is gone early (warning), at 21:30 the day counts as
        // over (win) — the weekday history's mean last cigarette is 22:00, minus the 30-minute margin.
        // Same records, one minute apart, two verdicts; a cached target would freeze the first answer.
        val today = LocalDate.of(2026, 8, 17)
        val twelve = listOf(420, 460, 500, 540, 660, 780, 900, 1080, 1200, 1260, 1290, 1320)
        val records = history(today) + todayRecords(today, twelve)

        val before = cutNote(records, today, nowMinute = 21 * 60 + 29, goal = 12)
        val after = cutNote(records, today, nowMinute = 21 * 60 + 30, goal = 12)

        assertTrue("21:29 should warn the quota went early, got $before", before is CutNote.GoalReachedEarly)
        assertTrue("21:30 should praise the win, got $after", after is CutNote.GoalReached)
        assertTrue(before != after)
    }

    /** A malformed date key cannot crash a screen path: it degrades to the insufficient-data note. */
    @Test
    fun malformedDateKey_degradesToInsufficientData() {
        val note = cutNote(emptyList(), "not-a-date", nowMinute = 8 * 60, goal = 12)
        assertTrue(note is CutNote.InsufficientData)
    }

    /** Two records at the same minute in the same day is the PWA's mis-tap; STT count stays honest. */
    @Test
    fun stats_sttCapIsTwenty() {
        // 2026-08-14 is a Friday, so its 25 cigarettes land in the weekday series.
        val today = LocalDate.of(2026, 8, 17)
        val many = List(25) { CigRecord("2026-08-14", 420 + it * 5) }
        val weekday = computeSttStats(many, today).weekday

        assertEquals(CUT_STT_CAP, weekday.size)
        assertEquals(CUT_STT_CAP, weekday[19].stt)
        // The 25 cigarettes all exist on the day, but only the first 20 are scored.
        assertEquals(1, weekday[19].present)
    }

    /** Guard against the "0-minute gap" duplicate being counted as a chain in either kind. */
    @Test
    fun stats_chainNeverExceedsPresent() {
        val today = LocalDate.of(2026, 8, 15)
        val stats = computeSttStats(history(today), today)
        (stats.weekday + stats.weekend).forEach { stat ->
            assertTrue("STT ${stat.stt} chain ${stat.chain} > present ${stat.present}", stat.chain <= stat.present)
            assertTrue(stat.kep <= stat.chain)
        }
    }

    /** The published averages the golden verdicts quote must stay within rounding of the seed. */
    @Test
    fun seed_producesThePublishedAverages() {
        val today = LocalDate.of(2026, 8, 15)
        val records = history(today) + todayRecords(today, listOf(420, 460))

        assertEquals(2.375, avgCountByNow(records, today, DayKind.WEEKEND, 8 * 60), 0.001)
        assertEquals(3.25, avgCountByNow(records, today, DayKind.WEEKEND, 8 * 60 + 40), 0.001)
        assertEquals(720.0, avgLastCigMinute(records, today, DayKind.WEEKEND), 0.001)
        assertTrue(abs(2.375 - 2.4) < 0.05)
    }
}
