package vn.dinhtrung.thuocla

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.dinhtrung.thuocla.ui.today.TodayScreen
import vn.dinhtrung.thuocla.ui.today.TodayViewModel
import vn.dinhtrung.thuocla.ui.today.TodayViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ThuoclaApp).container

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: TodayViewModel = viewModel(
                        factory = TodayViewModelFactory(container),
                    )
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
            }
        }
    }
}
