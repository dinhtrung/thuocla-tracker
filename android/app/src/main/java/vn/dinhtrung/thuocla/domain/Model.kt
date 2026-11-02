package vn.dinhtrung.thuocla.domain

/**
 * One logged cigarette.
 *
 * [dateKey] is the local calendar date "YYYY-MM-DD" and [minuteOfDay] the local wall-clock minute
 * (0..1439). Storing wall-clock instead of a UTC instant is design decision #2: a record keeps the
 * day and time the user saw when they logged it, even if the device time zone changes later.
 */
data class CigRecord(
    val dateKey: String,
    val minuteOfDay: Int,
    val triggerIdx: Int? = null,
    val note: String? = null,
)

/**
 * Which colour scheme the app draws with (#19).
 *
 * Stored in the config row by its [name], so adding a mode later never renumbers the existing ones.
 * [SYSTEM] is the default: a user who never opens Cài đặt sees the app follow the device, and an app
 * upgraded from a build without this field lands on [SYSTEM] rather than on a forced scheme.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    /** True when this mode should render the dark scheme, given whether the device is in dark mode. */
    fun resolveIsDark(systemInDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDark
        LIGHT -> false
        DARK -> true
    }

    /** The label the Cài đặt picker shows for this mode. */
    val label: String
        get() = when (this) {
            SYSTEM -> "Theo hệ thống"
            LIGHT -> "Sáng"
            DARK -> "Tối"
        }

    companion object {
        val DEFAULT = SYSTEM

        /**
         * Parses a stored value; anything unrecognised (an older row, a hand-edited backup) falls back
         * to [DEFAULT] instead of throwing in a startup path.
         */
        fun fromStorage(raw: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * User configuration. The four numeric defaults match the PWA's fallback object
 * (`{ goal: 10, intervalGoal: 60, pricePerPack: 25000, cigsPerPack: 20 }`).
 *
 * [themeMode] is native-only: the PWA keeps its theme under a separate `theme` key, but here the
 * config row is already the single settings record, so the mode lives beside the other settings.
 */
data class TrackerConfig(
    val goal: Int = 10,
    val intervalGoal: Int = 60,
    val pricePerPack: Long = 25_000L,
    val cigsPerPack: Int = 20,
    val themeMode: ThemeMode = ThemeMode.DEFAULT,
) {
    /**
     * A numeric field that is zero or negative is "not filled in" and falls back to the default.
     * [themeMode] is not numeric and every value of it is a real choice, so it passes through.
     */
    fun normalized(): TrackerConfig = TrackerConfig(
        goal = goal.takeIf { it > 0 } ?: DEFAULT.goal,
        intervalGoal = intervalGoal.takeIf { it > 0 } ?: DEFAULT.intervalGoal,
        pricePerPack = pricePerPack.takeIf { it > 0 } ?: DEFAULT.pricePerPack,
        cigsPerPack = cigsPerPack.takeIf { it > 0 } ?: DEFAULT.cigsPerPack,
        themeMode = themeMode,
    )

    companion object {
        val DEFAULT = TrackerConfig()
    }
}

/** Number of reasons in the PWA `TRIGGERS` array; `triggerIdx` is an index into it. */
const val TRIGGER_COUNT = 8

/** Why a write was refused. [NONE] means the write landed. */
enum class WriteBlock { NONE, DUPLICATE_MINUTE, INVALID }

/**
 * Result of an insert or a time move.
 *
 * [record] is the row as it now lives in the store on success, or the row that blocked the write on
 * [WriteBlock.DUPLICATE_MINUTE], or null when the input was invalid.
 */
data class WriteResult(
    val applied: Boolean,
    val record: CigRecord? = null,
    val block: WriteBlock = WriteBlock.NONE,
)
