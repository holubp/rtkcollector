#include <jni.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdlib>
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
    raw_t rover_raw{};
    rtcm_t correction_rtcm{};
    nav_t nav{};
    rtk_t rtk{};
    prcopt_t prcopt{};
    solopt_t nmea_opt{};
    solopt_t pos_opt{};
    std::vector<obsd_t> latest_base_obs;
    bool started = false;
    bool output_nmea = true;
    bool output_pos = true;
    int rover_format = STRFMT_UBX;
    uint64_t decoded_rover_epochs = 0;
    uint64_t decoded_correction_messages = 0;
    NativeResult latest;
};

static void release_rtklib_state(RtklibEngineHandle *engine) {
    rtkfree(&engine->rtk);
    free_raw(&engine->rover_raw);
    free_rtcm(&engine->correction_rtcm);
    freenav(&engine->nav, 0xFF);
    engine->rover_raw = raw_t{};
    engine->correction_rtcm = rtcm_t{};
    engine->nav = nav_t{};
    engine->rtk = rtk_t{};
    engine->latest_base_obs.clear();
    engine->started = false;
}

static void copy_ion_utc(nav_t *dst, const nav_t *src) {
    std::memcpy(dst->utc_gps, src->utc_gps, sizeof(dst->utc_gps));
    std::memcpy(dst->utc_glo, src->utc_glo, sizeof(dst->utc_glo));
    std::memcpy(dst->utc_gal, src->utc_gal, sizeof(dst->utc_gal));
    std::memcpy(dst->utc_qzs, src->utc_qzs, sizeof(dst->utc_qzs));
    std::memcpy(dst->utc_cmp, src->utc_cmp, sizeof(dst->utc_cmp));
    std::memcpy(dst->utc_irn, src->utc_irn, sizeof(dst->utc_irn));
    std::memcpy(dst->ion_gps, src->ion_gps, sizeof(dst->ion_gps));
    std::memcpy(dst->ion_gal, src->ion_gal, sizeof(dst->ion_gal));
    std::memcpy(dst->ion_qzs, src->ion_qzs, sizeof(dst->ion_qzs));
    std::memcpy(dst->ion_cmp, src->ion_cmp, sizeof(dst->ion_cmp));
    std::memcpy(dst->ion_irn, src->ion_irn, sizeof(dst->ion_irn));
}

static void copy_nav_update(nav_t *dst, const nav_t *src, int ephsat, int ephset) {
    if (!dst || !src || ephsat <= 0) return;
    int prn = 0;
    int sys = satsys(ephsat, &prn);
    if (sys == SYS_GLO && prn > 0 && prn <= dst->ngmax && prn <= src->ngmax) {
        dst->geph[prn - 1] = src->geph[prn - 1];
        return;
    }
    int index = ephsat - 1;
    if (ephset > 0) index += MAXSAT;
    if (index >= 0 && index < dst->nmax && index < src->nmax) {
        dst->eph[index] = src->eph[index];
    }
}

static bool init_nav(nav_t *nav) {
    std::memset(nav, 0, sizeof(*nav));
    nav->n = nav->nmax = MAXSAT * 2;
    nav->ng = nav->ngmax = MAXPRNGLO;
    nav->ns = nav->nsmax = MAXSAT;
    nav->eph = static_cast<eph_t *>(std::calloc(nav->nmax, sizeof(eph_t)));
    nav->geph = static_cast<geph_t *>(std::calloc(nav->ngmax, sizeof(geph_t)));
    nav->seph = static_cast<seph_t *>(std::calloc(nav->nsmax, sizeof(seph_t)));
    return nav->eph && nav->geph && nav->seph;
}

static void configure_options(RtklibEngineHandle *engine, const char *preset) {
    engine->prcopt = prcopt_default;
    engine->prcopt.soltype = SOLTYPE_FORWARD;
    engine->prcopt.mode = std::strcmp(preset, "TEMPORARY_BASE_STATIC_RTK") == 0 ? PMODE_STATIC : PMODE_KINEMA;
    engine->prcopt.navsys = SYS_GPS | SYS_GLO | SYS_GAL | SYS_CMP | SYS_QZS;
    engine->prcopt.nf = 2;
    engine->prcopt.refpos = POSOPT_RTCM;
    engine->prcopt.rovpos = POSOPT_POS_LLH;

    engine->nmea_opt = solopt_default;
    engine->nmea_opt.posf = SOLF_NMEA;
    engine->nmea_opt.times = TIMES_GPST;
    engine->nmea_opt.timeu = 3;
    engine->nmea_opt.outhead = 0;

    engine->pos_opt = solopt_default;
    engine->pos_opt.posf = SOLF_LLH;
    engine->pos_opt.times = TIMES_GPST;
    engine->pos_opt.timeu = 3;
    engine->pos_opt.outhead = 0;
}

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
    int week = 0;
    double tow = time2gpst(sol->time, &week);
    if (week <= 0) return 0;
    return static_cast<long long>((week * 604800.0 + tow) * 1000.0);
}

