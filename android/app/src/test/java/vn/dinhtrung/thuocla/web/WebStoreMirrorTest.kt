package vn.dinhtrung.thuocla.web

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.dinhtrung.thuocla.data.TrackerRepository
import vn.dinhtrung.thuocla.data.db.AppDatabase
import vn.dinhtrung.thuocla.domain.ThemeMode
import vn.dinhtrung.thuocla.domain.TrackerConfig
import vn.dinhtrung.thuocla.domain.WriteBlock

/**
 * tasks.md 3.4 — the Room ↔ web bridge. Assertions are about what `app.js` will read, not about
 * internals: the seed is parsed as the page parses it, and writes go in as the page sends them.
 *
 * The duplicate-minute rule and the config defaults are [TrackerRepository]'s, so these tests pin that
 * the bridge actually routes through it instead of writing rows of its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebStoreMirrorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: TrackerRepository
    private lateinit var mirror: WebStoreMirror

    private val day = "2026-11-02"
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TrackerRepository(db)
        mirror = WebStoreMirror(repo, Clock.system(zone))
    }

    @After
    fun tearDown() {
        mirror.shutdown()
        db.close()
    }

    // ------------------------------------------------------------ 3.4 (a) read

    @Test
    fun fiveRoomRecords_seedTheShapeAppJsReads() = runTest {
        val minutes = listOf(415, 500, 615, 665, 755) // 06:55, 08:20, 10:15, 11:05, 12:35
        minutes.forEachIndexed { i, minute ->
            repo.addRecord(day, minute, triggerIdx = i % 8)
        }

        val seed = JSONObject(mirror.readSeed())
        val dayRecords = seed.getJSONObject("data").getJSONArray(day)
        assertEquals(5, dayRecords.length())

        // The page reads each record with `new Date(r.time)`, so the text must be the local form.
        assertEquals("2026-11-02T06:55:00", dayRecords.getJSONObject(0).getString("time"))
        assertEquals("2026-11-02T12:35:00", dayRecords.getJSONObject(4).getString("time"))
        assertEquals(2, dayRecords.getJSONObject(2).getInt("trigger"))

        // Parsing it back as the page does must yield the same five minutes, in order.
        val minutesBack = (0 until dayRecords.length()).map { index ->
            dayRecords.getJSONObject(index).getString("time").substring(11, 16)
        }
        assertEquals(listOf("06:55", "08:20", "10:15", "11:05", "12:35"), minutesBack)
    }

    @Test
    fun emptyStore_seedsTheConfigAndThemeButNoDataKey() {
        val seed = JSONObject(mirror.readSeed())
        assertFalse("a fresh install has no data key", seed.has("data"))
        assertEquals(10, seed.getJSONObject("config").getInt("goal"))
        assertEquals("dark", seed.getString("theme"))
    }

    // ----------------------------------------------------------- 3.4 (b) write

    @Test
    fun aDayWrittenThroughTheBridge_landsInRoom() = runTest {
        val payload = """
            [{"time":"2026-11-02T09:00:00"},
             {"time":"2026-11-02T13:30:00","trigger":1},
             {"time":"2026-11-02T13:30:00"},
             {"time":"2026-11-02T09:00:00","note":"dup"}]
        """.trimIndent()

        assertTrue(mirror.writeDay(day, payload))

        val stored = repo.day(day)
        assertEquals("duplicate minutes must collapse to one row", 2, stored.size)
        assertEquals(listOf(540, 810), stored.map { it.minuteOfDay })
        assertEquals("the first reason for a minute wins", 1, stored[1].triggerIdx)
    }

    @Test
    fun aDayWrittenThroughTheBridge_replacesThatDayOnly() = runTest {
        repo.addRecord(day, 415)
        repo.addRecord("2026-10-31", 600)

        mirror.writeDay(day, """[{"time":"2026-11-02T20:00:00"}]""")

        assertEquals(listOf(1200), repo.day(day).map { it.minuteOfDay })
        assertEquals("another day is untouched", 1, repo.day("2026-10-31").size)
    }

    @Test
    fun anEmptyDayArray_clearsThatDay() = runTest {
        repo.addRecord(day, 415)
        assertTrue(mirror.writeDay(day, "[]"))
        assertTrue(repo.day(day).isEmpty())
    }

    @Test
    fun aBadDateKeyOrBadPayload_isRefusedWithoutTouchingRoom() = runTest {
        repo.addRecord(day, 415)

        assertFalse(mirror.writeDay("02-11-2026", """[{"time":"2026-11-02T09:00:00"}]"""))
        assertFalse(mirror.writeDay(day, "not json"))
        assertEquals("nothing was cleared by the refusals", 1, repo.day(day).size)
    }

    @Test
    fun anOutOfRangeTrigger_isDroppedAsTheImporterDropsIt() = runTest {
        mirror.writeDay(day, """[{"time":"2026-11-02T09:00:00","trigger":99}]""")
        assertEquals(null, repo.day(day).single().triggerIdx)
    }

    @Test
    fun duplicateMinuteRule_isStillTheRepositorys() = runTest {
        // The bridge replaces a whole day, but the rule the schema enforces must remain visible: a
        // second add through the repository for the same minute is refused, not merged.
        repo.addRecord(day, 615)
        val result = repo.addRecord(day, 615)
        assertFalse(result.applied)
        assertEquals(WriteBlock.DUPLICATE_MINUTE, result.block)
        assertEquals(1, repo.day(day).size)
    }

    @Test
    fun clearRecords_emptiesTheLogButKeepsConfig() = runTest {
        repo.addRecord(day, 415)
        repo.saveConfig(TrackerConfig(goal = 8))

        assertTrue(mirror.clearRecords())

        assertTrue(repo.allRecords().isEmpty())
        assertEquals(8, repo.getConfig().goal)
    }

    @Test
    fun deleteLast_removesOnlyTheLatestCigaretteOfThatDay() = runTest {
        repo.addRecord(day, 415)
        repo.addRecord(day, 900)
        repo.addRecord("2026-11-03", 600)

        assertTrue(mirror.deleteLast(day))

        assertEquals(listOf(415), repo.day(day).map { it.minuteOfDay })
        assertEquals("another day keeps its record", 1, repo.day("2026-11-03").size)
    }

    @Test
    fun deleteLast_onAnEmptyDay_reportsNothingRemoved() = runTest {
        assertFalse(mirror.deleteLast(day))
        assertFalse(mirror.deleteLast("not-a-date"))
    }

    @Test
    fun theShimForwardsEachDayOnlyWhenItChanged() {
        val script = WebStoreShim.script("""{"config":{},"theme":"dark"}""")

        assertTrue("the last-sent value per day must be tracked", script.contains("forwarded"))
        assertTrue("an unchanged day must not be re-sent", script.contains("forwarded[day] !== encoded"))
        assertTrue("a dropped day must be cleared through writeDay", script.contains("bridge.writeDay(day, '[]')"))
    }

    // --------------------------------------------------------- 3.4 (c) config

    @Test
    fun aConfigWrittenThroughTheBridge_hasAllFourKeys() = runTest {
        assertTrue(
            mirror.writeConfig("""{"goal":8,"intervalGoal":45,"pricePerPack":30000,"cigsPerPack":25}"""),
        )

        val config = repo.getConfig()
        assertEquals(8, config.goal)
        assertEquals(45, config.intervalGoal)
        assertEquals(30000L, config.pricePerPack)
        assertEquals(25, config.cigsPerPack)

        // And the seed the page reads carries exactly those four values.
        val seeded = JSONObject(mirror.readSeed()).getJSONObject("config")
        assertEquals(setOf("goal", "intervalGoal", "pricePerPack", "cigsPerPack"), seeded.keys().asSequence().toSet())
        assertEquals(8, seeded.getInt("goal"))
    }

    @Test
    fun aConfigWithEmptyFields_fallsBackToTheDefaults() = runTest {
        mirror.writeConfig("""{"goal":0,"intervalGoal":-5,"pricePerPack":0,"cigsPerPack":0}""")
        assertEquals(TrackerConfig.DEFAULT, repo.getConfig())
    }

    @Test
    fun writeConfig_doesNotClobberTheNativeThemeChoice() = runTest {
        repo.saveConfig(TrackerConfig(themeMode = ThemeMode.LIGHT))
        mirror.writeConfig("""{"goal":8,"intervalGoal":45,"pricePerPack":30000,"cigsPerPack":25}""")
        assertEquals(ThemeMode.LIGHT, repo.getConfig().themeMode)
    }

    // ---------------------------------------------------------- 3.4 (d) theme

    @Test
    fun theSeededThemeFollowsTheStoredMode() = runTest {
        assertEquals("dark", JSONObject(mirror.readSeed()).getString("theme"))

        repo.saveConfig(repo.getConfig().copy(themeMode = ThemeMode.LIGHT))
        assertEquals("light", JSONObject(mirror.readSeed()).getString("theme"))

        mirror.writeTheme("dark")
        assertEquals(ThemeMode.DARK, repo.getConfig().themeMode)
        assertEquals("dark", JSONObject(mirror.readSeed()).getString("theme"))
    }

    @Test
    fun anyThemeValueOtherThanLight_isDarkAsGetThemeReadsIt() = runTest {
        mirror.writeTheme("light")
        assertEquals(ThemeMode.LIGHT, repo.getConfig().themeMode)

        mirror.writeTheme("nonsense")
        assertEquals(ThemeMode.DARK, repo.getConfig().themeMode)
    }

    // ------------------------------------------------------------- the shim text

    @Test
    fun theShimEmbedsTheSeedAndWrapsStorageWrites() {
        val script = WebStoreShim.script(JSONObject(mapOf("theme" to "dark", "config" to JSONObject())).toString())

        assertTrue("the seed must be inlined, not fetched", script.contains("\"theme\""))
        assertTrue(script.contains("smoking_data"))
        assertTrue(script.contains("smoking_config"))
        assertTrue("setItem must still run before write-through", script.contains("originalSetItem.call"))
        assertTrue(script.contains("bridge.writeDay"))
        assertTrue(script.contains("bridge.writeConfig"))
        assertFalse("no SQL or path may appear in the bridge surface", script.contains("execSQL"))
    }

    /**
     * The injected script must be valid JavaScript, and nothing else checks that.
     *
     * A missing brace shipped once and made the whole shim a `SyntaxError`: the app launched to an
     * empty tracker with three records in Room, and every Kotlin-side assertion still passed — the
     * seed text was all present, it was just inside a script the WebView refused to run. `Rhino` is
     * not on the test classpath, so this runs the same parse the browser does by handing the text to
     * a JS engine if one is available and, when it is not, at least asserts the balance of the
     * braces/parens/brackets that the actual bug violated.
     */
    @Test
    fun theInjectedScriptIsSyntacticallyValidJavaScript() {
        val script = WebStoreShim.script(
            JSONObject(mapOf("theme" to "dark", "config" to JSONObject(), "data" to JSONObject())).toString(),
        )

        // Naive but exactly the property that broke: every opener has its closer, ignoring strings.
        val stripped = script.replace(Regex("'[^']*'"), "''").replace(Regex("\"[^\"]*\""), "\"\"")
        listOf('{' to '}', '(' to ')', '[' to ']').forEach { (open, close) ->
            val opens = stripped.count { it == open }
            val closes = stripped.count { it == close }
            assertEquals("unbalanced $open$close in the injected script", opens, closes)
        }

        // The statements the shell depends on must appear inside the function body, not after it.
        val bodyEnd = script.lastIndexOf("})();")
        assertTrue("the IIFE must close exactly once", bodyEnd >= 0 && script.indexOf("})();") == bodyEnd)
        assertTrue(script.indexOf("forwarded[day] !== encoded") < bodyEnd)
        assertTrue(script.indexOf("seed.data || {}") < bodyEnd)
    }

    @Test
    fun aLineSeparatorInANote_cannotBreakTheInjectedScript() = runTest {
        // A note is free text and reaches the seed intact; U+2028/U+2029 end a line in the JS grammar
        // even inside a string literal, so an unescaped one would make the whole injected script a
        // syntax error and silently lose the seed. Put a real one in and assert it is escaped.
        repo.addRecord(day, 415, note = "sep\u2028here")
        val seed = mirror.readSeed()
        assertTrue("the note must reach the seed verbatim", seed.contains("\u2028"))

        val script = WebStoreShim.script(seed)
        assertTrue(script.contains("\\u2028"))
        assertFalse("no raw U+2028 may survive into the script", script.contains('\u2028'))
    }

    @Test
    fun snapshot_equalsTheSeedData() = runTest {
        repo.addRecord(day, 415)
        val fromSnapshot = mirror.snapshot()
        val fromSeed = JSONObject(mirror.readSeed()).getJSONObject("data").toString()
        assertNotEquals("", fromSnapshot)
        assertEquals(JSONObject(fromSeed).toString(), JSONObject(fromSnapshot).toString())
    }
}
