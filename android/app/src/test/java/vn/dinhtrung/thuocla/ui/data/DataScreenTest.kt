package vn.dinhtrung.thuocla.ui.data

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.dinhtrung.thuocla.data.BackupSharer
import vn.dinhtrung.thuocla.data.DataPortability
import vn.dinhtrung.thuocla.data.TrackerRepository
import vn.dinhtrung.thuocla.data.db.AppDatabase
import vn.dinhtrung.thuocla.testutil.MainDispatcherRule

/**
 * The "Dữ liệu" screen and its state holder: the five buttons exist, the THAY THẾ / GỘP question is
 * raised before a JSON import touches the store, and cancelling it leaves the store untouched.
 *
 * Nothing here covers pixels or the system document picker; those need a device walkthrough, which
 * the PR body records as unverified.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: TrackerRepository
    private lateinit var portability: DataPortability
    private lateinit var sharer: BackupSharer
    private lateinit var shareDir: File

    private val day = "2026-11-02"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TrackerRepository(db)
        portability = DataPortability(
            db = db,
            repository = repo,
            clock = Clock.fixed(Instant.parse("2026-11-02T07:12:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")),
        )
        // Share copies go to a throwaway directory so the test never touches the app's real cache.
        shareDir = File(context.cacheDir, "backups-test")
        sharer = BackupSharer(portability, shareDir)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --------------------------------------------------------------- the screen

    @Test
    fun screen_showsExportAndImportActions() {
        render(DataUiState())

        // Header first: the assertions below scroll the column, which moves the header off-screen.
        composeRule.onNodeWithText("Dữ liệu").assertIsDisplayed()
        composeRule.onNodeWithTag("exportCsv").assertIsDisplayed()
        composeRule.onNodeWithTag("exportJson").assertIsDisplayed()
        composeRule.onNodeWithTag("shareJson").assertIsDisplayed()
        // The screen scrolls; the share action made the column taller, so the import card now needs a
        // scroll in the test window. Reachability, not position, is what this test pins.
        composeRule.onNodeWithTag("importJson").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("importCsv").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun screen_asksReplaceOrMergeBeforeWritingAJsonImport() {
        var replaceAsked = 0
        var mergeAsked = 0
        render(
            DataUiState(pendingJsonImport = """{"data":{}}"""),
            onConfirmReplace = { replaceAsked++ },
            onConfirmMerge = { mergeAsked++ },
        )

        composeRule.onNodeWithTag("confirmReplace").performClick()
        composeRule.onNodeWithTag("confirmMerge").performClick()

        assertEquals(1, replaceAsked)
        assertEquals(1, mergeAsked)
    }

    @Test
    fun screen_rendersAMessageAndDismissesIt() {
        var dismissed = 0
        render(
            DataUiState(message = "Đã nhập 3 ngày / 5 điếu", isError = false),
            onDismissMessage = { dismissed++ },
        )

        // The message card sits at the end of a scrolling column, so presence is the contract here.
        composeRule.onNodeWithText("Đã nhập 3 ngày / 5 điếu").fetchSemanticsNode()
        composeRule.onNodeWithTag("dismissDataMessage").performScrollTo().performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun screen_disablesActionsWhileBusy() {
        render(DataUiState(busy = true))

        composeRule.onNodeWithTag("exportCsv").assertIsNotEnabled()
        composeRule.onNodeWithTag("importJson").assertIsNotEnabled()
    }

    // ------------------------------------------------------------ the state holder

    @Test
    fun exportCsv_handsTextToTheCallbackAndReportsTheFileName() = runBlocking {
        repo.addRecord(day, 415, triggerIdx = 0)
        val viewModel = DataViewModel(portability, sharer)

        var exported: String? = null
        viewModel.exportCsv { exported = it }

        awaitUntil("CSV export finished") { viewModel.state.value.message != null }
        assertTrue("CSV text was produced", exported!!.startsWith(DataPortability.CSV_HEADER))
        assertEquals("Đã xuất thuocla_2026-11-02.csv", viewModel.state.value.message)
        assertEquals(false, viewModel.state.value.isError)
    }

    @Test
    fun exportCsv_onAnEmptyStore_reportsNothingToExport() = runBlocking {
        val viewModel = DataViewModel(portability, sharer)

        var called = false
        viewModel.exportCsv { called = true }

        awaitUntil("empty export reported") { viewModel.state.value.message != null }
        assertEquals(false, called)
        assertEquals(DataViewModel.NOTHING_TO_EXPORT, viewModel.state.value.message)
        assertTrue(viewModel.state.value.isError)
    }

    @Test
    fun offerJsonImport_holdsTheTextAndWritesNothingUntilConfirmed() = runBlocking {
        repo.addRecord(day, 415)
        val viewModel = DataViewModel(portability, sharer)

        viewModel.offerJsonImport(BACKUP_TWO_DAYS)

        assertEquals(BACKUP_TWO_DAYS, viewModel.state.value.pendingJsonImport)
        assertEquals("nothing written yet", 1, db.recordDao().all().size)

        viewModel.cancelJsonImport()

        assertNull(viewModel.state.value.pendingJsonImport)
        assertEquals("cancel leaves the store alone", 1, db.recordDao().all().size)
    }

    @Test
    fun confirmJsonImport_replace_wipesAndImports() = runBlocking {
        repo.addRecord(day, 415)
        val viewModel = DataViewModel(portability, sharer)

        viewModel.offerJsonImport(BACKUP_TWO_DAYS)
        viewModel.confirmJsonImport(replace = true)

        awaitUntil("replace import finished") { viewModel.state.value.message != null }
        assertNull(viewModel.state.value.pendingJsonImport)
        assertEquals("only the file's cigarettes remain", 2, db.recordDao().all().size)
        assertEquals(2, repo.distinctDayCount())
        assertTrue(viewModel.state.value.message!!.startsWith("Đã nhập 2 ngày / 2 điếu"))
    }

    @Test
    fun confirmJsonImport_merge_keepsExistingRows() = runBlocking {
        repo.addRecord("2026-10-30", 700)
        val viewModel = DataViewModel(portability, sharer)

        viewModel.offerJsonImport(BACKUP_TWO_DAYS)
        viewModel.confirmJsonImport(replace = false)

        awaitUntil("merge import finished") { viewModel.state.value.message != null }
        assertEquals("existing day survives beside the file's days", 3, repo.distinctDayCount())
    }

    @Test
    fun importCsv_reportsTheFirstBadLineAsAnError() = runBlocking {
        repo.addRecord(day, 415)
        val viewModel = DataViewModel(portability, sharer)

        viewModel.importCsv(CSV_WITH_BAD_TIME)

        awaitUntil("CSV import rejected") { viewModel.state.value.message != null }
        val message = viewModel.state.value.message!!
        assertTrue("names the offending row", message.contains("dòng 3"))
        assertTrue("names the bad value", message.contains("25:99"))
        assertTrue(viewModel.state.value.isError)
        assertEquals("nothing from the file was written", 1, db.recordDao().all().size)
    }

    // ---------------------------------------------------------------- helpers

    /** ViewModel work suspends on Room's executor, so poll for the state change instead of assuming a tick. */
    private suspend fun awaitUntil(what: String, condition: () -> Boolean) {
        val reached = withTimeoutOrNull(5_000) {
            while (!condition()) delay(10)
            true
        }
        assertTrue("timed out waiting for: $what", reached == true)
    }

    private fun render(
        state: DataUiState,
        onConfirmReplace: () -> Unit = {},
        onConfirmMerge: () -> Unit = {},
        onDismissMessage: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    DataScreen(
                        state = state,
                        onExportCsv = {},
                        onExportJson = {},
                        onImportJson = {},
                        onShareJson = {},
                        onImportCsv = {},
                        onConfirmReplace = onConfirmReplace,
                        onConfirmMerge = onConfirmMerge,
                        onCancelImport = {},
                        onDismissMessage = onDismissMessage,
                    )
                }
            }
        }
    }

    private companion object {
        const val BACKUP_TWO_DAYS = """{"app":"thuocla-tracker","format":1,"data":{
"2026-11-02":[{"time":"2026-11-02T06:55:00","trigger":0}],
"2026-11-03":[{"time":"2026-11-03T07:00:00","trigger":1}]}}"""

        const val CSV_WITH_BAD_TIME = """Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày
"2026-11-02","06:55","Sau ăn","","","1"
"2026-11-02","25:99","Cà phê","","","2""""
    }
}
