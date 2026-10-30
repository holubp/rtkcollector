from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt"
MONITOR = ROOT / "core/capture/src/main/kotlin/org/rtkcollector/core/capture/RecordingHealthMonitor.kt"


def test_service_records_protocol_bytes_and_checks_protocol_health():
    service = SERVICE.read_text(encoding="utf-8")
    assert "recordReceiverProtocolBytes(bytesRead, nowMillis)" in service
    assert "checkReceiverProtocol(" in service
    assert "shouldExpectReceiverProtocolFrames()" in service


def test_service_marks_valid_um980_and_ublox_protocol_frames():
    service = SERVICE.read_text(encoding="utf-8")
    assert service.count("ReceiverProtocolHealth.isValidNmea(record.bytes)") == 2
    assert "ReceiverProtocolHealth.isValidUnicoreAscii(record.bytes)" in service
    assert 'record.kind == "nmea" || record.kind == "unicore_ascii" || record.kind == "unicore_binary"' not in service
    assert 'record.kind == "ubx" || record.kind == "nmea"' not in service
    assert "markValidReceiverProtocolFrame(" in service


def test_service_gates_entire_text_processing_before_health_positions_and_alarms():
    service = SERVICE.read_text(encoding="utf-8")
    ublox = service.split("ubloxStreamParser.accept(bytes).forEach { record ->", 1)[1]
    ublox = ublox.split("private fun freshUbloxSatellitesInView", 1)[0]
    unicore = service.split("streamParser.accept(bytes).forEach { record ->", 1)[1]
    gate = "if (!validProtocolFrame) return@forEach"
    for block in (ublox, unicore):
        assert gate in block, "Invalid text must exit before any downstream parser or state update"
        assert block.index("ReceiverProtocolHealth.isValidNmea(record.bytes)") < block.index(gate)
        assert block.index(gate) < block.index("markValidReceiverProtocolFrame(")
        assert block.index(gate) < block.index("applyPrimaryScreenCandidate(")
    assert ublox.index(gate) < ublox.index("sharedNmeaGgaParser.accept(record.bytes)")
    assert unicore.index("ReceiverProtocolHealth.isValidUnicoreAscii(record.bytes)") < unicore.index(gate)
    for consumer in ("nmeaExporter", "ggaParser", "gsaParser", "gstParser", "gsvParser", "solutionParser", "obsvmaParser"):
        assert unicore.index(gate) < unicore.index(f"{consumer}.accept(record.bytes)")


def test_ublox_protocol_health_uses_monotonic_time_not_solution_wall_clock():
    service = SERVICE.read_text(encoding="utf-8")
    ublox = service.split("ubloxStreamParser.accept(bytes).forEach { record ->", 1)[1]
    ublox = ublox.split("private fun freshUbloxSatellitesInView", 1)[0]
    assert "markValidReceiverProtocolFrame(SystemClock.elapsedRealtime())" in ublox
    assert "markValidReceiverProtocolFrame(nowMillis)" not in ublox
    assert "telemetry" in ublox and "nowMillis" in ublox


def test_service_reconnects_on_protocol_stall_without_stopping_session():
    service = SERVICE.read_text(encoding="utf-8")
    assert "receiver-protocol-stalled" in service
    assert "tryReconnectUsb(recorder)" in service
    assert "Receiver bytes are arriving but no valid" in service
    stalled_block = service.split("receiver-protocol-stalled", 1)[1].split("receiver-protocol-recovered", 1)[0]
    assert "stopRecording(" not in stalled_block


def test_monitor_has_protocol_stall_event_and_threshold():
    monitor = MONITOR.read_text(encoding="utf-8")
    assert "ReceiverProtocolStalled" in monitor
    assert "ReceiverProtocolRecovered" in monitor
    assert "DEFAULT_RECEIVER_PROTOCOL_STALL_MILLIS" in monitor
