package vn.dinhtrung.thuocla.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import vn.dinhtrung.thuocla.AppContainer
import vn.dinhtrung.thuocla.data.TrackerRepository
import vn.dinhtrung.thuocla.domain.CigRecord
import vn.dinhtrung.thuocla.domain.CigaretteLog
import vn.dinhtrung.thuocla.domain.DailySummary
import vn.dinhtrung.thuocla.domain.CutNote
import vn.dinhtrung.thuocla.domain.ComparisonRow
import vn.dinhtrung.thuocla.domain.IntervalStatus
import vn.dinhtrung.thuocla.domain.TrackerConfig
import vn.dinhtrung.thuocla.domain.WriteBlock
import vn.dinhtrung.thuocla.domain.WriteResult
import vn.dinhtrung.thuocla.domain.comparisonRows
import vn.dinhtrung.thuocla.domain.cutNote
import vn.dinhtrung.thuocla.domain.formatHhMm
import vn.dinhtrung.thuocla.domain.intervalStatus

/** State the Today screen renders. Group 4 extends this with timer, quick stats and the cut note. */
data class TodayUiState(
    val dateKey: String,
    val records: List<CigRecord> = emptyList(),
    val goal: Int = TrackerConfig.DEFAULT.goal,
    /** In-card warning. It is a banner inside the counter card, never a toast (spec `cigarette-log`). */
    val warning: String? = null,
    /** Non-null while the reason sheet is open; the record the tap will write into. */
    val triggerTarget: CigRecord? = null,
    /** Four quick stats + comparison series for this day (tasks.md 4.1 / 4.3). */
    val summary: DailySummary? = null,
    /** Clock card facts for the minute the screen was built (tasks.md 4.2). */
    val interval: IntervalStatus = IntervalStatus.EMPTY,
    /**
     * The "Điếu nên cắt" note for this minute (tasks.md 5.2). Rebuilt from the same records the list
     * shows, so it changes on every +1/undo without an extra read and on every minute tick.
     */
    val cutNote: CutNote? = null,
) {
    val count: Int get() = records.size

    /** Progress is cigarettes over goal, clamped at 100% (spec `daily-summary`). */
    val progress: Float get() = (count.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)

    val selectedTriggerIdx: Int? get() = triggerTarget?.triggerIdx
}

/**
 * Today's log screen state holder.
 *
 * The reason sheet opens ~2 s after a successful +1 and is pinned to the record that +1 created, so
 * two quick taps cannot make the second tap attach its reason to the first cigarette (the PWA bug
 * fixed in v1.5.0 via `triggerPickerTarget`). A blocked duplicate does not open the sheet at all.
 */
