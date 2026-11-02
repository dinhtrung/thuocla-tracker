package vn.dinhtrung.thuocla.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.dinhtrung.thuocla.AppContainer
import vn.dinhtrung.thuocla.data.TrackerRepository
import vn.dinhtrung.thuocla.domain.TrackerConfig
import vn.dinhtrung.thuocla.domain.ThemeMode

/** What the four inputs currently hold, as the user typed them (blank is a valid intermediate). */
data class SettingsFields(
    val goal: String = "",
    val intervalGoal: String = "",
    val pricePerPack: String = "",
    val cigsPerPack: String = "",
) {
    companion object {
        fun of(config: TrackerConfig) = SettingsFields(
            goal = config.goal.toString(),
            intervalGoal = config.intervalGoal.toString(),
            pricePerPack = config.pricePerPack.toString(),
            cigsPerPack = config.cigsPerPack.toString(),
        )
    }
}

data class SettingsUiState(
    val fields: SettingsFields = SettingsFields(),
    val storedConfig: TrackerConfig = TrackerConfig.DEFAULT,
    val daysWithData: Int = 0,
    val versionName: String = "",
    val savedMessage: String? = null,
    /**
     * The mode the picker is showing. It starts as the stored mode and changes on tap, so the radio
     * group reflects the choice the user is about to save rather than the one already stored.
     */
    val themeMode: ThemeMode = ThemeMode.DEFAULT,
) {
    /** "v1.0.0 · 128 ngày" (tasks.md 7.2). */
    val footer: String get() = "v$versionName · $daysWithData ngày"
}

/**
 * Settings screen state (tasks.md 7.1 / 7.2).
 *
 * A blank or non-positive field is not an error: the store's `normalized()` replaces it with the
 * default (10 / 60 / 25000 / 20) and the normalized value is written back into the form, so the user
 * sees what was actually saved and an empty config row can never be created.
 */
class SettingsViewModel(
    private val repository: TrackerRepository,
    private val versionName: String,
) : ViewModel() {

    private val fields = MutableStateFlow(SettingsFields())
    private val savedMessage = MutableStateFlow<String?>(null)
    private val themeMode = MutableStateFlow(ThemeMode.DEFAULT)

    val state: StateFlow<SettingsUiState> = combine(
        fields,
        repository.configFlow(),
        repository.distinctDayCountFlow(),
        savedMessage,
        themeMode,
    ) { typed, config, days, saved, mode ->
        SettingsUiState(
            fields = typed,
            storedConfig = config,
            daysWithData = days,
            versionName = versionName,
            savedMessage = saved,
            themeMode = mode,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(versionName = versionName),
    )

    init {
        viewModelScope.launch {
            val stored = repository.getConfig()
            fields.value = SettingsFields.of(stored)
            themeMode.value = stored.themeMode
        }
    }

    /** Tapping a mode in Cài đặt: it takes effect on save, like every other field on this screen. */
    fun onThemeModeChange(mode: ThemeMode) {
        themeMode.value = mode
        savedMessage.value = null
    }

    fun onGoalChange(value: String) {
        fields.value = fields.value.copy(goal = value)
        savedMessage.value = null
    }

    fun onIntervalGoalChange(value: String) {
        fields.value = fields.value.copy(intervalGoal = value)
        savedMessage.value = null
    }

    fun onPricePerPackChange(value: String) {
        fields.value = fields.value.copy(pricePerPack = value)
        savedMessage.value = null
    }

    fun onCigsPerPackChange(value: String) {
        fields.value = fields.value.copy(cigsPerPack = value)
        savedMessage.value = null
    }

    fun save() {
        val typed = fields.value
        val requested = TrackerConfig(
            goal = typed.goal.toIntOrNull() ?: 0,
            intervalGoal = typed.intervalGoal.toIntOrNull() ?: 0,
            pricePerPack = typed.pricePerPack.toLongOrNull() ?: 0L,
            cigsPerPack = typed.cigsPerPack.toIntOrNull() ?: 0,
            themeMode = themeMode.value,
        )
        viewModelScope.launch {
            val stored = repository.saveConfig(requested)
            savedMessage.value = savedText(requested, stored)
            fields.value = SettingsFields.of(stored)
            // Re-read the mode the store kept, so the picker cannot drift from what is persisted.
            themeMode.value = stored.themeMode
        }
    }

    /** Compared before the form is rewritten, so the user is told when a fallback was applied. */
    private fun savedText(requested: TrackerConfig, stored: TrackerConfig): String =
        if (stored == requested) "Đã lưu" else "Đã lưu — ô trống/quá nhỏ dùng mặc định"
}

class SettingsViewModelFactory(
    private val container: AppContainer,
    private val versionName: String,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            "SettingsViewModelFactory cannot build ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(container.repository, versionName) as T
    }
}
