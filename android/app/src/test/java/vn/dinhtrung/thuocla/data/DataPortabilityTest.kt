package vn.dinhtrung.thuocla.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
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
import vn.dinhtrung.thuocla.domain.CigRecord
import vn.dinhtrung.thuocla.domain.TrackerConfig

/**
 * tasks.md 8.1 – 8.5: the PWA's CSV and JSON formats, both import modes, the normalize rules, and the
 * round-trip that must not lose a cigarette.
 *
 * The 8.1 numbers (`06:55`, gaps `85,115,50,90`) are the spec's own example, so the test asserts the
 * literal text rather than a recomputed equivalent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataPortabilityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: TrackerRepository
    private lateinit var portability: DataPortability

    private val zone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    private val day = "2026-11-02"

    /** 06:55, 08:20, 10:15, 11:05, 12:35 — gaps 85, 115, 50, 90 as in the spec scenario. */
    private val specMinutes = listOf(415, 500, 615, 665, 755)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TrackerRepository(db)
        portability = DataPortability(
            db = db,
            repository = repo,
            clock = Clock.fixed(Instant.parse("2026-11-02T07:12:00Z"), zone),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ------------------------------------------------------------------- 8.1

    @Test
    fun exportCsv_writesTheSixColumnsAndTheSpecGaps() = runTest {
        seedSpecDay()

        val csv = portability.exportCsv()
        assertNotNull(csv)
        val lines = csv!!.trim().split("\n")

        assertEquals("header plus five cigarettes", 6, lines.size)
        assertEquals(
            "Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày",
            lines[0].trim(),
        )
        assertEquals("\"2026-11-02\",\"06:55\",\"Sau ăn\",\"\",\"\",\"1\"", lines[1])
        assertEquals("\"2026-11-02\",\"08:20\",\"Cà phê\",\"\",\"85\",\"2\"", lines[2])
        assertEquals("\"2026-11-02\",\"10:15\",\"Stress\",\"\",\"115\",\"3\"", lines[3])
        assertEquals("\"2026-11-02\",\"11:05\",\"Nhậu\",\"\",\"50\",\"4\"", lines[4])
        assertEquals("\"2026-11-02\",\"12:35\",\"Buồn\",\"\",\"90\",\"5\"", lines[5])
    }

    @Test
    fun exportCsv_reasonHasNoEmojiAndEmptyReasonStaysEmpty() = runTest {
        repo.addRecord(day, 415, triggerIdx = null)
        repo.addRecord(day, 500, triggerIdx = 1)

        val lines = portability.exportCsv()!!.trim().split("\n")

        assertTrue("no emoji in the reason column", lines.none { it.contains("☕") })
        assertEquals("\"2026-11-02\",\"06:55\",\"\",\"\",\"\",\"1\"", lines[1])
        assertEquals("\"2026-11-02\",\"08:20\",\"Cà phê\",\"\",\"85\",\"2\"", lines[2])
    }

    @Test
    fun exportCsv_isNullWhenThereIsNothingToExport() = runTest {
        assertNull(portability.exportCsv())
    }

    // ------------------------------------------------------------------- 8.2

    @Test
    fun exportJson_hasTheBackupEnvelopeAndFiveRecordsForTheDay() = runTest {
        seedSpecDay()

        val root = JSONObject(portability.exportJson()!!)

        assertEquals("thuocla-tracker", root.getString("app"))
        assertEquals(1, root.getInt("format"))
        assertTrue("exportedAt is an ISO instant", root.getString("exportedAt").endsWith("Z"))
        assertEquals(10, root.getJSONObject("config").getInt("goal"))
        assertEquals(5, root.getJSONObject("data").getJSONArray(day).length())
    }

    @Test
    fun exportJson_recordsCarryTimeTriggerAndNoteOnlyWhenPresent() = runTest {
        repo.addRecord(day, 415, triggerIdx = 0, note = "sau bữa sáng")
        repo.addRecord(day, 500, triggerIdx = null)

        val records = JSONObject(portability.exportJson()!!).getJSONObject("data").getJSONArray(day)

        val first = records.getJSONObject(0)
        assertEquals("2026-11-02T06:55:00", first.getString("time"))
        assertEquals(0, first.getInt("trigger"))
        assertEquals("sau bữa sáng", first.getString("note"))

        val second = records.getJSONObject(1)
        assertEquals("2026-11-02T08:20:00", second.getString("time"))
        assertFalse(second.has("trigger"))
        assertFalse(second.has("note"))
    }

    @Test
    fun exportJson_fileNameMatchesThePwaPattern() {
        assertEquals("thuocla_backup_2026-11-02.json", portability.jsonFileName())
        assertEquals("thuocla_2026-11-02.csv", portability.csvFileName())
    }

    // ------------------------------------------------------------------- 8.3

    @Test
    fun importJsonReplace_replacesEverythingAndTakesTheFileConfig() = runTest {
        seedSpecDay()
        repo.saveConfig(TrackerConfig(goal = 12, intervalGoal = 45, pricePerPack = 30_000, cigsPerPack = 25))

        val outcome = portability.importJson(BACKUP_40_DAYS, replace = true)

        assertTrue(outcome is DataPortability.ImportOutcome.Imported)
        val imported = outcome as DataPortability.ImportOutcome.Imported
        assertEquals(0, imported.duplicatesSkipped)
        assertEquals(40, imported.days)
        assertTrue(imported.configApplied)
        assertEquals("the old five cigarettes are gone", 40, db.recordDao().all().size)
        assertEquals("file config wins", 8, repo.getConfig().goal)
    }

    @Test
    fun importJsonMerge_addsFortyDaysToThreeExistingDays() = runTest {
        repo.addRecord("2026-10-01", 600)
        repo.addRecord("2026-10-02", 610)
        repo.addRecord("2026-10-03", 620)

        val outcome = portability.importJson(BACKUP_40_DAYS, replace = false)

        assertTrue(outcome is DataPortability.ImportOutcome.Imported)
        assertEquals("3 existing + 40 file days", 43, repo.distinctDayCount())
        assertEquals("file config wins on merge too", 8, repo.getConfig().goal)
    }

    @Test
    fun importJsonMerge_keepsTheRecordAlreadyInTheStoreForTheSameMinute() = runTest {
        repo.addRecord(day, 415, triggerIdx = 7, note = "in the store")

        portability.importJson(BACKUP_ONE_DAY, replace = false)

        val rows = repo.day(day)
        assertEquals(2, rows.size)
        assertEquals("existing row survives", 7, rows.first { it.minuteOfDay == 415 }.triggerIdx)
        assertEquals("file row is added", 1, rows.first { it.minuteOfDay == 500 }.triggerIdx)
    }

    @Test
    fun importJsonReplace_keepsTheFileRowWhenTheSameMinuteCollides() = runTest {
        repo.addRecord(day, 415, triggerIdx = 7)

        portability.importJson(BACKUP_ONE_DAY, replace = true)

        assertNull("the file row for 06:55 carries no reason", repo.day(day).first { it.minuteOfDay == 415 }.triggerIdx)
    }

    @Test
    fun importJson_normalizesBadKeysTimesAndTriggers() = runTest {
        val outcome = portability.importJson(BACKUP_WITH_BAD_ROWS, replace = true)

        val imported = outcome as DataPortability.ImportOutcome.Imported
        assertEquals("invalid date key + unreadable time", 2, imported.invalidRecordsSkipped)
        val rows = repo.day(day)
        assertEquals("only the two usable rows land", 2, rows.size)
        assertEquals("trigger 9 is dropped to null", null, rows.first { it.minuteOfDay == 415 }.triggerIdx)
        assertEquals(2, rows.first { it.minuteOfDay == 500 }.triggerIdx)
    }

    @Test
    fun importJson_withoutDataKey_isRejectedAndChangesNothing() = runTest {
        seedSpecDay()

        val outcome = portability.importJson("""{"app":"thuocla-tracker","format":1}""", replace = true)

        assertTrue(outcome is DataPortability.ImportOutcome.Rejected)
        assertEquals("the store is untouched", 5, db.recordDao().all().size)
        assertEquals(1, repo.distinctDayCount())
    }

    @Test
    fun importJson_withMalformedJson_isRejectedAndChangesNothing() = runTest {
        seedSpecDay()

        val outcome = portability.importJson("{not json", replace = true)

        assertTrue(outcome is DataPortability.ImportOutcome.Rejected)
        assertEquals(5, db.recordDao().all().size)
    }

    @Test
    fun importJson_acceptsPwaIsoInstantsAndReadsTheLocalHour() = runTest {
        // The PWA writes `new Date(...).toISOString()`: 06:55 +07:00 is 23:55Z the day before.
        portability.importJson(BACKUP_PWA_INSTANTS, replace = true)

        val rows = repo.day(day)
        assertEquals(2, rows.size)
        assertEquals(415, rows[0].minuteOfDay)
        assertEquals(500, rows[1].minuteOfDay)
    }

    // ------------------------------------------------------------------- 8.4

    @Test
    fun importCsv_mapsReasonNamesToTheirIndices() = runTest {
        val outcome = portability.importCsv(CSV_SPEC_DAY)

        assertTrue(outcome is DataPortability.ImportOutcome.Imported)
        val rows = repo.day(day)
        assertEquals(5, rows.size)
        assertEquals(listOf(1, 0, 1, 2, 0), rows.map { it.triggerIdx })
        assertEquals("gap and ordinal columns are ignored", specMinutes, rows.map { it.minuteOfDay })
    }

    @Test
    fun importCsv_ignoresLyingGapAndOrdinalColumns() = runTest {
        portability.importCsv(
            """
            Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày
            "2026-11-02","06:55","Sau ăn","","999","99"
            "2026-11-02","08:20","Cà phê","","0","1"
            """.trimIndent(),
        )

        val rows = repo.day(day)
        assertEquals(2, rows.size)
        assertEquals("the store keeps only real minutes", specMinutes.take(2), rows.map { it.minuteOfDay })
    }

    @Test
    fun importCsv_withBadTime_abortsTheWholeFileAndNamesTheLine() = runTest {
        seedSpecDay()

        val outcome = portability.importCsv(CSV_WITH_BAD_TIME)

        assertTrue(outcome is DataPortability.ImportOutcome.Rejected)
        val rejected = outcome as DataPortability.ImportOutcome.Rejected
        assertEquals("the offending row is reported", 3, rejected.line)
        assertTrue("the message names the value", rejected.message.contains("25:99"))
        assertEquals("not one row from the file was written", 5, db.recordDao().all().size)
    }

    @Test
    fun importCsv_withUnknownReason_leavesTheReasonEmpty() = runTest {
        portability.importCsv(
            """
            Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày
            "2026-11-02","06:55","Không có trong danh sách","","","1"
            """.trimIndent(),
        )

        assertNull(repo.day(day).single().triggerIdx)
    }

    @Test
    fun importCsv_skipsTheDuplicateMinuteInsideTheFile() = runTest {
        portability.importCsv(
            """
            Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày
            "2026-11-02","06:55","Sau ăn","first","","1"
            "2026-11-02","06:55","Cà phê","second","","2"
            """.trimIndent(),
        )

        val row = repo.day(day).single()
        assertEquals("first row wins", "first", row.note)
        assertEquals(0, row.triggerIdx)
    }

    @Test
    fun importCsv_withWrongHeader_isRejected() = runTest {
        val outcome = portability.importCsv("Ngày,Giờ,Lý do\n\"2026-11-02\",\"06:55\",\"Sau ăn\"")

        assertTrue(outcome is DataPortability.ImportOutcome.Rejected)
        assertEquals(1, (outcome as DataPortability.ImportOutcome.Rejected).line)
    }

    // ------------------------------------------------------------------- 8.5

    @Test
    fun roundTrip_exportJsonThenWipeThenImport_keepsDaysRecordsAndReasons() = runTest {
        seedSpecDay()
        repo.addRecord("2026-10-31", 1000, triggerIdx = 3)
        repo.addRecord("2026-11-01", 1010, triggerIdx = 4, note = "buổi tối")
        repo.saveConfig(TrackerConfig(goal = 12, intervalGoal = 45, pricePerPack = 30_000, cigsPerPack = 25))

        val before = snapshot()
        val backup = portability.exportJson()!!

        db.recordDao().deleteAll()
        assertEquals(0, db.recordDao().all().size)

        val outcome = portability.importJson(backup, replace = true)

        assertTrue(outcome is DataPortability.ImportOutcome.Imported)
        assertEquals(before, snapshot())
        assertEquals("reasons survive the round-trip", listOf(0, 1, 2, 3, 4), repo.day(day).map { it.triggerIdx })
        assertEquals(12, repo.getConfig().goal)
    }

    @Test
    fun roundTrip_exportCsvThenImport_keepsDaysRecordsAndReasons() = runTest {
        seedSpecDay()
        repo.addRecord("2026-10-31", 1000, triggerIdx = 3)

        val before = snapshot()
        val csv = portability.exportCsv()!!

        db.recordDao().deleteAll()

        val outcome = portability.importCsv(csv)

        assertTrue(outcome is DataPortability.ImportOutcome.Imported)
        assertEquals(before, snapshot())
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun seedSpecDay() {
        specMinutes.forEachIndexed { index, minute ->
            repo.addRecord(day, minute, triggerIdx = index)
        }
    }

    /** Every row as "dateKey minute trigger note", ordered — the comparison unit for round-trips. */
    private suspend fun snapshot(): List<String> =
        db.recordDao().all().map { "${it.dateKey} ${it.minuteOfDay} ${it.triggerIdx} ${it.note}" }

    private companion object {

        /** The spec's 8.4 scenario: reasons Cà phê, Sau ăn, Cà phê, Stress, Sau ăn -> 1, 0, 1, 2, 0. */
        const val CSV_SPEC_DAY = """Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày
"2026-11-02","06:55","Cà phê","","","1"
"2026-11-02","08:20","Sau ăn","","85","2"
"2026-11-02","10:15","Cà phê","","115","3"
"2026-11-02","11:05","Stress","","50","4"
"2026-11-02","12:35","Sau ăn","","90","5""""

        const val CSV_WITH_BAD_TIME = """Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày
"2026-11-02","06:55","Sau ăn","","","1"
"2026-11-02","25:99","Cà phê","","","2"
"2026-11-02","08:20","Stress","","","3""""

        const val BACKUP_ONE_DAY = """{"app":"thuocla-tracker","format":1,"exportedAt":"2026-11-02T07:00:00.000Z",
"config":{"goal":8,"intervalGoal":45,"pricePerPack":30000,"cigsPerPack":25},
"data":{"2026-11-02":[{"time":"2026-11-02T06:55:00"},{"time":"2026-11-02T08:20:00","trigger":1}]}}"""

        /** 40 distinct days, one cigarette each, 2026-08-01 .. 2026-09-09 (clear of the store's 10-01..10-03). */
        val BACKUP_40_DAYS: String = buildString {
            append("""{"app":"thuocla-tracker","format":1,"exportedAt":"2026-11-02T07:00:00.000Z",""")
            append(""""config":{"goal":8,"intervalGoal":45,"pricePerPack":30000,"cigsPerPack":25},"data":{""")
            repeat(40) { i ->
                if (i > 0) append(',')
                val date = java.time.LocalDate.of(2026, 8, 1).plusDays(i.toLong())
                append("\"$date\":[{\"time\":\"${date}T09:00:00\",\"trigger\":2}]")
            }
            append("}}")
        }

        const val BACKUP_WITH_BAD_ROWS = """{"app":"thuocla-tracker","format":1,"data":{
"2026-13-01":[{"time":"2026-11-02T09:00:00"}],
"2026-11-02":[
  {"time":"not-a-time"},
  {"time":"2026-11-02T06:55:00","trigger":9},
  {"time":"2026-11-02T08:20:00","trigger":2}
]}}"""

        /** The PWA's own shape: `time` is a UTC instant, the key is the local day. */
        const val BACKUP_PWA_INSTANTS = """{"app":"thuocla-tracker","format":1,"data":{
"2026-11-02":[
  {"time":"2026-11-01T23:55:00.000Z","trigger":0},
  {"time":"2026-11-02T01:20:00.000Z","trigger":1}
]}}"""
    }
}
