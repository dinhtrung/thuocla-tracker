package vn.dinhtrung.thuocla.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.dinhtrung.thuocla.data.db.AppDatabase
import vn.dinhtrung.thuocla.data.db.RecordEntity
import vn.dinhtrung.thuocla.domain.CigRecord
import vn.dinhtrung.thuocla.domain.TrackerConfig
import vn.dinhtrung.thuocla.domain.WriteBlock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/** tasks.md 8.1-8.5 — Data portability CSV/JSON export/import + round-trip. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataPortabilityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: TrackerRepository
    private lateinit var portability: DataPortability

    private val day1 = "2026-11-02"
    private val day2 = "2026-11-03"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TrackerRepository(db)
        portability = DataPortability(db, repo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ------------------------------------------------------- 8.1 CSV Export

    @Test
    fun exportCsv_withFiveRecords_matchesExpectedFormat() = runTest {
        // Setup: 5 records on 2026-11-02 with specific times and reasons
        // 06:55 (no reason), 08:20 (Cà phê=1), 10:15 (Stress=2), 11:10 (Sau ăn=0), 12:40 (Nhậu=3)
        repo.addRecord(day1, 415) // 06:55
        repo.addRecord(day1, 500, triggerIdx = 1) // 08:20 Cà phê
        repo.addRecord(day1, 615, triggerIdx = 2) // 10:15 Stress
        repo.addRecord(day1, 670, triggerIdx = 0) // 11:10 Sau ăn
        repo.addRecord(day1, 760, triggerIdx = 3) // 12:40 Nhậu

        val csv = portability.exportCsv()

        val lines = csv.trim().split("\n")
        assertEquals(6, lines.size) // 1 header + 5 data

        // Header
        assertEquals("Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày", lines[0])

        // Row 1: 06:55, no reason, no gap, STT=1
        assertTrue(lines[1].startsWith("$day1,06:55,,,"))
        assertTrue(lines[1].endsWith(",1"))

        // Row 2: 08:20, Cà phê, gap=85, STT=2
        assertTrue(lines[2].contains(",08:20,Cà phê,,"))
        assertTrue(lines[2].contains(",85,"))
        assertTrue(lines[2].endsWith(",2"))

        // Row 3: 10:15, Stress, gap=115, STT=3
        assertTrue(lines[3].contains(",10:15,Stress,,"))
        assertTrue(lines[3].contains(",115,"))
        assertTrue(lines[3].endsWith(",3"))

        // Row 4: 11:10, Sau ăn, gap=55, STT=4
        assertTrue(lines[4].contains(",11:10,Sau ăn,,"))
        assertTrue(lines[4].contains(",55,"))
        assertTrue(lines[4].endsWith(",4"))

        // Row 5: 12:40, Nhậu, gap=90, STT=5
        assertTrue(lines[5].contains(",12:40,Nhậu,,"))
        assertTrue(lines[5].contains(",90,"))
        assertTrue(lines[5].endsWith(",5"))
    }


    @Test
    fun exportCsv_multipleDays_ordersByDateThenMinute() = runTest {
        repo.addRecord(day2, 600) // 10:00 day2
        repo.addRecord(day1, 720) // 12:00 day1

        val csv = portability.exportCsv()
        val lines = csv.trim().split("\n")

        assertEquals(3, lines.size)
        // day1 should come first
        assertTrue(lines[1].startsWith("$day1,12:00"))
        assertTrue(lines[2].startsWith("$day2,10:00"))
    }

    // ------------------------------------------------------- 8.2 JSON Export

    @Test
    fun exportJson_containsExpectedStructure() = runTest {
        repo.addRecord(day1, 415, triggerIdx = 1, note = "test note")
        repo.addRecord(day1, 500, triggerIdx = 2)
        repo.saveConfig(TrackerConfig(goal = 12, intervalGoal = 45, pricePerPack = 30000L, cigsPerPack = 20))

        val jsonStr = portability.exportJson()
        val json = Gson().fromJson(jsonStr, com.google.gson.JsonObject::class.java)

        assertEquals("thuocla-tracker", json.get("app").getAsString())
        assertEquals(1, json.get("format").getAsInt())
        assertNotNull(json.get("exportedAt"))
        assertNotNull(json.get("config"))
        assertNotNull(json.get("data"))

        val config = json.get("config").asJsonObject
        assertEquals(12, config.get("goal").getAsInt())
        assertEquals(45, config.get("intervalGoal").getAsInt())
        assertEquals(30000, config.get("pricePerPack").getAsLong())
        assertEquals(20, config.get("cigsPerPack").getAsInt())

        val data = json.get("data").asJsonObject
        assertTrue(data.has(day1))
        val dayArray = data.get(day1).asJsonArray
        assertEquals(2, dayArray.size())

        val first = dayArray[0].asJsonObject
        assertEquals("06:55", first.get("time").getAsString())
        assertEquals(1, first.get("trigger").getAsInt())
        assertEquals("test note", first.get("note").getAsString())

        val second = dayArray[1].asJsonObject
        assertEquals("08:20", second.get("time").getAsString())
        assertEquals(2, second.get("trigger").getAsInt())
        assertFalse(second.has("note"))
    }

    @Test
    fun exportJson_emptyDatabase_stillValidStructure() = runTest {
        val jsonStr = portability.exportJson()
        val json = Gson().fromJson(jsonStr, com.google.gson.JsonObject::class.java)

        assertEquals("thuocla-tracker", json.get("app").getAsString())
        assertEquals(1, json.get("format").getAsInt())
        assertNotNull(json.get("config"))
        assertNotNull(json.get("data"))
        assertEquals(0, json.get("data").asJsonObject.size())
    }

    // ------------------------------------------------------- 8.3 JSON Import (REPLACE)

    @Test
    fun importJson_replaceMode_clearsExistingAndLoadsFile() = runTest {
        // Existing data
        repo.addRecord(day1, 415, triggerIdx = 1)
        repo.saveConfig(TrackerConfig(goal = 10))

        // Import JSON with different data
        val importJson = """
            {
              "app": "thuocla-tracker",
              "format": 1,
              "exportedAt": 1234567890,
              "config": {"goal": 8, "intervalGoal": 60, "pricePerPack": 25000, "cigsPerPack": 20},
              "data": {
                "$day2": [{"time": "07:30", "trigger": 0}]
              }
            }
        """.trimIndent()

        val inputStream = ByteArrayInputStream(importJson.toByteArray(StandardCharsets.UTF_8))
        val result = portability.importJson(inputStream, replace = true)

        assertTrue(result is DataPortability.ImportResult.Success)
        val success = result as DataPortability.ImportResult.Success
        assertEquals(1, success.recordsAdded)
        assertEquals(1, success.daysAdded)
        assertTrue(success.configUpdated)

        // Old data gone
        assertTrue(repo.day(day1).isEmpty())
        // New data present
        val day2Records = repo.day(day2)
        assertEquals(1, day2Records.size)
        assertEquals(450, day2Records[0].minuteOfDay) // 07:30
        assertEquals(0, day2Records[0].triggerIdx)
        // Config from file
        assertEquals(8, repo.getConfig().goal)
    }

    @Test
    fun importJson_mergeMode_keepsExistingAndAddsNew() = runTest {
        // Existing data
        repo.addRecord(day1, 415, triggerIdx = 1)

        // Import JSON with overlapping and new data
        val importJson = """
            {
              "app": "thuocla-tracker",
              "format": 1,
              "exportedAt": 1234567890,
              "config": {"goal": 8, "intervalGoal": 60, "pricePerPack": 25000, "cigsPerPack": 20},
              "data": {
                "$day1": [{"time": "06:55", "trigger": 2}, {"time": "08:20"}],
                "$day2": [{"time": "10:00"}]
              }
            }
        """.trimIndent()

        val inputStream = ByteArrayInputStream(importJson.toByteArray(StandardCharsets.UTF_8))
        val result = portability.importJson(inputStream, replace = false)

        assertTrue(result is DataPortability.ImportResult.Success)
        val success = result as DataPortability.ImportResult.Success
        // 3 new records (06:55 with trigger 2, 08:20, 10:00) - but 06:55 might conflict with existing if same minute
        // Wait, 415 = 06:55, so the first record in file is same minute -> duplicate
        assertEquals(2, success.recordsAdded) // 08:20 and 10:00
        assertEquals(1, success.duplicatesSkipped) // 06:55
        assertTrue(success.configUpdated)

        // Old record still there
        assertEquals(1, repo.day(day1).first().triggerIdx)
        // New records added
        assertEquals(2, repo.day(day1).size) // 06:55 (old) + 08:20 (new)
        assertEquals(1, repo.day(day2).size)
        // Config from file wins
        assertEquals(8, repo.getConfig().goal)
    }

    @Test
    fun importJson_invalidJson_returnsFailure() = runTest {
        val inputStream = ByteArrayInputStream("not json".toByteArray(StandardCharsets.UTF_8))
        val result = portability.importJson(inputStream, replace = true)

        assertTrue(result is DataPortability.ImportResult.Failure)
        val failure = result as DataPortability.ImportResult.Failure
        assertTrue(failure.message.contains("JSON"))
    }

    @Test
    fun importJson_missingDataKey_returnsFailure() = runTest {
        val importJson = """
            {"app": "x", "format": 1, "config": {}, "data": "not-an-object"}
        """.trimIndent()

        val inputStream = ByteArrayInputStream(importJson.toByteArray(StandardCharsets.UTF_8))
        val result = portability.importJson(inputStream, replace = true)

        assertTrue(result is DataPortability.ImportResult.Failure)
        val failure = result as DataPortability.ImportResult.Failure
        assertTrue(failure.message.contains("data"))
    }

    @Test
    fun importJson_invalidDateKey_skipsThatDay() = runTest {
        val importJson = """
            {
              "data": {
                "not-a-date": [{"time": "07:00"}],
                "$day1": [{"time": "08:00"}]
              }
            }
        """.trimIndent()

        val inputStream = ByteArrayInputStream(importJson.toByteArray(StandardCharsets.UTF_8))
        val result = portability.importJson(inputStream, replace = true)

        assertTrue(result is DataPortability.ImportResult.Success)
        val success = result as DataPortability.ImportResult.Success
        assertEquals(1, success.recordsAdded) // Only day1
        assertEquals(1, success.invalidRecordsSkipped) // The bad date's record
    }

    @Test
    fun importJson_invalidTime_skipsRecord() = runTest {
        val importJson = """
            {
              "data": {
                "$day1": [{"time": "25:99"}, {"time": "08:00"}]
              }
            }
        """.trimIndent()

        val inputStream = ByteArrayInputStream(importJson.toByteArray(StandardCharsets.UTF_8))
        val result = portability.importJson(inputStream, replace = true)

        assertTrue(result is DataPortability.ImportResult.Success)
        val success = result as DataPortability.ImportResult.Success
        assertEquals(1, success.recordsAdded)
        assertEquals(1, success.invalidRecordsSkipped)
    }

    @Test
    fun importJson_triggerOutOfRange_dropsTrigger() = runTest {
        val importJson = """
            {
              "data": {
                "$day1": [{"time": "08:00", "trigger": 99}, {"time": "09:00", "trigger": 3}]
              }
            }
        """.trimIndent()

        val inputStream = ByteArrayInputStream(importJson.toByteArray(StandardCharsets.UTF_8))
        val result = portability.importJson(inputStream, replace = true)

        assertTrue(result is DataPortability.ImportResult.Success)
        val dayRecords = repo.day(day1)
        assertEquals(2, dayRecords.size)
        assertNull(dayRecords[0].triggerIdx) // 99 dropped
        assertEquals(3, dayRecords[1].triggerIdx) // 3 kept
    }

    // ------------------------------------------------------- 8.4 CSV Import

    @Test
    fun importCsv_validFile_addsRecords() = runTest {
        val csv = """
            Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày
            $day1,06:55,,,"",1
            $day1,08:20,Cà phê,,85,2
            $day1,10:15,Stress,,115,3
            $day2,07:00,Sau ăn,,,1
        """.trimIndent()

        val inputStream = ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        val result = portability.importCsv(inputStream)

        assertTrue(result is DataPortability.ImportResult.Success)
        val success = result as DataPortability.ImportResult.Success
        assertEquals(4, success.recordsAdded)
        assertEquals(2, success.daysAdded)
        assertFalse(success.configUpdated)

        assertEquals(3, repo.day(day1).size)
        assertEquals(1, repo.day(day2).size)
        assertEquals(1, repo.day(day1)[1].triggerIdx) // Cà phê = 1
        assertEquals(2, repo.day(day1)[2].triggerIdx) // Stress = 2
        assertEquals(0, repo.day(day2)[0].triggerIdx) // Sau ăn = 0
    }

    @Test
    fun importCsv_duplicateMinute_inFileSkipsDuplicate() = runTest {
        val csv = """
            Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày
            $day1,06:55,,,"",1
            $day1,06:55,Cà phê,,,2
        """.trimIndent()

        val inputStream = ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        val result = portability.importCsv(inputStream)

        assertTrue(result is DataPortability.ImportResult.Success)
        val success = result as DataPortability.ImportResult.Success
        assertEquals(1, success.recordsAdded)
        assertEquals(1, success.duplicatesSkipped)
        assertEquals(1, repo.day(day1).size)
    }

    @Test
    fun importCsv_unknownReason_leavesTriggerNull() = runTest {
        val csv = """
            Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày
            $day1,06:55,Không tồn tại,,,1
        """.trimIndent()

        val inputStream = ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        val result = portability.importCsv(inputStream)

        assertTrue(result is DataPortability.ImportResult.Success)
        assertNull(repo.day(day1)[0].triggerIdx)
    }

    @Test
    fun importCsv_invalidHeader_returnsFailure() = runTest {
        val csv = """
            Wrong,Header
            $day1,06:55,,,"",1
        """.trimIndent()

        val inputStream = ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        val result = portability.importCsv(inputStream)

        assertTrue(result is DataPortability.ImportResult.Failure)
        val failure = result as DataPortability.ImportResult.Failure
        assertTrue(failure.message.contains("Header"))
        assertEquals(1, failure.errorLine)
    }

    @Test
    fun importCsv_invalidDate_returnsFailureWithLine() = runTest {
        val csv = """
            Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày
            $day1,06:55,,,"",1
            invalid-date,07:00,,,"",2
        """.trimIndent()

        val inputStream = ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        val result = portability.importCsv(inputStream)

        assertTrue(result is DataPortability.ImportResult.Failure)
        val failure = result as DataPortability.ImportResult.Failure
        assertTrue(failure.message.contains("Ngày"))
        assertEquals(3, failure.errorLine) // line 3 (header=1, data1=2, data2=3)
    }

    @Test
    fun importCsv_invalidTime_returnsFailureWithLine() = runTest {
        val csv = """
            Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày
            $day1,25:99,,,"",1
        """.trimIndent()

        val inputStream = ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        val result = portability.importCsv(inputStream)

        assertTrue(result is DataPortability.ImportResult.Failure)
        val failure = result as DataPortability.ImportResult.Failure
        assertTrue(failure.message.contains("Giờ"))
        assertEquals(2, failure.errorLine)
    }

    @Test
    fun importCsv_wrongColumnCount_returnsFailure() = runTest {
        val csv = """
            Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày
            $day1,06:55,,,1
        """.trimIndent()

        val inputStream = ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        val result = portability.importCsv(inputStream)

        assertTrue(result is DataPortability.ImportResult.Failure)
        val failure = result as DataPortability.ImportResult.Failure
        assertTrue(failure.message.contains("cột"))
    }

    @Test
    fun importCsv_abortsOnError_noPartialImport() = runTest {
        val csv = """
            Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày
            $day1,06:55,,,"",1
            $day1,07:00,,,"",2
            $day1,25:99,,,"",3
            $day1,08:00,,,"",4
        """.trimIndent()

        val inputStream = ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        val result = portability.importCsv(inputStream)

        assertTrue(result is DataPortability.ImportResult.Failure)
        // No records should be imported due to validation failure
        assertTrue(repo.day(day1).isEmpty())
    }

    // ------------------------------------------------------- 8.5 Round-trip

    @Test
    fun roundTrip_jsonExportImport_preservesData() = runTest {
        // Setup original data
        repo.addRecord(day1, 415, triggerIdx = 1, note = "note 1")
        repo.addRecord(day1, 500, triggerIdx = 2, note = "note 2")
        repo.addRecord(day2, 480, triggerIdx = 0)
        repo.saveConfig(TrackerConfig(goal = 12, intervalGoal = 50, pricePerPack = 30000L, cigsPerPack = 25))

        // Export
        val jsonStr = portability.exportJson()

        // Clear and import
        portability.deleteAllRecords()
        val inputStream = ByteArrayInputStream(jsonStr.toByteArray(StandardCharsets.UTF_8))
        val result = portability.importJson(inputStream, replace = true)

        assertTrue(result is DataPortability.ImportResult.Success)

        // Verify
        assertEquals(2, repo.day(day1).size)
        assertEquals(1, repo.day(day2).size)
        assertEquals(1, repo.day(day1)[0].triggerIdx)
        assertEquals("note 1", repo.day(day1)[0].note)
        assertEquals(2, repo.day(day1)[1].triggerIdx)
        assertEquals("note 2", repo.day(day1)[1].note)
        assertEquals(0, repo.day(day2)[0].triggerIdx)
        assertEquals(12, repo.getConfig().goal)
        assertEquals(50, repo.getConfig().intervalGoal)
        assertEquals(30000L, repo.getConfig().pricePerPack)
        assertEquals(25, repo.getConfig().cigsPerPack)
    }
}