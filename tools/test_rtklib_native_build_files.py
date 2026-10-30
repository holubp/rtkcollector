from pathlib import Path
import hashlib
import os
import shutil
import subprocess
import textwrap

import pytest


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


def _bridge():
    return (REPO_ROOT / "app/src/main/cpp/rtklib_bridge.cpp").read_text(encoding="utf-8")


def _entrypoint(bridge, name):
    return bridge.split(f"RtklibNativeBridgeKt_nativeRtklib{name}(", 1)[1].split(
        'extern "C" JNIEXPORT', 1
    )[0]


def test_native_handles_are_non_recycled_ids_not_dereferenceable_addresses():
    bridge = _bridge()
    assert "reinterpret_cast<RtklibEngineHandle *>" not in bridge
    assert "reinterpret_cast<intptr_t>(engine)" not in bridge
    registry = bridge.split("static std::mutex engine_registry_mutex;", 1)[1].split(
        "static std::string fix_class_for", 1
    )[0]
    assert "std::shared_ptr<RtklibEngineHandle>" in registry
    assert "std::numeric_limits<jlong>::max()" in registry
    assert "next_engine_id++" in registry
    assert "if (handle <= 0)" in registry
    assert "engine_registry.find(handle)" in registry
    assert "engine_registry.erase" in registry
    assert "rtksvr" not in registry
    assert "register_engine(std::make_shared<RtklibEngineHandle>())" in _entrypoint(bridge, "Create")


def test_native_entrypoints_share_lifetime_and_exclude_destroy_before_engine_access():
    bridge = _bridge()
    access = bridge.split("class EngineAccess", 1)[1].split("static std::string fix_class_for", 1)[0]
    assert "find_engine(handle)" in access
    assert "std::unique_lock<std::mutex>(engine_->mutex)" in access
    assert "!engine_->destroyed" in access
    assert access.index("std::shared_ptr<RtklibEngineHandle> engine_;") < access.index(
        "std::unique_lock<std::mutex> lock_;"
    )
    for name in ("Start", "Feed", "Snapshot", "Stop"):
        entry = _entrypoint(bridge, name)
        assert entry.index("EngineAccess access(handle);") < entry.index("auto *engine = access.get();")
        assert "if (!engine)" in entry
    destroy = _entrypoint(bridge, "Destroy")
    assert destroy.index("take_engine(handle)") < destroy.index("lock(engine->mutex)")
    assert destroy.index("engine->destroyed = true;") < destroy.index("release_rtklib_state(engine.get())")
    assert "delete engine" not in destroy
    assert "RtklibEngineHandle::~RtklibEngineHandle()" in bridge
    start = _entrypoint(bridge, "Start")
    assert "ScopedServerStart rollback(engine);" in start
    assert "rollback.commit();" in start


def test_jni_allocations_and_exceptions_have_scoped_failure_cleanup():
    bridge = _bridge()
    assert "class ScopedLocalRef" in bridge
    assert "if (value_) env_->DeleteLocalRef(value_);" in bridge
    assert '"java/lang/OutOfMemoryError"' in bridge
    assert "catch (const std::bad_alloc &)" in bridge
    for name in ("Version", "Create", "Start", "Feed", "Snapshot", "Postprocess", "Stop", "Destroy"):
        entry = _entrypoint(bridge, name)
        assert "env->ExceptionCheck()" in entry or "new_java_string(env," in entry
        assert "catch (...)" in entry
        assert "translate_native_exception(env);" in entry
    utf = bridge.split("class ScopedUtfChars", 1)[1].split("struct RtklibEngineHandle", 1)[0]
    assert "value_ && !env_->ExceptionCheck()" in utf
    assert "env_->ReleaseStringUTFChars(value_, chars_)" in utf
    result = bridge.split("static jobjectArray to_java_result", 1)[1].split("}  // namespace", 1)[0]
    assert "ScopedLocalRef<jclass>" in result
    assert "ScopedLocalRef<jobjectArray>" in result
    assert "ScopedLocalRef<jstring>" in result
    assert "jni_allocation_ok(env, string_class.get())" in result
    assert "jni_allocation_ok(env, array.get())" in result
    assert "jni_allocation_ok(env, item.get())" in result
    assert result.index("jni_allocation_ok(env, item.get())") < result.index("SetObjectArrayElement")
    assert "return array.release();" in result
    feed = _entrypoint(bridge, "Feed")
    length_check = feed.split("GetArrayLength(bytes);", 1)[1]
    assert length_check.index("if (env->ExceptionCheck())") < length_check.index("std::vector<uint8_t>")
    assert "if (length > 0)" in feed
    assert "int written = length > 0\n" in bridge
    region = feed.split("GetByteArrayRegion", 1)[1]
    assert region.index("if (env->ExceptionCheck())") < region.index("feed_server_bytes")
    # This bridge returns String/String[] only; the audit's NewObject claim is not applicable.
    assert "NewObject(" not in bridge


def test_rtklib_message_allocation_failure_does_not_unwind_through_c_resources():
    bridge = _bridge()
    callback = bridge.split('extern "C" int showmsg(', 1)[1].split('extern "C" void settspan', 1)[0]
    assert "catch (const std::bad_alloc &)" in callback
    assert "rtklib_message_allocation_failed = true;" in callback
    assert "return 1;" in callback
    assert bridge.count("if (rtklib_message_allocation_failed) throw std::bad_alloc();") == 2


def test_native_boundary_preserves_downstream_disabled_sentinels_and_stop_join():
    bridge = _bridge()
    assert 'outputs[i] = buffers[i].data();' in bridge
    assert 'outputs[i] = nullptr' not in bridge
    assert 'const char *rcvopts[3] = {"", "", ""};' in bridge
    assert 'const char *cmds[3] = {nullptr, nullptr, nullptr};' in bridge
    postpos = bridge.split("int status = postpos(", 1)[1].split(");", 1)[0]
    assert 'output.c_str(),\n        "",\n        ""' in postpos
    stop = _entrypoint(bridge, "Stop")
    assert stop.index("rtksvrstop(&engine->server, cmds)") < stop.index("engine->server_started = false")
    release = bridge.split("static void release_rtklib_state(", 1)[1].split(
        "static void drain_stream_to_string", 1
    )[0]
    assert release.index("rtksvrstop(") < release.index("rtksvrfree(")


