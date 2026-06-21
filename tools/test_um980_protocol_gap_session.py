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
    assert 'record.kind == "nmea" || record.kind == "unicore_ascii" || record.kind == "unicore_binary"' in service
    assert 'record.kind == "ubx" || record.kind == "nmea"' in service
    assert "markValidReceiverProtocolFrame(" in service


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
