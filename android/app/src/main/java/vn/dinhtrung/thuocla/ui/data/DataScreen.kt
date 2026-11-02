package vn.dinhtrung.thuocla.ui.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The "Dữ liệu" tab: export CSV/JSON and import either format, with the THAY THẾ / GỘP question asked
 * before any JSON import touches the store (spec `data-portability`, design decision #5: file access
 * goes through SAF, so no storage permission is requested).
 *
 * The screen only raises intents; [onExportCsv], [onExportJson], [onImportJson], [onImportCsv] are
 * wired to `ActivityResultContracts` in [vn.dinhtrung.thuocla.MainActivity].
 */
@Composable
fun DataScreen(
    state: DataUiState,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onImportCsv: () -> Unit,
    onConfirmReplace: () -> Unit,
    onConfirmMerge: () -> Unit,
    onCancelImport: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Dữ liệu",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Mang dữ liệu từ bản web sang, hoặc xuất ngược lại đúng định dạng bản web đọc được.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Xuất", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(
                    onClick = onExportCsv,
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exportCsv"),
                ) { Text("Xuất CSV (6 cột)") }
                Button(
                    onClick = onExportJson,
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exportJson"),
                ) { Text("Xuất bản sao JSON") }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Nhập", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(
                    onClick = onImportJson,
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("importJson"),
                ) { Text("Nhập bản sao JSON") }
                OutlinedButton(
                    onClick = onImportCsv,
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("importCsv"),
                ) { Text("Nhập CSV") }
            }
        }

        state.message?.let { message ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dataMessage"),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message,
                        color = if (state.isError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    )
                    TextButton(
                        onClick = onDismissMessage,
                        modifier = Modifier.testTag("dismissDataMessage"),
                    ) { Text("Đóng") }
                }
            }
        }
    }

    if (state.pendingJsonImport != null) {
        AlertDialog(
            onDismissRequest = onCancelImport,
            title = { Text("Nhập bản sao JSON") },
            text = {
                Text(
                    "THAY THẾ xoá toàn bộ dữ liệu đang có rồi nhập file. " +
                        "GỘP giữ dữ liệu đang có và chỉ thêm điếu chưa có. " +
                        "Cả hai chế độ đều lấy cấu hình trong file.",
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmReplace, modifier = Modifier.testTag("confirmReplace")) {
                    Text("THAY THẾ")
                }
            },
            dismissButton = {
                TextButton(onClick = onConfirmMerge, modifier = Modifier.testTag("confirmMerge")) {
                    Text("GỘP")
                }
            },
        )
    }
}
