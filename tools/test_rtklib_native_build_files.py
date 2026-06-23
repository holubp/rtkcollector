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
    assert "${RTKLIB_SRC}/postpos.c" in cmake
    assert "${RTKLIB_SRC}/convrnx.c" in cmake
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
        "nativeRtklibPostprocess",
        "nativeRtklibStop",
        "nativeRtklibDestroy",
    ):
        assert f"Java_org_rtkcollector_core_rtklib_RtklibNativeBridgeKt_{symbol}" in bridge


def test_native_postprocess_uses_rtklib_library_calls_without_cli_shellout():
    bridge = (REPO_ROOT / "app/src/main/cpp/rtklib_bridge.cpp").read_text(encoding="utf-8")

    assert "convrnx(" in bridge
    assert "postpos(" in bridge
    assert "SOLTYPE_FORWARD" in bridge
    assert "SOLTYPE_COMBINED" in bridge
    assert "inputs.push_back(rover_rinex.observation);" in bridge
    assert "inputs.push_back(base_rinex.observation);" in bridge
    assert "system(" not in bridge
    assert "popen(" not in bridge


def test_native_postprocess_uses_converted_base_rinex_reference_position():
    bridge = (REPO_ROOT / "app/src/main/cpp/rtklib_bridge.cpp").read_text(encoding="utf-8")
    postprocess_options = bridge.split("static void configure_postprocess_options(", maxsplit=1)[1]
    postprocess_options = postprocess_options.split("static void configure_rinex_options(", maxsplit=1)[0]

    assert "prcopt->refpos = POSOPT_RINEX;" in postprocess_options
    assert "prcopt->refpos = POSOPT_RTCM;" not in postprocess_options


def test_native_live_rtklib_keeps_rtcm_reference_position():
    bridge = (REPO_ROOT / "app/src/main/cpp/rtklib_bridge.cpp").read_text(encoding="utf-8")
    live_options = bridge.split("static void configure_options(", maxsplit=1)[1]
    live_options = live_options.split("static void configure_postprocess_options(", maxsplit=1)[0]

    assert "engine->prcopt.refpos = POSOPT_RTCM;" in live_options
    assert "engine->prcopt.refpos = POSOPT_RINEX;" not in live_options


def test_native_bridge_defines_rtklib_postprocessing_callbacks():
    bridge = (REPO_ROOT / "app/src/main/cpp/rtklib_bridge.cpp").read_text(encoding="utf-8")

    assert 'extern "C" int showmsg' in bridge
    assert 'extern "C" void settspan' in bridge
    assert 'extern "C" void settime' in bridge


def test_native_postprocess_preserves_rtklib_messages_in_failure_details():
    bridge = (REPO_ROOT / "app/src/main/cpp/rtklib_bridge.cpp").read_text(encoding="utf-8")

    assert "#include <cstdarg>" in bridge
    assert "thread_local std::string rtklib_show_messages" in bridge
    assert "std::vsnprintf" in bridge
    assert "rtklib_show_messages.clear()" in bridge
    assert '"RTKLIB postpos failed for "' in bridge
    assert "status=" in bridge
    assert "rtklib_show_messages" in bridge


def test_native_postprocess_does_not_pass_null_convrnx_outputs():
    bridge = (REPO_ROOT / "app/src/main/cpp/rtklib_bridge.cpp").read_text(encoding="utf-8")

    assert "outputs[i] = nullptr" not in bridge
    assert "convrnx(format, &rnxopt, input.c_str(), outputs.data())" in bridge


def test_native_bridge_guards_jni_inputs_before_dereferencing():
    bridge = (REPO_ROOT / "app/src/main/cpp/rtklib_bridge.cpp").read_text(encoding="utf-8")

    assert "class ScopedUtfChars" in bridge
    assert "if (!preset_chars.ok() || !rover_chars.ok() || !correction_chars.ok())" in bridge
    assert "!receiver_rx_chars.ok()" in bridge
    assert "!output_nmea_chars.ok() || !output_pos_chars.ok()" in bridge
    assert "if (!bytes)" in bridge
