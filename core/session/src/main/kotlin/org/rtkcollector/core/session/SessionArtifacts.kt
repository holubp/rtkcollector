package org.rtkcollector.core.session

enum class SessionArtifactFile(val fileName: String) {
    SESSION_JSON("session.json"),
    RECEIVER_RX_RAW("receiver-rx.raw"),
    TX_TO_RECEIVER_RAW("tx-to-receiver.raw"),
    CORRECTION_INPUT_RAW("correction-input.raw"),
    CORRECTION_INPUT_RTCM3("correction-input.rtcm3"),
    BASE_CASTER_UPLOAD_RTCM3("base-caster-upload.rtcm3"),
    EVENTS_JSONL("events.jsonl"),
    QUALITY_LIVE_JSONL("quality-live.jsonl"),
    RECEIVER_SOLUTION_NMEA("receiver-solution.nmea"),
    RECEIVER_SOLUTION_JSONL("receiver-solution.jsonl"),
    RECEIVER_PPP_SOLUTION_JSONL("receiver-ppp-solution.jsonl"),
    BASE_POSITION_JSON("base-position.json"),
    RTCM_EXTRACTED_RTCM3("rtcm-extracted.rtcm3"),
}