class TodayViewModel(
    private val log: CigaretteLog,
    private val repository: TrackerRepository,
    private val triggerPromptDelayMillis: Long = TRIGGER_PROMPT_DELAY_MILLIS,
    /** Re-emits once a minute so the clock card and the "ít nhất" window roll over without a tap. */
    minuteTicker: Flow<Unit> = minuteTickerFlow(),
) : ViewModel() {

    private val dateKey: String = log.todayKey()
    private val warning = MutableStateFlow<String?>(null)
    private val triggerTarget = MutableStateFlow<CigRecord?>(null)
    private var promptJob: Job? = null

    val state: StateFlow<TodayUiState> = combine(
        repository.dayFlow(dateKey),
        repository.configFlow(),
        repository.dailySummaryFlow(dateKey),
        // The note's own inputs: the 30-day window's records, re-emitted on every write and on every
        // minute tick. Nested so the outer combine keeps its five-argument form.
        combine(repository.cutReminderFlow(dateKey), minuteTicker) { cutRecords, _ -> cutRecords },
        combine(warning, triggerTarget) { w, t -> w to t },
    ) { records, config, summary, cutRecords, (warn, target) ->
        val nowMinute = log.nowMinuteOfDay()
        TodayUiState(
            dateKey = dateKey,
            records = records,
            goal = config.goal,
            warning = warn,
            triggerTarget = target,
            summary = summary,
            interval = intervalStatus(
                lastMinute = records.lastOrNull()?.minuteOfDay,
                nowMinute = nowMinute,
                intervalGoal = config.intervalGoal,
            ),
            cutNote = cutNote(cutRecords, dateKey, nowMinute, config.goal),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TodayUiState(dateKey = dateKey, goal = TrackerConfig.DEFAULT.goal),
    )

    /** The big +1 button. */
    fun addCig() {
        viewModelScope.launch {
            val result = log.addNow()
            when {
                result.applied -> {
                    warning.value = null
                    scheduleTriggerPrompt(result.record)
                }

                result.block == WriteBlock.DUPLICATE_MINUTE -> warning.value = duplicateWarning(result)
                else -> warning.value = INVALID_TIME_WARNING
            }
        }
    }

    /** Undo removes today's newest cigarette and never reopens the reason sheet. */
    fun undo() {
        promptJob?.cancel()
        triggerTarget.value = null
        viewModelScope.launch {
            log.undoLastToday()
            warning.value = null
        }
    }

    /** Reason tapped on the sheet: sets it, or clears it when it is already attached. */
    fun selectTrigger(triggerIdx: Int) {
        val target = triggerTarget.value ?: return
        triggerTarget.value = null
        viewModelScope.launch {
            log.toggleTrigger(target.dateKey, target.minuteOfDay, triggerIdx)
        }
    }

    fun dismissTriggerSheet() {
        promptJob?.cancel()
        triggerTarget.value = null
    }

    /** "Thêm giờ khác" on any day, including past days; rounding and the duplicate rule still apply. */
    fun addAt(dateKey: String, hour: Int, minute: Int) {
        viewModelScope.launch {
            val result = log.addAt(dateKey, hour, minute)
            when {
                result.applied -> warning.value = null
                result.block == WriteBlock.DUPLICATE_MINUTE -> warning.value = duplicateWarning(result)
                else -> warning.value = INVALID_TIME_WARNING
            }
        }
    }

    /** Day-detail edit lands here in group 6; the rounding/duplicate rule is already enforced. */
    fun editTime(dateKey: String, fromMinute: Int, toMinute: Int) {
        viewModelScope.launch {
            val result = log.editTime(dateKey, fromMinute, toMinute)
            when {
                result.applied -> warning.value = null
                result.block == WriteBlock.DUPLICATE_MINUTE -> warning.value = duplicateWarning(result)
                else -> warning.value = INVALID_TIME_WARNING
            }
        }
    }

    fun clearWarning() {
        warning.value = null
    }

    private fun scheduleTriggerPrompt(record: CigRecord?) {
        if (record == null) return
        promptJob?.cancel()
        promptJob = viewModelScope.launch {
            delay(triggerPromptDelayMillis)
            triggerTarget.value = record
        }
    }

    private fun duplicateWarning(result: WriteResult): String {
        val at = result.record?.minuteOfDay ?: return DUPLICATE_WARNING
        return "⚠️ Đã có điếu lúc ${formatHhMm(at)} rồi — không thêm trùng nhé!"
    }

    companion object {
        /** The PWA opens its reason sheet 2000 ms after a successful +1 (`afterAddCig`). */
        const val TRIGGER_PROMPT_DELAY_MILLIS = 2_000L

        const val DUPLICATE_WARNING = "⚠️ Đã có điếu lúc này rồi — không thêm trùng nhé!"
        const val INVALID_TIME_WARNING = "⚠️ Giờ không hợp lệ"
    }
}

/** Rows of the three-column table for the state's own records and summary (tasks.md 4.3). */
fun TodayUiState.comparisonRows(): List<ComparisonRow> =
    summary?.let { comparisonRows(records, it) }.orEmpty()

/**
 * Emits immediately and then on every minute boundary of the wall clock.
 *
 * The PWA re-renders the timer every second and the cut note every minute; the native screen only
 * needs the minute boundary, because every number it shows is whole minutes.
 */
fun minuteTickerFlow(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        val millisToNextMinute = 60_000L - System.currentTimeMillis() % 60_000L
        delay(millisToNextMinute)
    }
}

class TodayViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(TodayViewModel::class.java)) {
            "TodayViewModelFactory cannot build ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return TodayViewModel(
            log = CigaretteLog(container.repository, container.clock),
            repository = container.repository,
        ) as T
    }
}
