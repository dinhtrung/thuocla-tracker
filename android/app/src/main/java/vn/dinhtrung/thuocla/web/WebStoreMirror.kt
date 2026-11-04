package vn.dinhtrung.thuocla.web

import android.util.Log
import java.time.Clock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import vn.dinhtrung.thuocla.data.RecordJson
import vn.dinhtrung.thuocla.data.TrackerRepository
import vn.dinhtrung.thuocla.data.db.RecordEntity
import vn.dinhtrung.thuocla.domain.CigRecord
import vn.dinhtrung.thuocla.domain.ThemeMode
import vn.dinhtrung.thuocla.domain.TrackerConfig

/**
 * Bridges Room and the web app's `localStorage` (tasks 3.1–3.3).
 *
 * **Room is the source of truth** (design D3). The web UI was written against `localStorage`, so the
 * shell keeps that illusion: on every page load the shim seeds `localStorage` from this snapshot, and
 * every write the page makes comes back here to be persisted. Wiping WebView storage therefore loses
 * nothing — the next load re-seeds it from Room.
 *
 * All Room access is serial on a single background thread. `@JavascriptInterface` methods are called
 * on the WebView's JavaBridge thread, never the UI thread, and Room refuses a main-thread query; a
 * single-thread executor also makes the write-through ordering the order the page performed it.
 */