def test_native_registry_invalid_stale_double_destroy_and_concurrent_ownership(tmp_path):
    """Compile the actual registry/guard/destroy code with an instrumented engine, not RTKLIB."""
    compiler = shutil.which("clang++") or shutil.which("c++")
    if compiler is None:
        pytest.skip("A C++17 compiler is required for the isolated handle ownership test")
    bridge = _bridge()
    assert "static std::mutex engine_registry_mutex;" in bridge
    registry = bridge.split("static std::mutex engine_registry_mutex;", 1)[1].split(
        "static std::string fix_class_for", 1
    )[0]
    destroy = bridge[bridge.index('extern "C" JNIEXPORT void JNICALL\nJava_org_rtkcollector_core_rtklib_RtklibNativeBridgeKt_nativeRtklibDestroy'):]
    source = textwrap.dedent(r"""
        #include <atomic>
        #include <cassert>
        #include <cstdint>
        #include <future>
        #include <limits>
        #include <memory>
        #include <mutex>
        #include <stdexcept>
        #include <thread>
        #include <unordered_map>
        #include <utility>
        using jlong = long long;
        using jclass = void *;
        #define JNIEXPORT
        #define JNICALL
        struct JNIEnv { bool ExceptionCheck() { return false; } };
        static std::atomic<int> releases{0}, destructions{0};
        struct RtklibEngineHandle {
            std::mutex mutex;
            bool destroyed = false, initialized = true;
            ~RtklibEngineHandle() { ++destructions; }
        };
        static void release_rtklib_state(RtklibEngineHandle *engine) {
            if (engine->initialized) { engine->initialized = false; ++releases; }
        }
        static void translate_native_exception(JNIEnv *) { assert(false); }
        static std::mutex engine_registry_mutex;
    """) + registry + destroy + textwrap.dedent(r"""
        int main() {
            JNIEnv env;
            auto destroy = [&](jlong id) {
                Java_org_rtkcollector_core_rtklib_RtklibNativeBridgeKt_nativeRtklibDestroy(&env, nullptr, id);
            };
            for (jlong invalid : {0LL, -1LL, 42LL, std::numeric_limits<jlong>::max()}) {
                assert(!EngineAccess(invalid).get());
                destroy(invalid);
            }
            auto owner = std::make_shared<RtklibEngineHandle>();
            std::weak_ptr<RtklibEngineHandle> weak = owner;
            jlong old = register_engine(owner);
            owner.reset();
            std::thread closer;
            {
                EngineAccess active(old);
                assert(active.get());
                closer = std::thread([&] { destroy(old); });
                // Removal must precede waiting for this instance's mutex.
                while (find_engine(old)) std::this_thread::yield();
                assert(!weak.expired());
                assert(releases == 0);
                assert(!EngineAccess(old).get());
                destroy(old);  // Concurrent double destroy must not wait on active.
                jlong other = register_engine(std::make_shared<RtklibEngineHandle>());
                assert(other != old);
                { EngineAccess unrelated(other); assert(unrelated.get()); }
                destroy(other);  // An unrelated instance must progress while active is locked.
                assert(releases == 1);
            }
            closer.join();
            assert(weak.expired());
            assert(releases == 2 && destructions == 2);
            destroy(old);
            assert(releases == 2);
            // An owner acquired before removal but waiting on the mutex must not revive it.
            jlong next = register_engine(std::make_shared<RtklibEngineHandle>());
            assert(next != old);
            auto borrowed = find_engine(next);
            destroy(next);
            { std::lock_guard<std::mutex> lock(borrowed->mutex); assert(borrowed->destroyed); }
            assert(!EngineAccess(next).get());
            borrowed.reset();
            // Never wrap/recycle IDs, even at signed jlong exhaustion.
            next_engine_id = static_cast<uint64_t>(std::numeric_limits<jlong>::max());
            jlong last = register_engine(std::make_shared<RtklibEngineHandle>());
            assert(last == std::numeric_limits<jlong>::max());
            bool exhausted = false;
            try { register_engine(std::make_shared<RtklibEngineHandle>()); }
            catch (const std::overflow_error &) { exhausted = true; }
            assert(exhausted);
            destroy(last);
        }
    """)
    harness = tmp_path / "native_handle_ownership.cpp"
    harness.write_text(source, encoding="utf-8")
    executable = tmp_path / "native_handle_ownership"
    subprocess.run([compiler, "-std=c++17", "-pthread", "-Wall", "-Wextra", "-Werror",
                    str(harness), "-o", str(executable)], check=True, timeout=60)
    subprocess.run([str(executable)], check=True, timeout=15)


