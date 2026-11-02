package vn.dinhtrung.thuocla.data

import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import vn.dinhtrung.thuocla.data.db.AppDatabase
import vn.dinhtrung.thuocla.data.db.RecordEntity
import vn.dinhtrung.thuocla.domain.CigRecord
import vn.dinhtrung.thuocla.domain.TrackerConfig
import vn.dinhtrung.thuocla.domain.TRIGGER_COUNT
import vn.dinhtrung.thuocla.domain.WriteBlock
import vn.dinhtrung.thuocla.domain.WriteResult

/**
 * Data portability: CSV and JSON backup export/import compatible with the PWA.
 *
 * PWA references (app.js):
 * - TRIGGERS array order: 0=Sau ăn, 1=Cà phê, 2=Stress, 3=Nhậu, 4=Buồn, 5=Thói quen, 6=Thèm, 7=Khác
 * - CSV header: Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày
 * - JSON backup: {app, format, exportedAt, config, data} where data is map dateKey -> records {time, trigger?, note?}
 * - normalizeBackup: validates date keys, parses time, clamps trigger to 0-7, drops bad records
 * - mergeDayRecords: combines by day, dedupes by minute keeping first
 */
class DataPortability(private val db: AppDatabase, private val repo: TrackerRepository) {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // Reason names without emojis (matching PWA's triggerName function)
    private val triggerNames = arrayOf(
        "Sau ăn",
        "Cà phê",
        "Stress",
        "Nhậu",
        "Buồn",
        "Thói quen",
        "Thèm",
        "Khác"
    )

    /**
     * Result of an import operation.
     */
    sealed interface ImportResult {
        data class Success(
            val daysAdded: Int,
            val recordsAdded: Int,
            val configUpdated: Boolean,
            val duplicatesSkipped: Int,
            val invalidRecordsSkipped: Int,
        ) : ImportResult

        data class Failure(val message: String, val errorLine: Int? = null) : ImportResult
    }

    /**
     * Exports all records to CSV format compatible with the PWA.
     * Returns the CSV content as a String.
     */
    suspend fun exportCsv(): String {
        val allRecords = db.recordDao().all()
        val recordsByDay = allRecords.groupBy { it.dateKey }.toSortedMap()

        val sb = StringBuilder()
        sb.append("Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày\n")

        recordsByDay.forEach { (dateKey, dayRecords) ->
            val sortedRecords = dayRecords.sortedBy { it.minuteOfDay }
            sortedRecords.forEachIndexed { index, record ->
                val time = minuteToTime(record.minuteOfDay)
                val reasonName = record.triggerIdx?.let { triggerNames.getOrNull(it) } ?: ""
                val note = record.note?.replace("\n", " ")?.replace(",", " ") ?: ""

                // Calculate gap from previous cigarette
                val gap = if (index == 0) "" else (record.minuteOfDay - sortedRecords[index - 1].minuteOfDay).toString()
                val stt = (index + 1).toString()

                sb.append("$dateKey,$time,$reasonName,$note,$gap,$stt\n")
            }
        }

        return sb.toString()
    }

    /**
     * Exports all records and config to JSON backup format.
     * Returns the JSON content as a String.
     */
    suspend fun exportJson(): String {
        val allRecords = db.recordDao().all()
        val config = repo.getConfig()

        val dataJson = JsonObject()
        val recordsByDay = allRecords.groupBy { it.dateKey }

        recordsByDay.forEach { (dateKey, dayRecords) ->
            val sortedRecords = dayRecords.sortedBy { it.minuteOfDay }
            val dayJsonArray = com.google.gson.JsonArray()
            sortedRecords.forEach { record ->
                val recordJson = JsonObject()
                recordJson.addProperty("time", minuteToTime(record.minuteOfDay))
                record.triggerIdx?.let { recordJson.addProperty("trigger", it) }
                record.note?.let { recordJson.addProperty("note", it) }
                dayJsonArray.add(recordJson)
            }
            dataJson.add(dateKey, dayJsonArray)
        }

        val configJson = JsonObject()
        configJson.addProperty("goal", config.goal)
        configJson.addProperty("intervalGoal", config.intervalGoal)
        configJson.addProperty("pricePerPack", config.pricePerPack)
        configJson.addProperty("cigsPerPack", config.cigsPerPack)

        val root = JsonObject()
        root.addProperty("app", "thuocla-tracker")
        root.addProperty("format", 1)
        root.addProperty("exportedAt", System.currentTimeMillis())
        root.add("config", configJson)
        root.add("data", dataJson)

        return gson.toJson(root)
    }

