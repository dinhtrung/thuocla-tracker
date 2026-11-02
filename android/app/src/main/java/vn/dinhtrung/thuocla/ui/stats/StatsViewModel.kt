package vn.dinhtrung.thuocla.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.dinhtrung.thuocla.data.TrackerRepository
import vn.dinhtrung.thuocla.domain.WriteBlock
import vn.dinhtrung.thuocla.domain.dayMonth
import vn.dinhtrung.thuocla.domain.dayName
import vn.dinhtrung.thuocla.domain.formatMinuteOfDay
import vn.dinhtrung.thuocla.domain.history.COMPARISON_WINDOW_DAYS
import vn.dinhtrung.thuocla.domain.history.DayDetailRow
import vn.dinhtrung.thuocla.domain.history.DayPoint
import vn.dinhtrung.thuocla.domain.history.TypeStats
import vn.dinhtrung.thuocla.domain.history.WeekWindow
import vn.dinhtrung.thuocla.domain.history.chartDays
import vn.dinhtrung.thuocla.domain.history.dayDetail
import vn.dinhtrung.thuocla.domain.history.weekWindow
import vn.dinhtrung.thuocla.domain.history.weekendComparison
import vn.dinhtrung.thuocla.domain.roundTo5

data class StatsUiState(
    val today: LocalDate,
    val window: WeekWindow,
    val days: List<DayPoint>,
    val goal: Int,
    val intervalGoal: Int,
    val weekend: TypeStats?,
    val weekday: TypeStats?,
    val selectedDateKey: String?,
    val detail: List<DayDetailRow>,
    val message: String?,
) {
    val canGoForward: Boolean get() = window.pagesBack > 0

    /** "Hôm nay" or "T3 28/10" — the PWA's day-detail title. */
    val detailTitle: String
        get() {
            val key = selectedDateKey ?: return ""
            val date = LocalDate.parse(key)
            return if (date == today) "Hôm nay" else "${dayName(date)} ${dayMonth(date)}"
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val repository: TrackerRepository,
    private val clock: Clock,
) : ViewModel() {

    private val pagesBack = MutableStateFlow(0)
    private val selectedDateKey = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<StatsUiState> =
        combine(pagesBack, selectedDateKey, message) { pages, selected, msg -> Triple(pages, selected, msg) }
            .flatMapLatest { (pages, selected, msg) ->
                val today = LocalDate.now(clock)
                val window = weekWindow(today, pages)
                val comparisonFrom = today.minusDays((COMPARISON_WINDOW_DAYS - 1).toLong()).toString()
                combine(
                    repository.rangeFlow(window.from.toString(), window.to.toString()),
                    repository.rangeFlow(comparisonFrom, today.toString()),
                    repository.configFlow(),
                    selected?.let { repository.dayFlow(it) } ?: flowOf(emptyList()),
                ) { weekRecords, comparisonRecords, config, detailRecords ->
                    val comparison = weekendComparison(comparisonRecords, today)
                    StatsUiState(
                        today = today,
                        window = window,
                        days = chartDays(weekRecords, window),
                        goal = config.goal,
                        intervalGoal = config.intervalGoal,
                        weekend = comparison.weekend,
                        weekday = comparison.weekday,
                        selectedDateKey = selected,
                        detail = dayDetail(detailRecords, config.intervalGoal),
                        message = msg,
                    )
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = initial(),
            )

    fun previousWeek() = pagesBack.update { it + 1 }

    fun nextWeek() = pagesBack.update { if (it > 0) it - 1 else 0 }

    /** Tapping a bar opens that day; tapping the selected bar again closes it (PWA `selectDay`). */
    fun selectDay(dateKey: String) = selectedDateKey.update { if (it == dateKey) null else dateKey }

    fun closeDayDetail() {
        selectedDateKey.value = null
    }

    fun clearMessage() {
        message.value = null
    }

    /** Moves one cigarette to [minuteOfDay], rounded to 5 minutes by the same rule as logging. */
    fun moveTime(row: DayDetailRow, minuteOfDay: Int) {
        val target = roundTo5(minuteOfDay)
        viewModelScope.launch {
            val result = repository.updateTime(row.record.dateKey, row.record.minuteOfDay, target)
            message.value = when (result.block) {
                WriteBlock.DUPLICATE_MINUTE ->
                    "Đã có điếu lúc ${formatMinuteOfDay(target)} rồi"
                WriteBlock.INVALID -> "Không sửa được giờ"
                WriteBlock.NONE -> null
            }
        }
    }

    /** Attaches a reason; passing the index that is already set removes it (tap-to-toggle). */
    fun setTrigger(row: DayDetailRow, triggerIdx: Int?) {
        val target = if (triggerIdx != null && triggerIdx == row.record.triggerIdx) null else triggerIdx
        viewModelScope.launch {
            repository.setTrigger(row.record.dateKey, row.record.minuteOfDay, target)
        }
    }

    private fun initial(): StatsUiState {
        val today = LocalDate.now(clock)
        return StatsUiState(
            today = today,
            window = weekWindow(today, 0),
            days = emptyList(),
            goal = 10,
            intervalGoal = 60,
            weekend = null,
            weekday = null,
            selectedDateKey = null,
            detail = emptyList(),
            message = null,
        )
    }
}

class StatsViewModelFactory(
    private val repository: TrackerRepository,
    private val clock: Clock,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(StatsViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return StatsViewModel(repository, clock) as T
    }
}
