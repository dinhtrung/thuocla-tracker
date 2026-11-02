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
 * User configuration. The defaults match the PWA's fallback object
 * (`{ goal: 10, intervalGoal: 60, pricePerPack: 25000, cigsPerPack: 20 }`).
 */
data class TrackerConfig(
    val goal: Int = 10,
    val intervalGoal: Int = 60,
    val pricePerPack: Long = 25_000L,
    val cigsPerPack: Int = 20,
) {
    /** A field that is zero or negative is "not filled in" and falls back to the default. */
    fun normalized(): TrackerConfig = TrackerConfig(
        goal = goal.takeIf { it > 0 } ?: DEFAULT.goal,
        intervalGoal = intervalGoal.takeIf { it > 0 } ?: DEFAULT.intervalGoal,
        pricePerPack = pricePerPack.takeIf { it > 0 } ?: DEFAULT.pricePerPack,
        cigsPerPack = cigsPerPack.takeIf { it > 0 } ?: DEFAULT.cigsPerPack,
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
