#include <jni.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdarg>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

extern "C" {
#include "rtklib.h"
}

namespace {
thread_local std::string rtklib_show_messages;

static void reset_rtklib_messages() {
    rtklib_show_messages.clear();
}

static std::string current_rtklib_messages() {
    return rtklib_show_messages;
}
}

extern "C" int showmsg(const char *format, ...) {
    if (!format || !*format) return 0;

    std::array<char, 2048> buffer{};
    va_list args;
    va_start(args, format);
    std::vsnprintf(buffer.data(), buffer.size(), format, args);
    va_end(args);

    if (buffer[0] != '\0') {
        if (!rtklib_show_messages.empty()) rtklib_show_messages.push_back('\n');
        rtklib_show_messages.append(buffer.data());
    }
    return 0;
}

extern "C" void settspan(gtime_t, gtime_t) {}

extern "C" void settime(gtime_t) {}

namespace {

constexpr int STREAM_ROVER = 0;
constexpr int STREAM_CORRECTION = 1;
constexpr int SERVER_STREAM_ROVER = 0;
constexpr int SERVER_STREAM_BASE = 1;
constexpr int SERVER_STREAM_NMEA = 3;
constexpr int SERVER_STREAM_POS = 4;
constexpr int RINEX_OUTPUT_FILE_COUNT = 9;

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

class ScopedUtfChars {
public:
    ScopedUtfChars(JNIEnv *env, jstring value) : env_(env), value_(value) {
        if (value_) chars_ = env_->GetStringUTFChars(value_, nullptr);
    }

    ScopedUtfChars(const ScopedUtfChars &) = delete;
    ScopedUtfChars &operator=(const ScopedUtfChars &) = delete;

    ~ScopedUtfChars() {
        if (chars_) env_->ReleaseStringUTFChars(value_, chars_);
    }

    bool ok() const {
        return value_ != nullptr && chars_ != nullptr;
    }

