package vn.dinhtrung.thuocla.ui.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.dinhtrung.thuocla.AppContainer
import vn.dinhtrung.thuocla.data.DataPortability

/** What the "Dữ liệu" screen shows after the last operation. */
data class DataUiState(
    val busy: Boolean = false,
    /** Result of the last export/import; null until something has happened. */
    val message: String? = null,
    /** True when [message] reports a failure, so the screen can colour it as an error. */
    val isError: Boolean = false,
    /** Non-null while the THAY THẾ / GỘP question is waiting for an answer. */
    val pendingJsonImport: String? = null,
)

/**
 * State holder for the "Dữ liệu" screen: exports produce text the Activity hands to SAF, imports take
 * text the Activity read from the chosen file. Keeping file IO in the Activity keeps this class free of
 * storage permissions and out of the Android framework, so the whole flow is unit-testable.
 */
class DataViewModel(
    private val portability: DataPortability,
) : ViewModel() {

    private val _state = MutableStateFlow(DataUiState())
    val state: StateFlow<DataUiState> = _state.asStateFlow()

    /** Exports CSV; [onReady] receives the text to write, or nothing when the store is empty. */
    fun exportCsv(onReady: (String) -> Unit) {
        _state.update { it.copy(busy = true, message = null, isError = false) }
        viewModelScope.launch {
            val text = portability.exportCsv()
            if (text == null) {
                _state.update { it.copy(busy = false, message = NOTHING_TO_EXPORT, isError = true) }
            } else {
                onReady(text)
                _state.update { it.copy(busy = false, message = "Đã xuất ${portability.csvFileName()}") }
            }
        }
    }

    /** Exports the JSON backup; [onReady] receives the text to write. */
    fun exportJson(onReady: (String) -> Unit) {
        _state.update { it.copy(busy = true, message = null, isError = false) }
        viewModelScope.launch {
            val text = portability.exportJson()
            if (text == null) {
                _state.update { it.copy(busy = false, message = NOTHING_TO_EXPORT, isError = true) }
            } else {
                onReady(text)
                _state.update { it.copy(busy = false, message = "Đã xuất ${portability.jsonFileName()}") }
            }
        }
    }

    /** A chosen backup file: hold its text and ask THAY THẾ / GỘP before touching the store. */
    fun offerJsonImport(text: String) {
        _state.update { it.copy(message = null, isError = false, pendingJsonImport = text) }
    }

    fun cancelJsonImport() {
        _state.update { it.copy(pendingJsonImport = null) }
    }

    /** [replace] true is THAY THẾ, false is GỘP. */
    fun confirmJsonImport(replace: Boolean) {
        val text = _state.value.pendingJsonImport ?: return
        _state.update { it.copy(pendingJsonImport = null, busy = true, message = null, isError = false) }
        viewModelScope.launch {
            _state.update { it.copy(busy = false, message = describe(portability.importJson(text, replace))) }
        }
    }

    /** A chosen CSV file: validate and write, or report the first bad line. */
    fun importCsv(text: String) {
        _state.update { it.copy(busy = true, message = null, isError = false) }
        viewModelScope.launch {
            val outcome = portability.importCsv(text)
            _state.update {
                it.copy(
                    busy = false,
                    message = describe(outcome),
                    isError = outcome is DataPortability.ImportOutcome.Rejected,
                )
            }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null, isError = false) }
    }

    /** The activity could not write the file the user picked (cancelled or storage refused). */
    fun reportWriteFailure() {
        _state.update {
            it.copy(busy = false, message = WRITE_FAILED, isError = true)
        }
    }

    /** The activity could not read the file the user picked. */
    fun reportReadFailure() {
        _state.update {
            it.copy(busy = false, message = READ_FAILED, isError = true)
        }
    }

    private fun describe(outcome: DataPortability.ImportOutcome): String = when (outcome) {
        is DataPortability.ImportOutcome.Imported -> buildString {
            append("Đã nhập ${outcome.days} ngày / ${outcome.records} điếu")
            if (outcome.duplicatesSkipped > 0) append(", bỏ ${outcome.duplicatesSkipped} điếu trùng phút")
            if (outcome.invalidRecordsSkipped > 0) append(", bỏ ${outcome.invalidRecordsSkipped} bản ghi lỗi")
            if (outcome.configApplied) append(", đã áp cấu hình trong file")
        }

        is DataPortability.ImportOutcome.Rejected ->
            outcome.message + (outcome.line?.let { " (dòng $it)" } ?: "")
    }

    companion object {
        const val NOTHING_TO_EXPORT = "Chưa có dữ liệu để xuất"
        const val WRITE_FAILED = "Không ghi được file — chưa có gì được lưu"
        const val READ_FAILED = "Không đọc được file đã chọn"
    }
}

class DataViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DataViewModel::class.java)) {
            "DataViewModelFactory cannot build ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return DataViewModel(container.dataPortability) as T
    }
}
