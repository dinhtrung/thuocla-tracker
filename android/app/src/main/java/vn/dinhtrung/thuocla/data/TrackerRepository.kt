package vn.dinhtrung.thuocla.data

import androidx.room.withTransaction
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vn.dinhtrung.thuocla.data.db.AppDatabase
import vn.dinhtrung.thuocla.data.db.ConfigEntity
import vn.dinhtrung.thuocla.data.db.RecordEntity
import vn.dinhtrung.thuocla.domain.CigRecord
import vn.dinhtrung.thuocla.domain.DailySummary
import vn.dinhtrung.thuocla.domain.LOWEST_DAY_WINDOW_DAYS
import vn.dinhtrung.thuocla.domain.buildDailySummary
import vn.dinhtrung.thuocla.domain.history.COMPARISON_WINDOW_DAYS
import vn.dinhtrung.thuocla.domain.TRIGGER_COUNT
import vn.dinhtrung.thuocla.domain.ThemeMode
import vn.dinhtrung.thuocla.domain.TrackerConfig
import vn.dinhtrung.thuocla.domain.WriteBlock
import vn.dinhtrung.thuocla.domain.WriteResult

/**
 * The only write path into the store (tasks.md 2.2).
 *
 * Rules this class owns:
 * - every multi-step operation runs in one transaction, so a killed process leaves either the old
 *   state or the new one (spec `offline-store`, "Ghi dữ liệu có tính nguyên tử");
 * - a duplicate (dateKey, minuteOfDay) is reported as [WriteBlock.DUPLICATE_MINUTE] and never
 *   replaces the row already there (PWA behaviour: tapping +1 twice in the same minute);
 * - no rounding happens here — callers round via `roundTo5` (tasks.md 3.1) so the store holds
 *   exactly what the user confirmed;
 * - config in the store is always complete: a row that was never written reads as the PWA defaults.
 */
class TrackerRepository(private val db: AppDatabase) {

    private val recordDao = db.recordDao()
    private val configDao = db.configDao()

    // ---------------------------------------------------------------- records

    suspend fun addRecord(
        dateKey: String,
        minuteOfDay: Int,
        triggerIdx: Int? = null,
        note: String? = null,
    ): WriteResult = db.withTransaction {
        if (!isValidDateKey(dateKey) || minuteOfDay !in MINUTE_RANGE) {
            return@withTransaction WriteResult(applied = false, block = WriteBlock.INVALID)
        }
        if (!isValidTrigger(triggerIdx)) {
            return@withTransaction WriteResult(applied = false, block = WriteBlock.INVALID)
        }
        val row = RecordEntity(dateKey, minuteOfDay, triggerIdx, note)
        val rowId = recordDao.insert(row)
        if (rowId == INSERT_IGNORED) {
            WriteResult(
                applied = false,
                record = recordDao.get(dateKey, minuteOfDay)?.toDomain(),
                block = WriteBlock.DUPLICATE_MINUTE,
            )
        } else {
            WriteResult(applied = true, record = row.toDomain())
        }
    }

    /** Removes the latest cigarette of [dateKey] and returns it, or null when the day is empty. */
    suspend fun deleteLast(dateKey: String): CigRecord? = db.withTransaction {
        if (!isValidDateKey(dateKey)) return@withTransaction null
        val last = recordDao.last(dateKey) ?: return@withTransaction null
        recordDao.delete(dateKey, last.minuteOfDay)
        last.toDomain()
    }

