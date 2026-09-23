package org.rtkcollector.core.correction

internal fun NtripRequest(
    host: String,
    port: Int,
    mountpoint: String,
    credentials: NtripCredentials? = null,
    userAgent: String = DEFAULT_NTRIP_USER_AGENT,
    protocolVersion: NtripProtocolVersion = NtripProtocolVersion.NTRIP_V2,
): NtripRequest = NtripRequest(
    policy = NtripEndpointSecurityPolicy.systemTrust(host, port),
    mountpoint = mountpoint,
    credentials = credentials,
    userAgent = userAgent,
    protocolVersion = protocolVersion,
)

internal fun NtripSourcetableRequest(
    host: String,
    port: Int,
    credentials: NtripCredentials? = null,
    userAgent: String = DEFAULT_NTRIP_USER_AGENT,
    protocolVersion: NtripProtocolVersion = NtripProtocolVersion.NTRIP_V2,
): NtripSourcetableRequest = NtripSourcetableRequest(
    policy = NtripEndpointSecurityPolicy.systemTrust(host, port),
    credentials = credentials,
    userAgent = userAgent,
    protocolVersion = protocolVersion,
)

internal fun NtripCasterUploadRequest(
    host: String,
    port: Int,
    mountpoint: String,
    credentials: NtripCredentials?,
    userAgent: String = DEFAULT_NTRIP_USER_AGENT,
    protocolVersion: NtripProtocolVersion = NtripProtocolVersion.NTRIP_V2,
): NtripCasterUploadRequest = NtripCasterUploadRequest(
    policy = NtripEndpointSecurityPolicy.systemTrust(host, port),
    mountpoint = mountpoint,
    credentials = credentials,
    userAgent = userAgent,
    protocolVersion = protocolVersion,
)
