package vn.dinhtrung.thuocla.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import kotlinx.coroutines.delay
import vn.dinhtrung.thuocla.AppContainer
import vn.dinhtrung.thuocla.domain.TRIGGERS
import vn.dinhtrung.thuocla.domain.formatMinuteOfDay
import vn.dinhtrung.thuocla.domain.history.DayDetailRow
import vn.dinhtrung.thuocla.domain.history.DayPoint
import vn.dinhtrung.thuocla.domain.history.TypeStats
import vn.dinhtrung.thuocla.domain.roundTo5
import vn.dinhtrung.thuocla.domain.triggerLabel

private val TodayBarColor = Color(0xFFFF6B81)
private val OtherBarColor = Color(0xFFE94560)

@Composable
fun StatsRoute(container: AppContainer) {
    val factory = remember(container) { StatsViewModelFactory(container.repository, container.clock) }
    val viewModel: StatsViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsState()

    StatsScreen(
        state = state,
        onPreviousWeek = viewModel::previousWeek,
        onNextWeek = viewModel::nextWeek,
        onSelectDay = viewModel::selectDay,
        onCloseDetail = viewModel::closeDayDetail,
        onMoveTime = viewModel::moveTime,
        onSetTrigger = viewModel::setTrigger,
        onClearMessage = viewModel::clearMessage,
    )
}

@Composable
fun StatsScreen(
    state: StatsUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectDay: (String) -> Unit,
    onCloseDetail: () -> Unit,
    onMoveTime: (DayDetailRow, Int) -> Unit,
    onSetTrigger: (DayDetailRow, Int?) -> Unit,
    onClearMessage: () -> Unit,
) {
    var timeEditRow by remember { mutableStateOf<DayDetailRow?>(null) }
    var reasonRow by remember { mutableStateOf<DayDetailRow?>(null) }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(3_000)
            onClearMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Thống kê",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        state.message?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("statsMessage"),
            )
        }

        ChartCard(
            state = state,
            onPreviousWeek = onPreviousWeek,
            onNextWeek = onNextWeek,
            onSelectDay = onSelectDay,
        )

        if (state.selectedDateKey != null) {
            DayDetailCard(
                state = state,
                onClose = onCloseDetail,
                onEditTime = { timeEditRow = it },
                onPickReason = { reasonRow = it },
            )
        }

        WeekendComparisonCard(state = state)
    }

    timeEditRow?.let { row ->
        TimeEditDialog(
            initialMinute = row.record.minuteOfDay,
            onDismiss = { timeEditRow = null },
            onConfirm = { minute ->
                timeEditRow = null
                onMoveTime(row, minute)
            },
        )
    }

    reasonRow?.let { row ->
        ReasonPickerDialog(
            selectedIdx = row.record.triggerIdx,
            onDismiss = { reasonRow = null },
            onPick = { idx ->
                reasonRow = null
                onSetTrigger(row, idx)
            },
        )
    }
}

