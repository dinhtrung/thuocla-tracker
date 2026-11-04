package vn.dinhtrung.thuocla.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject
import vn.dinhtrung.thuocla.domain.TrackerConfig

/**
 * The record/config JSON shape `app.js` reads and writes, in one place.
 *
 * `DataPortability` (backup files) and `WebStoreMirror` (the WebView store bridge) must agree
 * exactly on this shape, otherwise a record written back by the web UI or restored from a file would
 * disagree with one exported by the other. Two independent implementations of "the same" format is
 * how those two paths drift; so the shape lives here once and both callers use it.
 *
 * Shape, from `app.js` v1.5.2:
 * - `data[dateKey]` = `[{time, trigger?, note?}]` — `time` is a local ISO date-time without a zone
 *   suffix (`2026-11-02T06:55:00`), which is what makes the PWA read back the hour the user saw
 *   (design decision #2: wall clock, not UTC);
 * - `config` = `{goal, intervalGoal, pricePerPack, cigsPerPack}` — the four keys `loadConfig()` uses.
 */
internal object RecordJson {

    private val DATE_KEY_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")

    /** `2026-11-02T06:55:00` — the exact text the PWA's export writes and its readers accept. */
    private val LOCAL_ISO_SECONDS: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    fun localIsoTime(dateKey: String, minuteOfDay: Int): String {
        val date = LocalDate.parse(dateKey, DateTimeFormatter.ISO_LOCAL_DATE)
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        return LocalDateTime.of(date, time).format(LOCAL_ISO_SECONDS)
    }

    /**
     * Minute-of-day for a `time` value from the web or from a file.
     *
     * Accepts both forms the PWA produces: a bare local stamp (`2026-11-02T06:55:00`, ours) and an
     * instant with a zone (`2026-11-02T00:55:00.000Z`, the browser's `toISOString()`), the latter
     * converted to [zone] exactly as the PWA's own `new Date(...)`/`formatTime` pair does. Returns
     * null for anything unreadable — callers count it as an invalid record rather than guessing.
     */
    fun parseRecordTime(raw: String, zone: ZoneId): Int? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        if ('T' !in value) return parseHhMm(value)

        // OffsetDateTime covers both "…Z" and "…+07:00"; LocalDateTime covers a bare local stamp.
        val zoned = runCatching { OffsetDateTime.parse(value) }.getOrNull()
        if (zoned != null) {
            val local = zoned.atZoneSameInstant(zone).toLocalTime()
            return local.hour * 60 + local.minute
        }
        val plain = runCatching { LocalDateTime.parse(value) }.getOrNull()
        if (plain != null) return plain.hour * 60 + plain.minute
        return null
    }

    fun isRealDateKey(dateKey: String): Boolean =
        DATE_KEY_REGEX.matches(dateKey) &&
            runCatching { LocalDate.parse(dateKey, DateTimeFormatter.ISO_LOCAL_DATE) }.isSuccess

    /**
     * One day's records, ordered by minute — the array `data[dateKey]` holds.
     *
     * Takes the three fields rather than a row type so both callers, which hold different records
     * (`DataPortability` the table row, the web mirror the domain record), funnel through one
     * implementation and cannot drift into emitting two different shapes.
     */
    fun dayRecords(dateKey: String, rows: List<Triple<Int, Int?, String?>>): JSONArray {
        val array = JSONArray()
        rows.sortedBy { it.first }.forEach { (minute, triggerIdx, note) ->
            array.put(
                JSONObject().apply {
                    put("time", localIsoTime(dateKey, minute))
                    triggerIdx?.let { put("trigger", it) }
                    note?.takeIf { it.isNotBlank() }?.let { put("note", it) }
                },
            )
        }
        return array
    }

    /** The four keys `app.js` `loadConfig()` reads. The native-only theme is not part of this shape. */
    fun config(config: TrackerConfig): JSONObject = JSONObject().apply {
        put("goal", config.goal)
        put("intervalGoal", config.intervalGoal)
        put("pricePerPack", config.pricePerPack)
        put("cigsPerPack", config.cigsPerPack)
    }

    private fun parseHhMm(raw: String): Int? {
        val parts = raw.split(':')
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }
}