    /**
     * Imports JSON backup from an input stream.
     * [replace] = true for THAY THẾ (replace all), false for GỘP (merge).
     */
    suspend fun importJson(inputStream: java.io.InputStream, replace: Boolean): ImportResult {
        val reader = InputStreamReader(inputStream)
        val json: JsonElement
        try {
            json = JsonParser.parseReader(reader)
        } catch (e: Exception) {
            return ImportResult.Failure("File JSON không hợp lệ: ${e.message}")
        } finally {
            reader.close()
        }

        if (!json.isJsonObject) {
            return ImportResult.Failure("File JSON không đúng cấu trúc")
        }

        val root = json.asJsonObject
        if (!root.has("data") || !root.get("data").isJsonObject) {
            return ImportResult.Failure("Thiếu khóa 'data' hoặc sai kiểu")
        }

        // Parse config from file (wins on merge)
        val fileConfig = if (root.has("config") && root.get("config").isJsonObject) {
            val c = root.get("config").asJsonObject
            TrackerConfig(
                goal = c.get("goal")?.getAsInt() ?: TrackerConfig.DEFAULT.goal,
                intervalGoal = c.get("intervalGoal")?.getAsInt() ?: TrackerConfig.DEFAULT.intervalGoal,
                pricePerPack = c.get("pricePerPack")?.getAsLong() ?: TrackerConfig.DEFAULT.pricePerPack,
                cigsPerPack = c.get("cigsPerPack")?.getAsInt() ?: TrackerConfig.DEFAULT.cigsPerPack,
            ).normalized()
        } else {
            TrackerConfig.DEFAULT
        }

        val dataObj = root.get("data").asJsonObject

        // Normalize and collect valid records
        var recordsAdded = 0
        var duplicatesSkipped = 0
        var invalidRecordsSkipped = 0
        val daysInFile = mutableSetOf<String>()

        return db.withTransaction {
            if (replace) {
                db.recordDao().deleteAll()
                // Config will be overwritten below
            }

            dataObj.entrySet().forEach { entry ->
                val dateKey = entry.key
                if (!isValidDateKey(dateKey)) {
                    invalidRecordsSkipped += entry.value.asJsonArray.size()
                    return@forEach
                }
                daysInFile.add(dateKey)

                val recordsArray = entry.value.asJsonArray
                recordsArray.forEach { recElem ->
                    if (!recElem.isJsonObject) {
                        invalidRecordsSkipped++
                        return@forEach
                    }
                    val rec = recElem.asJsonObject

                    // Parse time (HH:mm)
                    val timeStr = rec.get("time")?.getAsString() ?: run { invalidRecordsSkipped++; return@forEach }
                    val minuteOfDay = parseTimeToMinute(timeStr) ?: run { invalidRecordsSkipped++; return@forEach }

                    // Parse trigger (0-7)
                    var triggerIdx: Int? = null
                    if (rec.has("trigger")) {
                        val t = rec.get("trigger").getAsInt()
                        if (t in 0 until TRIGGER_COUNT) {
                            triggerIdx = t
                        }
                        // Out of range -> treat as null (drop trigger)
                    }

                    // Parse note
                    val note = rec.get("note")?.getAsString()

                    // Insert (IGNORE handles duplicates)
                    val row = RecordEntity(dateKey, minuteOfDay, triggerIdx, note)
                    val result = db.recordDao().insert(row)
                    if (result == -1L) {
                        duplicatesSkipped++
                    } else {
                        recordsAdded++
                    }
                }
            }

            // Save config (file wins on merge)
            repo.saveConfig(fileConfig)

            val daysAdded = if (replace) daysInFile.size else daysInFile.size // approximation
            ImportResult.Success(
                daysAdded = daysAdded,
                recordsAdded = recordsAdded,
                configUpdated = true,
                duplicatesSkipped = duplicatesSkipped,
                invalidRecordsSkipped = invalidRecordsSkipped,
            )
        }
    }

