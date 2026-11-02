package vn.dinhtrung.thuocla.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import vn.dinhtrung.thuocla.domain.ComparisonRow
import vn.dinhtrung.thuocla.domain.DailySummary
import vn.dinhtrung.thuocla.domain.formatAverage
import vn.dinhtrung.thuocla.domain.lowestDayLabel
import vn.dinhtrung.thuocla.domain.CigRecord
import vn.dinhtrung.thuocla.domain.Triggers
import vn.dinhtrung.thuocla.domain.formatHhMm
import vn.dinhtrung.thuocla.ui.theme.ScrimBlack

/**
 * Screen "Hôm nay" for the cigarette-log slice: counter + goal progress, the big +1 button, the undo
 * button, the in-card duplicate warning, today's records, and the reason sheet that opens after +1.
 *
 * Group 4 adds the clock, quick stats, comparison table and cut note around this card.
 */
@Composable
fun TodayScreen(
    state: TodayUiState,
    onAdd: () -> Unit,
    onUndo: () -> Unit,
    onSelectTrigger: (Int) -> Unit,
    onDismissTriggerSheet: () -> Unit,
    onAddAt: (String, Int, Int) -> Unit,
    onEditTime: (String, Int, Int) -> Unit,
    /**
     * Opens the day detail for a quick-stat card (spec `daily-summary`, "Ngày đầu tiên dùng app":
     * the "ít nhất" card must open today's detail when tapped). The PWA's `openLowestDay()` and
     * `goToDay()` switch to Thống kê and select the day; the host activity does the same here.
     *
     * Defaulted so a host that has no day detail to show (a preview, a slice's own test) can omit it:
     * #16's `SettingsFlowTest` calls this screen without it, and a required parameter broke the merged
     * tree even though each branch was green alone. A no-op default keeps the tap harmless instead.
     */
    onOpenDay: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showAddAtDialog by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<CigRecord?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("todayScroll"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "Hôm nay",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            item { IntervalCard(state = state) }
            item { CounterCard(state = state, onAdd = onAdd, onUndo = onUndo) { showAddAtDialog = true } }
            item {
                Text(
                    text = "Điếu hôm nay",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.records.isEmpty()) {
                item {
                    Text("Chưa có điếu nào hôm nay", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.records, key = { "record_${it.minuteOfDay}" }) { record ->
                RecordRow(record = record, onClick = { editingRecord = record })
            }
            item { QuickStats(state = state, onOpenDay = onOpenDay) }
            state.summary?.let { summary ->
                item {
                    ComparisonTable(
                        summary = summary,
                        rows = state.comparisonRows(),
                        intervalGoal = state.interval.intervalGoal,
                        onEditRow = { row -> row.today?.let { editingRecord = it } },
                    )
                }
            }
        }

        if (state.triggerTarget != null) {
            TriggerSheet(
                selectedIdx = state.selectedTriggerIdx,
                onSelect = onSelectTrigger,
                onDismiss = onDismissTriggerSheet,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    editingRecord?.let { record ->
        EditTimeDialog(
            record = record,
            onDismiss = { editingRecord = null },
            onConfirm = { minuteOfDay ->
                editingRecord = null
                onEditTime(record.dateKey, record.minuteOfDay, minuteOfDay)
            },
        )
    }

    if (showAddAtDialog) {
        AddAtDialog(
            defaultDateKey = state.dateKey,
            onDismiss = { showAddAtDialog = false },
            onConfirm = { dateKey, hour, minute ->
                showAddAtDialog = false
                onAddAt(dateKey, hour, minute)
            },
        )
    }
}

/**
 * Clock card (tasks.md 4.2): time elapsed since the last cigarette, the interval goal, and either the
 * expected next time with the minutes left or the minutes already over the goal.
 */
@Composable
private fun IntervalCard(state: TodayUiState) {
    val interval = state.interval
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("intervalCard"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Từ điếu cuối",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (state.records.isEmpty()) "--:--" else formatHhMm(interval.elapsedMinutes),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("intervalElapsed"),
                )
                Text(
                    text = "Mục tiêu: ≥${interval.intervalGoal} phút",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (interval.expectedNext != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "🎯 Dự kiến",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = interval.expectedNext,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("intervalNext"),
                    )
                    Text(
                        text = "còn ${interval.minutesRemaining} phút",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("intervalRemaining"),
                    )
                }
            } else if (interval.reachedGoal && state.records.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "✅ Đạt mục tiêu",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "+${interval.minutesOver} phút",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("intervalOver"),
                    )
                }
            }
        }
    }
}

