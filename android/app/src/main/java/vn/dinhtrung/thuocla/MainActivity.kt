package vn.dinhtrung.thuocla

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import vn.dinhtrung.thuocla.data.BackupSharer
import androidx.core.content.FileProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.dinhtrung.thuocla.ui.data.DataScreen
import vn.dinhtrung.thuocla.ui.data.DataViewModel
import vn.dinhtrung.thuocla.ui.data.DataViewModelFactory
import vn.dinhtrung.thuocla.ui.settings.SettingsScreen
import vn.dinhtrung.thuocla.ui.settings.SettingsViewModel
import vn.dinhtrung.thuocla.ui.settings.SettingsViewModelFactory
import vn.dinhtrung.thuocla.ui.stats.StatsRoute
import vn.dinhtrung.thuocla.domain.TrackerConfig
import vn.dinhtrung.thuocla.ui.theme.ThuoclaTheme
import vn.dinhtrung.thuocla.ui.today.TodayScreen
import vn.dinhtrung.thuocla.ui.today.TodayViewModel
import vn.dinhtrung.thuocla.ui.today.TodayViewModelFactory

private const val TAB_TODAY = 0
private const val TAB_STATS = 1
private const val TAB_DATA = 2
private const val TAB_SETTINGS = 3

/**
 * Four-tab shell: Hôm nay (cigarette-log), Thống kê (history-stats), Dữ liệu (data-portability) and
 * a Cài đặt placeholder.
 *
 * The tab list is the union of what #13, #14 and this slice each needed; neither side's screen is
 * dropped. File access is Storage Access Framework only (design decision #5): the pickers hand back
 * a content URI, this activity reads or writes UTF-8 text through [contentResolver], and no storage
 * permission is ever requested — nor is `android.permission.INTERNET`.
 *
 * "Cài đặt" is task 7's settings screen (four config fields + version footer); [DataScreen] keeps
 * its own tab so #15's export/import stays reachable rather than being folded in.
 */
class MainActivity : ComponentActivity() {

    private val container: AppContainer by lazy { (application as ThuoclaApp).container }

    /**
     * Activity-scoped so the document-picker callbacks registered below — which run outside any
     * composition — can report a failed write into the same message slot the screen renders.
     */
    private val dataViewModel: DataViewModel by viewModels { DataViewModelFactory(container) }

    private var pendingExportText: String? = null
    private var pendingImportKind: ImportKind? = null

