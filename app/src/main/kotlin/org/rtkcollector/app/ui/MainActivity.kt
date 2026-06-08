package org.rtkcollector.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.rtkcollector.app.ui.dashboard.DashboardState
import org.rtkcollector.app.ui.dashboard.HomeDashboard

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RtkCollectorApp()
        }
    }
}

@Composable
fun RtkCollectorApp() {
    val state = DashboardState.planned(
        workflow = "Plain rover recording",
        mountpoint = "n/a",
        receiver = "UM980",
        storage = "App-private",
    )

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            HomeDashboard(
                state = state,
                onPrimaryAction = {},
                onMenu = {},
                onNtrip = {},
                onMark = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RtkCollectorAppPreview() {
    RtkCollectorApp()
}
