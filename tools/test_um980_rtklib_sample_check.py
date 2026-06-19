from pathlib import Path
import struct
import zipfile

from um980_rtklib_sample_check import (
    classify_init_script,
    classify_status,
    count_unicore_messages,
    inspect_session,
)


def unicore_frame(message_id: int, payload_len: int = 0) -> bytes:
    header = bytearray(24)
    header[0:3] = b"\xaa\x44\xb5"
    struct.pack_into("<H", header, 4, message_id)
    struct.pack_into("<H", header, 6, payload_len)
    return bytes(header) + (b"\x00" * payload_len) + b"\x00\x00\x00\x00"


def test_count_unicore_messages_resyncs_and_counts_known_frames():
    data = b"noise" + unicore_frame(2118, 4) + b"x" + unicore_frame(2125)

    assert count_unicore_messages(data) == {2118: 1, 2125: 1}


def test_classify_init_script_distinguishes_obsvmb_from_compact_obsvmcmpb():
    assert classify_init_script("OBSVMB COM1 1") == (True, False)
    assert classify_init_script("OBSVMCMPB COM1 0.25") == (False, True)
    assert classify_init_script("", "um980-binary-multihz") == (False, True)


def test_inspect_session_zip_reports_direct_obsvmb_with_corrections(tmp_path: Path):
    session_zip = tmp_path / "session.zip"
    with zipfile.ZipFile(session_zip, "w") as archive:
        archive.writestr("session/receiver-rx.raw", unicore_frame(2118))
        archive.writestr("session/init-script.txt", "OBSVMB COM1 1\n")
        archive.writestr("session/correction-input.raw", b"\xd3\x00\x00")

    report = inspect_session(session_zip)

    assert report.status == "direct-unicore-obsvmb"
    assert report.direct_rtklib_ready
    assert report.message_counts == {2118: 1}


def test_direct_obsvmb_requires_receiver_rx_bytes():
    assert classify_status(
        configured_obsvmb=True,
        configured_obsvmcmpb=False,
        receiver_rx_bytes=0,
        correction_bytes=3,
    ) == "missing-receiver-rx"


def test_inspect_session_zip_reports_compact_obsvmcmpb_as_converter_required(tmp_path: Path):
    session_zip = tmp_path / "session.zip"
    with zipfile.ZipFile(session_zip, "w") as archive:
        archive.writestr("receiver-rx.raw", unicore_frame(2118))
        archive.writestr("init-script.txt", "OBSVMCMPB COM1 0.25\n")
        archive.writestr("correction-input.raw", b"\xd3\x00\x00")

    report = inspect_session(session_zip)

    assert report.status == "converter-required-obsvmcmpb"
    assert not report.direct_rtklib_ready


def test_inspect_session_zip_uses_session_command_profile_hint(tmp_path: Path):
    session_zip = tmp_path / "session.zip"
    with zipfile.ZipFile(session_zip, "w") as archive:
        archive.writestr("receiver-rx.raw", unicore_frame(2118))
        archive.writestr("session.json", '{"commandProfileId":"um980-binary-multihz"}')
        archive.writestr("correction-input.raw", b"\xd3\x00\x00")

    report = inspect_session(session_zip)

    assert report.status == "converter-required-obsvmcmpb"
    assert report.configured_obsvmcmpb
