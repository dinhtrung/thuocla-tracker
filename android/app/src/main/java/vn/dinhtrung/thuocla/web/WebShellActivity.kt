package vn.dinhtrung.thuocla.web

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.webkit.WebViewAssetLoader

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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

            loadUrl(INDEX_URL)
        }

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

    override fun onDestroy() {
        // Detach before destroying: a WebView still attached to a window can leak the activity.
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    private companion object {
        const val ASSET_HOST = "appassets.androidplatform.net"
        const val INDEX_URL = "https://$ASSET_HOST/assets/web/index.html"
    }
}
