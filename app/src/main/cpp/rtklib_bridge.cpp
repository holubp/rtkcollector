#include <jni.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

extern "C" {
#include "rtklib.h"
}

namespace {

constexpr int STREAM_ROVER = 0;
constexpr int STREAM_CORRECTION = 1;
constexpr int SERVER_STREAM_ROVER = 0;
constexpr int SERVER_STREAM_BASE = 1;
constexpr int SERVER_STREAM_NMEA = 3;
constexpr int SERVER_STREAM_POS = 4;

struct NativeResult {
    std::string state = "RUNNING";
    std::string warning;
    std::string error;
    std::string nmea;
    std::string pos;
    std::string fix_class;
    std::string timestamp_millis;
    std::string lat_deg;
    std::string lon_deg;
    std::string height_m;
    std::string h_acc_m;
    std::string v_acc_m;
    std::string satellites_used;
};

struct RtklibEngineHandle {
    std::mutex mutex;
    rtksvr_t server{};
    prcopt_t prcopt{};
    solopt_t solopt[2]{};
    bool server_initialized = false;
    bool server_started = false;
    bool output_nmea = true;
    bool output_pos = true;
    uint64_t decoded_rover_epochs = 0;
    uint64_t decoded_correction_messages = 0;
    NativeResult latest;
};

static std::string fix_class_for(int stat) {
    switch (stat) {
        case SOLQ_FIX: return "RTK_FIXED";
        case SOLQ_FLOAT: return "RTK_FLOAT";
        case SOLQ_DGPS: return "DGPS";
        case SOLQ_SINGLE: return "SINGLE";
        case SOLQ_PPP: return "PPP";
        case SOLQ_NONE: return "NONE";
        default: return "INVALID";
    }
}

static std::string double_to_string(double value) {
    if (!std::isfinite(value)) return "";
    std::ostringstream out;
    out.precision(12);
    out << value;
    return out.str();
}

static std::string long_to_string(long long value) {
    std::ostringstream out;
    out << value;
    return out.str();
}

static long long solution_time_millis(const sol_t *sol) {
    if (sol->time.time <= 0) return 0;
    return static_cast<long long>(sol->time.time) * 1000LL +
           static_cast<long long>(sol->time.sec * 1000.0);
}

static int rover_format_for(const char *format) {
    if (std::strcmp(format, "UBX_RXM_RAWX_SFRBX") == 0) return STRFMT_UBX;
    if (std::strcmp(format, "UNICORE_OBSVMB") == 0) return STRFMT_UNICORE;
    if (std::strcmp(format, "UNICORE_OBSVMCMPB") == 0) return STRFMT_UNICORE;
    return -1;
}

static int clamp_positive(int value, int minimum) {
    return std::max(value, minimum);
}

static void configure_options(RtklibEngineHandle *engine, const char *preset, int frequency_count) {
    engine->prcopt = prcopt_default;
    engine->prcopt.soltype = SOLTYPE_FORWARD;
    engine->prcopt.mode = std::strcmp(preset, "TEMPORARY_BASE_STATIC_RTK") == 0 ? PMODE_STATIC : PMODE_KINEMA;
    engine->prcopt.navsys = SYS_GPS | SYS_GLO | SYS_GAL | SYS_CMP | SYS_QZS;
    engine->prcopt.nf = std::max(1, std::min(3, frequency_count));
    engine->prcopt.refpos = POSOPT_RTCM;
    engine->prcopt.rovpos = POSOPT_POS_LLH;

    engine->solopt[0] = solopt_default;
    engine->solopt[0].posf = SOLF_NMEA;
    engine->solopt[0].times = TIMES_GPST;
    engine->solopt[0].timeu = 3;
    engine->solopt[0].outhead = 0;

    engine->solopt[1] = solopt_default;
    engine->solopt[1].posf = SOLF_LLH;
    engine->solopt[1].times = TIMES_GPST;
    engine->solopt[1].timeu = 3;
    engine->solopt[1].outhead = 0;
}

static void release_rtklib_state(RtklibEngineHandle *engine) {
    if (engine->server_started) {
        const char *cmds[3] = {nullptr, nullptr, nullptr};
        rtksvrstop(&engine->server, cmds);
        engine->server_started = false;
    }
    if (engine->server_initialized) {
        rtksvrfree(&engine->server);
        engine->server_initialized = false;
    }
    engine->decoded_rover_epochs = 0;
    engine->decoded_correction_messages = 0;
}

static void drain_stream_to_string(stream_t *stream, std::string *target) {
    uint8_t buff[4096] = {0};
    while (true) {
        int n = strread(stream, buff, static_cast<int>(sizeof(buff)));
        if (n <= 0) break;
        target->append(reinterpret_cast<const char *>(buff), n);
    }
    if (!target->empty() && target->back() != '\n') {
        target->push_back('\n');
    }
}

static void drain_solution_streams(RtklibEngineHandle *engine, NativeResult *result) {
    if (engine->output_nmea) drain_stream_to_string(engine->server.stream + SERVER_STREAM_NMEA, &result->nmea);
    if (engine->output_pos) drain_stream_to_string(engine->server.stream + SERVER_STREAM_POS, &result->pos);
}

static void update_result_from_server(RtklibEngineHandle *engine, NativeResult *result) {
    sol_t sol{};
    uint64_t rover_messages = 0;
    uint64_t base_messages = 0;

    rtksvrlock(&engine->server);
    sol = engine->server.rtk.sol;
    for (int i = 0; i < 10; i++) {
        rover_messages += static_cast<uint64_t>(engine->server.nmsg[SERVER_STREAM_ROVER][i]);
        base_messages += static_cast<uint64_t>(engine->server.nmsg[SERVER_STREAM_BASE][i]);
    }
    rtksvrunlock(&engine->server);

    engine->decoded_rover_epochs = rover_messages;
    engine->decoded_correction_messages = base_messages;

    if (sol.stat <= SOLQ_NONE) return;

    double pos[3] = {0.0, 0.0, 0.0};
    ecef2pos(sol.rr, pos);
    result->fix_class = fix_class_for(sol.stat);
    result->timestamp_millis = long_to_string(solution_time_millis(&sol));
    result->lat_deg = double_to_string(pos[0] * R2D);
    result->lon_deg = double_to_string(pos[1] * R2D);
    result->height_m = double_to_string(pos[2]);
    result->satellites_used = long_to_string(sol.ns);
    if (sol.qr[0] > 0.0 && sol.qr[1] > 0.0) {
        result->h_acc_m = double_to_string(std::sqrt(sol.qr[0] + sol.qr[1]));
    }
    if (sol.qr[2] > 0.0) {
        result->v_acc_m = double_to_string(std::sqrt(sol.qr[2]));
    }
}

static NativeResult feed_server_bytes(RtklibEngineHandle *engine, int stream_kind, const uint8_t *bytes, int length) {
    NativeResult result = engine->latest;
    result.nmea.clear();
    result.pos.clear();
    result.warning.clear();
    result.error.clear();

    if (!engine->server_started) {
        result.state = "FAILED";
        result.error = "RTKLIB server is not started";
        return result;
    }

    int stream_index = -1;
    if (stream_kind == STREAM_ROVER) {
        stream_index = SERVER_STREAM_ROVER;
    } else if (stream_kind == STREAM_CORRECTION) {
        stream_index = SERVER_STREAM_BASE;
    } else {
        result.warning = "RTKLIB received unknown stream kind";
        engine->latest = result;
        return result;
    }

    int written = strwrite(engine->server.stream + stream_index, const_cast<uint8_t *>(bytes), length);
    if (written < length) {
        result.warning = "RTKLIB memory stream accepted fewer bytes than offered";
    }

    drain_solution_streams(engine, &result);
    update_result_from_server(engine, &result);
    result.state = result.error.empty() ? "RUNNING" : "FAILED";
    engine->latest = result;
    return result;
}

static jobjectArray to_java_result(JNIEnv *env, const RtklibEngineHandle *engine, const NativeResult &result) {
    std::string decoded_rover = long_to_string(static_cast<long long>(engine ? engine->decoded_rover_epochs : 0));
    std::string decoded_correction = long_to_string(static_cast<long long>(engine ? engine->decoded_correction_messages : 0));
    std::string values[] = {
        result.state,
        result.warning,
        result.error,
        result.nmea,
        result.pos,
        result.fix_class,
        result.timestamp_millis,
        result.lat_deg,
        result.lon_deg,
        result.height_m,
        result.h_acc_m,
        result.v_acc_m,
        result.satellites_used,
        decoded_rover,
        decoded_correction,
    };
    jclass string_class = env->FindClass("java/lang/String");
    jobjectArray array = env->NewObjectArray(15, string_class, env->NewStringUTF(""));
    for (int i = 0; i < 15; i++) {
        env->SetObjectArrayElement(array, i, env->NewStringUTF(values[i].c_str()));
    }
    return array;
}

static RtklibEngineHandle *from_handle(jlong handle) {
    return reinterpret_cast<RtklibEngineHandle *>(static_cast<intptr_t>(handle));
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_org_rtkcollector_core_rtklib_RtklibNativeBridgeKt_nativeRtklibVersion(JNIEnv *env, jclass) {
    return env->NewStringUTF("RTKLIB-EX " PATCH_LEVEL);
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_rtkcollector_core_rtklib_RtklibNativeBridgeKt_nativeRtklibCreate(JNIEnv *, jclass) {
    auto *engine = new RtklibEngineHandle();
    return static_cast<jlong>(reinterpret_cast<intptr_t>(engine));
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_rtkcollector_core_rtklib_RtklibNativeBridgeKt_nativeRtklibStart(
    JNIEnv *env,
    jclass,
    jlong handle,
    jstring preset,
    jstring rover_format,
    jstring correction_format,
    jboolean output_nmea,
    jboolean output_pos,
    jint frequency_count,
    jint server_cycle_millis,
    jint server_buffer_bytes,
    jint solution_buffer_bytes
) {
    auto *engine = from_handle(handle);
    if (!engine) return env->NewStringUTF("RTKLIB native handle is null");

    std::lock_guard<std::mutex> lock(engine->mutex);
    release_rtklib_state(engine);

    const char *preset_chars = env->GetStringUTFChars(preset, nullptr);
    const char *rover_chars = env->GetStringUTFChars(rover_format, nullptr);
    const char *correction_chars = env->GetStringUTFChars(correction_format, nullptr);

    int raw_format = rover_format_for(rover_chars);
    std::string error;
    if (raw_format < 0) {
        error = std::string("Unsupported RTKLIB rover format: ") + rover_chars;
    } else if (std::strcmp(correction_chars, "RTCM3") != 0) {
        error = std::string("Unsupported RTKLIB correction format: ") + correction_chars;
    } else {
        configure_options(engine, preset_chars, frequency_count);
        engine->output_nmea = output_nmea == JNI_TRUE;
        engine->output_pos = output_pos == JNI_TRUE;
        if (!rtksvrinit(&engine->server)) {
            error = "RTKLIB server initialization failed";
        } else {
            engine->server_initialized = true;
        }
    }

    if (error.empty()) {
        int strs[8] = {
            STR_MEMBUF,
            STR_MEMBUF,
            STR_NONE,
            engine->output_nmea ? STR_MEMBUF : STR_NONE,
            engine->output_pos ? STR_MEMBUF : STR_NONE,
            STR_NONE,
            STR_NONE,
            STR_NONE,
        };
        int server_buffer = clamp_positive(server_buffer_bytes, 4096);
        int solution_buffer = clamp_positive(solution_buffer_bytes, 4096);
        std::string rover_path = std::to_string(server_buffer);
        std::string base_path = std::to_string(server_buffer);
        std::string nmea_path = std::to_string(solution_buffer);
        std::string pos_path = std::to_string(solution_buffer);
        const char *paths[8] = {
            rover_path.c_str(),
            base_path.c_str(),
            "",
            nmea_path.c_str(),
            pos_path.c_str(),
            "",
            "",
            "",
        };
        int formats[3] = {raw_format, STRFMT_RTCM3, STRFMT_RTCM3};
        const char *cmds[3] = {nullptr, nullptr, nullptr};
        const char *periodic_cmds[3] = {nullptr, nullptr, nullptr};
        const char *rcvopts[3] = {"", "", ""};
        double nmeapos[3] = {0.0, 0.0, 0.0};
        char errmsg[2048] = {0};

        if (!rtksvrstart(
                &engine->server,
                clamp_positive(server_cycle_millis, 1),
                server_buffer,
                strs,
                paths,
                formats,
                0,
                cmds,
                periodic_cmds,
                rcvopts,
                0,
                0,
                nmeapos,
                &engine->prcopt,
                engine->solopt,
                nullptr,
                errmsg)) {
            error = errmsg[0] ? errmsg : "RTKLIB server start failed";
        } else {
            engine->server_started = true;
            engine->latest = NativeResult{};
            engine->latest.state = "RUNNING";
        }
    }

    env->ReleaseStringUTFChars(preset, preset_chars);
    env->ReleaseStringUTFChars(rover_format, rover_chars);
    env->ReleaseStringUTFChars(correction_format, correction_chars);

    if (!error.empty()) {
        release_rtklib_state(engine);
    }
    if (error.empty()) return nullptr;
    return env->NewStringUTF(error.c_str());
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_rtkcollector_core_rtklib_RtklibNativeBridgeKt_nativeRtklibFeed(
    JNIEnv *env,
    jclass,
    jlong handle,
    jint stream_kind,
    jbyteArray bytes
) {
    auto *engine = from_handle(handle);
    if (!engine) {
        NativeResult result;
        result.state = "FAILED";
        result.error = "RTKLIB native handle is null";
        return to_java_result(env, nullptr, result);
    }
    jsize length = env->GetArrayLength(bytes);
    std::vector<uint8_t> buffer(static_cast<size_t>(length));
    env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(buffer.data()));
    std::lock_guard<std::mutex> lock(engine->mutex);
    NativeResult result = feed_server_bytes(engine, stream_kind, buffer.data(), length);
    return to_java_result(env, engine, result);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_rtkcollector_core_rtklib_RtklibNativeBridgeKt_nativeRtklibSnapshot(JNIEnv *env, jclass, jlong handle) {
    auto *engine = from_handle(handle);
    if (!engine) {
        NativeResult result;
        result.state = "STOPPED";
        result.warning = "RTKLIB native handle is null";
        return to_java_result(env, nullptr, result);
    }
    std::lock_guard<std::mutex> lock(engine->mutex);
    NativeResult result = engine->latest;
    result.nmea.clear();
    result.pos.clear();
    if (engine->server_started) {
        drain_solution_streams(engine, &result);
        update_result_from_server(engine, &result);
        engine->latest = result;
    }
    return to_java_result(env, engine, result);
}

extern "C" JNIEXPORT void JNICALL
Java_org_rtkcollector_core_rtklib_RtklibNativeBridgeKt_nativeRtklibStop(JNIEnv *, jclass, jlong handle) {
    auto *engine = from_handle(handle);
    if (!engine) return;
    std::lock_guard<std::mutex> lock(engine->mutex);
    if (engine->server_started) {
        const char *cmds[3] = {nullptr, nullptr, nullptr};
        rtksvrstop(&engine->server, cmds);
        engine->server_started = false;
    }
    engine->latest.state = "STOPPED";
}

extern "C" JNIEXPORT void JNICALL
Java_org_rtkcollector_core_rtklib_RtklibNativeBridgeKt_nativeRtklibDestroy(JNIEnv *, jclass, jlong handle) {
    auto *engine = from_handle(handle);
    if (!engine) return;
    {
        std::lock_guard<std::mutex> lock(engine->mutex);
        release_rtklib_state(engine);
    }
    delete engine;
}
