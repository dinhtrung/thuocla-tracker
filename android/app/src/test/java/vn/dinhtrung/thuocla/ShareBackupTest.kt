package vn.dinhtrung.thuocla

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import android.os.Looper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import vn.dinhtrung.thuocla.data.BackupSharer
import vn.dinhtrung.thuocla.ui.data.DataViewModel

/**
 * The share-out path of issue #34, driven through the real activity: pressing **Chia sẻ bản sao JSON**
 * must leave a `content://` copy in the provider's cache directory and start an `ACTION_SEND` intent
 * carrying it.
 *
 * Robolectric stands in for the device: `FileProvider.getUriForFile` runs against the merged manifest
 * and `res/xml/file_paths.xml`, and [shadowOf] records what the activity started. The chooser is an
 * `ACTION_CHOOSER` wrapper by contract, so the assertions unwrap `EXTRA_INTENT` to reach the `ACTION_SEND`
 * intent the receiver actually sees. Nothing here opens a real app, so the PR body records the AVD check
 * as the separate, still-outstanding proof.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareBackupTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /**
     * [FileProvider] caches each authority's parsed path strategy in a static map. Production parses it
     * once per process against one stable `cacheDir`, but Robolectric gives every test method a fresh
     * cache directory, so the second method would resolve against the first method's directory and throw
     * "Failed to find configured root". Clearing the cache between methods restores the production
     * invariant. Robolectric-only artefact — no production behaviour changes.
     */
    @Before
    fun clearProviderPathCache() {
        val field = androidx.core.content.FileProvider::class.java
            .getDeclaredField("sCache")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (field.get(null) as MutableMap<String, *>).clear()
    }

    /** Directory the provider exposes, matching `res/xml/file_paths.xml`'s single `<cache-path>`. */
    private val shareDir: File
        get() = File(composeRule.activity.cacheDir, BackupSharer.DIRECTORY_NAME)

    /**
     * Seeds two cigarettes for today so there is something to share, opens the Dữ liệu tab and presses
     * the share action. Returns the started intent, unwrapped from the chooser.
     */
    private fun pressShare(): Intent? {
        val activity = composeRule.activity
        val container = (activity.application as ThuoclaApp).container
        val today = LocalDate.now(container.clock).toString()
        runBlocking {
            container.repository.addRecord(today, 505)
            container.repository.addRecord(today, 590)
        }

        // The activity only starts the chooser when some app accepts application/json, so register a
        // stand-in receiver the way a real Drive/Zalo/Telegram install would appear to PackageManager.
        // This must happen before the press: the guard runs inside the click's coroutine.
        registerJsonReceiver()

        composeRule.onNodeWithTag("tabData").performClick()
        composeRule.onNodeWithTag("shareJson").performScrollTo().performClick()

        // The share runs in viewModelScope, and Robolectric's main looper is paused: idle it on each
        // pass so the coroutine actually resumes and reaches startActivity.
        composeRule.waitUntil(TIMEOUT_MS) {
            shadowOf(Looper.getMainLooper()).idle()
            // peek, not next: nextStartedActivity pops the queue and would consume the intent here.
            shadowOf(activity).peekNextStartedActivity() != null
        }
        val started = shadowOf(activity).nextStartedActivity ?: return null
        return if (started.action == Intent.ACTION_CHOOSER) {
            @Suppress("DEPRECATION")
            started.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
        } else {
            started
        }
    }

    /** Makes `PackageManager` report a receiver for `ACTION_SEND` + `application/json`. */
    private fun registerJsonReceiver() {
        val pm = composeRule.activity.packageManager
        shadowOf(pm).addResolveInfoForIntent(
            Intent(Intent.ACTION_SEND).setType("application/json"),
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "com.example.receiver"
                    name = "com.example.receiver.ReceiverActivity"
                }
            },
        )
    }

    @Test
    fun shareAction_startsAnActionSendIntentWithAFileProviderUri() {
        val send = pressShare()
        assertNotNull("no ACTION_SEND intent was started", send)

        assertEquals(Intent.ACTION_SEND, send!!.action)
        assertEquals("application/json", send.type)

        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
        assertNotNull("EXTRA_STREAM is missing", uri)
        assertEquals(
            "the stream must come from the app's FileProvider",
            "${composeRule.activity.packageName}.fileprovider",
            uri!!.authority,
        )
        assertTrue(
            "the read grant must be carried by the intent",
            send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
        // Receivers that read the display name from the clip label rather than EXTRA_TITLE need this.
        assertNotNull("clipData is missing", send.clipData)
        assertEquals(
            "the clip label carries the file name",
            send.getStringExtra(Intent.EXTRA_TITLE),
            send.clipData!!.description.label.toString(),
        )
    }

    @Test
    fun shareAction_writesTheBackupIntoTheProviderDirectory() {
        val send = pressShare()
        assertNotNull("no ACTION_SEND intent was started", send)

        val files = shareDir.listFiles().orEmpty()
        assertEquals("exactly one share copy", 1, files.size)

        val file = files.single()
        assertEquals(
            "name must match the PWA backup convention",
            "thuocla_backup_${LocalDate.now((composeRule.activity.application as ThuoclaApp).container.clock)}.json",
            file.name,
        )

        // Same payload shape the SAF export writes: a top-level `data` object keyed by date.
        val payload = JSONObject(file.readText())
        assertTrue("payload has no data object", payload.has("data"))
        assertTrue("payload has no format", payload.has("format"))
        val today = LocalDate.now((composeRule.activity.application as ThuoclaApp).container.clock).toString()
        assertEquals(
            "today's two cigarettes must be in the shared copy",
            2,
            payload.getJSONObject("data").getJSONArray(today).length(),
        )
    }

    @Test
    fun sharingTwice_leavesOnlyTheNewestFile() {
        pressShare()
        assertEquals("first share wrote one file", 1, shareDir.listFiles().orEmpty().size)

        // Clear the recorded intent so the second press is observable on its own.
        shadowOf(composeRule.activity).nextStartedActivity
        pressShare()

        assertEquals(
            "the previous share copy must be deleted, not accumulated",
            1,
            shareDir.listFiles().orEmpty().size,
        )
    }

    @Test
    fun shareAction_onAnEmptyStore_reportsNothingToExportAndStartsNoIntent() {
        // No records seeded: the share action must behave like the JSON export does.
        shadowOf(composeRule.activity).nextStartedActivity
        composeRule.onNodeWithTag("tabData").performClick()
        composeRule.onNodeWithTag("shareJson").performScrollTo().performClick()

        composeRule.waitUntil(TIMEOUT_MS) {
            // viewModelScope resumes on Dispatchers.Main, and Robolectric's looper is paused: idle it so
            // the coroutine reaches its message update and the recomposition is observable.
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodes(
                androidx.compose.ui.test.hasText("Chưa có dữ liệu để xuất", substring = true),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            "an empty store must not create an empty file",
            0,
            shareDir.listFiles().orEmpty().size,
        )
        assertEquals(
            "no intent should be started with nothing to share",
            null,
            shadowOf(composeRule.activity).nextStartedActivity,
        )
    }

    /**
     * The spec scenario "Không có ứng dụng nào nhận": with data to share but no app accepting
     * `application/json`, the action must report and not crash. The file is still written first — the
     * activity only discovers the missing receiver when it builds the intent — so this asserts both
     * halves: message shown, nothing started.
     */
    @Test
    fun shareAction_withNoJsonReceiver_reportsAndStartsNoIntent() {
        val activity = composeRule.activity
        val container = (activity.application as ThuoclaApp).container
        val today = LocalDate.now(container.clock).toString()
        runBlocking { container.repository.addRecord(today, 505) }

        // Deliberately register nothing: PackageManager has no ACTION_SEND receiver for application/json.
        shadowOf(activity).nextStartedActivity
        composeRule.onNodeWithTag("tabData").performClick()
        composeRule.onNodeWithTag("shareJson").performScrollTo().performClick()

        composeRule.waitUntil(TIMEOUT_MS) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodes(
                androidx.compose.ui.test.hasText(DataViewModel.SHARE_UNAVAILABLE, substring = true),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            "no intent may be started when no app accepts application/json",
            null,
            shadowOf(activity).peekNextStartedActivity(),
        )
    }

    /**
     * Robolectric's PackageManager does not implement API 30+ package-visibility filtering, so the
     * "receiver is invisible without <queries>" failure cannot be exercised behaviourally in a JVM test.
     * It was measured on device instead: with the declaration removed and the only JSON receiver a
     * user-installed app, the share was refused with "Không có ứng dụng nào nhận được bản sao JSON";
     * with the declaration present, the same build delivered ACTION_SEND to that receiver.
     *
     * This asserts the declaration itself, because losing it is silent in every other test and the
     * consequence is a user who cannot share at all.
     */
    @Test
    fun manifest_declaresTheSendQueryThatMakesTheReceiverGuardHonest() {
        // Gradle runs unit tests with the module as working directory, so this is android/app.
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("source manifest not found at ${manifest.absolutePath}", manifest.exists())

        val text = manifest.readText()
        val queries = Regex("<queries>.*?</queries>", RegexOption.DOT_MATCHES_ALL)
            .find(text)?.value
        assertNotNull(
            "<queries> is missing: on API 30+ resolveActivity returns null for installed receivers, " +
                "so the share would be refused for a user whose only receiver is a third-party app",
            queries,
        )

        val block = queries!!
        assertTrue(
            "<queries> must declare ACTION_SEND, found: $block",
            block.contains("android.intent.action.SEND"),
        )
        assertTrue(
            "<queries> must declare the application/json data type, found: $block",
            block.contains("application/json"),
        )
    }

    private companion object {
        /**
         * Generous on purpose: these are wall-clock waits on a shared box, and the fleet runs many Gradle
         * daemons at once (one full-gate run took 29 minutes under load 200+). A tight budget here fails
         * on contention rather than on behaviour, which is a false signal.
         */
        const val TIMEOUT_MS = 60_000L
    }
}
