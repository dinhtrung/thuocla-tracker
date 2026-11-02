package vn.dinhtrung.thuocla.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The single source of truth (spec `offline-store`). No [RoomDatabase.Builder.fallbackToDestructiveMigration]:
 * a schema change must ship an explicit migration, because silently dropping the user's log is the
 * worst bug this app can have.
 */
@Database(
    entities = [RecordEntity::class, ConfigEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recordDao(): RecordDao

    abstract fun configDao(): ConfigDao

    companion object {
        const val NAME = "thuocla.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME).build()
    }
}