    /**
     * Moves one cigarette to another minute of the same day, keeping its reason and note.
     * Refused with [WriteBlock.DUPLICATE_MINUTE] when the target minute is occupied — the original
     * row is left untouched (the pre-check runs inside the same transaction as the move).
     */
    suspend fun updateTime(dateKey: String, fromMinute: Int, toMinute: Int): WriteResult =
        db.withTransaction {
            if (!isValidDateKey(dateKey) || fromMinute !in MINUTE_RANGE || toMinute !in MINUTE_RANGE) {
                return@withTransaction WriteResult(applied = false, block = WriteBlock.INVALID)
            }
            val existing = recordDao.get(dateKey, fromMinute)
                ?: return@withTransaction WriteResult(applied = false, block = WriteBlock.INVALID)
            if (fromMinute == toMinute) {
                return@withTransaction WriteResult(applied = true, record = existing.toDomain())
            }
            recordDao.get(dateKey, toMinute)?.let { blocker ->
                return@withTransaction WriteResult(
                    applied = false,
                    record = blocker.toDomain(),
                    block = WriteBlock.DUPLICATE_MINUTE,
                )
            }
            recordDao.delete(dateKey, fromMinute)
            val moved = existing.copy(minuteOfDay = toMinute)
            if (recordDao.insert(moved) == INSERT_IGNORED) {
                // Cannot happen after the check above unless another writer beat us inside this
                // transaction; put the original row back so nothing is lost.
                recordDao.insert(existing)
                WriteResult(
                    applied = false,
                    record = recordDao.get(dateKey, toMinute)?.toDomain(),
                    block = WriteBlock.DUPLICATE_MINUTE,
                )
            } else {
                WriteResult(applied = true, record = moved.toDomain())
            }
        }

    /** Attaches [triggerIdx] to one cigarette; null removes the reason. False when the row is absent. */
    suspend fun setTrigger(dateKey: String, minuteOfDay: Int, triggerIdx: Int?): Boolean =
        db.withTransaction {
            if (!isValidDateKey(dateKey) || minuteOfDay !in MINUTE_RANGE) {
                return@withTransaction false
            }
            if (!isValidTrigger(triggerIdx)) return@withTransaction false
            recordDao.updateTrigger(dateKey, minuteOfDay, triggerIdx) > 0
        }

    suspend fun day(dateKey: String): List<CigRecord> =
        if (!isValidDateKey(dateKey)) emptyList() else recordDao.day(dateKey).map { it.toDomain() }

    fun dayFlow(dateKey: String): Flow<List<CigRecord>> =
        recordDao.dayFlow(dateKey).map { rows -> rows.map { it.toDomain() } }

    /** Inclusive on both ends, ordered by date then minute. An inverted or malformed range is empty. */
    suspend fun range(from: String, to: String): List<CigRecord> =
        if (!isValidRange(from, to)) {
            emptyList()
        } else {
            recordDao.range(from, to).map { it.toDomain() }
        }

    fun rangeFlow(from: String, to: String): Flow<List<CigRecord>> =
        recordDao.rangeFlow(from, to).map { rows -> rows.map { it.toDomain() } }

    /**
     * Cigarettes in the calendar month [monthKey] ("YYYY-MM") — the number the PWA shows as
     * "tháng này". Days are bounded by real month length, not by a `-31` string trick.
     */
    suspend fun monthCount(monthKey: String): Int {
        val bounds = monthBounds(monthKey) ?: return 0
        return recordDao.countBetween(bounds.first, bounds.second)
    }

    fun monthCountFlow(monthKey: String): Flow<Int> {
        val bounds = monthBounds(monthKey) ?: return kotlinx.coroutines.flow.flowOf(0)
        return recordDao.countBetweenFlow(bounds.first, bounds.second)
    }

    /** Number of days that have at least one cigarette; drives `v1.0.0 · 128 ngày`. */
    suspend fun distinctDayCount(): Int = recordDao.distinctDayCount()

    fun distinctDayCountFlow(): Flow<Int> = recordDao.distinctDayCountFlow()

    // ------------------------------------------------------- daily-summary (task 4)

    /**
     * Everything the "Hôm nay" screen needs besides today's own records: the four quick stats and
     * the 30-day comparison series (tasks.md 4.1 / 4.3).
     *
     * One read covers both the 31-day "ít nhất" window and the 30-day average window — the second is
     * a subset of the first — and [dailySummary] derives every number from it. Today's records come
     * from [dayFlow] so the screen's own list and the summary never disagree.
     */
    suspend fun dailySummary(today: String): DailySummary {
        val todayDate = runCatching { LocalDate.parse(today) }.getOrNull()
            ?: return DailySummary.empty(today)
        val from = todayDate.minusDays((LOWEST_DAY_WINDOW_DAYS - 1).toLong()).toString()
        val to = todayDate.minusDays(1).toString()
        return buildDailySummary(range(from, to) + day(today), today)
    }

