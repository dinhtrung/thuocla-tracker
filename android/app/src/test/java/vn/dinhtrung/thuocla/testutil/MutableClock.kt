package vn.dinhtrung.thuocla.testutil

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * A clock the test moves by hand.
 *
 * The +1 path reads the time from `AppContainer.clock`, so freezing and stepping it is the only way
 * to exercise the acceptance scenarios (14:07 -> 14:05, and two taps one hour apart) without waiting.
 */
class MutableClock(
    private var current: Instant,
    private val zone: ZoneId,
) : Clock() {

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    override fun instant(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }

    companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

        /** e.g. `at("2026-11-02T14:07:30+07:00")`. */
        fun at(isoOffsetDateTime: String, zone: ZoneId = ZONE): MutableClock =
            MutableClock(OffsetDateTime.parse(isoOffsetDateTime).toInstant(), zone)
    }
}
