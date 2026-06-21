from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BRIDGE = ROOT / "app/src/main/cpp/rtklib_bridge.cpp"
CMAKE = ROOT / "app/src/main/cpp/CMakeLists.txt"


def test_native_bridge_uses_rtklib_server_memory_streams():
    bridge = BRIDGE.read_text(encoding="utf-8")
    assert "rtksvr_t" in bridge
    assert "rtksvrinit(" in bridge
    assert "rtksvrstart(" in bridge
    assert "rtksvrstop(" in bridge
    assert "rtksvrfree(" in bridge
    assert "STR_MEMBUF" in bridge
    assert "strwrite(" in bridge
    assert "strread(" in bridge


def test_native_bridge_does_not_use_single_latest_base_observation_snapshot():
    bridge = BRIDGE.read_text(encoding="utf-8")
    assert "latest_base_obs" not in bridge
    assert "store_base_observations" not in bridge
    assert "solve_if_possible" not in bridge
    assert "rtkpos(&engine->rtk" not in bridge


def test_native_build_includes_rtklib_server_source():
    cmake = CMAKE.read_text(encoding="utf-8")
    assert "${RTKLIB_SRC}/rtksvr.c" in cmake