    /** The same summary as a flow, recomputed whenever the records table changes. */
    fun dailySummaryFlow(today: String): Flow<DailySummary> {
        val todayDate = runCatching { LocalDate.parse(today) }.getOrNull()
            ?: return kotlinx.coroutines.flow.flowOf(DailySummary.empty(today))
        val from = todayDate.minusDays((LOWEST_DAY_WINDOW_DAYS - 1).toLong()).toString()
        return recordDao.rangeFlow(from, today).map { rows ->
            buildDailySummary(rows.map { it.toDomain() }, today)
        }
    }

    /**
     * The records the "Điếu nên cắt" note scores (tasks.md 5.1): the 30 days ending at [today],
     * today included. Emits on every change to that window, so the note recomputes after each +1 and
     * undo without a separate read path — [cutNote] itself takes the records and the minute.
     */
    fun cutReminderFlow(today: String): Flow<List<CigRecord>> {
        val todayDate = runCatching { LocalDate.parse(today) }.getOrNull()
            ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        val from = todayDate.minusDays((COMPARISON_WINDOW_DAYS - 1).toLong()).toString()
        return recordDao.rangeFlow(from, today).map { rows -> rows.map { it.toDomain() } }
    }

    /** Cigarettes in [dateKey] — the count behind "Hôm qua" and the day-detail header. */
    suspend fun countInDay(dateKey: String): Int =
        if (!isValidDateKey(dateKey)) 0 else recordDao.countInDay(dateKey)

    // ----------------------------------------------------------------- config

    /** Never null: if no row exists yet, the defaults are stored and returned. */
    suspend fun getConfig(): TrackerConfig = db.withTransaction {
        configDao.get()?.toDomain() ?: TrackerConfig.DEFAULT.also { configDao.upsert(it.toEntity()) }
    }

    fun configFlow(): Flow<TrackerConfig> =
        configDao.flow().map { row -> row?.toDomain() ?: TrackerConfig.DEFAULT }

    /** Stores [config] with empty/too-small fields replaced by their default, and returns what was stored. */
    suspend fun saveConfig(config: TrackerConfig): TrackerConfig = db.withTransaction {
        val normalized = config.normalized()
        configDao.upsert(normalized.toEntity())
        normalized
    }

    // ---------------------------------------------------------------- helpers

    private fun isValidDateKey(dateKey: String): Boolean =
        DATE_KEY_REGEX.matches(dateKey) && runCatching { LocalDate.parse(dateKey) }.isSuccess

    private fun isValidTrigger(triggerIdx: Int?): Boolean =
        triggerIdx == null || triggerIdx in 0 until TRIGGER_COUNT

    private fun isValidRange(from: String, to: String): Boolean =
        isValidDateKey(from) && isValidDateKey(to) && from <= to

    private fun monthBounds(monthKey: String): Pair<String, String>? {
        if (!MONTH_KEY_REGEX.matches(monthKey)) return null
        val month = runCatching { YearMonth.parse(monthKey) }.getOrNull() ?: return null
        return month.atDay(1).toString() to month.atEndOfMonth().toString()
    }

    private fun RecordEntity.toDomain() = CigRecord(dateKey, minuteOfDay, triggerIdx, note)

    private fun TrackerConfig.toEntity() = ConfigEntity(
        goal = goal,
        intervalGoal = intervalGoal,
        pricePerPack = pricePerPack,
        cigsPerPack = cigsPerPack,
        themeMode = themeMode.name,
    )

    private fun ConfigEntity.toDomain() = TrackerConfig(
        goal = goal,
        intervalGoal = intervalGoal,
        pricePerPack = pricePerPack,
        cigsPerPack = cigsPerPack,
        themeMode = ThemeMode.fromStorage(themeMode),
    )

    private companion object {
        val MINUTE_RANGE = 0..1439
        const val INSERT_IGNORED = -1L
        val DATE_KEY_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")
        val MONTH_KEY_REGEX = Regex("""\d{4}-\d{2}""")
    }
}