/**
 * The four quick stats (tasks.md 4.1), in the PWA's order and labels.
 *
 * The "ít nhất" and "Hôm qua" cards are clickable exactly like the PWA's `.stat-card` buttons
 * (`onclick="openLowestDay()"` / `goToDay(getYesterdayKey())`): the spec's "Ngày đầu tiên dùng app"
 * scenario requires the "ít nhất" card to open the day detail. "TB 7 ngày" and "Tháng này" are
 * aggregates over many days rather than one day, so they stay static.
 */
@Composable
private fun QuickStats(state: TodayUiState, onOpenDay: (String) -> Unit) {
    val summary = state.summary
    val lowest = summary?.lowestDay
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quickStats"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickStatCard(
            value = lowest?.count?.toString() ?: "-",
            label = lowestDayLabel(lowest),
            modifier = Modifier
                .weight(1f)
                .testTag("statLowest"),
            onClick = lowest?.takeIf { it.count > 0 }?.let { day -> { onOpenDay(day.dateKey) } },
        )
        QuickStatCard(
            value = summary?.yesterdayCount?.toString() ?: "-",
            label = "Hôm qua",
            modifier = Modifier
                .weight(1f)
                .testTag("statYesterday"),
            onClick = summary?.yesterdayKey?.let { key -> { onOpenDay(key) } },
        )
        QuickStatCard(
            value = summary?.let { formatAverage(it.avg7Days) } ?: "-",
            label = "TB 7 ngày",
            modifier = Modifier
                .weight(1f)
                .testTag("statAvg7"),
        )
        QuickStatCard(
            value = summary?.monthCount?.toString() ?: "-",
            label = "Tháng này",
            modifier = Modifier
                .weight(1f)
                .testTag("statMonth"),
        )
    }
}

@Composable
private fun QuickStatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Three-column comparison table (tasks.md 4.3): Hôm nay | Ngày ít nhất | TB 30 ngày.
 *
 * Every cell carries the time and, from the second cigarette on, the gap to the previous one with
 * its difference from the interval goal. The "Hôm nay" column is always drawn in full — the two
 * reference columns show "—" while there is no history.
 */
@Composable
private fun ComparisonTable(
    summary: DailySummary,
    rows: List<ComparisonRow>,
    intervalGoal: Int,
    onEditRow: (ComparisonRow) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("comparisonTable"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "📋 So sánh",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            ComparisonHeader("Hôm nay", "(${rows.count { it.today != null }})", Modifier.weight(1f))
            ComparisonHeader(
                "Ngày ít nhất",
                summary.lowestDay?.takeIf { it.isReference }?.let { "(${it.count})" } ?: "(-)",
                Modifier.weight(1f),
                Alignment.CenterHorizontally,
            )
            ComparisonHeader(
                "TB 30 ngày",
                summary.avgCount.takeIf { it > 0f }?.let { "(${formatAverage(it)})" } ?: "(-)",
                Modifier.weight(1f),
                Alignment.End,
            )
        }
        if (rows.isEmpty()) {
            Text("Chưa có điếu nào hôm nay", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("comparisonRow_${row.stt}")
                    .clickable(enabled = row.today != null) { onEditRow(row) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ComparisonCell(
                    time = row.today?.minuteOfDay?.let(::formatHhMm) ?: "—",
                    gap = row.today?.let { gapToPrevious(rows, row.stt, intervalGoal) },
                    modifier = Modifier.weight(1f),
                )
                ComparisonCell(
                    time = row.lowest?.minuteOfDay?.let(::formatHhMm) ?: "—",
                    gap = row.lowest?.let { gapToPreviousLowest(rows, row.stt, intervalGoal) },
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                )
                ComparisonCell(
                    time = row.avg?.avgMinute?.let(::formatHhMm) ?: "—",
                    gap = row.avg?.avgGapMinutes?.let { gapText(it, intervalGoal) },
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                )
            }
        }
    }
}