static void append_lines(std::string *target, const uint8_t *buff, int n) {
    if (n <= 0) return;
    target->append(reinterpret_cast<const char *>(buff), n);
    if (!target->empty() && target->back() != '\n') {
        target->push_back('\n');
    }
}

static void update_result_from_solution(RtklibEngineHandle *engine, NativeResult *result) {
    if (engine->rtk.sol.stat <= SOLQ_NONE) return;

    double pos[3] = {0.0, 0.0, 0.0};
    ecef2pos(engine->rtk.sol.rr, pos);

    result->fix_class = fix_class_for(engine->rtk.sol.stat);
    result->timestamp_millis = long_to_string(solution_time_millis(&engine->rtk.sol));
    result->lat_deg = double_to_string(pos[0] * R2D);
    result->lon_deg = double_to_string(pos[1] * R2D);
    result->height_m = double_to_string(pos[2]);
    result->satellites_used = long_to_string(engine->rtk.sol.ns);
    if (engine->rtk.sol.qr[0] > 0.0 && engine->rtk.sol.qr[1] > 0.0) {
        result->h_acc_m = double_to_string(std::sqrt(engine->rtk.sol.qr[0] + engine->rtk.sol.qr[1]));
    }
    if (engine->rtk.sol.qr[2] > 0.0) {
        result->v_acc_m = double_to_string(std::sqrt(engine->rtk.sol.qr[2]));
    }

    uint8_t buff[4096] = {0};
    if (engine->output_nmea) {
        int n = outsols(buff, &engine->rtk.sol, engine->rtk.rb, &engine->nmea_opt);
        append_lines(&result->nmea, buff, n);
    }
    if (engine->output_pos) {
        int n = outsols(buff, &engine->rtk.sol, engine->rtk.rb, &engine->pos_opt);
        append_lines(&result->pos, buff, n);
    }
}

static void maybe_update_base_position(RtklibEngineHandle *engine) {
    if (norm(engine->correction_rtcm.sta.pos, 3) > 0.0) {
        for (int i = 0; i < 3; i++) {
            engine->rtk.rb[i] = engine->correction_rtcm.sta.pos[i];
            engine->rtk.opt.rb[i] = engine->correction_rtcm.sta.pos[i];
        }
    }
}

static void store_base_observations(RtklibEngineHandle *engine) {
    engine->latest_base_obs.assign(
        engine->correction_rtcm.obs.data,
        engine->correction_rtcm.obs.data + engine->correction_rtcm.obs.n
    );
    for (auto &obs : engine->latest_base_obs) {
        obs.rcv = 2;
    }
}

static void solve_if_possible(RtklibEngineHandle *engine, NativeResult *result, const obs_t *rover_obs) {
    if (!rover_obs || rover_obs->n <= 0) return;
    if (engine->latest_base_obs.empty()) {
        result->warning = "RTKLIB waiting for RTCM base observations";
        return;
    }
    std::vector<obsd_t> combined;
    combined.reserve(static_cast<size_t>(rover_obs->n) + engine->latest_base_obs.size());
    for (int i = 0; i < rover_obs->n; i++) {
        obsd_t obs = rover_obs->data[i];
        obs.rcv = 1;
        combined.push_back(obs);
    }
    combined.insert(combined.end(), engine->latest_base_obs.begin(), engine->latest_base_obs.end());
    if (combined.empty()) return;
    if (!rtkpos(&engine->rtk, combined.data(), static_cast<int>(combined.size()), &engine->nav)) {
        result->warning = engine->rtk.errbuf[0] ? engine->rtk.errbuf : "RTKLIB has no solution yet";
        return;
    }
    update_result_from_solution(engine, result);
}