class WebStoreMirror(
    private val repository: TrackerRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "web-store-mirror").apply { isDaemon = true }
    }

    /** Runs [block] on the mirror thread and waits for it. Used by the bridge and by tests. */
    fun <T> await(block: suspend () -> T): T = executor.submit<T> { runBlocking { block() } }.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

    /** Fire-and-forget variant for the shim's write-through path: the page must never block. */
    fun enqueue(block: suspend () -> Unit) {
        executor.execute {
            try {
                runBlocking { block() }
            } catch (e: Exception) {
                // A failed persist must not break the UI (task 3.2). The page keeps its value in
                // localStorage, so the user sees their action; the next load re-seeds from Room and
                // the discrepancy is visible rather than silent-crashed.
                Log.e(TAG, "web store write-through failed", e)
            }
        }
    }

    /**
     * The state the shim seeds `localStorage` with, as one JSON object:
     * `{"data": {...}, "config": {...}, "theme": "dark"}`.
     *
     * `data` is omitted when there are no records, matching `loadData()`'s `|| {}` fallback: an empty
     * map would be written to `localStorage` and look like a restore, while a missing key reads as a
     * fresh install. `config` and `theme` are always present, because both have real values even
     * before the first cigarette (goal 10, dark).
     */
    fun readSeed(): String {
        val records = await { repository.allRecords() }
        val config = await { repository.getConfig() }

        val seed = JSONObject()
        if (records.isNotEmpty()) seed.put("data", dataJson(records))
        seed.put("config", RecordJson.config(config))
        seed.put("theme", webTheme(config.themeMode))
        return seed.toString()
    }

    /** `{dateKey: [{time, trigger?, note?}]}`, ordered by day then minute — the shape `data` holds. */
    private fun dataJson(records: List<CigRecord>): JSONObject {
        val data = JSONObject()
        records.groupBy { it.dateKey }.toSortedMap().forEach { (dateKey, rows) ->
            data.put(dateKey, RecordJson.dayRecords(dateKey, rows.map { Triple(it.minuteOfDay, it.triggerIdx, it.note) }))
        }
        return data
    }

    /**
     * The web app has two themes; `SYSTEM` has no web equivalent, so it seeds the same value a fresh
     * web install reads (`getTheme()` defaults to dark).
     */
    private fun webTheme(mode: ThemeMode): String = if (mode == ThemeMode.LIGHT) "light" else "dark"
    /**
     * Replaces one day with the page's array for it.
     *
     * The web UI edits a day as a whole (`setDayData`), including deleting a cigarette from the
     * middle, so a per-row diff would have to infer deletes. Replacing inside one transaction is the
     * same observable result and cannot leave a half-applied day. Invalid minutes, duplicate minutes
     * within the payload, and out-of-range triggers are dropped exactly as the backup importer drops
     * them — a bad page value must not create a row the native store would refuse.
     */
    fun writeDay(dateKey: String, recordsJson: String): Boolean = await {
        if (!RecordJson.isRealDateKey(dateKey)) return@await false
        // Unreadable JSON is refused, not treated as an empty day: the web UI sends `[]` when it
        // means "no cigarettes left", so treating a parse failure the same way would silently delete a
        // day's records on a bug in the page.
        val rows = parseDay(recordsJson) ?: return@await false
        repository.replaceDay(dateKey, rows)
        true
    }

    /** Persists the page's four config values. Empty/too-small fields fall back to defaults. */
    fun writeConfig(configJson: String): Boolean = await {
        val parsed = try {
            JSONObject(configJson)
        } catch (e: Exception) {
            Log.e(TAG, "web store config is not JSON", e)
            return@await false
        }
        repository.saveConfig(
            TrackerConfig(
                goal = parsed.optInt("goal", 0),
                intervalGoal = parsed.optInt("intervalGoal", 0),
                pricePerPack = parsed.optLong("pricePerPack", 0L),
                cigsPerPack = parsed.optInt("cigsPerPack", 0),
                // The web app has no theme setting in this object; keep the native choice.
                themeMode = repository.getConfig().themeMode,
            ),
        )
        true
    }

    /**
     * Drops the latest cigarette of [dateKey] — the page's `removeLastRecord`/`undoFromDay`, which
     * pops the last element of that day's array. Pairs with [writeDay]: a pop would otherwise arrive
     * as a whole-day rewrite, which is correct but sends every remaining record on each undo.
     */
    fun deleteLast(dateKey: String): Boolean = await {
        if (!RecordJson.isRealDateKey(dateKey)) return@await false
        repository.deleteLast(dateKey) != null
    }

    /** `localStorage.removeItem(DB_KEY)` / `clear()` — clears the log, matching `clearAllData()`. */
    fun clearRecords(): Boolean = await {
        repository.clearAllRecords()
        true
    }

    /** `setTheme` on the page. `light` maps to LIGHT; anything else to DARK, as `getTheme()` does. */
    fun writeTheme(theme: String): Boolean = await {
        val current = repository.getConfig()
        repository.saveConfig(current.copy(themeMode = if (theme == "light") ThemeMode.LIGHT else ThemeMode.DARK))
        true
    }

    /**
     * Every stored record as `{dateKey: [{time, trigger?, note?}]}`, for the bridge's `readAll`.
     *
     * The same JSON the seed carries, so a page that re-reads never disagrees with what it was seeded
     * with.
     */
    fun snapshot(): String = await { dataJson(repository.allRecords()).toString() }

    fun shutdown() {
        executor.shutdown()
    }

    /** The payload's rows, or null when it is not an array at all (nothing may be written then). */
    private fun parseDay(recordsJson: String): List<RecordEntity>? {
        val array = try {
            JSONArray(recordsJson)
        } catch (e: Exception) {
            Log.e(TAG, "web store day is not a JSON array", e)
            return null
        }
        val seen = mutableSetOf<Int>()
        val rows = mutableListOf<RecordEntity>()
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val minute = RecordJson.parseRecordTime(row.optString("time", ""), clock.zone) ?: continue
            if (minute !in MINUTE_RANGE || !seen.add(minute)) continue
            rows += RecordEntity(
                dateKey = "", // stamped by the repository, which also validates the minute
                minuteOfDay = minute,
                triggerIdx = row.optInt("trigger", -1).takeIf { it in 0 until TRIGGER_COUNT },
                note = row.optString("note", "").takeIf { it.isNotBlank() },
            )
        }
        return rows
    }

    private companion object {
        const val TAG = "WebStoreMirror"
        const val TIMEOUT_SECONDS = 30L
        val MINUTE_RANGE = 0..1439
        const val TRIGGER_COUNT = 8
    }
}
