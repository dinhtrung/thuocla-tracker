package vn.dinhtrung.thuocla.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The single source of truth (spec `offline-store`). No [RoomDatabase.Builder.fallbackToDestructiveMigration]:
 * a schema change must ship an explicit migration, because silently dropping the user's log is the
 * worst bug this app can have.
 *
 * Version 2 adds `config.themeMode` (#19). The migration only appends a column with a default, so the
 * `records` table — every cigarette the user ever logged — is not touched.
 */
@Database(
    entities = [RecordEntity::class, ConfigEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recordDao(): RecordDao

    abstract fun configDao(): ConfigDao

    companion object {
        const val NAME = "thuocla.db"

        /**
         * Adds `config.themeMode`. `ALTER TABLE ADD COLUMN` with a constant default is an O(1)
         * metadata change in SQLite, and existing rows read back as `SYSTEM` — the app follows the
         * device until the user picks a mode, and no cigarette is lost.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE config ADD COLUMN themeMode TEXT NOT NULL DEFAULT 'SYSTEM'",
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
