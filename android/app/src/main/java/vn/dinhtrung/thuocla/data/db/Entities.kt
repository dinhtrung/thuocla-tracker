package vn.dinhtrung.thuocla.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A cigarette row.
 *
 * The composite primary key (dateKey, minuteOfDay) is the UNIQUE INDEX required by tasks.md 2.1:
 * SQLite backs a composite primary key with a unique index, so a second row for the same minute on
 * the same day cannot be stored at all — not through the repository, not through an import path,
 * not through a future feature. That is deliberate: design decision #3 makes the bad state
 * unrepresentable instead of validating in the UI.
 */
@Entity(
    tableName = "records",
    primaryKeys = ["dateKey", "minuteOfDay"],
)
data class RecordEntity(
    @ColumnInfo(name = "dateKey") val dateKey: String,
    @ColumnInfo(name = "minuteOfDay") val minuteOfDay: Int,
    @ColumnInfo(name = "triggerIdx") val triggerIdx: Int? = null,
    @ColumnInfo(name = "note") val note: String? = null,
)

/**
 * Single-row configuration table; [id] is always 1.
 *
 * [themeMode] was added in schema version 2 (#19). It is a TEXT column holding a `ThemeMode` name, and
 * the migration fills existing rows with `SYSTEM`, so an upgraded install keeps every cigarette and
 * simply follows the device until the user chooses otherwise.
 */
@Entity(tableName = "config")
data class ConfigEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "goal") val goal: Int,
    @ColumnInfo(name = "intervalGoal") val intervalGoal: Int,
    @ColumnInfo(name = "pricePerPack") val pricePerPack: Long,
    @ColumnInfo(name = "cigsPerPack") val cigsPerPack: Int,
    @ColumnInfo(name = "themeMode", defaultValue = "SYSTEM") val themeMode: String = "SYSTEM",
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