def test_native_jni_allocation_failure_releases_locals_and_preserves_pending_exception(tmp_path):
    """Run actual JNI adapter helpers against a checked fault-injecting JNI test double."""
    compiler = shutil.which("clang++") or shutil.which("c++")
    if compiler is None:
        pytest.skip("A C++17 compiler is required for isolated JNI allocation tests")
    bridge = _bridge()
    assert "class ScopedLocalRef" in bridge
    helpers = bridge[bridge.index("template <typename T>"):bridge.index("struct RtklibEngineHandle")]
    native_result = bridge[bridge.index("struct NativeResult"):bridge.index("template <typename T>")]
    to_java = bridge[bridge.index("static jobjectArray to_java_result"):bridge.index("}  // namespace", bridge.index("static jobjectArray to_java_result"))]
    number = bridge[bridge.index("static std::string long_to_string"):bridge.index("static long long solution_time_millis")]
    source = textwrap.dedent(r"""
        #include <cassert>
        #include <cstdint>
        #include <cstring>
        #include <new>
        #include <sstream>
        #include <string>
        #include <utility>
        struct Object { bool live = false; };
        using jobject = Object *;
        using jclass = jobject;
        using jstring = jobject;
        using jobjectArray = jobject;
        struct JNIEnv {
            Object objects[64];
            int calls = 0, live = 0, fail_at = 0, sets = 0, utf_calls = 0, utf_releases = 0;
            int utf_fail_at = 0, set_fail_at = -1;
            bool failure_throws = true;
            const char *exception = nullptr;
            bool ExceptionCheck() { return exception != nullptr; }
            jobject allocate() {
                assert(!exception);
                if (++calls == fail_at) {
                    if (failure_throws) exception = "original allocation exception";
                    return nullptr;
                }
                assert(calls < 64);
                objects[calls].live = true;
                ++live;
                return &objects[calls];
            }
            jclass FindClass(const char *) { return allocate(); }
            jstring NewStringUTF(const char *value) { assert(value); return allocate(); }
            jobjectArray NewObjectArray(int count, jclass type, jobject) {
                assert(count == 16 && type && type->live && !exception);
                return allocate();
            }
            void DeleteLocalRef(jobject object) {
                assert(object && object->live);
                object->live = false;
                --live;
            }
            void SetObjectArrayElement(jobjectArray array, int index, jstring item) {
                assert(array && array->live && item && item->live && !exception && index < 16);
                ++sets;
                if (index == set_fail_at) exception = "original array element exception";
            }
            int ThrowNew(jclass type, const char *message) {
                assert(type && type->live && !exception);
                exception = message;
                return 0;
            }
            const char *GetStringUTFChars(jstring value, void *) {
                assert(value && !exception);
                if (++utf_calls == utf_fail_at) { exception = "original UTF exception"; return nullptr; }
                return "test";
            }
            void ReleaseStringUTFChars(jstring value, const char *chars) {
                assert(value && chars);  // This release is legal while an exception is pending.
                ++utf_releases;
            }
        };
        struct RtklibEngineHandle {
            uint64_t decoded_rover_epochs = 0, decoded_correction_messages = 0;
        };
    """) + native_result + helpers + number + to_java + textwrap.dedent(r"""
        int main() {
            NativeResult result;
            for (bool throws : {false, true}) {
                // FindClass, NewObjectArray and every one of the 16 NewStringUTF allocations.
                for (int fail_at = 1; fail_at <= 18; ++fail_at) {
                    JNIEnv env;
                    env.fail_at = fail_at;
                    env.failure_throws = throws;
                    assert(!to_java_result(&env, nullptr, result));
                    assert(env.ExceptionCheck() && env.live == 0);
                    if (throws) assert(!std::strcmp(env.exception, "original allocation exception"));
                }
            }
            JNIEnv success;
            jobjectArray array = to_java_result(&success, nullptr, result);
            assert(array && success.live == 1 && success.sets == 16 && !success.ExceptionCheck());
            success.DeleteLocalRef(array);
            assert(success.live == 0);
            for (int index = 0; index < 16; ++index) {
                JNIEnv set_failure;
                set_failure.set_fail_at = index;
                assert(!to_java_result(&set_failure, nullptr, result));
                assert(set_failure.live == 0 && set_failure.sets == index + 1);
                assert(!std::strcmp(set_failure.exception, "original array element exception"));
            }
            JNIEnv pending;
            pending.exception = "keep me";
            assert(!jni_input_error(&pending, "must not replace exception"));
            assert(!to_java_result(&pending, nullptr, result));
            assert(pending.calls == 0 && !std::strcmp(pending.exception, "keep me"));
            try { throw std::bad_alloc(); } catch (...) { translate_native_exception(&pending); }
            assert(pending.calls == 0 && !std::strcmp(pending.exception, "keep me"));
            Object input;
            JNIEnv utf;
            utf.utf_fail_at = 2;
            {
                ScopedUtfChars first(&utf, &input);
                ScopedUtfChars failed(&utf, &input);
                ScopedUtfChars skipped(&utf, &input);
                assert(!first.ok() && !failed.ok() && !skipped.ok());
                assert(utf.utf_calls == 2);
            }
            assert(utf.utf_releases == 1 && !std::strcmp(utf.exception, "original UTF exception"));
            JNIEnv oom;
            try {
                ScopedUtfChars chars(&oom, &input);
                assert(chars.ok());
                throw std::bad_alloc();
            } catch (...) { translate_native_exception(&oom); }
            assert(oom.utf_releases == 1 && oom.ExceptionCheck() && oom.live == 0);
        }
    """)
    harness = tmp_path / "jni_allocation_failures.cpp"
    harness.write_text(source, encoding="utf-8")
    executable = tmp_path / "jni_allocation_failures"
    subprocess.run([compiler, "-std=c++17", "-Wall", "-Wextra", "-Werror",
                    str(harness), "-o", str(executable)], check=True, timeout=60)
    subprocess.run([str(executable)], check=True, timeout=15)


