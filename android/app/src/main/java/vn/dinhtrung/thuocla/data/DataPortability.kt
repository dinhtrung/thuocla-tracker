package vn.dinhtrung.thuocla.data

import androidx.room.withTransaction
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import vn.dinhtrung.thuocla.data.db.AppDatabase
import vn.dinhtrung.thuocla.data.db.ConfigEntity
import vn.dinhtrung.thuocla.data.db.RecordEntity
import vn.dinhtrung.thuocla.domain.Triggers
import vn.dinhtrung.thuocla.domain.TrackerConfig

/**
 * CSV + JSON backup export and import, matching the PWA at v1.5.2 (spec `data-portability`).
 *
 * Behaviour source of truth is `app.js`:
 * - `exportCSV()` — header `Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày`, six quoted
 *   fields per row, reason text without its emoji, gap empty on the first cigarette of a day and
 *   `minute - previousMinute` afterwards, ordinal from 1.
 * - `backupPayload()` — `{app, format, exportedAt, config, data}` where `data[dateKey]` is a list of
 *   `{time, trigger?, note?}`.
 * - `normalizeBackup()` — drops invalid date keys, unreadable `time` and out-of-range `trigger`;
 *   returns nothing usable when no cigarette survives.
 * - `mergeDayRecords()` — merging keeps the first record for a minute, so the store wins over a file.
 * - `restoreFromFile()` — THAY THẾ replaces everything, GỘP adds only what is missing, and the file's
 *   config wins in both modes.
 *
 * Design decision #2 (wall clock, not UTC) is preserved on export: `time` is written as a local ISO
 * date-time without a zone suffix, so the PWA's `new Date(...)` reads the same hour the user saw.
 * Import accepts that form as well as the PWA's own `...Z` form, converting an instant to the device
 * zone exactly as the PWA's `formatTime` does.
 *
 * Design decision #3 (the unique minute index) stays the only duplicate guard: every insert here is
 * `OnConflictStrategy.IGNORE`, so a collision is counted, never overwritten.
 */
