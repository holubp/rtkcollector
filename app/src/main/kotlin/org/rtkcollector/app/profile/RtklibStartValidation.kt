package org.rtkcollector.app.profile

import org.rtkcollector.core.rtklib.RtklibSnapshot

internal data class RtklibStartValidation(
    val routePlan: String?,
    val validationSummary: String?,
    val errors: List<String>,
) {
    val valid: Boolean
        get() = errors.isEmpty()
}

internal object RtklibStartValidator {
    fun validate(
        enabled: Boolean,
        receiverProfileId: String,
        commands: List<String>,
        ntripEnabled: Boolean,
        ntripConfigured: Boolean,
        outputNmea: Boolean,
        outputPos: Boolean,
    ): RtklibStartValidation {
        if (!enabled) {
            return RtklibStartValidation(
                routePlan = null,
                validationSummary = "RTKLIB disabled",
                errors = emptyList(),
            )
        }

        val errors = mutableListOf<String>()
        if (!ntripEnabled || !ntripConfigured) {
            errors += "RTKLIB real-time MVP requires NTRIP RTCM3 corrections."
        }
        if (!outputNmea && !outputPos) {
            errors += "RTKLIB real-time requires NMEA or POS output."
        }

        val roverRoute = roverRoute(receiverProfileId, commands)
        roverRoute.error?.let(errors::add)
        val correctionRoute = "correction=input_rtcm3(RTCM3)"
        val routePlan = "rover=${roverRoute.summary}; $correctionRoute; direction=FORWARD_ONLY"
        val validationSummary = if (errors.isEmpty()) {
            "valid; snapshot=${RtklibSnapshot.ID}"
        } else {
            "invalid: ${errors.joinToString("; ")}"
        }
        return RtklibStartValidation(
            routePlan = routePlan,
            validationSummary = validationSummary,
            errors = errors,
        )
    }

    private fun roverRoute(receiverProfileId: String, commands: List<String>): RoverRoute {
        val upperCommands = commands.map { it.trim().uppercase() }
        val hasObsvmcmpb = upperCommands.any { it.startsWith("OBSVMCMPB") }
        val hasObsvmb = upperCommands.any { it.startsWith("OBSVMB") }
        val isUblox = receiverProfileId.startsWith("ublox", ignoreCase = true)
        val isUm980 = receiverProfileId.startsWith("um980", ignoreCase = true) ||
            receiverProfileId.startsWith("unicore", ignoreCase = true)

        return when {
            isUblox -> RoverRoute("input_ubx(UBX_RXM_RAWX_SFRBX)")
            isUm980 && hasObsvmb -> RoverRoute("input_unicore(UNICORE_OBSVMB)")
            isUm980 && hasObsvmcmpb -> RoverRoute(
                summary = "unsupported(UNICORE_OBSVMCMPB)",
                error = "UM980 OBSVMCMPB requires a converter before RTKLIB real-time processing.",
            )
            isUm980 -> RoverRoute(
                summary = "not_configured(UNICORE_OBSVMB)",
                error = "UM980 RTKLIB real-time processing requires an OBSVMB command profile.",
            )
            else -> RoverRoute(
                summary = "unsupported($receiverProfileId)",
                error = "Receiver profile does not declare a supported RTKLIB rover input route.",
            )
        }
    }

    private data class RoverRoute(
        val summary: String,
        val error: String? = null,
    )
}
