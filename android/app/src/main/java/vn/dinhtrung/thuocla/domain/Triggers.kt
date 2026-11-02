package vn.dinhtrung.thuocla.domain

/**
 * One of the eight fixed reasons, in the PWA's order.
 *
 * Only the index is stored (design decision #4): [emoji] is presentation and [name] is the CSV label
 * the PWA derives with `split(' ').slice(1).join(' ')`, so neither may be persisted.
 */
data class Trigger(val idx: Int, val emoji: String, val name: String) {
    val label: String get() = "$emoji $name"
}

/**
 * The single source of truth for the reason list, mirroring `TRIGGERS` in app.js v1.5.2.
 *
 * [TRIGGER_COUNT] lives in [Model] because the store validates against it; [ALL] is the display and
 * order contract. `TriggersTest` pins `ALL.size == TRIGGER_COUNT` and the exact order, so the two
 * cannot disagree without a red test.
 */
object Triggers {

    val ALL: List<Trigger> = listOf(
        Trigger(0, "🍜", "Sau ăn"),
        Trigger(1, "☕", "Cà phê"),
        Trigger(2, "😤", "Stress"),
        Trigger(3, "🍻", "Nhậu"),
        Trigger(4, "😞", "Buồn"),
        Trigger(5, "🌀", "Thói quen"),
        Trigger(6, "🚬", "Thèm"),
        Trigger(7, "🤷", "Khác"),
    )

    init {
        check(ALL.size == TRIGGER_COUNT) {
            "Triggers.ALL has ${ALL.size} entries but TRIGGER_COUNT is $TRIGGER_COUNT"
        }
        check(ALL.mapIndexed { i, t -> t.idx == i }.all { it }) { "Trigger.idx must match its position" }
    }

    fun byIdx(idx: Int?): Trigger? = idx?.let { ALL.getOrNull(it) }

    fun label(idx: Int?): String = byIdx(idx)?.label ?: ""
}