class DataPortability(
    private val db: AppDatabase,
    private val repository: TrackerRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    /** Outcome of an import. [Rejected] means nothing was written. */
    sealed interface ImportOutcome {

        data class Imported(
            val days: Int,
            val records: Int,
            val duplicatesSkipped: Int,
            val invalidRecordsSkipped: Int,
            val configApplied: Boolean,
        ) : ImportOutcome

        data class Rejected(
            val message: String,
            /** First offending line in the file, when the format has line numbers. */
            val line: Int? = null,
        ) : ImportOutcome
    }

    // ------------------------------------------------------------------ export

    /** File name the PWA's export uses: `thuocla_YYYY-MM-DD.csv`. */
    fun csvFileName(): String = "thuocla_${LocalDate.now(clock)}.csv"

    /** File name the PWA's backup uses: `thuocla_backup_YYYY-MM-DD.json`. */
    fun jsonFileName(): String = "thuocla_backup_${LocalDate.now(clock)}.json"

    /**
     * The CSV text the PWA's `exportCSV()` would write, or null when there is nothing to export.
     *
     * Notes have CR/LF folded to spaces so one cigarette stays on one row: the PWA's own CSV writer
     * would split such a row in two, and no reader could tell the halves apart.
     */
    suspend fun exportCsv(): String? {
        val byDay = allRecords().groupBy { it.dateKey }.toSortedMap()
        if (byDay.isEmpty()) return null

        val out = StringBuilder(CSV_HEADER).append('\n')
        byDay.forEach { (dateKey, rows) ->
            val ordered = rows.sortedBy { it.minuteOfDay }
            ordered.forEachIndexed { index, row ->
                val gap = if (index == 0) "" else (row.minuteOfDay - ordered[index - 1].minuteOfDay).toString()
                out.append('"').append(csvField(dateKey)).append('"').append(',')
                out.append('"').append(formatHhMm(row.minuteOfDay)).append('"').append(',')
                out.append('"').append(csvField(Triggers.byIdx(row.triggerIdx)?.name ?: "")).append('"').append(',')
                out.append('"').append(csvField(row.note?.replace(CRLF, " ") ?: "")).append('"').append(',')
                out.append('"').append(gap).append('"').append(',')
                out.append('"').append(index + 1).append('"').append('\n')
            }
        }
        return out.toString()
    }

    /** The JSON text the PWA's `backupPayload()` would write, or null when there is nothing to export. */
    suspend fun exportJson(): String? {
        val byDay = allRecords().groupBy { it.dateKey }.toSortedMap()
        if (byDay.isEmpty()) return null

        val data = JSONObject()
        byDay.forEach { (dateKey, rows) ->
            val array = JSONArray()
            rows.sortedBy { it.minuteOfDay }.forEach { row ->
                array.put(
                    JSONObject().apply {
                        put("time", localIsoTime(dateKey, row.minuteOfDay))
                        row.triggerIdx?.let { put("trigger", it) }
                        row.note?.takeIf { it.isNotBlank() }?.let { put("note", it) }
                    },
                )
            }
            data.put(dateKey, array)
        }

        val config = repository.getConfig()
        val payload = JSONObject().apply {
            put("app", "thuocla-tracker")
            put("format", 1)
            put("exportedAt", Instant.now(clock).toString())
            put(
                "config",
                JSONObject().apply {
                    put("goal", config.goal)
                    put("intervalGoal", config.intervalGoal)
                    put("pricePerPack", config.pricePerPack)
                    put("cigsPerPack", config.cigsPerPack)
                },
            )
            put("data", data)
        }
        // The PWA writes JSON.stringify(payload, null, 1); matching the indent keeps diffs readable.
        return payload.toString(1)
    }

    // ------------------------------------------------------------------ import

    /**
     * Imports a backup JSON. [replace] true is THAY THẾ (wipe first), false is GỘP.
     *
     * The whole file is validated before the first write, so a malformed backup leaves the store
     * exactly as it was (spec scenario "File không phải bản sao hợp lệ").
     */
    suspend fun importJson(json: String, replace: Boolean): ImportOutcome {
        val parsed = parseBackup(json) ?: return ImportOutcome.Rejected(NO_DATA_MESSAGE)
        return writeParsed(parsed, replace)
    }

    /** Imports a six-column CSV exported by the PWA; one bad row aborts the whole file. */
    suspend fun importCsv(csv: String): ImportOutcome {
        val rows = when (val parsed = parseCsv(csv)) {
            is CsvParse.Ok -> parsed.rows
            is CsvParse.Bad -> return ImportOutcome.Rejected(parsed.message, parsed.line)
        }
        if (rows.isEmpty()) return ImportOutcome.Rejected("File CSV không có dòng dữ liệu", 1)
        return writeParsed(ParsedBackup(rows, null, invalidRecords = 0), replace = false)
    }

    // -------------------------------------------------------------- internals

    private data class ParsedBackup(
        val records: List<RecordEntity>,
        val config: TrackerConfig?,
        val invalidRecords: Int,
    )

    private suspend fun writeParsed(parsed: ParsedBackup, replace: Boolean): ImportOutcome {
        // Read the current config before opening the transaction; a merge needs it as the base.
        val current = repository.getConfig()
        val mergedConfig = parsed.config?.let { file ->
            TrackerConfig(
                goal = file.goal.takeIf { it > 0 } ?: current.goal,
                intervalGoal = file.intervalGoal.takeIf { it > 0 } ?: current.intervalGoal,
                pricePerPack = file.pricePerPack.takeIf { it > 0 } ?: current.pricePerPack,
                cigsPerPack = file.cigsPerPack.takeIf { it > 0 } ?: current.cigsPerPack,
                // The backup format is the PWA's, and it carries only the four numeric settings — no
                // theme. Importing must not silently reset the user's chosen mode, so the device's
                // current one is kept rather than defaulted.
                themeMode = current.themeMode,
            ).normalized()
        }

        var inserted = 0
        var duplicates = 0
        db.withTransaction {
            if (replace) db.recordDao().deleteAll()
            // insertAll is INSERT OR IGNORE: existing minutes survive, which is GỘP's "first wins".
            val ids = db.recordDao().insertAll(parsed.records)
            inserted = ids.count { it != INSERT_IGNORED }
            duplicates = ids.count { it == INSERT_IGNORED }
            if (mergedConfig != null) {
                db.configDao().upsert(
                    ConfigEntity(
                        goal = mergedConfig.goal,
                        intervalGoal = mergedConfig.intervalGoal,
                        pricePerPack = mergedConfig.pricePerPack,
                        cigsPerPack = mergedConfig.cigsPerPack,
                        themeMode = mergedConfig.themeMode.name,
                    ),
                )
            }
        }

        return ImportOutcome.Imported(
            days = parsed.records.map { it.dateKey }.toSet().size,
            records = inserted,
            duplicatesSkipped = duplicates,
            invalidRecordsSkipped = parsed.invalidRecords,
            configApplied = mergedConfig != null,
        )
    }

    /** Null when the text is not a usable backup at all. */
    private fun parseBackup(json: String): ParsedBackup? {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            return null
        }
        val data = root.optJSONObject("data") ?: return null
        if (data.length() == 0) return null

        var invalid = 0
        val records = mutableListOf<RecordEntity>()
        for (dateKey in data.keys().asSequence().toList().sorted()) {
            val day = data.optJSONArray(dateKey)
            if (!isRealDateKey(dateKey) || day == null) {
                invalid += day?.length() ?: 1
                continue
            }
            // mergeDayRecords: first record for a minute wins, within the file and against the store.
            val seen = mutableSetOf<Int>()
            for (index in 0 until day.length()) {
                val row = day.optJSONObject(index)
                if (row == null) {
                    invalid++
                    continue
                }
                val minute = parseBackupTime(row.optString("time", "")) ?: run {
                    invalid++
                    -1
                }
                if (minute < 0) continue
                if (!seen.add(minute)) {
                    invalid++
                    continue
                }
                records += RecordEntity(
                    dateKey = dateKey,
                    minuteOfDay = minute,
                    triggerIdx = row.optInt("trigger", -1).takeIf { it in 0 until Triggers.ALL.size },
                    note = row.optString("note", "").takeIf { it.isNotBlank() },
                )
            }
        }
        if (records.isEmpty()) return null

        return ParsedBackup(records, parseBackupConfig(root.optJSONObject("config")), invalid)
    }

    private fun parseBackupConfig(config: JSONObject?): TrackerConfig? {
        config ?: return null
        return TrackerConfig(
            goal = config.optInt("goal", 0),
            intervalGoal = config.optInt("intervalGoal", 0),
            pricePerPack = config.optLong("pricePerPack", 0L),
            cigsPerPack = config.optInt("cigsPerPack", 0),
        )
    }

    private sealed interface CsvParse {
        class Ok(val rows: List<RecordEntity>) : CsvParse
        class Bad(val message: String, val line: Int?) : CsvParse
    }

    /**
     * Parses the PWA's CSV. Gap and ordinal columns are read for position but deliberately ignored:
     * they are derived numbers, and a file that lies about them must still import correctly.
     */
    private fun parseCsv(csv: String): CsvParse {
        val text = csv.removePrefix(UTF8_BOM)
        if (text.isBlank()) return CsvParse.Bad("File CSV rỗng", null)

        val rows = splitCsvRows(text)
        val header = rows.firstOrNull() ?: return CsvParse.Bad("File CSV rỗng", null)
        if (header.line != 1 || header.fields.map { it.trim() } != CSV_HEADER_FIELDS) {
            return CsvParse.Bad("Header CSV không đúng định dạng", header.line)
        }

        val records = mutableListOf<RecordEntity>()
        for (row in rows.drop(1)) {
            if (row.fields.size == 1 && row.fields[0].isBlank()) continue
            if (row.fields.size != CSV_HEADER_FIELDS.size) {
                return CsvParse.Bad("Sai số cột (cần 6)", row.line)
            }
            val dateKey = row.fields[0].trim()
            val time = row.fields[1].trim()
            val reason = row.fields[2].trim()
            val note = row.fields[3].trim()

            if (!isRealDateKey(dateKey)) return CsvParse.Bad("Ngày không hợp lệ: $dateKey", row.line)
            val minute = parseHhMm(time) ?: return CsvParse.Bad("Giờ không hợp lệ: $time", row.line)

            records += RecordEntity(
                dateKey = dateKey,
                minuteOfDay = minute,
                triggerIdx = Triggers.ALL.firstOrNull { it.name == reason }?.idx,
                note = note.takeIf { it.isNotBlank() },
            )
        }

        val deduped = mutableListOf<RecordEntity>()
        val seen = mutableSetOf<String>()
        for (row in records) {
            if (seen.add("${row.dateKey}-${row.minuteOfDay}")) deduped += row
        }
        return CsvParse.Ok(deduped)
    }

    private data class CsvRow(val line: Int, val fields: List<String>)

    /**
     * RFC 4180 row splitter: quoted fields keep commas and newlines, `""` is a literal quote. The
     * first row is the header, so [CsvRow.line] counts from 1.
     */
    private fun splitCsvRows(text: String): List<CsvRow> {
        val rows = mutableListOf<CsvRow>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var line = 1
        var rowStartLine = 1
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' -> {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }

                inQuotes -> {
                    if (c == '\n') line++
                    field.append(c)
                }

                c == '"' -> inQuotes = true
                c == ',' -> {
                    fields += field.toString()
                    field.clear()
                }

                c == '\r' -> Unit
                c == '\n' -> {
                    fields += field.toString()
                    field.clear()
                    rows += CsvRow(rowStartLine, fields.toList())
                    fields.clear()
                    line++
                    rowStartLine = line
                }

                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || fields.isNotEmpty()) {
            fields += field.toString()
            rows += CsvRow(rowStartLine, fields.toList())
        }
        return rows
    }

    /** "06:55" or "6:55" to minutes; null when the clock face is impossible. */
    private fun parseHhMm(raw: String): Int? {
        val parts = raw.trim().split(':')
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
    }

    /**
     * Reads a backup `time` value in either form:
     * - local ISO without a zone (`2026-11-02T06:55:00`) — what this app exports;
     * - an instant with `Z` or an offset (`2026-11-01T23:55:00.000Z`) — what the PWA exports.
     *
     * Only the time of day is kept; the record stays under the day key it was filed under, which is
     * how the PWA renders it too (`formatTime` reads the instant in local time).
     */
    private fun parseBackupTime(raw: String): Int? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        if ('T' !in value) return parseHhMm(value)

        // OffsetDateTime covers both "…Z" and "…+07:00"; LocalDateTime covers a bare local stamp.
        val zoned = runCatching { OffsetDateTime.parse(value) }.getOrNull()
        if (zoned != null) {
            val local = zoned.atZoneSameInstant(clock.zone).toLocalTime()
            return local.hour * 60 + local.minute
        }
        val plain = runCatching { LocalDateTime.parse(value) }.getOrNull()
        if (plain != null) return plain.hour * 60 + plain.minute
        return null
    }

    private fun isRealDateKey(dateKey: String): Boolean =
        DATE_KEY_REGEX.matches(dateKey) &&
            runCatching { LocalDate.parse(dateKey, DateTimeFormatter.ISO_LOCAL_DATE) }.isSuccess

    private fun localIsoTime(dateKey: String, minuteOfDay: Int): String {
        val date = LocalDate.parse(dateKey, DateTimeFormatter.ISO_LOCAL_DATE)
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        return LocalDateTime.of(date, time).format(LOCAL_ISO_SECONDS)
    }

    private fun formatHhMm(minuteOfDay: Int): String =
        "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    private fun csvField(value: String): String = value.replace("\"", "\"\"")

    private suspend fun allRecords(): List<RecordEntity> = db.recordDao().all()

    companion object {
        const val CSV_HEADER = "Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày"

        val CSV_HEADER_FIELDS = listOf("Ngày", "Giờ", "Lý do", "Ghi chú", "Khoảng cách (phút)", "STT trong ngày")

        const val NO_DATA_MESSAGE = "File này không có dữ liệu thuốc lá (thiếu \"data\")"

        private const val INSERT_IGNORED = -1L
        private const val UTF8_BOM = "\uFEFF"
        private val CRLF = Regex("\r\n|\r|\n")
        private val DATE_KEY_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")
        private val LOCAL_ISO_SECONDS: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }
}