def test_native_failed_start_rolls_back_actual_adapter_allocations(tmp_path):
    """Exercise actual adapter rollback with fault-injected downstream ownership states.

    The doubles model pinned rtksvrstart allocation order and decoder self-cleanup;
    they are not a replacement for testing the pinned RTKLIB implementation.
    """
    compiler = shutil.which("clang++") or shutil.which("c++")
    if compiler is None:
        pytest.skip("A C++17 compiler is required for the partial-start cleanup test")
    bridge = _bridge()
    cleanup_begin = ("static void release_failed_server_start(" if "static void release_failed_server_start(" in bridge
                     else "static void release_rtklib_state(")
    cleanup = bridge[bridge.index(cleanup_begin):
                     bridge.index("static void drain_stream_to_string(")]
    # Interpose only adapter deallocations so the ledger can detect double frees.
    cleanup = cleanup.replace("std::free(", "tracked_free(")
    source = textwrap.dedent(r"""
        #include <cassert>
        #include <cstdlib>
        #include <cstdint>
        #include <future>
        #include <memory>
        #include <new>
        #include <thread>
        #include <unordered_set>
        constexpr int MAXSTRRTK = 8;
        struct raw_t { void *owned[6]{}; };
        struct rtcm_t { void *owned[3]{}; void *dgps = nullptr; };
        struct stream_t { void *port = nullptr; };
        struct rtksvr_t {
            uint8_t *buff[3]{}, *pbuf[3]{}, *sbuf[2]{};
            int nb[3]{}, npb[3]{}, nsb[2]{};
            raw_t raw[3];
            rtcm_t rtcm[3];
            stream_t stream[MAXSTRRTK];
            void *nav = nullptr;
        };
        struct RtklibEngineHandle {
            rtksvr_t server;
            bool server_initialized = false, server_started = false, server_start_pending = false;
            uint64_t decoded_rover_epochs = 1, decoded_correction_messages = 1;
            ~RtklibEngineHandle();
        };
        static std::unordered_set<void *> owned;
        static int calls = 0, fail_at = 0, frees = 0, joins = 0, raw_frees = 0;
        static bool worker_active = false;
        static void *allocate() {
            if (++calls == fail_at) return nullptr;
            auto *p = std::malloc(1);
            assert(p && owned.insert(p).second);
            return p;
        }
        static void tracked_free(void *p) {
            assert(!worker_active);
            if (p) { assert(owned.erase(p) == 1); ++frees; std::free(p); }
        }
        static void free_raw(raw_t *raw) {
            ++raw_frees;
            for (auto &p : raw->owned) { tracked_free(p); p = nullptr; }
        }
        static void free_rtcm(rtcm_t *rtcm) {
            for (auto &p : rtcm->owned) { tracked_free(p); p = nullptr; }
            // dgps aliases server nav: the decoder must not free it.
        }
        static void strclose(stream_t *stream) {
            tracked_free(stream->port); stream->port = nullptr;
        }
        static void rtksvrfree(rtksvr_t *server) {
            tracked_free(server->nav);  // Pinned free does not null its owned pointers.
        }
        static void worker_cleanup(rtksvr_t *s) {
            for (auto &stream : s->stream) strclose(&stream);
            for (int i = 0; i < 3; ++i) {
                tracked_free(s->buff[i]); s->buff[i] = nullptr;
                tracked_free(s->pbuf[i]); s->pbuf[i] = nullptr;
                free_raw(s->raw + i); free_rtcm(s->rtcm + i);
            }
            for (auto &p : s->sbuf) { tracked_free(p); p = nullptr; }
        }
        static void rtksvrstop(rtksvr_t *s, const char **cmds) {
            assert(cmds && !cmds[0] && !cmds[1] && !cmds[2] && worker_active);
            std::promise<void> exit;
            auto ready = exit.get_future();
            std::thread worker([&] { worker_active = false; worker_cleanup(s); exit.set_value(); });
            ready.wait();
            worker.join();
            ++joins;
        }
    """) + cleanup + textwrap.dedent(r"""
        static void initialize(RtklibEngineHandle *engine) {
            calls = 0; fail_at = 0;
            engine->server.nav = allocate();
            engine->server_initialized = true;
        }
        static bool start(rtksvr_t *s, int stream_failure, bool thread_failure) {
            for (int i = 0; i < 3; ++i) {
                s->buff[i] = static_cast<uint8_t *>(allocate());
                if (!s->buff[i]) return false;
                s->pbuf[i] = static_cast<uint8_t *>(allocate());
                if (!s->pbuf[i]) return false;
                for (auto &p : s->raw[i].owned) {
                    p = allocate();
                    if (!p) { free_raw(s->raw + i); break; }
                }
                for (auto &p : s->rtcm[i].owned) {
                    p = allocate();
                    if (!p) { free_rtcm(s->rtcm + i); break; }
                }
                s->rtcm[i].dgps = s->nav;
            }
            for (auto &p : s->sbuf) {
                p = static_cast<uint8_t *>(allocate());
                if (!p) return false;
            }
            for (int i = 0; i < MAXSTRRTK; ++i) {
                s->stream[i].port = allocate();
                if (i == stream_failure || !s->stream[i].port) {
                    // A partially opened failed stream may still own resources.
                    for (int j = 0; j < i; ++j) strclose(s->stream + j);
                    return false;
                }
            }
            if (thread_failure) {
                for (auto &stream : s->stream) strclose(&stream);
                return false;
            }
            worker_active = true;
            return true;
        }
        static void assert_released(RtklibEngineHandle *engine) {
            assert(owned.empty());
            assert(!engine->server_initialized && !engine->server_started && !engine->server_start_pending);
            assert(!engine->decoded_rover_epochs && !engine->decoded_correction_messages);
            int count = frees;
            release_rtklib_state(engine);
            assert(frees == count);
        }
        int main() {
            // Every allocation boundary, including decoder failures ignored by pinned start.
            for (int failure = 43; failure >= 1; --failure) {
                auto engine = std::make_unique<RtklibEngineHandle>();
                initialize(engine.get());
                calls = 0; fail_at = failure;
                {
                    ScopedServerStart rollback(engine.get());
                    engine->server_start_pending = true;
                    if (start(&engine->server, -1, false)) {
                        engine->server_started = true;
                        engine->server_start_pending = false;
                    }
                }
                assert_released(engine.get());
            }
            for (int failure = 0; failure < MAXSTRRTK; ++failure) {
                RtklibEngineHandle engine;
                initialize(&engine);
                { ScopedServerStart rollback(&engine); engine.server_start_pending = true;
                  assert(!start(&engine.server, failure, false)); }
                assert_released(&engine);
            }
            { RtklibEngineHandle engine; initialize(&engine);
              { ScopedServerStart rollback(&engine); engine.server_start_pending = true;
                assert(!start(&engine.server, -1, true)); }
              assert_released(&engine); }
            // Exceptions before downstream start and after successful thread creation.
            for (bool after_start : {false, true}) {
                RtklibEngineHandle engine;
                initialize(&engine);
                try {
                    ScopedServerStart rollback(&engine);
                    if (after_start) {
                        engine.server_start_pending = true;
                        assert(start(&engine.server, -1, false));
                        engine.server_started = true;
                        engine.server_start_pending = false;
                    }
                    throw std::bad_alloc();
                } catch (const std::bad_alloc &) {}
                assert_released(&engine);
            }
            // Explicit normal stop followed by destroy must not free worker allocations again.
            { RtklibEngineHandle engine; initialize(&engine);
              { ScopedServerStart rollback(&engine); engine.server_start_pending = true;
                assert(start(&engine.server, -1, false)); engine.server_started = true;
                engine.server_start_pending = false; rollback.commit(); }
              const char *cmds[3] = {nullptr, nullptr, nullptr};
              rtksvrstop(&engine.server, cmds); engine.server_started = false;
              int worker_frees = raw_frees;
              release_rtklib_state(&engine); assert_released(&engine);
              assert(raw_frees == worker_frees); }
            // Failed rtksvrinit has already freed its allocations; the flag stays false.
            { RtklibEngineHandle engine; release_rtklib_state(&engine); assert_released(&engine); }
            assert(joins > 0 && owned.empty());
        }
    """)
    harness = tmp_path / "native_partial_start.cpp"
    harness.write_text(source, encoding="utf-8")
    executable = tmp_path / "native_partial_start"
    subprocess.run([compiler, "-std=c++17", "-pthread", "-Wall", "-Wextra", "-Werror",
                    str(harness), "-o", str(executable)], check=True, timeout=60)
    subprocess.run([str(executable)], check=True, timeout=15)
    start = _entrypoint(bridge, "Start")
    assert start.index("engine->server_start_pending = true;") < start.index("if (!rtksvrstart(")
    success = start.split("engine->server_started = true;", 1)[1]
    assert success.index("engine->server_start_pending = false;") < success.index("engine->latest =")


