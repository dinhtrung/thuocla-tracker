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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import vn.dinhtrung.thuocla.domain.CigRecord
import vn.dinhtrung.thuocla.domain.Triggers
import vn.dinhtrung.thuocla.domain.formatHhMm

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
    modifier: Modifier = Modifier,
) {
    var showAddAtDialog by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<CigRecord?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Hôm nay",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            CounterCard(state = state, onAdd = onAdd, onUndo = onUndo) { showAddAtDialog = true }

            Text(
                text = "Điếu hôm nay",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recordList"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (state.records.isEmpty()) {
                    item { Text("Chưa có điếu nào hôm nay", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(state.records, key = { it.minuteOfDay }) { record ->
                    RecordRow(record = record, onClick = { editingRecord = record })
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
            color = Color.White,
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
                .background(Color.Black.copy(alpha = 0.32f))
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
