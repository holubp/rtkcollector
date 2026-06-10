package org.rtkcollector.app.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

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
    TX_TO_RECEIVER(
        title = "TX to receiver",
        body = "Bytes transmitted by the app toward the receiver serial input.",
    ),
    NTRIP_URL(
        title = "NTRIP URL",
        body = "Caster host, port and mountpoint without password or token.",
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
        title = { Text(topic.title) },
        text = { Text(topic.body) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}
