package vn.dinhtrung.thuocla.web

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The shell's contract, checked at the activity rather than by reading the source: the launcher is the
 * shell, the page it loads is the asset copy inside the APK, the WebView is configured for a local
 * https origin only, and back walks the web history before it leaves the app.
 *
 * The asset loader is exercised for real — Robolectric serves `assets/web/` from the merged test
 * resources — so a missing or renamed asset fails here instead of showing a blank page on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebShellActivityTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun launcher_isTheWebShell() {
        val launcher = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertNotNull("app has no launcher intent", launcher)
        assertEquals(WebShellActivity::class.java.name, launcher!!.component?.className)
    }

    @Test
    fun composeActivity_isNoLongerALauncher() {
        val launchers = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(context.packageName),
            PackageManager.MATCH_ALL,
        ).map { it.activityInfo.name }

        assertEquals(listOf(WebShellActivity::class.java.name), launchers)
    }

    @Test
    fun loadsTheAssetCopyOfTheWebApp() {
        val activity = Robolectric.buildActivity(WebShellActivity::class.java).setup().get()
        val webView = activity.webView

        assertEquals(
            "https://appassets.androidplatform.net/assets/web/index.html",
            shadowOf(webView).lastLoadedUrl,
        )
        assertTrue(webView.settings.javaScriptEnabled)
        assertTrue(webView.settings.domStorageEnabled)
        assertFalse(webView.settings.allowFileAccess)
        assertFalse(webView.settings.allowContentAccess)
        assertFalse(webView.settings.allowUniversalAccessFromFileURLs)
    }

    @Test
    fun assetLoader_servesTheRealIndexHtml() {
        val activity = Robolectric.buildActivity(WebShellActivity::class.java).setup().get()
        val client = shadowOf(activity.webView).webViewClient

        val response = client.shouldInterceptRequest(
            activity.webView,
            request("https://appassets.androidplatform.net/assets/web/index.html"),
        )
        assertNotNull("asset loader returned nothing for index.html", response)
        val body = response!!.data.bufferedReader().use { it.readText() }
        assertTrue("index.html served from assets is not the web app", body.contains("Thuốc Lá Tracker"))
    }

    @Test
    fun navigation_outsideTheAssetOrigin_isBlocked() {
        val activity = Robolectric.buildActivity(WebShellActivity::class.java).setup().get()
        val client = shadowOf(activity.webView).webViewClient

        assertTrue(
            client.shouldOverrideUrlLoading(
                activity.webView,
                request("https://thuocla-tracker.vercel.app/"),
            ),
        )
        assertFalse(
            client.shouldOverrideUrlLoading(
                activity.webView,
                request("https://appassets.androidplatform.net/assets/web/index.html"),
            ),
        )
    }

    @Test
    fun back_walksWebHistoryBeforeLeavingTheApp() {
        val activity = Robolectric.buildActivity(WebShellActivity::class.java).setup().get()
        val shadow = shadowOf(activity.webView)

        shadow.setCanGoBack(true)
        activity.onBackPressedDispatcher.onBackPressed()
        assertEquals(1, shadow.goBackInvocations)
        assertFalse(activity.isFinishing)

        shadow.setCanGoBack(false)
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun destroy_destroysTheWebView() {
        val controller = Robolectric.buildActivity(WebShellActivity::class.java).setup()
        val webView = controller.get().webView
        controller.pause().stop().destroy()
        assertTrue(shadowOf(webView).wasDestroyCalled())
    }

    private fun request(url: String) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame(): Boolean = true
        override fun isRedirect(): Boolean = false
        override fun hasGesture(): Boolean = false
        override fun getMethod(): String = "GET"
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }
}