    /**
     * The screen produces the file text first and the document picker second, so [pendingExportText]
     * carries it across the system dialog. The MIME type is generic text: the same contract serves a
     * `.csv` and a `.json` file without the app claiming either type in the manifest.
     */
    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            val text = pendingExportText
            pendingExportText = null
            if (uri != null && text != null) {
                writeText(uri, text)
            } else {
                // The user backed out of the picker: nothing was written, say so.
                dataViewModel.dismissMessage()
            }
        }

    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) readText(uri) else pendingImportKind = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // The theme sits above the Scaffold so the bottom bar and every screen share one scheme.
            // themeMode comes from the store (SYSTEM by default); isSystemInDarkTheme() is only
            // consulted when the user has left the choice to the device.
            val config by container.repository.configFlow()
                .collectAsState(initial = TrackerConfig.DEFAULT)
            ThuoclaTheme(isDark = config.themeMode.resolveIsDark(isSystemInDarkTheme())) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootScreen(container)
                }
            }
        }
    }

    @Composable
    private fun RootScreen(container: AppContainer) {
        var selectedTab by rememberSaveable { mutableIntStateOf(TAB_TODAY) }
        // Set when a quick-stat card on the Today screen asks for one day's detail; the stats tab
        // consumes it and clears it. This is the PWA's goToDay(): switchTab('stats') + selectDay(key).
        var requestedDay by rememberSaveable { mutableStateOf<String?>(null) }
        val dataState by dataViewModel.state.collectAsState()

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == TAB_TODAY,
                        onClick = { selectedTab = TAB_TODAY },
                        icon = { Text("🚬") },
                        label = { Text("Hôm nay") },
                        modifier = Modifier.testTag("tabToday"),
                    )
                    NavigationBarItem(
                        selected = selectedTab == TAB_STATS,
                        onClick = { selectedTab = TAB_STATS },
                        icon = { Text("📊") },
                        label = { Text("Thống kê") },
                        modifier = Modifier.testTag("tabStats"),
                    )
                    NavigationBarItem(
                        selected = selectedTab == TAB_DATA,
                        onClick = { selectedTab = TAB_DATA },
                        icon = { Text("💾") },
                        label = { Text("Dữ liệu") },
                        modifier = Modifier.testTag("tabData"),
                    )
                    NavigationBarItem(
                        selected = selectedTab == TAB_SETTINGS,
                        onClick = { selectedTab = TAB_SETTINGS },
                        icon = { Text("⚙️") },
                        label = { Text("Cài đặt") },
                        modifier = Modifier.testTag("tabSettings"),
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (selectedTab) {
                    TAB_STATS -> StatsRoute(
                        container = container,
                        requestedDay = requestedDay,
                        onDayConsumed = { requestedDay = null },
                    )
                    TAB_DATA -> DataScreen(
                        state = dataState,
                        onExportCsv = {
                            dataViewModel.exportCsv { text ->
                                pendingExportText = text
                                createDocumentLauncher.launch(container.dataPortability.csvFileName())
                            }
                        },
                        onExportJson = {
                            dataViewModel.exportJson { text ->
                                pendingExportText = text
                                createDocumentLauncher.launch(container.dataPortability.jsonFileName())
                            }
                        },
                        onShareJson = {
                            dataViewModel.shareJson { copy -> launchShareChooser(copy) }
                        },
                        onImportJson = {
                            pendingImportKind = ImportKind.JSON
                            openDocumentLauncher.launch(DOCUMENT_MIME_TYPES)
                        },
                        onImportCsv = {
                            pendingImportKind = ImportKind.CSV
                            openDocumentLauncher.launch(DOCUMENT_MIME_TYPES)
                        },
                        onConfirmReplace = { dataViewModel.confirmJsonImport(replace = true) },
                        onConfirmMerge = { dataViewModel.confirmJsonImport(replace = false) },
                        onCancelImport = dataViewModel::cancelJsonImport,
                        onDismissMessage = dataViewModel::dismissMessage,
                    )

                    TAB_SETTINGS -> SettingsRoute(container)
                    else -> TodayRoute(
                        container = container,
                        onOpenDay = { day ->
                            requestedDay = day
                            selectedTab = TAB_STATS
                        },
                    )
                }
            }
        }
    }

    private enum class ImportKind { JSON, CSV }

    /**
     * Writes [text] to the document the user chose. A failure cannot be shown in a snackbar from here
     * because the tab may not be visible, so it lands in the same message slot the screen renders.
     */
    private fun writeText(uri: Uri, text: String) {
        val written = runCatching {
            contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
            } ?: false
        }.isSuccess
        if (!written) {
            dataViewModel.reportWriteFailure()
        }
    }

    /**
     * Hands the freshly written share copy to whatever app accepts `application/json`.
     *
     * The file leaves the app only as a `content://` URI issued by the [FileProvider] declared in the
     * manifest: [Intent.FLAG_GRANT_READ_URI_PERMISSION] plus [ClipData] give the chosen receiver a
     * one-shot read grant without any storage permission. [Intent.createChooser] is used so the user
     * picks the destination (Drive, Zalo, Telegram…); when nothing resolves, the message slot reports it
     * instead of throwing `ActivityNotFoundException`, and the return value tells the caller not to claim
     * a share that never happened.
     */
    private fun launchShareChooser(copy: BackupSharer.ShareCopy): Boolean {
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            copy.file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, copy.fileName)
            // Some receivers read the name from the clip label rather than EXTRA_TITLE.
            clipData = ClipData.newUri(contentResolver, copy.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // Check the SEND intent, not the chooser: a chooser always resolves to the system chooser activity
        // even when no app accepts the type, so testing the chooser would never catch "no receiver"
        // (spec scenario "Không có ứng dụng nào nhận"). This is also what resolveActivity can answer in
        // Robolectric, where the chooser activity itself is absent.
        if (send.resolveActivity(packageManager) == null) {
            // No app accepts application/json: report through the screen's message slot, do not crash.
            dataViewModel.reportShareUnavailable()
            return false
        }
        startActivity(Intent.createChooser(send, "Chia sẻ bản sao JSON").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        return true
    }

    /** Reads the chosen file and routes it to the import the user asked for. */
    private fun readText(uri: Uri) {
        val text = runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull()

        if (text == null) {
            dataViewModel.reportReadFailure()
            pendingImportKind = null
            return
        }

        when (pendingImportKind) {
            ImportKind.JSON -> dataViewModel.offerJsonImport(text)
            ImportKind.CSV -> dataViewModel.importCsv(text)
            null -> Unit
        }
        pendingImportKind = null
    }

    private companion object {
        /** Kept broad on purpose: some file managers report `.csv` as text/plain and `.json` as octet-stream. */
        val DOCUMENT_MIME_TYPES = arrayOf("text/*", "application/json", "application/octet-stream")
    }
}

@Composable
private fun TodayRoute(container: AppContainer, onOpenDay: (String) -> Unit) {
    val viewModel: TodayViewModel = viewModel(factory = TodayViewModelFactory(container))
    val state by viewModel.state.collectAsState()

    TodayScreen(
        state = state,
        onAdd = viewModel::addCig,
        onUndo = viewModel::undo,
        onSelectTrigger = viewModel::selectTrigger,
        onDismissTriggerSheet = viewModel::dismissTriggerSheet,
        onAddAt = viewModel::addAt,
        onEditTime = viewModel::editTime,
        onOpenDay = onOpenDay,
    )
}

@Composable
private fun SettingsRoute(container: AppContainer) {
    val vm: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(container, BuildConfig.VERSION_NAME),
    )
    val state by vm.state.collectAsState()

    SettingsScreen(
        state = state,
        onGoalChange = vm::onGoalChange,
        onIntervalGoalChange = vm::onIntervalGoalChange,
        onPriceChange = vm::onPricePerPackChange,
        onCigsPerPackChange = vm::onCigsPerPackChange,
        onThemeModeChange = vm::onThemeModeChange,
        onSave = vm::save,
    )
}
