package vn.dinhtrung.thuocla.web

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import vn.dinhtrung.thuocla.ThuoclaApp

/**
 * The app's launcher: the web UI, served out of the APK.
 *
 * The six files in `assets/web/` are byte-identical copies of the web build at the repo root
 * (`scripts/verify-web-assets.sh` fails CI on drift), so the shell renders the same screens the PWA
 * does instead of a second, hand-written UI. [WebViewAssetLoader] serves them under
 * `https://appassets.androidplatform.net/`, which is a real https origin — `localStorage` and the
 * service worker behave as they do on the web, and nothing is fetched from the network. The app
 * declares no `INTERNET` permission, so a request to any other host could not succeed anyway; the
 * navigation guard below keeps such a request from even being attempted inside the WebView.
 *
 * Deliberately not enabled: `allowFileAccess`, `allowContentAccess`,
 * `allowUniversalAccessFromFileURLs`. Assets arrive through the loader, so the WebView never needs
 * file:// or content:// access, and leaving them off keeps a compromised page from reading local
 * files.
 */
class WebShellActivity : ComponentActivity() {

    internal lateinit var webView: WebView

    /** Room ↔ `localStorage` (task 3.1). Created per activity so its executor dies with the shell. */
    internal lateinit var mirror: WebStoreMirror

    /**
     * The page's waiting `<input type=file>`, held across the system picker. It must be answered
     * exactly once — with the selection or with null — or the page's input never re-enables.
     */
    private var pendingFileChooser: android.webkit.ValueCallback<Array<android.net.Uri>>? = null