static void handle_rover_ret(RtklibEngineHandle *engine, int ret, NativeResult *result) {
    if (ret == 1) {
        engine->decoded_rover_epochs++;
        solve_if_possible(engine, result, &engine->rover_raw.obs);
    } else if (ret == 2) {
        copy_nav_update(&engine->nav, &engine->rover_raw.nav, engine->rover_raw.ephsat, engine->rover_raw.ephset);
    } else if (ret == 9) {
        copy_ion_utc(&engine->nav, &engine->rover_raw.nav);
    } else if (ret < 0) {
        result->warning = "RTKLIB rover decoder rejected a frame";
    }
}

static void handle_correction_ret(RtklibEngineHandle *engine, int ret, NativeResult *result) {
    if (ret != 0) {
        engine->decoded_correction_messages++;
    }
    if (ret == 1) {
        store_base_observations(engine);
    } else if (ret == 2) {
        copy_nav_update(&engine->nav, &engine->correction_rtcm.nav, engine->correction_rtcm.ephsat, engine->correction_rtcm.ephset);
    } else if (ret == 5) {
        maybe_update_base_position(engine);
    } else if (ret == 9) {
        copy_ion_utc(&engine->nav, &engine->correction_rtcm.nav);
    } else if (ret < 0) {
        result->warning = "RTKLIB correction decoder rejected a frame";
    }
}

static NativeResult feed_bytes(RtklibEngineHandle *engine, int stream_kind, const uint8_t *bytes, int length) {
    NativeResult result = engine->latest;
    result.nmea.clear();
    result.pos.clear();
    result.warning.clear();
    result.error.clear();
    if (!engine->started) {
        result.state = "FAILED";
        result.error = "RTKLIB native engine is not started";
        return result;
    }

    for (int i = 0; i < length; i++) {
        int ret = 0;
        if (stream_kind == STREAM_ROVER) {
            ret = input_raw(&engine->rover_raw, engine->rover_format, bytes[i]);
            handle_rover_ret(engine, ret, &result);
        } else if (stream_kind == STREAM_CORRECTION) {
            ret = input_rtcm3(&engine->correction_rtcm, bytes[i]);
            handle_correction_ret(engine, ret, &result);
        } else {
            result.warning = "RTKLIB received unknown stream kind";
            break;
        }
    }
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

static int rover_format_for(const char *name) {
    if (std::strcmp(name, "UBX_RXM_RAWX_SFRBX") == 0) return STRFMT_UBX;
    if (std::strcmp(name, "UNICORE_OBSVMB") == 0) return STRFMT_UNICORE;
    if (std::strcmp(name, "UNICORE_OBSVMCMPB") == 0) return STRFMT_UNICORE;
    return -1;
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
    jboolean output_pos
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
    } else if (!init_nav(&engine->nav)) {
        error = "RTKLIB native nav allocation failed";
    } else if (!init_raw(&engine->rover_raw, raw_format)) {
        error = "RTKLIB native raw decoder allocation failed";
    } else if (!init_rtcm(&engine->correction_rtcm)) {
        error = "RTKLIB native RTCM decoder allocation failed";
    } else {
        configure_options(engine, preset_chars);
        rtkinit(&engine->rtk, &engine->prcopt);
        engine->rover_format = raw_format;
        engine->output_nmea = output_nmea == JNI_TRUE;
        engine->output_pos = output_pos == JNI_TRUE;
        engine->started = true;
        engine->latest = NativeResult{};
        engine->latest.state = "RUNNING";
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
    NativeResult result = feed_bytes(engine, stream_kind, buffer.data(), length);
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
    return to_java_result(env, engine, engine->latest);
}

extern "C" JNIEXPORT void JNICALL
Java_org_rtkcollector_core_rtklib_RtklibNativeBridgeKt_nativeRtklibStop(JNIEnv *, jclass, jlong handle) {
    auto *engine = from_handle(handle);
    if (!engine) return;
    std::lock_guard<std::mutex> lock(engine->mutex);
    engine->started = false;
    engine->latest.state = "STOPPED";
}

extern "C" JNIEXPORT void JNICALL
Java_org_rtkcollector_core_rtklib_RtklibNativeBridgeKt_nativeRtklibDestroy(JNIEnv *, jclass, jlong handle) {
    auto *engine = from_handle(handle);
    if (!engine) return;
    {
        std::lock_guard<std::mutex> lock(engine->mutex);
        if (engine->started) {
            engine->started = false;
        }
        release_rtklib_state(engine);
    }
    delete engine;
}
