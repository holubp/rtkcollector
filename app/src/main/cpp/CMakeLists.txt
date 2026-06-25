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
    "${RTKLIB_SRC}/rtksvr.c"
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