@Composable
private fun ComparisonHeader(
    title: String,
    count: String,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(
            text = "$title $count",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ComparisonCell(
    time: String,
    gap: String?,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(text = time, fontWeight = FontWeight.SemiBold)
        Text(
            text = gap ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "85 phút ✅ +25" style cell text: gap to the previous cigarette and its distance from the goal. */
private fun gapText(gapMinutes: Int, intervalGoal: Int): String {
    if (gapMinutes <= 0) return "—"
    return if (intervalGoal > 0) {
        if (gapMinutes < intervalGoal) {
            "$gapMinutes phút ⚠️ -${intervalGoal - gapMinutes}"
        } else {
            "$gapMinutes phút ✅ +${gapMinutes - intervalGoal}"
        }
    } else {
        "$gapMinutes phút"
    }
}

private fun gapToPrevious(rows: List<ComparisonRow>, stt: Int, intervalGoal: Int): String? {
    if (stt <= 1) return null
    val previous = rows.getOrNull(stt - 2)?.today ?: return null
    val current = rows[stt - 1].today ?: return null
    return gapText(current.minuteOfDay - previous.minuteOfDay, intervalGoal)
}

private fun gapToPreviousLowest(rows: List<ComparisonRow>, stt: Int, intervalGoal: Int): String? {
    if (stt <= 1) return null
    val previous = rows.getOrNull(stt - 2)?.lowest ?: return null
    val current = rows[stt - 1].lowest ?: return null
    return gapText(current.minuteOfDay - previous.minuteOfDay, intervalGoal)
}
@Composable
private fun CounterCard(
    state: TodayUiState,
    onAdd: () -> Unit,
    onUndo: () -> Unit,
    onAddAtClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("counterCard"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${state.count} / ${state.goal} điếu",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("todayCounter"),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(state.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .testTag("todayProgress"),
                color = MaterialTheme.colorScheme.primary,
            )

            state.warning?.let { warning ->
                // Banner inside the counter card — the spec forbids a floating toast here.
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("counterWarning"),
                ) {
                    Text(
                        text = warning,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onUndo, modifier = Modifier.testTag("undoCig")) {
                    Text("↶ Hoàn tác")
                }
                Spacer(modifier = Modifier.weight(1f))
                AddCigButton(onClick = onAdd)
            }

            TextButton(onClick = onAddAtClick, modifier = Modifier.testTag("addAtOpen")) {
                Text("Thêm giờ khác")
            }
        }
    }
}

/** Large gradient +1 button (tasks.md 3.2). */
@Composable
private fun AddCigButton(onClick: () -> Unit) {
    val brush = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
    )
    Box(
        modifier = Modifier
            .height(64.dp)
            .width(160.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .testTag("addCig")
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+1",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun RecordRow(record: CigRecord, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("record_${record.minuteOfDay}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatHhMm(record.minuteOfDay),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(64.dp),
        )
        Text(text = Triggers.label(record.triggerIdx))
        record.note?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The reason sheet: eight fixed choices in spec order, the current reason highlighted, tapping the
 * highlighted one clears it. Rendered as a bottom-anchored surface rather than a dialog so it lives
 * in the same test tree as the screen.
 */
@Composable
private fun TriggerSheet(
    selectedIdx: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Scrim: tapping outside the sheet dismisses it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(ScrimBlack.copy(alpha = 0.32f))
                .clickable(onClick = onDismiss),
        )
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("triggerSheet"),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "☕ Lý do hút?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("triggerSheetClose")) {
                        Text("✕")
                    }
                }
                Triggers.ALL.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { trigger ->
                            val selected = trigger.idx == selectedIdx
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                border = if (selected) {
                                    androidx.compose.foundation.BorderStroke(
                                        1.5.dp,
                                        MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("trigger_${trigger.idx}")
                                    .clickable { onSelect(trigger.idx) },
                            ) {
                                Text(
                                    text = trigger.label,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Thêm giờ khác": date + time, validated before it reaches the log. */
@Composable
private fun AddAtDialog(
    defaultDateKey: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int) -> Unit,
) {
    var date by remember { mutableStateOf(defaultDateKey) }
    var time by remember { mutableStateOf("") }
    val parsed = parseHhMm(time)
    val validDate = runCatching { java.time.LocalDate.parse(date) }.isSuccess

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm điếu vào giờ khác") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Ngày (YYYY-MM-DD)") },
                    isError = !validDate,
                    singleLine = true,
                    modifier = Modifier.testTag("addAtDate"),
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Giờ (HH:mm)") },
                    isError = parsed == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.testTag("addAtTime"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onConfirm(date, it.first, it.second) } },
                enabled = validDate && parsed != null,
                modifier = Modifier.testTag("addAtConfirm"),
            ) {
                Text("Thêm")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } },
    )
}

/** Editing an existing cigarette's time: the new time is rounded and duplicate-checked too. */
@Composable
private fun EditTimeDialog(
    record: CigRecord,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var time by remember { mutableStateOf(formatHhMm(record.minuteOfDay)) }
    val parsed = parseHhMm(time)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sửa giờ điếu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Giờ (HH:mm)") },
                    isError = parsed == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.testTag("editTimeInput"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onConfirm(it.first * 60 + it.second) } },
                enabled = parsed != null,
                modifier = Modifier.testTag("editTimeConfirm"),
            ) {
                Text("Lưu")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } },
    )
}

/** Parses "H:mm" / "HH:mm" into hour/minute, or null. */
internal fun parseHhMm(raw: String): Pair<Int, Int>? {
    val parts = raw.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) hour to minute else null
}
