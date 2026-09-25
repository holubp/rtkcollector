package org.rtkcollector.app.ui.dashboard

import org.rtkcollector.core.correction.NtripTransportMode

internal fun NtripTransportMode?.plaintextTag(): String? =
    "\u26A0 PLAINTEXT".takeIf { this == NtripTransportMode.PLAINTEXT }
