cmake_minimum_required(VERSION 3.22)

project(rtkcollector_rtklib LANGUAGES C CXX)

set(RTKLIB_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/rtklib-ex/upstream")
set(RTKLIB_SRC "${RTKLIB_ROOT}/src")
set(RTKLIB_GENERATED_DIR "${CMAKE_CURRENT_BINARY_DIR}/generated")
file(MAKE_DIRECTORY "${RTKLIB_GENERATED_DIR}")

file(READ "${RTKLIB_SRC}/rcv/unicore.c" RTKCOLLECTOR_UNICORE_SOURCE)
string(REPLACE
    "#define ID_OBSVM       12      /* Observation of the Master Antenna */"
    "#define ID_OBSVM       12      /* Observation of the Master Antenna */\n#define ID_OBSVMCMP    138     /* Compact observation of the Master Antenna (RtkCollector shim) */"
    RTKCOLLECTOR_UNICORE_SOURCE
    "${RTKCOLLECTOR_UNICORE_SOURCE}"
)
string(REPLACE
    "case ID_OBSVM: return decode_obsvmb(raw);"
    "case ID_OBSVM:\n    case ID_OBSVMCMP: return decode_obsvmb(raw);"
    RTKCOLLECTOR_UNICORE_SOURCE
    "${RTKCOLLECTOR_UNICORE_SOURCE}"
)
file(WRITE "${RTKLIB_GENERATED_DIR}/unicore_obsvmcmp.c" "${RTKCOLLECTOR_UNICORE_SOURCE}")

# RC10_PINNED_SERVER_ADAPTATION_BEGIN
# RTKLIB-EX 8dfabc9a106b2e74c069bc80f0d7743f314e6ab4 only.
# Keep the upstream checkout immutable; normalize Windows autocrlf before pin checks.
file(READ "${RTKLIB_SRC}/rtksvr.c" RTKCOLLECTOR_RTKSVR_SOURCE)
string(REPLACE "\r\n" "\n" RTKCOLLECTOR_RTKSVR_SOURCE "${RTKCOLLECTOR_RTKSVR_SOURCE}")
string(SHA256 RTKCOLLECTOR_RTKSVR_SHA256 "${RTKCOLLECTOR_RTKSVR_SOURCE}")
if(NOT RTKCOLLECTOR_RTKSVR_SHA256 STREQUAL "e1685aa9c990a3df672770dcaa122e1f4bbdec191f07b40f793a727e1d5e8b5b")
    message(FATAL_ERROR "RTKLIB rtksvr.c normalized source SHA256 mismatch; review the pinned server adaptation")
endif()

function(rtkcollector_patch_server expected before after)
    string(LENGTH "${RTKCOLLECTOR_RTKSVR_SOURCE}" original_length)
    string(REPLACE "${before}" "" without_anchor "${RTKCOLLECTOR_RTKSVR_SOURCE}")
    string(LENGTH "${without_anchor}" stripped_length)
    string(LENGTH "${before}" anchor_length)
    math(EXPR anchor_count "(${original_length} - ${stripped_length}) / ${anchor_length}")
    if(NOT anchor_count EQUAL expected)
        message(FATAL_ERROR "RTKLIB rtksvr.c anchor count mismatch: expected ${expected}, found ${anchor_count}")
    endif()
    string(REPLACE "${before}" "${after}" adapted "${RTKCOLLECTOR_RTKSVR_SOURCE}")
    set(RTKCOLLECTOR_RTKSVR_SOURCE "${adapted}" PARENT_SCOPE)
endfunction()

rtkcollector_patch_server(1 [=[/* rtk server thread ---------------------------------------------------------*/]=] [=[
/* State publication is shared by start, stop, worker exit and telemetry. */
static int rtkcollector_server_running(rtksvr_t *svr)
{
    int running;
    rtksvrlock(svr);
    running=svr->state;
    rtksvrunlock(svr);
    return running;
}
static void rtkcollector_server_state(rtksvr_t *svr, int state)
{
    rtksvrlock(svr);
    svr->state=state;
    rtksvrunlock(svr);
}
/* rtk server thread ---------------------------------------------------------*/]=])
rtkcollector_patch_server(1 [=[        init_raw(svr->raw+i,formats[i]);
        init_rtcm(svr->rtcm+i);]=] [=[        if (!init_raw(svr->raw+i,formats[i])||!init_rtcm(svr->rtcm+i)) {
            sprintf(errmsg,"rtk server decoder allocation error");
            return 0;
        }]=])
rtkcollector_patch_server(1 [=[      trace(1, "rtksvrthread: obsd_t alloc failed\n");
      return 0;]=] [=[      trace(1, "rtksvrthread: obsd_t alloc failed\n");
      goto rtkcollector_worker_cleanup;]=])
# Only start may publish running; a delayed worker must never undo Stop.
rtkcollector_patch_server(1 "    svr->state=1;\n" "")
rtkcollector_patch_server(1 "for (cycle=0;svr->state;cycle++)" "for (cycle=0;rtkcollector_server_running(svr);cycle++)")
rtkcollector_patch_server(1 "    free(data);\n" "rtkcollector_worker_cleanup:\n    rtkcollector_server_state(svr,0);\n    free(data);\n")
rtkcollector_patch_server(1 "    /* create rtk server thread */" "    rtkcollector_server_state(svr,1);\n    /* create rtk server thread */")
rtkcollector_patch_server(1 [=[        sprintf(errmsg,"thread create error\n");]=] [=[        rtkcollector_server_state(svr,0);
        sprintf(errmsg,"thread create error\n");]=])
rtkcollector_patch_server(1 "    svr->state=0;" "    rtkcollector_server_state(svr,0);")
rtkcollector_patch_server(1 "if (svr->state)" "if (rtkcollector_server_running(svr))")
rtkcollector_patch_server(4 "!svr->state" "!rtkcollector_server_running(svr)")
file(WRITE "${RTKLIB_GENERATED_DIR}/rtksvr.c" "${RTKCOLLECTOR_RTKSVR_SOURCE}")
# RC10_PINNED_SERVER_ADAPTATION_END

add_library(
    rtkcollector_rtklib
    SHARED
    rtklib_bridge.cpp
    "${RTKLIB_SRC}/datum.c"
    "${RTKLIB_SRC}/ephemeris.c"
    "${RTKLIB_SRC}/geoid.c"
    "${RTKLIB_SRC}/ionex.c"
    "${RTKLIB_SRC}/lambda.c"
    "${RTKLIB_SRC}/options.c"
    "${RTKLIB_SRC}/pntpos.c"
    "${RTKLIB_SRC}/postpos.c"
    "${RTKLIB_SRC}/ppp.c"
    "${RTKLIB_SRC}/ppp_ar.c"
    "${RTKLIB_SRC}/preceph.c"
    "${RTKLIB_SRC}/rcvraw.c"
    "${RTKLIB_SRC}/rinex.c"
    "${RTKLIB_SRC}/rtcm.c"
    "${RTKLIB_SRC}/rtcm2.c"
    "${RTKLIB_SRC}/rtcm3.c"
    "${RTKLIB_SRC}/rtcm3e.c"
    "${RTKLIB_SRC}/rtkcmn.c"
    "${RTKLIB_SRC}/rtkpos.c"
    "${RTKLIB_GENERATED_DIR}/rtksvr.c"
    "${RTKLIB_SRC}/sbas.c"
    "${RTKLIB_SRC}/sofa.c"
    "${RTKLIB_SRC}/solution.c"
    "${RTKLIB_SRC}/stream.c"
    "${RTKLIB_SRC}/tides.c"
    "${RTKLIB_SRC}/tle.c"
    "${RTKLIB_SRC}/trace.c"
    "${RTKLIB_SRC}/convrnx.c"
    "${RTKLIB_SRC}/rcv/binex.c"
    "${RTKLIB_SRC}/rcv/crescent.c"
    "${RTKLIB_SRC}/rcv/javad.c"
    "${RTKLIB_SRC}/rcv/novatel.c"
    "${RTKLIB_SRC}/rcv/nvs.c"
    "${RTKLIB_SRC}/rcv/rt17.c"
    "${RTKLIB_SRC}/rcv/septentrio.c"
    "${RTKLIB_SRC}/rcv/skytraq.c"
    "${RTKLIB_SRC}/rcv/swiftnav.c"
    "${RTKLIB_SRC}/rcv/ublox.c"
    "${RTKLIB_GENERATED_DIR}/unicore_obsvmcmp.c"
)

target_include_directories(
    rtkcollector_rtklib
    PRIVATE
    "${RTKLIB_SRC}"
)

target_compile_definitions(
    rtkcollector_rtklib
    PRIVATE
    ENAGLO
    ENAGAL
    ENAQZS
    ENACMP
    NFREQ=3
    NEXOBS=3
)

target_compile_features(rtkcollector_rtklib PRIVATE cxx_std_17)

find_library(log-lib log)
target_link_libraries(rtkcollector_rtklib PRIVATE ${log-lib})

target_link_options(
    rtkcollector_rtklib
    PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
