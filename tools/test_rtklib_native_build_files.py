from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]


def test_app_cmake_builds_pinned_rtklib_without_command_line_apps():
    cmake = (REPO_ROOT / "app/src/main/cpp/CMakeLists.txt").read_text(encoding="utf-8")

    assert "rtkcollector_rtklib" in cmake
    assert "rtklib_bridge.cpp" in cmake
    assert "${RTKLIB_SRC}/rcvraw.c" in cmake
    assert "${RTKLIB_SRC}/rtkpos.c" in cmake
    assert "${RTKLIB_SRC}/rtksvr.c" in cmake
    assert "${RTKLIB_SRC}/solution.c" in cmake
    assert "${RTKLIB_SRC}/rcv/novatel.c" in cmake
    assert "${RTKLIB_SRC}/rcv/swiftnav.c" in cmake
    assert "app/consapp" not in cmake


def test_app_cmake_declares_obsvmcmpb_unicore_decoder_shim():
    cmake = (REPO_ROOT / "app/src/main/cpp/CMakeLists.txt").read_text(encoding="utf-8")

    assert "#define ID_OBSVMCMP    138" in cmake
    assert "case ID_OBSVMCMP: return decode_obsvmb(raw);" in cmake
    assert "unicore_obsvmcmp.c" in cmake


def test_native_bridge_exports_expected_jni_symbols():
    bridge = (REPO_ROOT / "app/src/main/cpp/rtklib_bridge.cpp").read_text(encoding="utf-8")

    for symbol in (
        "nativeRtklibVersion",
        "nativeRtklibCreate",
        "nativeRtklibStart",
        "nativeRtklibFeed",
        "nativeRtklibSnapshot",
        "nativeRtklibStop",
        "nativeRtklibDestroy",
    ):
        assert f"Java_org_rtkcollector_core_rtklib_RtklibNativeBridgeKt_{symbol}" in bridge
