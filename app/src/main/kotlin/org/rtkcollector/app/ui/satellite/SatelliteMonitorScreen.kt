package org.rtkcollector.app.ui.satellite

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.rtkcollector.app.ui.dashboard.SatelliteMonitorBarSegment
import org.rtkcollector.app.ui.dashboard.SatelliteMonitorDashboardState
import org.rtkcollector.app.ui.dashboard.SatelliteMonitorDetailGroupingMode
import org.rtkcollector.app.ui.dashboard.SatelliteMonitorFrequencyRow
import org.rtkcollector.app.ui.dashboard.SatelliteMonitorSignalCount
import org.rtkcollector.app.ui.dashboard.detailGroups

private const val DetailBarSegments = 12

@Composable
fun SatelliteMonitorScreen(
    state: SatelliteMonitorDashboardState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var constellationPrimary by rememberSaveable { mutableStateOf(false) }
    val groupingMode = if (constellationPrimary) {
        SatelliteMonitorDetailGroupingMode.CONSTELLATION
    } else {
        SatelliteMonitorDetailGroupingMode.FREQUENCY
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Satellite monitor",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onBack) {
                Text("Back")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.engineLabel,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (constellationPrimary) "Constellation" else "Frequency",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(
                checked = constellationPrimary,
                onCheckedChange = { constellationPrimary = it },
            )
        }
        Text(
            text = "R rover · B base · muted visible · saturated used",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!state.hasFrequencyGroups) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Text(
                    text = state.message,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.detailGroups(groupingMode).forEach { group ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = group.primaryLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        group.sections.forEach { section ->
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    text = section.secondaryLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                )
                                section.frequencies.forEach { frequency ->
                                    DetailFrequencyRows(frequency)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailFrequencyRows(frequency: SatelliteMonitorFrequencyRow) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailSignalRow("R ${frequency.bandLabel}", frequency.rover, Color(0xFF2F7FD8))
        DetailSignalRow("B ${frequency.bandLabel}", frequency.base, Color(0xFFE6B73D))
    }
}

@Composable
private fun DetailSignalRow(
    label: String,
    count: SatelliteMonitorSignalCount,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(52.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Row(
            modifier = Modifier.weight(1f).height(10.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            count.boxedSegments(DetailBarSegments).forEach { segment ->
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = when (segment) {
                        SatelliteMonitorBarSegment.USED -> color
                        SatelliteMonitorBarSegment.VISIBLE -> color.copy(alpha = 0.34f)
                        SatelliteMonitorBarSegment.EMPTY -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    content = {},
                )
            }
        }
        Box(modifier = Modifier.width(44.dp), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = count.displayValue,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
            )
        }
    }
}
