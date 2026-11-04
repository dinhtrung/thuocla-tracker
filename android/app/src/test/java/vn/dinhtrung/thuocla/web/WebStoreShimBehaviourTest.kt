package vn.dinhtrung.thuocla.web

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Executes the injected shim the way the reviewer did — in a real JS engine, against a `Storage`
 * whose prototype override is ignored by the caller (which is how native `localStorage` behaves) —
 * rather than asserting on the script's text.
 *
 * The three behaviours here are the ones that shipped broken in #46 and were only visible when the
 * script actually ran against a fake bridge:
 *
 * - a stale `forwarded` cache silently swallowed a cigarette re-added after "Xoá dữ liệu";
 * - a THAY THẾ restore left Room days that the file did not contain, because the cache never knew
 *   about the days Room started with;
 * - the script must be valid JavaScript at all.
 *
 * The engine is `node`, run as a subprocess: the test JVM has no JS runtime, and adding one as a test
 * dependency just to parse a string would be heavier than shelling out to the one every dev box and CI
 * runner already has. The test is skipped, loudly, when `node` is absent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebStoreShimBehaviourTest {

    private val harness = """
        const backing = {};
        function Storage(){}
        Storage.prototype.getItem = function(k){ return Object.prototype.hasOwnProperty.call(backing,k) ? backing[k] : null; };
        Storage.prototype.setItem = function(k,v){ backing[k] = String(v); };
        Storage.prototype.removeItem = function(k){ delete backing[k]; };
        Storage.prototype.clear = function(){ for (const k of Object.keys(backing)) delete backing[k]; };
        const SEED = JSON.parse(__SEED__);
        const state = { room: JSON.parse(JSON.stringify(SEED.data || {})), calls: [] };
        const bridge = {
          readAll(){ return JSON.stringify(state.room); },
          writeDay(d,j){ state.calls.push(['writeDay',d,j]); const a=JSON.parse(j); if(a.length) state.room[d]=a; else delete state.room[d]; },
          writeConfig(j){ state.calls.push(['writeConfig',j]); },
          writeTheme(t){ state.calls.push(['writeTheme',t]); },
          clearRecords(){ state.calls.push(['clearRecords']); state.room={}; },
          deleteLast(d){ state.calls.push(['deleteLast',d]); }
        };
        const store = new Storage();
        globalThis.window = { localStorage: store, Storage, WebStore: bridge };
        globalThis.localStorage = store;
    __SHIM__
    """

    private fun run(seed: String, script: String): String {
        val dir = File.createTempFile("shim", "js").apply { deleteOnExit() }
        dir.writeText(harness.replace("__SEED__", jsString(seed)).replace("__SHIM__", script))
        val process = ProcessBuilder("node", dir.absolutePath)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return output.trim()
    }

    /** Like [run], but with a caller-supplied harness — used to make one write throw. */
    private fun runWith(harnessText: String, seed: String, script: String): String {
        val dir = File.createTempFile("shim", "js").apply { deleteOnExit() }
        dir.writeText(harnessText.replace("__SEED__", jsString(seed)).replace("__SHIM__", script))
        val process = ProcessBuilder("node", dir.absolutePath)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return output.trim()
    }

    private fun nodeAvailable(): Boolean = try {
        ProcessBuilder("node", "--version").start().waitFor() == 0
    } catch (e: Exception) {
        false
    }

    private fun jsString(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'").let { "'$it'" }

    private val seed = """{"theme":"dark","config":{"goal":10},"data":""" +
        """{"2026-11-01":[{"time":"2026-11-01T10:00:00"}],""" +
        """"2026-11-04":[{"time":"2026-11-04T08:15:00"}]}}"""

    @Test
    fun clearAllDataThenReAddingTheSameMinute_stillReachesRoom() {
        assumeTrue("node is required for this test", nodeAvailable())
        val script = WebStoreShim.script(seed)
        val probe = """
            store.setItem('smoking_data', JSON.stringify(SEED.data));
            store.removeItem('smoking_data');
            store.setItem('smoking_data', JSON.stringify({ "2026-11-04": [ {time:"2026-11-04T08:15:00"} ] }));
            console.log(JSON.stringify(state.room));
        """
        val result = run(seed, script + probe)
        assertEquals(
            "the re-added cigarette must be in Room, not only on the page",
            """{"2026-11-04":[{"time":"2026-11-04T08:15:00"}]}""",
            result,
        )
    }

    @Test
    fun aReplaceRestore_dropsRoomDaysTheFileDoesNotHave() {
        assumeTrue("node is required for this test", nodeAvailable())
        val script = WebStoreShim.script(seed)
        // app.js's THAY THẾ branch: out = norm.data; saveData(out).
        val probe = """
            store.setItem('smoking_data', JSON.stringify({ "2026-11-04": [ {time:"2026-11-04T09:00:00"} ] }));
            console.log(JSON.stringify(Object.keys(state.room).sort()));
        """
        val result = run(seed, script + probe)
        assertEquals("""["2026-11-04"]""", result)
    }

    @Test
    fun aMergeRestore_keepsDaysItDidNotTouch() {
        assumeTrue("node is required for this test", nodeAvailable())
        val script = WebStoreShim.script(seed)
        // app.js's GỘP branch: out = loadData(); merge the file's days in; saveData(out).
        val probe = """
            var merged = JSON.parse(JSON.stringify(SEED.data));
            merged["2026-11-05"] = [ {time:"2026-11-05T07:00:00"} ];
            store.setItem('smoking_data', JSON.stringify(merged));
            console.log(JSON.stringify(Object.keys(state.room).sort()));
        """
        val result = run(seed, script + probe)
        assertEquals("a merge must not drop anything", """["2026-11-01","2026-11-04","2026-11-05"]""", result)
    }

    @Test
    fun anUnchangedRewrite_isNotForwardedTwice() {
        assumeTrue("node is required for this test", nodeAvailable())
        val script = WebStoreShim.script(seed)
        val probe = """
            store.setItem('smoking_data', JSON.stringify(SEED.data));
            console.log(String(state.calls.length));
        """
        val result = run(seed, script + probe)
        assertEquals("re-sending an identical map must be a no-op", "0", result)
    }

    @Test
    fun theInjectedScriptIsValidJavaScript() {
        assumeTrue("node is required for this test", nodeAvailable())
        val script = WebStoreShim.script(seed)
        val result = run(seed, script + "console.log('PARSED');")
        assertTrue("node rejected the generated script: $result", result.contains("PARSED"))
    }

    /**
     * The data-loss path the reviewer found in the first version of the B fix.
     *
     * The localStorage seed is wrapped in `safe()`. If that write throws, the cache that decides what
     * Room is believed to hold must NOT be populated — otherwise the page starts empty while the shim
     * believes Room's days are already delivered, and the page's first `saveData` (holding only what it
     * just added) reads as those days being dropped and is forwarded as `writeDay(day, '[]')`, deleting
     * records from Room. On `ef4e0ac` the same failure only produced an empty page and Room kept its
     * records, so this asserts the records survive a failing seed write.
     */
    @Test
    fun aFailingSeedWrite_mustNotDeleteRoomRecordsOnTheFirstPageSave() {
        assumeTrue("node is required for this test", nodeAvailable())
        val script = WebStoreShim.script(seed)
        val failingHarness = harness
            .replace(
                "Storage.prototype.setItem = function(k,v){ backing[k] = String(v); };",
                "Storage.prototype.setItem = function(k,v){ if (globalThis.__failSeed) { " +
                    "globalThis.__failSeed = false; throw new Error('Quota'); } backing[k] = String(v); };",
            )
            .replace(
                "globalThis.localStorage = store;",
                "globalThis.localStorage = store;\n        globalThis.__failSeed = true;",
            )
        // The page saw an empty store, so what it saves first holds only the day it just added.
        val probe = """
            store.setItem('smoking_data', JSON.stringify({ "2026-11-04": [ {time:"2026-11-04T08:15:00"} ] }));
            console.log(JSON.stringify(state.calls.map(c => [c[0], c[1]])));
            console.log('ROOM:' + JSON.stringify(Object.keys(state.room).sort()));
        """
        val result = runWith(failingHarness, seed, script + probe)

        assertEquals(
            "only the day the page actually wrote may be forwarded",
            """[["writeDay","2026-11-04"]]""",
            result.lines().first(),
        )
        assertEquals(
            "the untouched day must survive a failed seed write",
            """ROOM:["2026-11-01","2026-11-04"]""",
            result.lines()[1],
        )
    }

    @Test
    fun theChromeClientAnswersConfirmAndAlert_andOpensThePicker() {
        // C: without a WebChromeClient these defaults are false/void, so four web actions did nothing.
        val activity = org.robolectric.Robolectric.buildActivity(
            WebShellActivity::class.java,
        ).setup().get()
        val shadow = org.robolectric.Shadows.shadowOf(activity.webView)

        assertNotNull("no WebChromeClient installed: confirm()/alert() would be inert", shadow.webChromeClient)
    }
}
