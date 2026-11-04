package vn.dinhtrung.thuocla.web

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.dinhtrung.thuocla.data.BackupSharer
import vn.dinhtrung.thuocla.data.DataPortability
import vn.dinhtrung.thuocla.data.TrackerRepository
import vn.dinhtrung.thuocla.data.db.AppDatabase

/**
 * tasks.md 4.1–4.3 — the page's file actions, routed through native.
 *
 * The point of these tests is *parity*, not plumbing: task 4.3 requires the shell to keep the web
 * build's formats and rules, so every assertion here is made against the same `DataPortability` the
 * native screens use, and the CSV/JSON text is checked for the shape the spec names (six columns;
 * `{app, format, exportedAt, config, data}`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebFileBridgeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: TrackerRepository
    private lateinit var portability: DataPortability
    private lateinit var bridge: WebFileBridge

    private val day = "2026-11-02"
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")

    /** Every result the bridge sent to the page, in order. */
    private val reported = mutableListOf<JSONObject>()

    private var createdDocument: Pair<String, String>? = null
    private var openDocumentAsked = 0
    private var shareAsked = 0
    private var shareAllowed = true

    /** How many times the bridge asked the page to adopt the store after an import. */
    private var resynced = 0

    /**
     * [FileProvider] caches each authority's parsed path strategy in a static map. Robolectric gives
     * every test method a fresh cache directory, so without this the second method resolves against
     * the first one's directory and throws "Failed to find configured root". Same Robolectric-only
     * fix `ShareBackupTest` already applies; no production behaviour is involved.
     */
    @Before
    fun clearProviderPathCache() {
        val field = androidx.core.content.FileProvider::class.java
            .getDeclaredField("sCache")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (field.get(null) as MutableMap<String, *>).clear()
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TrackerRepository(db)
        portability = DataPortability(db, repo, Clock.system(zone))
        val sharer = BackupSharer(portability, File(context.cacheDir, BackupSharer.DIRECTORY_NAME))

        bridge = WebFileBridge(
            portability = portability,
            sharer = sharer,
            openCreateDocument = { text, fileName -> createdDocument = text to fileName },
            openOpenDocument = { openDocumentAsked++ },
            openShare = { shareAsked++; shareAllowed },
            notifyPage = { json -> reported += JSONObject(json) },
            afterImport = { resynced++ },
        )
    }

    @After
    fun tearDown() {
        bridge.shutdown()
        db.close()
    }

    @Test
    fun exportCsv_handsTheSafDialogSixColumnTextNamedLikeThePage() = runTest {
        listOf(415, 500, 615).forEach { repo.addRecord(day, it) }

        assertTrue(bridge.exportCsv())

        val (text, fileName) = createdDocument!!
        // The name comes from `DataPortability` and carries today's date, so assert the contract
        // (same name the page's own export would use) rather than a literal.
        assertEquals(portability.csvFileName(), fileName)
        val lines = text.trim().split('\n')
        // The header is unquoted in the PWA too (`app.js:1410` starts `rows` as a plain array; only
        // the data rows get quotes), so this pins the exact text the web app produces.
        assertEquals("Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày", lines.first())
        assertEquals("header + 3 rows", 4, lines.size)
        assertEquals(6, lines[1].split(',').size)
    }

    @Test
    fun exportBackup_handsTheSafDialogThePwaSchemaNamedLikeThePage() = runTest {
        repo.addRecord(day, 415)

        assertTrue(bridge.exportBackup())

        val (text, fileName) = createdDocument!!
        assertEquals(portability.jsonFileName(), fileName)
        val payload = JSONObject(text)
        // The four keys the PWA's `restoreFromFile`/`normalizeBackup` require.
        assertEquals("thuocla-tracker", payload.getString("app"))
        assertEquals(1, payload.getInt("format"))
        assertTrue(payload.has("exportedAt"))
        assertTrue(payload.has("config"))
        assertEquals(1, payload.getJSONObject("data").getJSONArray(day).length())
    }

    @Test
    fun exportingAnEmptyStore_reportsTheSameMessageAndDoesNotOpenTheDialog() = runTest {
        assertFalse(bridge.exportCsv())
        assertEquals("Chưa có dữ liệu để xuất!", reported.last().getString("message"))
        assertEquals("no dialog may open for an empty export", null, createdDocument)

        assertFalse(bridge.exportBackup())
        assertEquals("Chưa có dữ liệu để sao lưu!", reported.last().getString("message"))
    }

    @Test
    fun pickRestoreFile_asksForAJsonDocument() {
        assertTrue(bridge.pickRestoreFile())
        assertEquals(1, openDocumentAsked)
    }

    @Test
    fun importingWithReplace_wipesFirst_andWithMergeKeepsWhatWasThere() = runTest {
        repo.addRecord("2026-11-01", 600)

        val backup = JSONObject().apply {
            put("app", "thuocla-tracker")
            put("format", 1)
            put("exportedAt", "2026-11-02T00:00:00Z")
            put("config", JSONObject().put("goal", 9))
            put(
                "data",
                JSONObject().put(
                    day,
                    org.json.JSONArray().put(JSONObject().put("time", "2026-11-02T09:00:00")),
                ),
            )
        }.toString()

        assertTrue(bridge.importText(backup, replace = false))
        assertEquals("GỘP keeps the untouched day", 2, repo.allRecords().size)

        assertTrue(bridge.importText(backup, replace = true))
        assertEquals("THAY THẾ drops the day the file does not have", 1, repo.allRecords().size)
        assertEquals(day, repo.allRecords().single().dateKey)
        assertEquals(9, repo.getConfig().goal)
        assertTrue(reported.last().getBoolean("ok"))
        // Task 4.2/spec: the screen must update at once. The page only does that when it is told to
        // re-adopt the store, so an import that did not resync would leave the old numbers on screen —
        // measured on the AVD before this hook existed.
        assertEquals("each successful import must resync the page", 2, resynced)
    }

    @Test
    fun aRejectedBackup_doesNotResyncThePage() = runTest {
        // Nothing changed in the store, so the page must not be re-rendered — and in particular the
        // resync must not fire on the failure path where the page still holds the user's old data.
        assertFalse(bridge.importText("""{"app":"x"}""", replace = true))
        assertEquals(0, resynced)
    }

    @Test
    fun anUnusableBackup_isRejectedAndChangesNothing() = runTest {
        repo.addRecord(day, 415)

        assertFalse(bridge.importText("""{"app":"x"}""", replace = true))

        assertEquals(1, repo.allRecords().size)
        assertEquals(
            "File này không có dữ liệu thuốc lá (thiếu \"data\")",
            reported.last().getString("message"),
        )
    }

    @Test
    fun shareBackup_writesTheCacheCopyAndOpensTheChooser() = runTest {
        repo.addRecord(day, 415)

        assertTrue(bridge.shareBackup())

        assertEquals(1, shareAsked)
        val dir = File(context.cacheDir, BackupSharer.DIRECTORY_NAME)
        assertEquals(1, dir.listFiles()?.size)
        assertEquals(portability.jsonFileName(), dir.listFiles()!!.single().name)
    }

    @Test
    fun shareBackup_withNoReceiver_reportsItInsteadOfClaimingSuccess() = runTest {
        repo.addRecord(day, 415)
        shareAllowed = false

        assertFalse(bridge.shareBackup())

        assertEquals("Không có ứng dụng nào nhận được bản sao JSON", reported.last().getString("message"))
    }

    @Test
    fun shareBackup_onAnEmptyStore_reportsNothingToShare() = runTest {
        assertFalse(bridge.shareBackup())
        assertEquals("Chưa có dữ liệu để sao lưu!", reported.last().getString("message"))
        assertEquals("the chooser must not open for an empty share", 0, shareAsked)
    }

    @Test
    fun theShareIntent_goesThroughFileProviderWithATemporaryReadGrant() = runTest {
        // Task 4.4 / design D4: the copy leaves the app only as a content:// URI handed to the chosen
        // receiver — not as a file path and not as a Blob. A path would be unreadable to the receiver
        // (scoped storage) and a missing grant would make the receiver open an empty document.
        repo.addRecord(day, 415)
        val copy = BackupSharer(portability, File(context.cacheDir, BackupSharer.DIRECTORY_NAME))
            .writeShareCopy() ?: error("a store with one record has a copy")

        val intent = WebFileBridge.shareIntent(context, copy)

        assertEquals("the receiver must be the chooser", "android.intent.action.SEND", intent.action)
        assertEquals("the backup is JSON", "application/json", intent.type)
        val stream = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
        assertNotNull("the copy must ride EXTRA_STREAM", stream)
        assertEquals("content", stream!!.scheme)
        assertEquals(
            "the authority must be this app's FileProvider",
            "${context.packageName}.fileprovider",
            stream.authority,
        )
        assertEquals(
            "without a read grant the receiver cannot open the copy",
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        assertEquals("the clip label carries the file name", copy.fileName, intent.clipData?.getDescription()?.label)
    }
}