    private val openDocumentLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
            pendingFileChooser?.onReceiveValue(uri?.let { arrayOf(it) } ?: emptyArray())
            pendingFileChooser = null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as ThuoclaApp).container
        mirror = WebStoreMirror(container.repository, container.clock)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Defaults are already false on modern WebView, but state them: a future edit that flips
            // one of these must be a visible decision, not an inherited default.
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                /**
                 * Only the asset origin may load here. Anything else is dropped rather than handed to
                 * an external app: the app has no INTERNET permission, so a link out is either a bug
                 * in the page or an attempt to leave the offline sandbox, and neither should open a
                 * browser on the user's behalf.
                 */
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = request.url.host != ASSET_HOST
            }

            // Without a WebChromeClient, `confirm()` returns false and `alert()` is a no-op, so the
            // web app's delete/clear confirmations and every result message silently do nothing.
            // `onShowFileChooser` likewise defaults to false, which is what makes <input type=file>
            // (the "Phục hồi" picker) inert.
            webChromeClient = WebChromeDialogs(this@WebShellActivity, ::openFileChooser)

            // The page may call exactly the allow-listed methods on WebAppBridge, under the name the
            // shim looks for.
            addJavascriptInterface(WebAppBridge(mirror), WebAppBridge.JS_NAME)
        }

        // Runs after `webView` is assigned: the document-start hook needs the instance, and it must be
        // installed before the load below starts, or `app.js` would read an empty store.
        seedAndTrackWrites()
        webView.loadUrl(INDEX_URL)

        setContentView(webView)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // The web app keeps its own history (tab switches, day detail). Going back inside
                    // it is what the user expects; only at the root does back leave the app.
                    if (webView.canGoBack()) webView.goBack() else finish()
                }
            },
        )
    }

    /**
     * The WebView's `<input type=file>` — the web app's only import entry point (`Phục hồi`).
     *
     * The page asks for `accept=".json,application/json"`; the picker is opened for exactly that so
     * the user cannot hand the app a file it would only reject. The callback is held until the picker
     * answers: it must be invoked once (with the selection, or with null when the user backs out), or
     * the page's input stays disabled forever.
     */
    private fun openFileChooser(
        callback: android.webkit.ValueCallback<Array<android.net.Uri>>,
        params: android.webkit.WebChromeClient.FileChooserParams,
    ): Boolean {
        pendingFileChooser?.onReceiveValue(null)
        pendingFileChooser = callback
        return try {
            openDocumentLauncher.launch(pickerMimeTypes(params.acceptTypes))
            true
        } catch (e: android.content.ActivityNotFoundException) {
            pendingFileChooser = null
            Log.e(TAG, "no file picker available", e)
            false
        }
    }

    /**
     * `accept` values the picker understands.
     *
     * The page sends extensions (`.json`) and MIME types together; `OpenDocument` takes MIME types
     * only. An extension that is not in [EXTENSION_MIME] maps to `application/octet-stream` rather
     * than to nothing: an empty `EXTRA_MIME_TYPES` makes the picker accept *any* file, which is the
     * opposite of what the page's `accept` asked for. The page currently sends only `.json`
     * (`index.html:348`), and the plain MIME type it also lists is what usually decides the filter.
     */
    private fun mimeTypesFor(accept: String): List<String> {
        val value = accept.trim()
        return when {
            value.isEmpty() -> emptyList()
            value.startsWith(".") -> listOf(EXTENSION_MIME[value.lowercase()] ?: UNKNOWN_MIME)
            else -> listOf(value)
        }
    }

    /** Joins the page's `accept` list into the picker's filter, never empty when the page named one. */
    private fun pickerMimeTypes(acceptTypes: Array<String>): Array<String> {
        val types = acceptTypes.flatMap(::mimeTypesFor).distinct()
        return (types.ifEmpty { listOf(UNKNOWN_MIME) }).toTypedArray()
    }

    /**
     * Seeds `localStorage` from Room and installs the write-through wrapper *before* `app.js` runs.
     *
     * `WebViewCompat.addDocumentStartJavaScript` is the only hook early enough — `app.js` reads the
     * three keys in its own top-level `init()`, so seeding after `onPageFinished` would render an
     * empty UI first. Two things can stop it: a WebView without the feature (it throws
     * `UnsupportedOperationException`) and an origin rule it rejects (it throws
     * `IllegalArgumentException` from Chromium — a bare host is not an origin rule). Either way the
     * page still runs, because it is served from the APK; it just starts from its own `localStorage`
     * instead of the Room data. That is a degraded shell, never a crash on launch.
     */
    private fun seedAndTrackWrites() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            Log.w(TAG, "WebView lacks DOCUMENT_START_SCRIPT; starting from web storage only")
            return
        }
        try {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                WebStoreShim.script(mirror.readSeed()),
                // An origin rule, not a host: Chromium rejects `appassets.androidplatform.net`.
                setOf(ASSET_ORIGIN),
            )
        } catch (e: RuntimeException) {
            Log.e(TAG, "could not install the store shim; starting from web storage only", e)
        }
    }

    override fun onDestroy() {
        // A picker still open when the task dies would otherwise leave the page waiting forever.
        pendingFileChooser?.onReceiveValue(null)
        pendingFileChooser = null
        // Detach before destroying: a WebView still attached to a window can leak the activity.
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        // No task: the executor drops queued jobs, which is what "the page is gone" means.
        mirror.shutdown()
        webView.destroy()
        super.onDestroy()
    }

    internal companion object {
        const val TAG = "WebShellActivity"
        const val ASSET_HOST = "appassets.androidplatform.net"
        const val ASSET_ORIGIN = "https://$ASSET_HOST"
        const val INDEX_URL = "$ASSET_ORIGIN/assets/web/index.html"

        /** What an extension [EXTENSION_MIME] does not know is filtered as — see `mimeTypesFor`. */
        const val UNKNOWN_MIME = "application/octet-stream"

        /**
         * The extensions the web app's `accept` attributes use, mapped to the MIME type the system
         * picker filters on. The page's restore input accepts `.json,application/json`
         * (`index.html:348`); S4 adds nothing to this list, because both of its pickers are opened by
         * native with a fixed type rather than from an `accept` value.
         */
        val EXTENSION_MIME = mapOf(
            ".json" to "application/json",
        )
    }
}
