package vn.dinhtrung.thuocla.domain

import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class TimeAndTriggersTest {

    @Test
    fun roundTo5_roundsToTheNearestFiveMinutes() {
        // tasks.md 3.1 numbers, restated here because stats reuse the same rule for time edits.
        assertEquals(14 * 60 + 5, roundTo5(14 * 60 + 7))
        assertEquals(14 * 60 + 10, roundTo5(14 * 60 + 8))
        assertEquals(0, roundTo5(2))
        assertEquals(5, roundTo5(3))
        assertEquals(10, roundTo5(12))
    }

    @Test
    fun roundTo5_neverCrossesMidnight() {
        assertEquals(1435, roundTo5(1439))
        assertEquals(1435, roundTo5(1436))
        assertEquals(0, roundTo5(-1))
    }

    @Test
    fun formatMinuteOfDay_padsToFourDigits() {
        assertEquals("00:00", formatMinuteOfDay(0))
        assertEquals("00:05", formatMinuteOfDay(5))
        assertEquals("10:15", formatMinuteOfDay(10 * 60 + 15))
        assertEquals("23:55", formatMinuteOfDay(1435))
    }

    @Test
    fun dayName_matchesThePwaLabels() {
        assertEquals("T2", dayName(LocalDate.of(2026, 11, 2)))   // Monday
        assertEquals("T7", dayName(LocalDate.of(2026, 10, 31)))  // Saturday
        assertEquals("CN", dayName(LocalDate.of(2026, 11, 1)))   // Sunday
    }

    @Test
    fun triggers_areEightInThePwaOrder() {
        assertEquals(TRIGGER_COUNT, TRIGGERS.size)
        assertEquals(
            listOf(
                "🍜 Sau ăn",
                "☕ Cà phê",
                "😤 Stress",
                "🍻 Nhậu",
                "😞 Buồn",
                "🌀 Thói quen",
                "🚬 Thèm",
                "🤷 Khác",
            ),
            TRIGGERS,
        )
        assertEquals("Sau ăn", triggerName(0))
        assertEquals("Khác", triggerName(7))
        assertNull(triggerName(null))
        assertNull(triggerName(8))
        assertTrue(triggerLabel(1)!!.startsWith("☕"))
    }
}
