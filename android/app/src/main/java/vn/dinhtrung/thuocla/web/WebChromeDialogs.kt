package vn.dinhtrung.thuocla.web

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Makes the web app's own dialogs work inside the shell.
 *
 * `app.js` drives four user-visible actions through `confirm()` — deleting a cigarette in the day
 * detail, deleting one on the stats screen, the THAY THẾ/GỘP choice on restore, and "Xoá toàn bộ lịch
 * sử" — and reports every outcome through `alert()`. A `WebView` with no `WebChromeClient` answers
 * `false` to `onJsConfirm` and drops `onJsAlert`, so without this class those four actions silently do
 * nothing and the user gets no feedback at all. Nothing here changes what the web app does; it only
 * renders the dialogs the page already asks for.
 *
 * `onShowFileChooser` is left to the caller ([WebShellActivity]) because it needs an activity result
 * launcher, which only the activity can register.
 */
internal class WebChromeDialogs(
    private val activity: Activity,
    private val onShowFileChooser: (
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ) -> Boolean,
) : WebChromeClient() {

    /** `confirm()` — the page's OK/Cancel. Cancel must map to `false`, which every caller treats as "no". */
    override fun onJsConfirm(
        view: WebView,
        url: String,
        message: String,
        result: JsResult,
    ): Boolean {
        AlertDialog.Builder(activity)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
            .setOnCancelListener { result.cancel() }
            .show()
        return true
    }

    /** `alert()` — every success/failure message in the data path. OK is the only button. */
    override fun onJsAlert(
        view: WebView,
        url: String,
        message: String,
        result: JsResult,
    ): Boolean {
        AlertDialog.Builder(activity)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
            .setOnCancelListener { result.confirm() }
            .show()
        return true
    }

    /** `prompt()` is not used by the page; returning false keeps the default (which cancels it). */
    override fun onJsPrompt(
        view: WebView,
        url: String,
        message: String,
        defaultValue: String,
        result: JsPromptResult,
    ): Boolean = false

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean = onShowFileChooser(filePathCallback, fileChooserParams)

    /** The shim reports native failures with `console.warn`; keep them visible in logcat. */
    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        val level = when (consoleMessage.messageLevel()) {
            ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
            ConsoleMessage.MessageLevel.WARNING -> Log.WARN
            else -> Log.INFO
        }
        Log.println(
            level,
            TAG,
            "${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})",
        )
        return true
    }

    private companion object {
        const val TAG = "WebShell"
    }
}
