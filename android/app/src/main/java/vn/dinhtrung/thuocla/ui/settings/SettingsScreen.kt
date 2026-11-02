package vn.dinhtrung.thuocla.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Settings screen (tasks.md 7.1 / 7.2): four fields, a save button, and the footer
 * "v1.0.0 · N ngày".
 *
 * The fields show the *stored* values, including normalized fallbacks, so the user never sees a
 * saved 0 or an empty box after pressing save.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onGoalChange: (String) -> Unit,
    onIntervalGoalChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onCigsPerPackChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Cài đặt",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        NumberField(
            value = state.fields.goal,
            onValueChange = onGoalChange,
            label = "Mục tiêu điếu/ngày (để trống = 10)",
            tag = "settingGoal",
        )
        NumberField(
            value = state.fields.intervalGoal,
            onValueChange = onIntervalGoalChange,
            label = "Khoảng cách mục tiêu, phút (để trống = 60)",
            tag = "settingIntervalGoal",
        )
        NumberField(
            value = state.fields.pricePerPack,
            onValueChange = onPriceChange,
            label = "Giá mỗi bao (để trống = 25000)",
            tag = "settingPricePerPack",
        )
        NumberField(
            value = state.fields.cigsPerPack,
            onValueChange = onCigsPerPackChange,
            label = "Điếu mỗi bao (để trống = 20)",
            tag = "settingCigsPerPack",
        )

        Button(onClick = onSave, modifier = Modifier.testTag("settingSave")) {
            Text("Lưu")
        }

        state.savedMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("settingSaved"),
            )
        }

        Text(
            text = state.footer,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("settingFooter"),
        )
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    )
}
