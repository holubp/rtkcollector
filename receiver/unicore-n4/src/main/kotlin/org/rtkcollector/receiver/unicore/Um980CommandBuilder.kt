package org.rtkcollector.receiver.unicore

class Um980CommandBuilder {
    fun build(request: Um980CommandProfileRequest): List<String> {
        val commands = mutableListOf<String>()
        commands += "UNLOG ${request.comPort}"

        when (request.mode) {
            Um980WorkflowMode.ROVER,
            Um980WorkflowMode.ROVER_NTRIP -> commands += roverCommands(request)
            Um980WorkflowMode.TEMPORARY_BASE,
            Um980WorkflowMode.TEMPORARY_BASE_NTRIP -> commands += temporaryBaseCommands(request)
            Um980WorkflowMode.FIXED_BASE_STATUS -> commands += fixedBaseCommands(request, outputRtcm = false)
            Um980WorkflowMode.FIXED_BASE_RTCM_OUTPUT -> commands += fixedBaseCommands(request, outputRtcm = true)
        }

        return commands.onEach(Um980RuntimeCommandValidator::validateRuntimeCommand)
    }

    private fun roverCommands(request: Um980CommandProfileRequest): List<String> =
        listOf(
            "MODE ROVER",
            "CONFIG MMP ENABLE",
            "BESTNAVB ${request.comPort} ${request.outputPeriod()}",
            "GPGGA ${request.comPort} ${request.outputPeriod()}",
            "OBSVMCMPB ${request.comPort} ${request.outputPeriod()}",
        )

    private fun temporaryBaseCommands(request: Um980CommandProfileRequest): List<String> =
        buildList {
            add("MODE ROVER")
            add("CONFIG MMP ENABLE")
            if (request.enablePpp) {
                add("CONFIG PPP ENABLE E6-HAS")
                add("CONFIG PPP DATUM WGS84")
                add("CONFIG PPP TIMEOUT 120")
                add("CONFIG PPP CONVERGE 15 30")
                add("PPPNAVB ${request.comPort} 10")
            }
            add("BESTNAVB ${request.comPort} ${request.outputPeriod()}")
            add("GPGGA ${request.comPort} ${request.outputPeriod()}")
            add("OBSVMCMPB ${request.comPort} ${request.outputPeriod()}")
        }

    private fun fixedBaseCommands(
        request: Um980CommandProfileRequest,
        outputRtcm: Boolean,
    ): List<String> {
        val coordinate = requireNotNull(request.baseCoordinate) {
            "Fixed-base UM980 workflow requires an accepted base coordinate."
        }
        return buildList {
            add(
                "MODE BASE %.10f %.10f %.4f".format(
                    java.util.Locale.US,
                    coordinate.latDeg,
                    coordinate.lonDeg,
                    coordinate.heightM,
                ),
            )
            add("GPGGA ${request.comPort} ${request.outputPeriod()}")
            add("BESTNAVB ${request.comPort} ${request.outputPeriod()}")
            if (outputRtcm) {
                add("RTCM1006 ${request.outputPeriod()}")
                add("RTCM1033 10")
                add("RTCM1074 ${request.outputPeriod()}")
                add("RTCM1084 ${request.outputPeriod()}")
                add("RTCM1094 ${request.outputPeriod()}")
                add("RTCM1124 ${request.outputPeriod()}")
                add("RTCM1019 60")
                add("RTCM1020 60")
                add("RTCM1042 60")
                add("RTCM1045 60")
                add("RTCM1046 60")
            }
        }
    }

    private fun Um980CommandProfileRequest.outputPeriod(): String {
        val whole = outputIntervalSeconds.toLong()
        return if (whole.toDouble() == outputIntervalSeconds) {
            whole.toString()
        } else {
            "%.3f".format(java.util.Locale.US, outputIntervalSeconds).trimEnd('0').trimEnd('.')
        }
    }
}
