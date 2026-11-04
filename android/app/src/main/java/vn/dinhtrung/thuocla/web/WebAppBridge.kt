package vn.dinhtrung.thuocla.web

import android.webkit.JavascriptInterface

/**
 * The only surface the page may call into native (task 3.3).
 *
 * Allow-listed by construction: four data methods, no SQL, no path, no generic escape hatch. Each one
 * takes the page's JSON for a single concept, validates it, and hands it to [WebStoreMirror]; a
 * malformed argument is refused with `false` rather than throwing into the WebView.
 *
 * Registered under a name the page's shim knows (`WebStore`). Because the instance is exposed whole,
 * every method here is reachable from JS — so nothing here may ever accept a string it interprets as
 * code, a query, or a location.
 */
// The bridge is called from the WebView's JavaScript thread, never the UI thread, so the default
// lint suppression for untrusted JavaScript is not what it looks like here: this app serves only its
// own asset copy (WebShellActivity drops every other origin) and every argument is validated.
@Suppress("unused")
class WebAppBridge(private val mirror: WebStoreMirror) {

    /** Every stored record, keyed by date, as the JSON object the page's `loadData()` expects. */
    @JavascriptInterface
    fun readAll(): String = mirror.snapshot()

    /** Replaces [dateKey] with the page's array. False when the key or payload is unusable. */
    @JavascriptInterface
    fun writeDay(dateKey: String, recordsJson: String): Boolean = mirror.writeDay(dateKey, recordsJson)

    /** Stores the page's config object; empty or non-positive fields fall back to the defaults. */
    @JavascriptInterface
    fun writeConfig(configJson: String): Boolean = mirror.writeConfig(configJson)

    /** Drops the last cigarette of [dateKey] — the page's undo and day-detail delete. */
    @JavascriptInterface
    fun deleteLast(dateKey: String): Boolean = mirror.deleteLast(dateKey)

    /** The page cleared the log (`removeItem(DB_KEY)` or `clear()`). */
    @JavascriptInterface
    fun clearRecords(): Boolean = mirror.clearRecords()

    /** The page switched theme; anything other than `"light"` is dark, exactly as `getTheme()` reads. */
    @JavascriptInterface
    fun writeTheme(theme: String): Boolean = mirror.writeTheme(theme)

    companion object {
        /** The name `window.WebStore` resolves to inside the page. */
        const val JS_NAME = "WebStore"
    }
}
