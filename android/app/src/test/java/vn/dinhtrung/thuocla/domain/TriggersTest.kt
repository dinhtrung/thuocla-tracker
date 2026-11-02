package vn.dinhtrung.thuocla.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the eight reasons to the PWA's array, so the list and [TRIGGER_COUNT] cannot drift. */
class TriggersTest {

    @Test
    fun all_hasExactlyTriggerCountEntries() {
        assertEquals(TRIGGER_COUNT, Triggers.ALL.size)
        assertEquals(8, Triggers.ALL.size)
    }

    @Test
    fun all_isInTheSpecOrder() {
        assertEquals(
            listOf("Sau ăn", "Cà phê", "Stress", "Nhậu", "Buồn", "Thói quen", "Thèm", "Khác"),
            Triggers.ALL.map { it.name },
        )
        assertEquals(
            listOf("🍜", "☕", "😤", "🍻", "😞", "🌀", "🚬", "🤷"),
            Triggers.ALL.map { it.emoji },
        )
        Triggers.ALL.forEachIndexed { index, trigger -> assertEquals(index, trigger.idx) }
    }

    @Test
    fun label_isEmojiThenName_andTheNameHasNoLeadingSpace() {
        val coffee = Triggers.ALL[1]
        assertEquals("☕ Cà phê", coffee.label)
        // The CSV export strips the emoji with `split(' ').slice(1)`, which needs the name to be the
        // remainder of the label without a leading space.
        assertEquals(coffee.name, coffee.label.substringAfter(' '))
        assertTrue(Triggers.ALL.none { it.label.startsWith(" ") })
    }

    @Test
    fun byIdx_handlesUnknownIndexes() {
        assertEquals("Stress", Triggers.byIdx(2)?.name)
        assertEquals(null, Triggers.byIdx(null))
        assertEquals(null, Triggers.byIdx(8))
        assertEquals("", Triggers.label(99))
    }
}
