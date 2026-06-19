package org.rtkcollector.core.workflow

class RtklibInputRouter(
    private val rtklibCapabilities: RtklibEngineCapabilities = RtklibExCapabilities.current(),
) {
    fun plan(spec: WorkflowSpec): RtklibInputRoutePlan =
        RtklibInputRoutePlan(
            roverInput = roverInputRoute(spec),
            correctionInput = correctionInputRoute(spec),
            solutionDirection = RtklibSolutionDirection.FORWARD_ONLY,
        )

    private fun roverInputRoute(spec: WorkflowSpec): RtklibInputRoute {
        if (SolutionEngine.RTKLIB_REALTIME !in spec.solutionEngines) {
            return RtklibInputRoute(
                kind = RtklibInputRouteKind.NOT_CONFIGURED,
                reason = "RTKLIB real-time solution is not enabled.",
            )
        }

        val candidates = spec.receiverCapabilities.rtklibRoverInputCapabilities
            .filter { capability ->
                spec.rtklibPreferredRoverInputFormat == null ||
                    capability.format == spec.rtklibPreferredRoverInputFormat
            }
            .sortedWith(compareByDescending<RtklibRoverInputCapability> { it.preferred }
                .thenBy { it.format.name })

        if (candidates.isEmpty()) {
            return RtklibInputRoute(
                kind = RtklibInputRouteKind.UNSUPPORTED,
                format = spec.rtklibPreferredRoverInputFormat,
                reason = "Receiver profile does not declare an RTKLIB rover observation input format.",
            )
        }

        candidates.firstOrNull { it.format in rtklibCapabilities.directRoverInputFormats }?.let { capability ->
            return RtklibInputRoute(
                kind = RtklibInputRouteKind.DIRECT_RTKLIB_DECODER,
                format = capability.format,
                decoderId = directRoverDecoderId(capability.format),
                reason = "RTKLIB-EX declares a direct rover decoder for ${capability.format}.",
            )
        }

        candidates.firstOrNull { !it.converterId.isNullOrBlank() || !spec.rtklibRawConverterId.isNullOrBlank() }
            ?.let { capability ->
                return RtklibInputRoute(
                    kind = RtklibInputRouteKind.CONVERTER,
                    format = capability.format,
                    converterId = capability.converterId ?: spec.rtklibRawConverterId,
                    reason = "Receiver output requires a configured converter before RTKLIB-EX input.",
                )
            }

        return RtklibInputRoute(
            kind = RtklibInputRouteKind.UNSUPPORTED,
            format = candidates.first().format,
            reason = "Receiver output is not directly supported by RTKLIB-EX and no converter is configured.",
        )
    }

    private fun correctionInputRoute(spec: WorkflowSpec): RtklibCorrectionInputRoute {
        if (SolutionEngine.RTKLIB_REALTIME !in spec.solutionEngines) {
            return RtklibCorrectionInputRoute(
                kind = RtklibInputRouteKind.NOT_CONFIGURED,
                reason = "RTKLIB real-time solution is not enabled.",
            )
        }

        val format = spec.correctionSource.expectedCorrectionFormatOrNull()
            ?: return RtklibCorrectionInputRoute(
                kind = RtklibInputRouteKind.UNSUPPORTED,
                reason = "RTKLIB real-time solution has no correction or base-observation input format.",
            )

        if (format in rtklibCapabilities.directCorrectionFormats) {
            return RtklibCorrectionInputRoute(
                kind = RtklibInputRouteKind.DIRECT_RTKLIB_DECODER,
                format = format,
                decoderId = directCorrectionDecoderId(format),
                reason = "RTKLIB-EX declares a direct correction decoder for $format.",
            )
        }

        if (!spec.rtklibRawConverterId.isNullOrBlank()) {
            return RtklibCorrectionInputRoute(
                kind = RtklibInputRouteKind.CONVERTER,
                format = format,
                converterId = spec.rtklibRawConverterId,
                reason = "Correction input requires a configured converter before RTKLIB-EX input.",
            )
        }

        return RtklibCorrectionInputRoute(
            kind = RtklibInputRouteKind.UNSUPPORTED,
            format = format,
            reason = "Correction input is not directly supported by RTKLIB-EX and no converter is configured.",
        )
    }

    private fun directRoverDecoderId(format: RtklibRoverInputFormat): String =
        when (format) {
            RtklibRoverInputFormat.UBX_RXM_RAWX_SFRBX -> "input_ubx"
            RtklibRoverInputFormat.UNICORE_OBSVMB -> "input_unicore"
            RtklibRoverInputFormat.RTCM3_OBSERVATIONS -> "input_rtcm3"
            RtklibRoverInputFormat.UNICORE_OBSVMCMPB,
            RtklibRoverInputFormat.CONVERTED_OBSERVATION_EPOCHS,
            RtklibRoverInputFormat.UNKNOWN,
            -> "converter"
        }

    private fun directCorrectionDecoderId(format: CorrectionFormat): String =
        when (format) {
            CorrectionFormat.RTCM3,
            CorrectionFormat.RTCM_OBSERVATIONS,
            -> "input_rtcm3"
            CorrectionFormat.RECEIVER_NATIVE_RAW,
            CorrectionFormat.FILE_REPLAY,
            CorrectionFormat.UNKNOWN,
            -> "converter"
        }

    private fun CorrectionSourceSpec.expectedCorrectionFormatOrNull(): CorrectionFormat? =
        when (this) {
            is CorrectionSourceSpec.Ntrip -> expectedCorrectionFormat
            is CorrectionSourceSpec.LocalBaseStream -> expectedCorrectionFormat
            is CorrectionSourceSpec.ExternalSerialOrTcp -> expectedCorrectionFormat
            is CorrectionSourceSpec.FileReplay -> expectedCorrectionFormat
            CorrectionSourceSpec.None -> null
        }
}
