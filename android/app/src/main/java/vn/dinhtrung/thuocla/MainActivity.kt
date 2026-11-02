package vn.dinhtrung.thuocla

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.dinhtrung.thuocla.ui.stats.StatsRoute
import vn.dinhtrung.thuocla.ui.today.TodayScreen
import vn.dinhtrung.thuocla.ui.today.TodayViewModel
import vn.dinhtrung.thuocla.ui.today.TodayViewModelFactory

private const val TAB_TODAY = 0
private const val TAB_STATS = 1
private const val TAB_SETTINGS = 2

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ThuoclaApp).container

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootScreen(container)
                }
            }
        }
    }
}

/**
 * Three-tab shell: Hôm nay (cigarette-log), Thống kê (history-stats), Cài đặt.
 *
 * The Settings tab is a placeholder here and is replaced by the settings work; it exists so the
 * navigation has its final shape and neither feature has to rewrite the others' screens.
 */
@Composable
private fun RootScreen(container: AppContainer) {
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_TODAY) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == TAB_TODAY,
                    onClick = { selectedTab = TAB_TODAY },
                    icon = { Text("🚬") },
                    label = { Text("Hôm nay") },
                    modifier = Modifier.testTag("tabToday"),
                )
                NavigationBarItem(
                    selected = selectedTab == TAB_STATS,
                    onClick = { selectedTab = TAB_STATS },
                    icon = { Text("📊") },
                    label = { Text("Thống kê") },
                    modifier = Modifier.testTag("tabStats"),
                )
                NavigationBarItem(
                    selected = selectedTab == TAB_SETTINGS,
                    onClick = { selectedTab = TAB_SETTINGS },
                    icon = { Text("⚙️") },
                    label = { Text("Cài đặt") },
                    modifier = Modifier.testTag("tabSettings"),
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                TAB_STATS -> StatsRoute(container)
                TAB_SETTINGS -> SettingsPlaceholder()
                else -> TodayRoute(container)
            }
        }
    }
}

@Composable
private fun TodayRoute(container: AppContainer) {
    val viewModel: TodayViewModel = viewModel(factory = TodayViewModelFactory(container))
    val state by viewModel.state.collectAsState()

    TodayScreen(
        state = state,
        onAdd = viewModel::addCig,
        onUndo = viewModel::undo,
        onSelectTrigger = viewModel::selectTrigger,
        onDismissTriggerSheet = viewModel::dismissTriggerSheet,
        onAddAt = viewModel::addAt,
        onEditTime = viewModel::editTime,
    )
}

@Composable
private fun SettingsPlaceholder() {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text = "Cài đặt", style = MaterialTheme.typography.headlineMedium)
    }
}