    const char *c_str() const {
        return chars_ ? chars_ : "";
    }

private:
    JNIEnv *env_;
    jstring value_;
    const char *chars_ = nullptr;
};

static jstring jni_input_error(JNIEnv *env, const char *message) {
    if (env->ExceptionCheck()) return nullptr;
    return env->NewStringUTF(message);
}

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

static int solution_type_for(const char *solution_type) {
    if (std::strcmp(solution_type, "FORWARD") == 0) return SOLTYPE_FORWARD;
    if (std::strcmp(solution_type, "FORWARD_BACKWARD") == 0) return SOLTYPE_COMBINED;
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

static void configure_postprocess_options(
    const char *preset,
    int frequency_count,
    int solution_type,
    int solution_format,
    prcopt_t *prcopt,
    solopt_t *solopt
) {
    *prcopt = prcopt_default;
    prcopt->soltype = solution_type;
    prcopt->mode = std::strcmp(preset, "TEMPORARY_BASE_STATIC_RTK") == 0 ? PMODE_STATIC : PMODE_KINEMA;
    prcopt->navsys = SYS_GPS | SYS_GLO | SYS_GAL | SYS_CMP | SYS_QZS;
    prcopt->nf = std::max(1, std::min(3, frequency_count));
    prcopt->refpos = POSOPT_RINEX;
    prcopt->rovpos = POSOPT_POS_LLH;

    *solopt = solopt_default;
    solopt->posf = solution_format;
    solopt->times = TIMES_GPST;
    solopt->timeu = 3;
    solopt->outhead = solution_format == SOLF_LLH ? 1 : 0;
}

static void configure_rinex_options(rnxopt_t *rnxopt, int frequency_count) {
    std::memset(rnxopt, 0, sizeof(rnxopt_t));
    rnxopt->rnxver = 304;
    rnxopt->obstype = OBSTYPE_PR | OBSTYPE_CP | OBSTYPE_DOP | OBSTYPE_SNR;
    rnxopt->navsys = SYS_GPS | SYS_GLO | SYS_GAL | SYS_QZS | SYS_SBS | SYS_CMP | SYS_IRN;
    rnxopt->ttol = 0.005;
    rnxopt->freqtype = FREQTYPE_L1;
    if (frequency_count >= 2) rnxopt->freqtype |= FREQTYPE_L2;
    if (frequency_count >= 3) rnxopt->freqtype |= FREQTYPE_L3;
    for (int i = 0; i < RNX_NUMSYS; i++) {
        for (int j = 0; j < MAXCODE; j++) rnxopt->mask[i][j] = '1';
        rnxopt->mask[i][MAXCODE] = '\0';
    }
    std::strncpy(rnxopt->prog, "RtkCollector", sizeof(rnxopt->prog) - 1);
    std::strncpy(rnxopt->runby, "RtkCollector", sizeof(rnxopt->runby) - 1);
}

static bool file_non_empty(const std::string &path) {
    FILE *fp = std::fopen(path.c_str(), "rb");
    if (!fp) return false;
    std::fseek(fp, 0, SEEK_END);
    long size = std::ftell(fp);
    std::fclose(fp);
    return size > 0;
}

static void add_existing_file(const std::string &path, std::vector<std::string> *files) {
    if (!path.empty() && file_non_empty(path)) files->push_back(path);
}

static std::string postprocess_work_path(const std::string &output_pos, const char *suffix) {
    return output_pos + suffix;
}

struct RinexConversionOutput {
    std::string observation;
    std::vector<std::string> navigation;
    std::vector<std::string> generated_files;
};

static bool convert_raw_to_rinex(
    int format,
    int frequency_count,
    const std::string &input,
    const std::string &prefix,
    RinexConversionOutput *output,
    std::string *error
) {
    rnxopt_t rnxopt{};
    configure_rinex_options(&rnxopt, frequency_count);
    std::array<std::string, RINEX_OUTPUT_FILE_COUNT> names = {
        prefix + ".obs",
        prefix + ".nav",
        prefix + ".gnav",
        prefix + ".hnav",
        prefix + ".qnav",
        prefix + ".lnav",
        prefix + ".cnav",
        prefix + ".inav",
        "",
    };
    std::array<std::array<char, 1024>, RINEX_OUTPUT_FILE_COUNT> buffers{};
    std::array<char *, RINEX_OUTPUT_FILE_COUNT> outputs{};
    for (int i = 0; i < RINEX_OUTPUT_FILE_COUNT; i++) {
        std::snprintf(buffers[i].data(), buffers[i].size(), "%s", names[i].c_str());
        outputs[i] = buffers[i].data();
        if (!names[i].empty()) std::remove(names[i].c_str());
    }

    reset_rtklib_messages();
    int status = convrnx(format, &rnxopt, input.c_str(), outputs.data());
    if (status <= 0) {
        *error = "RTKLIB convrnx failed for " + input + " (status=" + std::to_string(status) + ")";
        std::string messages = current_rtklib_messages();
        if (!messages.empty()) *error += ": " + messages;
        return false;
    }

    for (int i = 0; i <= 7; i++) {
        if (file_non_empty(names[i])) output->generated_files.push_back(names[i]);
    }
    if (file_non_empty(names[0])) output->observation = names[0];
    for (int i = 1; i <= 7; i++) add_existing_file(names[i], &output->navigation);
    return !output->observation.empty();
}

static bool run_postpos_output(
    const std::vector<std::string> &inputs,
    const char *preset,
    int frequency_count,
    int solution_type,
    int solution_format,
    const std::string &output,
    std::string *error
) {
    prcopt_t prcopt{};
    solopt_t solopt{};
    filopt_t filopt{};
    configure_postprocess_options(preset, frequency_count, solution_type, solution_format, &prcopt, &solopt);

    std::vector<const char *> input_ptrs;
    input_ptrs.reserve(inputs.size());
    for (const auto &path : inputs) input_ptrs.push_back(path.c_str());
    std::remove(output.c_str());

    gtime_t zero{};
    reset_rtklib_messages();
    int status = postpos(
        zero,
        zero,
        0.0,
        0.0,
        &prcopt,
        &solopt,
        &filopt,
        input_ptrs.data(),
        static_cast<int>(input_ptrs.size()),
        output.c_str(),
        "",
        ""
    );
    if (status != 0 || !file_non_empty(output)) {
        *error = "RTKLIB postpos failed for " + output + " (status=" + std::to_string(status) + ")";
        if (!file_non_empty(output)) *error += ", output is empty";
        std::string messages = current_rtklib_messages();
        if (!messages.empty()) *error += ": " + messages;
        return false;
    }
    return true;
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
    if (!string_class) return nullptr;
    jstring empty = env->NewStringUTF("");
    if (!empty) return nullptr;
    jobjectArray array = env->NewObjectArray(15, string_class, empty);
    env->DeleteLocalRef(empty);
    if (!array) return nullptr;
    for (int i = 0; i < 15; i++) {
        jstring item = env->NewStringUTF(values[i].c_str());
        if (!item) return nullptr;
        env->SetObjectArrayElement(array, i, item);
        env->DeleteLocalRef(item);
        if (env->ExceptionCheck()) return nullptr;
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

    ScopedUtfChars preset_chars(env, preset);
    ScopedUtfChars rover_chars(env, rover_format);
    ScopedUtfChars correction_chars(env, correction_format);
    if (!preset_chars.ok() || !rover_chars.ok() || !correction_chars.ok()) {
        return jni_input_error(env, "RTKLIB native start received null or unavailable string input");
    }

    int raw_format = rover_format_for(rover_chars.c_str());
    std::string error;
    if (raw_format < 0) {
        error = std::string("Unsupported RTKLIB rover format: ") + rover_chars.c_str();
    } else if (std::strcmp(correction_chars.c_str(), "RTCM3") != 0) {
        error = std::string("Unsupported RTKLIB correction format: ") + correction_chars.c_str();
    } else {
        configure_options(engine, preset_chars.c_str(), frequency_count);
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
    if (!bytes) {
        NativeResult result;
        result.state = "FAILED";
        result.error = "RTKLIB native feed received null bytes";
        return to_java_result(env, engine, result);
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

extern "C" JNIEXPORT jstring JNICALL
Java_org_rtkcollector_core_rtklib_RtklibNativeBridgeKt_nativeRtklibPostprocess(
    JNIEnv *env,
    jclass,
    jstring preset,
    jstring rover_format,
    jint frequency_count,
    jstring solution_type,
    jstring receiver_rx_raw,
    jstring correction_rtcm3,
    jstring output_nmea,
    jstring output_pos
) {
    ScopedUtfChars preset_chars(env, preset);
    ScopedUtfChars rover_chars(env, rover_format);
    ScopedUtfChars solution_type_chars(env, solution_type);
    ScopedUtfChars receiver_rx_chars(env, receiver_rx_raw);
    ScopedUtfChars correction_chars(env, correction_rtcm3);
    ScopedUtfChars output_nmea_chars(env, output_nmea);
    ScopedUtfChars output_pos_chars(env, output_pos);
    if (!preset_chars.ok() || !rover_chars.ok() || !solution_type_chars.ok() ||
        !receiver_rx_chars.ok() || !correction_chars.ok() ||
        !output_nmea_chars.ok() || !output_pos_chars.ok()) {
        return jni_input_error(env, "RTKLIB native postprocess received null or unavailable string input");
    }

    std::string error;
    int rover_format_id = rover_format_for(rover_chars.c_str());
    int solution_type_id = solution_type_for(solution_type_chars.c_str());
    if (rover_format_id < 0) {
        error = std::string("Unsupported RTKLIB postprocess rover format: ") + rover_chars.c_str();
    } else if (solution_type_id < 0) {
        error = std::string("Unsupported RTKLIB postprocess solution type: ") + solution_type_chars.c_str();
    } else {
        std::vector<std::string> inputs;
        std::vector<std::string> generated_files;
        std::string output_pos_string(output_pos_chars.c_str());
        std::string rover_prefix = postprocess_work_path(output_pos_string, ".rover");
        std::string base_prefix = postprocess_work_path(output_pos_string, ".base");
        RinexConversionOutput rover_rinex{};
        RinexConversionOutput base_rinex{};

        bool rover_ok = convert_raw_to_rinex(
            rover_format_id,
            frequency_count,
            receiver_rx_chars.c_str(),
            rover_prefix,
            &rover_rinex,
            &error);
        generated_files.insert(
            generated_files.end(),
            rover_rinex.generated_files.begin(),
            rover_rinex.generated_files.end());
        if (!rover_ok) {
            if (error.empty()) error = "RTKLIB rover raw conversion produced no observations";
        } else {
            bool base_ok = convert_raw_to_rinex(
                STRFMT_RTCM3,
                frequency_count,
                correction_chars.c_str(),
                base_prefix,
                &base_rinex,
                &error);
            generated_files.insert(
                generated_files.end(),
                base_rinex.generated_files.begin(),
                base_rinex.generated_files.end());

            if (!base_ok) {
                if (error.empty()) error = "RTKLIB correction conversion produced no base observations";
            } else {
                inputs.push_back(rover_rinex.observation);
                inputs.push_back(base_rinex.observation);
                inputs.insert(inputs.end(), rover_rinex.navigation.begin(), rover_rinex.navigation.end());
                inputs.insert(inputs.end(), base_rinex.navigation.begin(), base_rinex.navigation.end());

                if (!run_postpos_output(
                        inputs,
                        preset_chars.c_str(),
                        frequency_count,
                        solution_type_id,
                        SOLF_LLH,
                        output_pos_chars.c_str(),
                        &error)) {
                    if (error.empty()) error = "RTKLIB POS postprocess failed";
                } else if (!run_postpos_output(
                        inputs,
                        preset_chars.c_str(),
                        frequency_count,
                        solution_type_id,
                        SOLF_NMEA,
                        output_nmea_chars.c_str(),
                        &error)) {
                    if (error.empty()) error = "RTKLIB NMEA postprocess failed";
                }
            }
        }
        for (const auto &path : generated_files) std::remove(path.c_str());
    }

    if (error.empty()) return nullptr;
    return env->NewStringUTF(error.c_str());
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
