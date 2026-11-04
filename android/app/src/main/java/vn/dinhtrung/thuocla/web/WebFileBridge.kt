package vn.dinhtrung.thuocla.web

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.JavascriptInterface
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import vn.dinhtrung.thuocla.data.DataPortability

/**
 * The page's three file actions, routed through native (tasks 4.1–4.2).
 *
 * A `WebView` cannot download a Blob and cannot open SAF, so `exportCSV`, `exportBackup` and
 * `pickRestoreFile` are overridden in the shim and arrive here. Everything the formats themselves need
 * — the six CSV columns, the `{app, format, exportedAt, config, data}` schema, the normalize rules and
 * THAY THẾ/GỘP — is [DataPortability]'s, unchanged: this class only moves bytes and reports back.
 *
 * The flow is split because the system owns the pickers:
 * 1. the bridge computes the text ([exportCsvText]/[exportJsonText]) or reads the chosen file
 *    ([importFile]);
 * 2. the activity opens the SAF dialog or the chooser ([WebShellActivity]);
 * 3. the activity calls back into [onExportFinished]/[onImportFinished] with what the user did, and
 *    the page is told through `window.__thuoclaFileResult`, which the shim turns into the same
 *    message the browser build would have shown.
 */
class WebFileBridge(
    private val portability: DataPortability,
    /** Called on the bridge thread when native needs to open a picker; runs on the UI thread. */
    private val openCreateDocument: (fileName: String, mimeType: String) -> Unit,
    private val openOpenDocument: () -> Unit,
    /** The WebView, told that a file action finished. Set by the activity once it is created. */
    private val notifyPage: (json: String) -> Unit,
    /**
     * Re-seeds and re-renders the page after a native import. The store changed underneath it, so the
     * screen must adopt the new state — see the shim's `__thuoclaAfterImport`.
     */
    private val afterImport: () -> Unit,
) {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "web-file-bridge").apply { isDaemon = true }
    }

    /** Runs [block] on the bridge thread and waits for it — the JS bridge thread must not do IO. */
    private fun <T> await(block: suspend () -> T): T =
        executor.submit<T> { runBlocking { block() } }.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

    /**
     * The three methods the shim calls, plus the share action. `@JavascriptInterface` is what makes
     * them reachable from the page; everything else on this class stays private to native.
     *
     * Each returns quickly: the blocking work runs on the bridge thread, and opening a dialog is
     * handed to the activity, which owns the UI thread and the activity-result launchers.
     */
    @JavascriptInterface
    fun exportCsv(): Boolean {
        val text = await { portability.exportCsv() }
        openCreateDocumentFor(text, portability.csvFileName(), NOTHING_TO_EXPORT)
        return text != null
    }

    @JavascriptInterface
    fun exportBackup(): Boolean {
        val text = await { portability.exportJson() }
        // The page's own wording for this export is "để sao lưu", not "để xuất" — the two exports
        // share a code path here but must not share a message.
        openCreateDocumentFor(text, portability.jsonFileName(), NOTHING_TO_BACKUP)
        return text != null
    }

    @JavascriptInterface
    fun pickRestoreFile(): Boolean = try {
        openOpenDocument()
        true
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "no file picker available", e)
        notifyPage(result(ok = false, message = NO_PICKER))
        false
    }

    /**
     * Hands the SAF dialog the text to write, or reports there was nothing to export.
     *
     * Nothing is written here: the destination comes from the dialog, and the activity owns it plus the
     * activity-result launcher that answers it.
     */
    private fun openCreateDocumentFor(text: String?, fileName: String, emptyMessage: String) {
        if (text == null) {
            notifyPage(result(ok = false, message = emptyMessage))
            return
        }
        openCreateDocument(text, fileName)
    }

    /** Reads and imports the file the user chose. [replace] is the page's THAY THẾ (`true`) vs GỘP. */
    fun importFile(context: Context, uri: Uri, replace: Boolean): Boolean {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()

        if (text == null) {
            notifyPage(result(ok = false, message = READ_FAILED))
            return false
        }
        return importText(text, replace)
    }

    /** The import itself, separated so tests can drive it without a `content://` URI. */
    fun importText(text: String, replace: Boolean): Boolean = await {
        when (val outcome = portability.importJson(text, replace)) {
            is DataPortability.ImportOutcome.Imported -> {
                // The store changed, so the page must adopt the new state before it reports success;
                // otherwise the screen keeps the pre-restore numbers (see `afterImport`).
                afterImport()
                notifyPage(
                    result(
                        ok = true,
                        message = "Đã phục hồi (${if (replace) "thay thế" else "gộp"}): " +
                            "${outcome.days} ngày / ${outcome.records} điếu",
                    ),
                )
                true
            }
            is DataPortability.ImportOutcome.Rejected -> {
                notifyPage(result(ok = false, message = outcome.message))
                false
            }
        }
    }

    /** The export produced nothing — `app.js`'s `alert('Chưa có dữ liệu để xuất!')`. */
    fun reportNothingToExport() = notifyPage(result(ok = false, message = NOTHING_TO_EXPORT))

    /** The chosen file could not be read. */
    fun reportReadFailure() = notifyPage(result(ok = false, message = READ_FAILED))

    /** The SAF dialog's outcome, reported as the same success/failure message the page would show. */
    fun reportExportFinished(ok: Boolean, fileName: String) = notifyPage(
        result(ok = ok, message = if (ok) "Đã xuất: $fileName" else NO_PICKER),
    )

    fun shutdown() {
        executor.shutdown()
    }

    /** The shape the shim's `window.__thuoclaFileResult` reads. */
    private fun result(ok: Boolean, message: String?): String = JSONObject().apply {
        put("ok", ok)
        put("message", message ?: "")
    }.toString()

    companion object {
        const val TAG = "WebFileBridge"

        /** The name `window.ThuoclaFiles` resolves to inside the page. */
        const val JS_NAME = "ThuoclaFiles"
        private const val TIMEOUT_SECONDS = 30L

        const val NOTHING_TO_EXPORT = "Chưa có dữ liệu để xuất!"

        /** The page's wording for a backup export with nothing to write. */
        const val NOTHING_TO_BACKUP = "Chưa có dữ liệu để sao lưu!"
        const val READ_FAILED = "Không đọc được file"
        const val NO_PICKER = "Không mở được hộp thoại chọn file"

        /** The file name the page's export would use, so the SAF dialog pre-fills the same one. */
        fun fileNameFor(kind: Kind, portability: DataPortability): String = when (kind) {
            Kind.CSV -> portability.csvFileName()
            Kind.JSON -> portability.jsonFileName()
        }

        enum class Kind { CSV, JSON }
    }
}