@Composable
private fun ChartCard(
    state: StatsUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectDay: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("statsChart")) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPreviousWeek, modifier = Modifier.testTag("weekBack")) {
                    Text("◀ Tuần trước")
                }
                Text(
                    text = state.window.label,
                    modifier = Modifier.weight(1f).testTag("weekLabel"),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(
                    onClick = onNextWeek,
                    enabled = state.canGoForward,
                    modifier = Modifier.testTag("weekForward"),
                ) {
                    Text("Tuần sau ▶")
                }
            }

            val maxCount = maxOf(1, state.days.maxOfOrNull { it.count } ?: 0)
            Row(
                modifier = Modifier.fillMaxWidth().height(170.dp).padding(top = 12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                state.days.forEach { day -> ChartBar(day, maxCount, state, onSelectDay) }
            }

            Text(
                text = "Mục tiêu: ${state.goal} điếu/ngày",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun RowScope.ChartBar(
    day: DayPoint,
    maxCount: Int,
    state: StatsUiState,
    onSelectDay: (String) -> Unit,
) {
    val isToday = day.dateKey == state.today.toString()
    val isSelected = day.dateKey == state.selectedDateKey
    val barHeight = if (day.count == 0) 0.dp else maxOf(2f, 110f * day.count / maxCount).dp

    Column(
        modifier = Modifier
            .weight(1f)
            .clickable { onSelectDay(day.dateKey) }
            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)) else Modifier)
            .padding(vertical = 4.dp)
            .testTag("dayBar_${day.dateKey}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = day.count.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(barHeight)
                .background(if (isToday) TodayBarColor else OtherBarColor, RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (day.count > 0) day.dayName else day.dateKey.takeLast(5),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        Text(
            text = day.avgGapMinutes?.let { "≃${it}ph" } ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun DayDetailCard(
    state: StatsUiState,
    onClose: () -> Unit,
    onEditTime: (DayDetailRow) -> Unit,
    onPickReason: (DayDetailRow) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("dayDetail")) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "📋 ${state.detailTitle} (${state.detail.size} điếu)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClose, modifier = Modifier.testTag("closeDayDetail")) {
                    Text("Đóng")
                }
            }

            if (state.detail.isEmpty()) {
                Text(
                    text = "Không có điếu nào trong ngày này",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            state.detail.forEach { row ->
                DayDetailLine(
                    row = row,
                    intervalGoal = state.intervalGoal,
                    onEditTime = { onEditTime(row) },
                    onPickReason = { onPickReason(row) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DayDetailLine(
    row: DayDetailRow,
    intervalGoal: Int,
    onEditTime: () -> Unit,
    onPickReason: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatMinuteOfDay(row.record.minuteOfDay),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(56.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Điếu #${row.index}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            row.gapMinutes?.let { gap ->
                val delta = row.gapDeltaToGoal ?: (gap - intervalGoal)
                val gapText = if (delta < 0) {
                    "${gap}ph sau · ⚠️ ${-delta}ph sớm"
                } else {
                    "${gap}ph sau · ✅ +${delta}ph"
                }
                Text(
                    text = gapText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onPickReason,
                modifier = Modifier.testTag("reason_${row.index}"),
            ) {
                Text(triggerLabel(row.record.triggerIdx) ?: "❓ Chọn lý do")
            }
        }
        TextButton(
            onClick = onEditTime,
            modifier = Modifier.testTag("editTime_${row.index}"),
        ) {
            Text("⏰")
        }
    }
}

@Composable
private fun WeekendComparisonCard(state: StatsUiState) {
    Card(modifier = Modifier.fillMaxWidth().testTag("weekendComparison")) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "So sánh 30 ngày: cuối tuần vs ngày thường",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TypeColumn(title = "Cuối tuần", stats = state.weekend, modifier = Modifier.weight(1f))
                TypeColumn(title = "Ngày thường", stats = state.weekday, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TypeColumn(title: String, stats: TypeStats?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.testTag("typeColumn_$title")) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Metric("TB/ngày", stats?.let { "${it.avgPerDay} điếu" })
        Metric("Gap TB", stats?.avgGapMinutes?.let { "${it}ph" })
        Metric("Hút theo", stats?.let { String.format(Locale.US, "%.1f/ngày", it.chainPerDay) })
        Metric("Đầu–cuối", rangeLabel(stats))
    }
}

@Composable
private fun Metric(label: String, value: String?) {
    Text(
        text = "$label: ${value ?: "—"}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun rangeLabel(stats: TypeStats?): String? {
    val first = stats?.avgFirstMinute ?: return null
    val last = stats.avgLastMinute ?: return null
    return "${formatMinuteOfDay(first)}–${formatMinuteOfDay(last)}"
}

@Composable
private fun ReasonPickerDialog(
    selectedIdx: Int?,
    onDismiss: () -> Unit,
    onPick: (Int?) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .testTag("reasonPicker"),
            ) {
                Text("Chọn lý do", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                TRIGGERS.forEachIndexed { idx, label ->
                    TextButton(
                        onClick = { onPick(idx) },
                        modifier = Modifier.fillMaxWidth().testTag("reasonOption_$idx"),
                    ) {
                        Text(if (idx == selectedIdx) "$label ✓" else label)
                    }
                }
                if (selectedIdx != null) {
                    TextButton(
                        onClick = { onPick(null) },
                        modifier = Modifier.fillMaxWidth().testTag("reasonClear"),
                    ) {
                        Text("Bỏ lý do")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeEditDialog(
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = true,
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 4.dp) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Sửa giờ điếu", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                TimePicker(state = pickerState)
                Text(
                    text = "Làm tròn 5 phút: " +
                        formatMinuteOfDay(roundTo5(pickerState.hour * 60 + pickerState.minute)),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp).testTag("roundedPreview"),
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Huỷ") }
                    TextButton(
                        onClick = { onConfirm(pickerState.hour * 60 + pickerState.minute) },
                        modifier = Modifier.testTag("timeSave"),
                    ) {
                        Text("Lưu")
                    }
                }
            }
        }
    }
}