def _server_cmake_block():
    cmake = (REPO_ROOT / "app/src/main/cpp/CMakeLists.txt").read_text(encoding="utf-8")
    return cmake.split("# RC10_PINNED_SERVER_ADAPTATION_BEGIN", 1)[1].split(
        "# RC10_PINNED_SERVER_ADAPTATION_END", 1
    )[0]


def test_ci_runs_generated_native_fault_tests_after_provisioning_before_assembly():
    workflow = (REPO_ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
    provision = workflow.index("name: Provision pinned RTKLIB-EX source")
    native_tests = workflow.index("name: Test native boundaries and startup recovery")
    assembly = workflow.index("name: Assemble debug bootstrap")
    assert provision < native_tests < assembly
    block = workflow[native_tests:workflow.index("      - name:", native_tests)]
    for required in ("pytest==9.1.1", "command -v cmake", "command -v clang",
                     "command -v clang++", "RTKCOLLECTOR_NATIVE_TEST_SOURCE_DIR",
                     "tools/test_rtklib_native_build_files.py",
                     "tools/test_rtklib_native_server_backend.py"):
        assert required in block


def _pinned_native_sources():
    override = os.environ.get("RTKCOLLECTOR_NATIVE_TEST_SOURCE_DIR")
    src = Path(override) if override else REPO_ROOT / "third_party/rtklib-ex/upstream/src"
    required = ("rtksvr.c", "rtklib.h", "rcvraw.c", "rtcm.c")
    missing = [name for name in required if not (src / name).is_file()]
    if missing:
        if override:
            pytest.fail(f"Incomplete explicit pinned native test source directory: {missing}")
        pytest.skip("Provision the pinned RTKLIB checkout to run generated-C native tests")
    return src


def _generate_server(tmp_path, content, block=None):
    cmake = shutil.which("cmake")
    if cmake is None:
        pytest.skip("CMake is required to test the production native source adaptation")
    tmp_path.mkdir(parents=True, exist_ok=True)
    src = tmp_path / "src"
    generated = tmp_path / "generated"
    src.mkdir(exist_ok=True)
    generated.mkdir(exist_ok=True)
    (src / "rtksvr.c").write_bytes(content)
    script = tmp_path / "adapt.cmake"
    script.write_text(
        f'set(RTKLIB_SRC [=[{src.as_posix()}]=])\n'
        f'set(RTKLIB_GENERATED_DIR [=[{generated.as_posix()}]=])\n'
        + (_server_cmake_block() if block is None else block), encoding="utf-8"
    )
    result = subprocess.run([cmake, "-P", str(script)], capture_output=True, text=True, timeout=30)
    assert (src / "rtksvr.c").read_bytes() == content  # Never mutate the upstream input.
    return result, generated / "rtksvr.c"


def test_server_cmake_compiles_only_the_pin_checked_generated_server():
    cmake = (REPO_ROOT / "app/src/main/cpp/CMakeLists.txt").read_text(encoding="utf-8")
    block = _server_cmake_block()
    assert 'string(REPLACE "\\r\\n" "\\n"' in block
    assert block.index('string(REPLACE "\\r\\n" "\\n"') < block.index("string(SHA256")
    assert "e1685aa9c990a3df672770dcaa122e1f4bbdec191f07b40f793a727e1d5e8b5b" in block
    library_sources = cmake.split("add_library(", 1)[1].split("target_include_directories(", 1)[0]
    assert '"${RTKLIB_GENERATED_DIR}/rtksvr.c"' in library_sources
    assert '"${RTKLIB_SRC}/rtksvr.c"' not in library_sources
    assert '"${RTKCOLLECTOR_RTKSVR_SOURCE}"' in block


@pytest.mark.parametrize("line_ending", [b"\n", b"\r\n"], ids=["LF", "Windows-CRLF"])
def test_server_cmake_normalizes_pinned_checkout_and_preserves_c_semicolons(tmp_path, line_ending):
    original = (_pinned_native_sources() / "rtksvr.c").read_bytes().replace(b"\r\n", b"\n")
    result, generated = _generate_server(tmp_path, original.replace(b"\n", line_ending))
    assert result.returncode == 0, result.stdout + result.stderr
    adapted = generated.read_bytes()
    assert b"\r" not in adapted
    assert b"if (!init_raw(svr->raw+i,formats[i])||!init_rtcm(svr->rtcm+i))" in adapted
    assert b"goto rtkcollector_worker_cleanup;" in adapted
    assert b"for (cycle=0;rtkcollector_server_running(svr);cycle++)" in adapted
    assert adapted.count(b"rtkcollector_server_state(svr,1);") == 1
    assert b"rtkcollector_worker_cleanup:\n    rtkcollector_server_state(svr,0);\n    free(data);" in adapted


def test_server_cmake_rejects_real_pin_drift(tmp_path):
    original = (_pinned_native_sources() / "rtksvr.c").read_bytes()
    result, generated = _generate_server(tmp_path, original + b"\n/* unintended drift */\n")
    assert result.returncode != 0
    assert "normalized source SHA256 mismatch" in result.stderr
    assert not generated.exists()


@pytest.mark.parametrize("copies", [0, 2])
def test_server_cmake_rejects_missing_or_duplicate_anchor_even_after_hash_gate(tmp_path, copies):
    original = (_pinned_native_sources() / "rtksvr.c").read_bytes().replace(b"\r\n", b"\n")
    anchor = b"    svr->state=1;\n"
    assert original.count(anchor) == 1
    changed = original.replace(anchor, anchor * copies)
    # Isolate the independent anchor-count gate; production retains its exact pin SHA.
    block = _server_cmake_block().replace(hashlib.sha256(original).hexdigest(),
                                        hashlib.sha256(changed).hexdigest())
    result, generated = _generate_server(tmp_path, changed, block)
    assert result.returncode != 0
    assert "anchor count mismatch" in result.stderr
    assert not generated.exists()


def _c_function(source, signature):
    begin = source.index(signature)
    return source[begin:source.index("\n}", begin) + 2] + "\n"


def test_generated_server_fault_cleanup_stop_ordering_and_failed_native_telemetry(tmp_path):
    """Compile production CMake output control paths, pinned decoders, and actual C++ rollback.

    GNSS computation and stream I/O are doubles. Actual pthreads use deterministic
    gates, not sleeps, to place Stop before worker entry and OOM before telemetry.
    """
    cc, cxx = shutil.which("clang") or shutil.which("cc"), shutil.which("clang++") or shutil.which("c++")
    if not cc or not cxx:
        pytest.skip("C and C++17 compilers are required for generated native control tests")
    src = _pinned_native_sources()
    result, generated = _generate_server(tmp_path, (src / "rtksvr.c").read_bytes())
    assert result.returncode == 0, result.stdout + result.stderr
    adapted = generated.read_text(encoding="utf-8")
    bridge = _bridge()
    rollback = bridge[bridge.index("static void release_failed_server_start("):
                      bridge.index("static void drain_stream_to_string(")]
    rollback = rollback.replace("std::free(", "test_free(")
    observe = _c_function(bridge, "static bool observe_server_failure(")
    for entry in ("Feed", "Snapshot"):
        assert "observe_server_failure(engine, &result)" in (
            _entrypoint(bridge, entry) if entry == "Snapshot" else
            bridge.split("static NativeResult feed_server_bytes", 1)[1].split("static jobjectArray", 1)[0]
        )
    c_source = textwrap.dedent(r"""
        #include "rtklib.h"
        #include <assert.h>
        #define MIN_INT_RESET 30000
        extern void *test_malloc(size_t);
        extern void *test_calloc(size_t,size_t);
        extern void test_free(void *);
        extern int test_create(pthread_t *,const pthread_attr_t *,void *(*)(void *),void *);
        extern int test_join(pthread_t,void **);
        #define malloc test_malloc
        #define calloc test_calloc
        #define free test_free
        #define pthread_create test_create
        #define pthread_join test_join
        const prcopt_t prcopt_default={0};
        const solopt_t solopt_default={0};
        void rtkinit(rtk_t *r,const prcopt_t *o) { memset(r,0,sizeof(*r)); r->opt=*o; }
        void rtkfree(rtk_t *r) { (void)r; }
        void strinitcom(void) {}
        gtime_t timeget(void) { gtime_t t={0}; return t; }
        gtime_t utc2gpst(gtime_t t) { return t; }
        gtime_t gpst2utc(gtime_t t) { return t; }
        gtime_t timeadd(gtime_t t,double d) { (void)d; return t; }
        void timeset(gtime_t t) { (void)t; }
        uint32_t tickget(void) { return 1000; }
        gtime_t strgettime(stream_t *s) { (void)s; return timeget(); }
        void strsync(stream_t *a,stream_t *b) { (void)a; (void)b; }
        int strwrite(stream_t *s,uint8_t *b,int n) { (void)s; (void)b; return n; }
        int strread(stream_t *s,uint8_t *b,int n) { (void)s; (void)b; (void)n; return 0; }
        void strsendcmd(stream_t *s,const char *c) { (void)s; assert(!c); }
        extern void test_sleepms(int n);
        void sleepms(int n) { test_sleepms(n); }
        int init_rt17(raw_t *r) { (void)r; assert(0); return 0; }
        int init_sbf(raw_t *r) { (void)r; assert(0); return 0; }
        void free_rt17(raw_t *r) { (void)r; assert(0); }
        void free_sbf(raw_t *r) { (void)r; assert(0); }
        void strinit(stream_t *s) { memset(s,0,sizeof(*s)); }
        int stream_failure=-1, stream_calls=0, decode_calls=0;
        typedef struct { void *buffer; } port_t;
        int stropen(stream_t *s,int type,int mode,const char *path) {
            (void)path; int index=stream_calls++; s->type=type; s->mode=mode;
            if (index==stream_failure) return 0;
            if (type==STR_NONE) return 1;
            assert(type==STR_MEMBUF);
            port_t *p=malloc(sizeof(*p)); if (!p) return 0;
            if (!(p->buffer=malloc(4096))) { free(p); return 0; }
            s->port=p; return 1;
        }
        void strclose(stream_t *s) {
            if (s->port) { port_t *p=s->port; free(p->buffer); free(p); } s->port=NULL;
        }
        static void writesolhead(stream_t *s,const solopt_t *o,const prcopt_t *p) { (void)s; (void)o; (void)p; }
        static void writesol(rtksvr_t *s,int i) { (void)s; (void)i; }
        static void periodic_cmd(int c,const char *cmd,stream_t *s) { (void)c; (void)cmd; (void)s; }
        static void send_nmea(rtksvr_t *s,uint32_t *t) { (void)s; (void)t; }
        static void corr_phase_bias(obsd_t *o,int n,const nav_t *v) { (void)o; (void)n; (void)v; assert(0); }
        static void decodefile(rtksvr_t *s,int i) { (void)s; (void)i; assert(0); }
        static int decoderaw(rtksvr_t *s,int i) { assert(s->nb[i]==0); ++decode_calls; return 0; }
        int pntpos(const obsd_t *o,int n,const nav_t *v,const prcopt_t *p,sol_t *s,double *a,ssat_t *t,char *m) {
            (void)o;(void)n;(void)v;(void)p;(void)s;(void)a;(void)t;(void)m; assert(0); return 0;
        }
        int rtkpos(rtk_t *r,const obsd_t *o,int n,const nav_t *v) { (void)r;(void)o;(void)n;(void)v; assert(0); return 0; }
    """)
    for filename, signatures in (
        ("rcvraw.c", ("extern int init_raw(", "extern void free_raw(")),
        ("rtcm.c", ("extern int init_rtcm(", "extern void free_rtcm(")),
    ):
        content = (src / filename).read_text(encoding="utf-8")
        for signature in signatures:
            c_source += _c_function(content, signature)
    c_source += "\n".join(line for line in adapted.splitlines()
                          if line.startswith(("extern void rtksvrlock  (", "extern void rtksvrunlock("))) + "\n"
    for signature in ("static int rtkcollector_server_running(", "static void rtkcollector_server_state("):
        c_source += _c_function(adapted, signature)
    worker = adapted[adapted.index("/* rtk server thread ---"):]
    c_source += worker[:worker.index("\n}") + 2] + "\n"
    for signature in ("extern int rtksvrinit(", "extern void rtksvrfree(", "extern int rtksvrstart(", "extern void rtksvrstop("):
        c_source += _c_function(adapted, signature)
    c_file = tmp_path / "generated_server_control.c"
    c_file.write_text(c_source, encoding="utf-8")
    native_result = bridge[bridge.index("struct NativeResult"):bridge.index("template <typename T>")]
    cpp_source = textwrap.dedent(r"""
        #include <cassert>
        #include <cerrno>
        #include <condition_variable>
        #include <cstring>
        #include <cstdlib>
        #include <cstdint>
        #include <cstdio>
        #include <memory>
        #include <mutex>
        #include <string>
        #include <unordered_set>
        extern "C" {
        #include "rtklib.h"
        extern int stream_failure,stream_calls,decode_calls;
        }
        static std::mutex allocation_mutex, schedule_mutex;
        static std::condition_variable schedule;
        static std::unordered_set<void *> owned;
        static int calls=0,fail_at=0,creates=0,joins=0;
        static bool fail_thread=false,fail_worker=false,permit=false,exited=false;
        static bool entered_cycle=false,release_cycle=false;
        static void *(*body)(void *);
        static void *argument;
        static rtksvr_t *active;
        extern "C" void *test_malloc(size_t size) {
            std::lock_guard<std::mutex> lock(allocation_mutex);
            if (++calls==fail_at) return nullptr;
            void *p=std::malloc(size); assert(p && owned.insert(p).second); return p;
        }
        extern "C" void *test_calloc(size_t n,size_t size) {
            if (fail_worker) return nullptr;
            auto *p=test_malloc(n*size); if (p) std::memset(p,0,n*size); return p;
        }
        extern "C" void test_free(void *p) {
            std::lock_guard<std::mutex> lock(allocation_mutex);
            if (p) { assert(owned.erase(p)==1); std::free(p); }
        }
        extern "C" void test_sleepms(int) {
            std::unique_lock<std::mutex> lock(schedule_mutex);
            entered_cycle=true; schedule.notify_all();
            schedule.wait(lock,[]{return release_cycle;});
        }
        static void *delayed_body(void *) {
            { std::unique_lock<std::mutex> lock(schedule_mutex); schedule.wait(lock,[]{return permit;}); }
            void *result=body(argument);
            { std::lock_guard<std::mutex> lock(schedule_mutex); exited=true; }
            schedule.notify_all(); return result;
        }
        extern "C" int test_create(pthread_t *t,const pthread_attr_t *a,void *(*fn)(void *),void *arg) {
            ++creates; if (fail_thread) return EAGAIN;
            body=fn; argument=arg; return pthread_create(t,a,delayed_body,nullptr);
        }
        extern "C" int test_join(pthread_t t,void **result) {
            ++joins; assert(active);
            rtksvrlock(active); assert(active->state==0); rtksvrunlock(active);
            { std::lock_guard<std::mutex> lock(schedule_mutex); permit=release_cycle=true; }
            schedule.notify_all(); return pthread_join(t,result);
        }
    """) + native_result + textwrap.dedent(r"""
        struct RtklibEngineHandle {
            rtksvr_t server{};
            bool server_initialized=false,server_started=false,server_start_pending=false;
            uint64_t decoded_rover_epochs=0,decoded_correction_messages=0;
            NativeResult latest;
            ~RtklibEngineHandle();
        };
    """) + rollback + observe + textwrap.dedent(r"""
        static void reset() {
            assert(owned.empty()); calls=fail_at=creates=joins=0;
            stream_failure=-1; stream_calls=decode_calls=0;
            fail_thread=fail_worker=permit=exited=entered_cycle=release_cycle=false; active=nullptr;
        }
        static bool initialize(RtklibEngineHandle *e) {
            if (!rtksvrinit(&e->server)) return false;
            e->server_initialized=true; active=&e->server; return true;
        }
        static int start(RtklibEngineHandle *e,int raw_format=STRFMT_UBX) {
            int strs[8]={STR_MEMBUF,STR_MEMBUF,STR_NONE,STR_MEMBUF,STR_MEMBUF,STR_NONE,STR_NONE,STR_NONE};
            const char *paths[8]={"4096","4096","","4096","4096","","",""};
            int formats[3]={raw_format,STRFMT_RTCM3,STRFMT_RTCM3};
            const char *cmds[3]={nullptr,nullptr,nullptr},*opts[3]={"","",""};
            double pos[3]={}; prcopt_t opt{}; solopt_t sol[2]{}; char error[2048]{};
            e->server_start_pending=true;
            int ok=rtksvrstart(&e->server,1,4096,strs,paths,formats,0,cmds,cmds,opts,0,0,pos,&opt,sol,nullptr,error);
            if (ok) { e->server_started=true; e->server_start_pending=false; }
            return ok;
        }
        static void clean(RtklibEngineHandle *e) {
            release_rtklib_state(e);
            assert(owned.empty() && !e->server_initialized && !e->server_start_pending && !e->server_started);
            int count=joins; release_rtklib_state(e); assert(joins==count && owned.empty());
        }
        int main() {
            reset(); auto e=std::make_unique<RtklibEngineHandle>();
            assert(initialize(e.get())); int init_allocations=calls;
            calls=0; fail_thread=true; assert(!start(e.get())); int start_allocations=calls;
            assert(creates==1 && joins==0 && e->server.state==0); clean(e.get());
            for (int failure=1;failure<=init_allocations;++failure) {
                reset(); e=std::make_unique<RtklibEngineHandle>(); fail_at=failure;
                assert(!initialize(e.get())); clean(e.get()); assert(creates==0 && joins==0);
            }
            for (int format : {STRFMT_UBX,STRFMT_UNICORE}) {
                for (int failure=1;failure<=start_allocations;++failure) {
                    reset(); e=std::make_unique<RtklibEngineHandle>(); assert(initialize(e.get()));
                    calls=0; fail_at=failure; assert(!start(e.get(),format));
                    assert(creates==0 && joins==0); clean(e.get());
                }
            }
            for (int failure=0;failure<8;++failure) {
                reset(); e=std::make_unique<RtklibEngineHandle>(); assert(initialize(e.get()));
                stream_failure=failure; assert(!start(e.get())); assert(creates==0); clean(e.get());
            }
            // Stop precedes the worker body; join's gate then releases it. No resurrection or decoding.
            for (int repeat=0;repeat<20;++repeat) {
                reset(); e=std::make_unique<RtklibEngineHandle>(); assert(initialize(e.get()));
                assert(start(e.get())); assert(e->server.state==1);
                NativeResult before; assert(!observe_server_failure(e.get(),&before));
                clean(e.get()); assert(joins==1 && e->server.state==0 && decode_calls==0);
            }
            // Stop also terminates an already-running worker at the next locked state check.
            reset(); e=std::make_unique<RtklibEngineHandle>(); assert(initialize(e.get())); assert(start(e.get()));
            { std::lock_guard<std::mutex> lock(schedule_mutex); permit=true; } schedule.notify_all();
            { std::unique_lock<std::mutex> lock(schedule_mutex); schedule.wait(lock,[]{return entered_cycle;}); }
            NativeResult running; assert(!observe_server_failure(e.get(),&running));
            clean(e.get()); assert(joins==1 && e->server.state==0 && decode_calls==3);
            // Worker-local OOM occurs before telemetry: cleanup, FAILED status, one join only.
            reset(); e=std::make_unique<RtklibEngineHandle>(); assert(initialize(e.get()));
            fail_worker=true; assert(start(e.get()));
            NativeResult initially_running; assert(!observe_server_failure(e.get(),&initially_running));
            { std::lock_guard<std::mutex> lock(schedule_mutex); permit=true; } schedule.notify_all();
            { std::unique_lock<std::mutex> lock(schedule_mutex); schedule.wait(lock,[]{return exited;}); }
            NativeResult failed;
            assert(observe_server_failure(e.get(),&failed));
            assert(failed.state=="FAILED" && !failed.error.empty() && e->latest.state=="FAILED");
            assert(joins==1 && owned.empty() && !e->server_started);
            assert(!observe_server_failure(e.get(),&failed)); clean(e.get()); assert(joins==1);
            std::printf("PASS generated C: %d init failures, 2 x %d start failures before thread launch, "
                        "8 stream failures, thread-create failure, 20 stop-before-body orderings, "
                        "stop while running, worker calloc failure -> FAILED with single join\n",init_allocations,start_allocations);
        }
    """)
    cpp_file = tmp_path / "generated_server_adapter.cpp"
    cpp_file.write_text(cpp_source, encoding="utf-8")
    defines = ["-DENAGLO", "-DENAGAL", "-DENAQZS", "-DENACMP", "-DNFREQ=3", "-DNEXOBS=3"]
    subprocess.run([cc, "-std=c11", *defines, "-I", str(src), "-fsyntax-only", str(generated)], check=True, timeout=60)
    obj = tmp_path / "generated_server_control.o"
    subprocess.run([cc, "-std=c11", *defines, "-I", str(src), "-c", str(c_file), "-o", str(obj)], check=True, timeout=60)
    executable = tmp_path / "generated_server_adapter"
    subprocess.run([cxx, "-std=c++17", "-pthread", "-Wall", "-Wextra", "-Werror", *defines,
                    "-I", str(src), str(cpp_file), str(obj), "-o", str(executable)], check=True, timeout=60)
    result = subprocess.run([str(executable)], check=True, capture_output=True, text=True, timeout=30)
    print(result.stdout)