    /**
     * Imports CSV export from an input stream.
     * Validates all rows first; if any row is invalid, aborts entire import.
     */
    suspend fun importCsv(inputStream: java.io.InputStream): ImportResult {
        val reader = InputStreamReader(inputStream)
        val lines = reader.readText().split("\n").filter { it.isNotBlank() }
        reader.close()

        if (lines.isEmpty()) {
            return ImportResult.Failure("File CSV rỗng")
        }

        // Validate header
        val header = lines[0].trim()
        if (header != "Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày") {
            return ImportResult.Failure("Header CSV không đúng định dạng", 1)
        }

        // Parse all rows first (validate)
        val parsedRows = mutableListOf<ParsedCsvRow>()
        var lineNum = 1
        for (i in 1 until lines.size) {
            lineNum++
            val line = lines[i].trim()
            if (line.isBlank()) continue

            val cols = parseCsvLine(line)
            if (cols.size != 6) {
                return ImportResult.Failure("Sai số cột (cần 6)", lineNum)
            }

            val dateKey = cols[0].trim()
            val timeStr = cols[1].trim()
            val reasonName = cols[2].trim()
            val note = cols[3].trim()
            // cols[4] is gap (ignored, we recalculate)
            // cols[5] is STT (ignored, we recalculate)

            if (!isValidDateKey(dateKey)) {
                return ImportResult.Failure("Ngày không hợp lệ: $dateKey", lineNum)
            }

            val minuteOfDay = parseTimeToMinute(timeStr) ?: return ImportResult.Failure("Giờ không hợp lệ: $timeStr", lineNum)

            var triggerIdx: Int? = null
            if (reasonName.isNotBlank()) {
                val idx = triggerNames.indexOfFirst { it == reasonName }
                if (idx >= 0) triggerIdx = idx
                // Unknown reason -> leave as null
            }

            val cleanNote = if (note.isBlank()) null else note

            parsedRows.add(ParsedCsvRow(dateKey, minuteOfDay, triggerIdx, cleanNote))
        }

        // All valid, now insert in transaction
        var recordsAdded = 0
        var duplicatesSkipped = 0

        db.withTransaction {
            parsedRows.forEach { row ->
                val entity = RecordEntity(row.dateKey, row.minuteOfDay, row.triggerIdx, row.note)
                val result = db.recordDao().insert(entity)
                if (result == -1L) {
                    duplicatesSkipped++
                } else {
                    recordsAdded++
                }
            }
        }

        return ImportResult.Success(
            daysAdded = parsedRows.map { it.dateKey }.toSet().size,
            recordsAdded = recordsAdded,
            configUpdated = false,
            duplicatesSkipped = duplicatesSkipped,
            invalidRecordsSkipped = 0,
        )
    }

    /**
     * Deletes all records (used for testing round-trip).
     */
    suspend fun deleteAllRecords() {
        db.recordDao().deleteAll()
    }

    // ---- Private helpers ----

    private data class ParsedCsvRow(
        val dateKey: String,
        val minuteOfDay: Int,
        val triggerIdx: Int?,
        val note: String?
    )

    private fun minuteToTime(minuteOfDay: Int): String {
        val h = minuteOfDay / 60
        val m = minuteOfDay % 60
        return String.format("%02d:%02d", h, m)
    }

    private fun parseTimeToMinute(timeStr: String): Int? {
        try {
            val parts = timeStr.split(":")
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            return h * 60 + m
        } catch (e: Exception) {
            return null
        }
    }

    private fun isValidDateKey(dateKey: String): Boolean {
        return try {
            LocalDate.parse(dateKey, dateFormatter)
            true
        } catch (e: DateTimeParseException) {
            false
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        // Simple CSV parser: handles quoted fields with commas
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }
}