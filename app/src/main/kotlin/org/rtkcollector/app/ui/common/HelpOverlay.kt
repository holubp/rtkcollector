package org.rtkcollector.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

enum class HelpTopic(
    val title: String,
    val body: String,
) {
    SATS_USED_VIEW(
        title = "Sats used/view",
        body = "Used is the number of satellites in the receiver solution. View is the aggregate visible-satellite count reported by telemetry such as GSV or STADOP.",
    ),
    ELLIPSOIDAL_HEIGHT(
        title = "Ellipsoidal height",
        body = "Height above the reference ellipsoid, distinct from orthometric altitude.",
    ),
    COORDINATE_ACTIONS(
        title = "Position actions",
        body = "Tap the coordinate to copy it as geo:lat,lon, lat,lon, lat or lon. In base workflows, Base selects fixed-base operation using the current coordinate, then you must choose a matching MODE BASE command profile before starting. Avg starts a live coordinate average and stops automatically if the fix type changes.",
    ),
    TX_TO_RECEIVER(
        title = "TX to receiver",
        body = "Bytes transmitted by the app toward the receiver serial input.",
    ),
    NTRIP_URL(
        title = "NTRIP URL",
        body = "Caster host, port and mountpoint without password or token.",
    ),
    SETTINGS_GROUPS(
        title = "Settings",
        body = "Settings are grouped by session setup, receiver and USB, corrections, and sessions. Dashboard tiles select active profiles; detailed editing stays here.",
    ),
}

@Composable
fun HelpOverlay(
    topic: HelpTopic?,
    onDismiss: () -> Unit,
) {
    if (topic == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(topic.title, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("X")
                }
            }
        },
        text = { Text(topic.body) },
        confirmButton = {},
    )
}
