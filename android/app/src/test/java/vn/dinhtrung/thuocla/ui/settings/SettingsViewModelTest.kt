package vn.dinhtrung.thuocla.ui.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.dinhtrung.thuocla.data.TrackerRepository
import vn.dinhtrung.thuocla.data.db.AppDatabase
import vn.dinhtrung.thuocla.domain.TrackerConfig
import vn.dinhtrung.thuocla.testutil.MainDispatcherRule

/**
 * tasks.md 7.1 / 7.2 at the state level: the defaults 10 / 60 / 25000 / 20 appear for an empty
 * store, blank or non-positive input saves the default instead of an empty config, a real value is
 * persisted, and the footer carries the version plus the number of days with data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: TrackerRepository

    private val day = "2026-11-02"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TrackerRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun viewModel() = SettingsViewModel(repo, versionName = "1.0.0")

    @Test
    fun emptyStore_showsTheFourDefaults() = runBlocking {
        val vm = viewModel()
        awaitUntil("defaults loaded") { vm.state.value.fields.goal == "10" }

        val fields = vm.state.value.fields
        assertEquals("10", fields.goal)
        assertEquals("60", fields.intervalGoal)
        assertEquals("25000", fields.pricePerPack)
        assertEquals("20", fields.cigsPerPack)
        assertEquals(TrackerConfig.DEFAULT, vm.state.value.storedConfig)
    }

    @Test
    fun blankAndZeroFields_saveTheDefaults_notAnEmptyConfig() = runBlocking {
        val vm = viewModel()
        awaitUntil("defaults loaded") { vm.state.value.fields.goal == "10" }

        vm.onGoalChange("")
        vm.onIntervalGoalChange("0")
        vm.onPricePerPackChange("")
        vm.onCigsPerPackChange("-3")
        vm.save()
        awaitUntil("save finished") { vm.state.value.savedMessage != null }

        assertEquals(TrackerConfig.DEFAULT, repo.getConfig())
        assertEquals("10", vm.state.value.fields.goal)
        assertEquals("60", vm.state.value.fields.intervalGoal)
        assertEquals("25000", vm.state.value.fields.pricePerPack)
        assertEquals("20", vm.state.value.fields.cigsPerPack)
        assertTrue(vm.state.value.savedMessage!!.contains("mặc định"))
    }

    @Test
    fun changingGoalToEight_persistsIt_andKeepsOtherFields() = runBlocking {
        val vm = viewModel()
        awaitUntil("defaults loaded") { vm.state.value.fields.goal == "10" }

        vm.onGoalChange("8")
        vm.save()
        awaitUntil("goal saved") { repo.getConfig().goal == 8 }

        assertEquals(8, repo.getConfig().goal)
        assertEquals(60, repo.getConfig().intervalGoal)
        assertEquals("8", vm.state.value.fields.goal)
        assertEquals("Đã lưu", vm.state.value.savedMessage)
    }

    @Test
    fun footer_showsVersionAndDaysWithData() = runBlocking {
        repo.addRecord(day, 615)
        repo.addRecord("2026-10-31", 500)
        val vm = viewModel()

        awaitUntil("two days counted") { vm.state.value.daysWithData == 2 }
        assertEquals("v1.0.0 · 2 ngày", vm.state.value.footer)
    }

    private suspend fun awaitUntil(what: String, condition: suspend () -> Boolean) {
        val reached = withTimeoutOrNull(5_000) {
            while (!condition()) delay(10)
            true
        }
        assertTrue("timed out waiting for: $what", reached == true)
    }
}
