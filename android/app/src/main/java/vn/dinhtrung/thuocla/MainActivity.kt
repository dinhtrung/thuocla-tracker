package vn.dinhtrung.thuocla

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vn.dinhtrung.thuocla.data.DataPortability
import vn.dinhtrung.thuocla.data.TrackerRepository
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@ExperimentalMaterial3Api
class MainActivity : ComponentActivity() {

    // CreateDocument takes mimeType in constructor and filename as input
    private val createCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { pendingExportUri = it }
    }

    private val createJsonLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { pendingExportUri = it }
    }

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pendingImportUri = it }
    }

    private var pendingExportUri: Uri? = null
    private var pendingImportUri: Uri? = null
    private var pendingExportType: ExportType? = null
    private var pendingImportType: ImportType? = null

    private enum class ExportType { CSV, JSON }
    private enum class ImportType { JSON_REPLACE, JSON_MERGE, CSV }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val container = (application as ThuoclaApp).container
                    SettingsScreen(
                        repository = container.repository,
                        dataPortability = container.dataPortability,
                        onExportCsv = { 
                            pendingExportType = ExportType.CSV
                            createCsvLauncher.launch("thuocla_export_${LocalDate.now()}.csv") 
                        },
                        onExportJson = { 
                            pendingExportType = ExportType.JSON
                            createJsonLauncher.launch("thuocla_backup_${LocalDate.now()}.json") 
                        },
                        onImportJsonReplace = { 
                            pendingImportType = ImportType.JSON_REPLACE
                            openDocumentLauncher.launch(arrayOf("application/json")) 
                        },
                        onImportJsonMerge = { 
                            pendingImportType = ImportType.JSON_MERGE
                            openDocumentLauncher.launch(arrayOf("application/json")) 
                        },
                        onImportCsv = { 
                            pendingImportType = ImportType.CSV
                            openDocumentLauncher.launch(arrayOf("text/csv")) 
                        },
                        onShowImportResult = { message ->
                            showImportResultDialog = true
                            importResultMessage = message
                        }
                    )
                }
            }
        }
    }

    private var importResultMessage: String? = null
    private var showImportResultDialog = false

    private fun exportCsv(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val csv = (application as ThuoclaApp).container.dataPortability.exportCsv()
            writeToUri(uri, csv)
        }
    }

    private fun exportJson(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val json = (application as ThuoclaApp).container.dataPortability.exportJson()
            writeToUri(uri, json)
        }
    }

    private fun importJson(uri: Uri, replace: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            val inputStream = contentResolver.openInputStream(uri) ?: return@launch
            val result = (application as ThuoclaApp).container.dataPortability.importJson(inputStream, replace)
            runOnUiThread { showImportResult(result) }
        }
    }

    private fun importCsv(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val inputStream = contentResolver.openInputStream(uri) ?: return@launch
            val result = (application as ThuoclaApp).container.dataPortability.importCsv(inputStream)
            runOnUiThread { showImportResult(result) }
        }
    }

    private fun writeToUri(uri: Uri, content: String) {
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(content.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun showImportResult(result: DataPortability.ImportResult) {
        val message = when (result) {
            is DataPortability.ImportResult.Success ->
                "Nhập thành công: ${result.recordsAdded} điếu, ${result.daysAdded} ngày${if (result.duplicatesSkipped > 0) ", ${result.duplicatesSkipped} trùng phút bỏ qua" else ""}${if (result.invalidRecordsSkipped > 0) ", ${result.invalidRecordsSkipped} bản ghi lỗi bỏ qua" else ""}"
            is DataPortability.ImportResult.Failure ->
                "Nhập thất bại: ${result.message}${result.errorLine?.let { " (dòng $it)" } ?: ""}"
        }
        importResultMessage = message
        showImportResultDialog = true
    }

    override fun onResume() {
        super.onResume()
        // Process pending URIs after activity result
        pendingExportUri?.let { uri ->
            when (pendingExportType) {
                ExportType.CSV -> exportCsv(uri)
                ExportType.JSON -> exportJson(uri)
                null -> {}
            }
            pendingExportUri = null
            pendingExportType = null
        }
        pendingImportUri?.let { uri ->
            when (pendingImportType) {
                ImportType.JSON_REPLACE -> importJson(uri, replace = true)
                ImportType.JSON_MERGE -> importJson(uri, replace = false)
                ImportType.CSV -> importCsv(uri)
                null -> {}
            }
            pendingImportUri = null
            pendingImportType = null
        }
    }
}

@Composable
@ExperimentalMaterial3Api
fun SettingsScreen(
    repository: TrackerRepository,
    dataPortability: DataPortability,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onImportJsonReplace: () -> Unit,
    onImportJsonMerge: () -> Unit,
    onImportCsv: () -> Unit,
    onShowImportResult: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Cài đặt", style = MaterialTheme.typography.headlineMedium)

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Dữ liệu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Button(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
                    Text("Xuất CSV")
                }
                Button(onClick = onExportJson, modifier = Modifier.fillMaxWidth()) {
                    Text("Xuất JSON Backup")
                }
                Button(onClick = onImportJsonReplace, modifier = Modifier.fillMaxWidth()) {
                    Text("Nhập JSON (THAY THẾ)")
                }
                Button(onClick = onImportJsonMerge, modifier = Modifier.fillMaxWidth()) {
                    Text("Nhập JSON (GỘP)")
                }
                Button(onClick = onImportCsv, modifier = Modifier.fillMaxWidth()) {
                    Text("Nhập CSV")
                }
            }
        }
    }
}