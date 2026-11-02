package vn.dinhtrung.thuocla.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.dinhtrung.thuocla.data.TrackerRepository
import vn.dinhtrung.thuocla.domain.CigRecord
import vn.dinhtrung.thuocla.domain.ThemeMode
import vn.dinhtrung.thuocla.domain.TrackerConfig

/**
 * #19: `themeMode` is a stored setting, not a view-layer preference. It survives a process restart,
 * and upgrading a version-1 database appends the column without touching a single cigarette row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeConfigStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var db: AppDatabase? = null

    private val day = "2026-11-02"

    @Before
    fun setUp() {
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(DB_NAME)
    }

    // ------------------------------------------------------------ persistence

    @Test
    fun chosenMode_survivesClosingAndReopeningTheDatabase() = runTest {
        open()
        TrackerRepository(db!!).saveConfig(TrackerConfig(goal = 8, themeMode = ThemeMode.LIGHT))
        db!!.close()

        open()
        val reopened = TrackerRepository(db!!)
        assertEquals(ThemeMode.LIGHT, reopened.getConfig().themeMode)
        assertEquals(8, reopened.getConfig().goal)
    }

    @Test
    fun aStoreThatNeverChoseAMode_readsBackAsSystem() = runTest {
        open()

        assertEquals(ThemeMode.SYSTEM, TrackerRepository(db!!).getConfig().themeMode)
        assertEquals(ThemeMode.DEFAULT, ThemeMode.SYSTEM)
    }

    @Test
    fun unknownStoredText_readsBackAsSystemInsteadOfThrowing() = runTest {
        open()
        // A hand-edited backup or a column written by a future build: the app must still start.
        db!!.openHelper.writableDatabase.execSQL("UPDATE config SET themeMode = 'sepia' WHERE id = 1")

        assertEquals(ThemeMode.SYSTEM, TrackerRepository(db!!).getConfig().themeMode)
    }

    // -------------------------------------------------------------- migration

    /**
     * "migration từ DB cũ chạy được": build a version-1 database by hand, let Room migrate it, and
     * check both halves of the promise — every cigarette is still there and the config row now reads
     * `SYSTEM`.
     */
    @Test
    fun migratingAVersionOneDatabase_keepsEveryRecordAndAddsTheDefaultMode() = runTest {
        seedVersionOneDatabase()

        open()
        val repo = TrackerRepository(db!!)

        assertEquals(
            listOf(
                CigRecord(day, 615, triggerIdx = 2, note = "after lunch"),
                CigRecord(day, 720),
            ),
            repo.day(day),
        )
        val config = repo.getConfig()
        assertEquals(8, config.goal)
        assertEquals(45, config.intervalGoal)
        assertEquals(30_000L, config.pricePerPack)
        assertEquals(ThemeMode.SYSTEM, config.themeMode)
    }

    /**
     * A real version-1 database file: the DDL Room generated for schema 1 (config without `themeMode`),
     * one config row and two cigarettes, then `PRAGMA user_version = 1` so Room knows to migrate.
     */
    private fun seedVersionOneDatabase() {
        val file = context.getDatabasePath(DB_NAME)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { v1 ->
            v1.execSQL(
                "CREATE TABLE IF NOT EXISTS `records` (`dateKey` TEXT NOT NULL, " +
                    "`minuteOfDay` INTEGER NOT NULL, `triggerIdx` INTEGER, `note` TEXT, " +
                    "PRIMARY KEY(`dateKey`, `minuteOfDay`))",
            )
            v1.execSQL(
                "CREATE TABLE IF NOT EXISTS `config` (`id` INTEGER NOT NULL, " +
                    "`goal` INTEGER NOT NULL, `intervalGoal` INTEGER NOT NULL, " +
                    "`pricePerPack` INTEGER NOT NULL, `cigsPerPack` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            v1.execSQL(
                "INSERT INTO records(dateKey, minuteOfDay, triggerIdx, note) " +
                    "VALUES('$day', 615, 2, 'after lunch')",
            )
            v1.execSQL("INSERT INTO records(dateKey, minuteOfDay) VALUES('$day', 720)")
            v1.execSQL(
                "INSERT INTO config(id, goal, intervalGoal, pricePerPack, cigsPerPack) " +
                    "VALUES(1, 8, 45, 30000, 20)",
            )
            v1.version = 1
        }
        assertEquals(1, SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version })
    }

    private fun open() {
        db = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    private companion object {
        const val DB_NAME = "theme-test.db"
    }
}
