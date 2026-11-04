package vn.dinhtrung.thuocla.web

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import vn.dinhtrung.thuocla.data.BackupSharer
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
    private val sharer: BackupSharer,
    /** Called on the bridge thread when native needs to open a picker; runs on the UI thread. */
    private val openCreateDocument: (fileName: String, mimeType: String) -> Unit,
    private val openOpenDocument: () -> Unit,
    private val openShare: (BackupSharer.ShareCopy) -> Boolean,
    /** The WebView, told that a file action finished. Set by the activity once it is created. */
    private val notifyPage: (json: String) -> Unit,
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
        // The page's own wording for this export is "để sao lưu", not "để xuất" — the two buttons
        // share a code path here but must not share a message.
        openCreateDocumentFor(text, portability.jsonFileName(), NOTHING_TO_SHARE)
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
     * `Chia sẻ bản sao` — writes the cache copy and hands it to the chooser.
     *
     * The write is blocking IO, so it happens on the bridge thread; the chooser is opened through the
     * activity, which owns the UI thread and the share-guard that decides whether anything can receive.
     */
    @JavascriptInterface
    fun shareBackup(): Boolean {
        val copy = await { sharer.writeShareCopy() } ?: run {
            notifyPage(result(ok = false, message = NOTHING_TO_SHARE))
            return false
        }
        val started = openShare(copy)
        notifyPage(
            result(
                ok = started,
                message = if (started) null else NO_RECEIVER,
            ),
        )
        return started
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
        const val NOTHING_TO_SHARE = "Chưa có dữ liệu để sao lưu!"
        const val NO_RECEIVER = "Không có ứng dụng nào nhận được bản sao JSON"
        const val READ_FAILED = "Không đọc được file"
        const val NO_PICKER = "Không mở được hộp thoại chọn file"

        /** A receiver that opens the SAF dialogs and the share chooser for [intent]-style actions. */
        fun shareIntent(context: Context, copy: BackupSharer.ShareCopy): Intent {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", copy.file)
            return Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, copy.fileName)
                // Some receivers read the name from the clip label rather than EXTRA_TITLE.
                clipData = ClipData.newUri(context.contentResolver, copy.fileName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        /** True when something can receive the backup; false means the caller must not claim a share. */
        fun canShare(context: Context, copy: BackupSharer.ShareCopy): Boolean = try {
            shareIntent(context, copy).resolveActivity(context.packageManager) != null
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "no receiver for the backup", e)
            false
        }

        /** The file name the page's export would use, so the SAF dialog pre-fills the same one. */
        fun fileNameFor(kind: Kind, portability: DataPortability): String = when (kind) {
            Kind.CSV -> portability.csvFileName()
            Kind.JSON -> portability.jsonFileName()
        }

        enum class Kind { CSV, JSON }

        /** Exposed for the activity so both threads agree on the directory. */
        fun shareDirectory(context: Context): File = File(context.cacheDir, BackupSharer.DIRECTORY_NAME)
    }
}
