package org.rtkcollector.app.base

data class BaseCoordinateForm(
    val id: String = "",
    val name: String = "",
    val latDeg: String = "",
    val lonDeg: String = "",
    val ellipsoidalHeightM: String = "",
    val frame: String = "",
    val epoch: String = "",
    val method: String = "",
    val durationSeconds: String = "",
    val horizontalUncertaintyM: String = "",
    val verticalUncertaintyM: String = "",
    val antennaHeightM: String = "",
    val antennaReferencePoint: String = "",
    val sourceSessionId: String = "",
    val sourceDescription: String = "",
) {
    fun validateForSave(): BaseCoordinateFormValidation =
        toAcceptedBaseCoordinate()
            .fold(
                onSuccess = { coordinate ->
                    runCatching { coordinate.validate() }
                        .fold(
                            onSuccess = { BaseCoordinateFormValidation(valid = true) },
                            onFailure = { error ->
                                BaseCoordinateFormValidation(
                                    valid = false,
                                    messages = listOf(error.message ?: "Base coordinate is invalid."),
                                )
                            },
                        )
                },
                onFailure = { error ->
                    BaseCoordinateFormValidation(
                        valid = false,
                        messages = listOf(error.message ?: "Base coordinate form is invalid."),
                    )
                },
            )

    fun toAcceptedBaseCoordinate(): Result<AcceptedBaseCoordinate> =
        runCatching {
            AcceptedBaseCoordinate(
                id = id.trim(),
                name = name.trim(),
                latDeg = requiredDouble("latitude", latDeg),
                lonDeg = requiredDouble("longitude", lonDeg),
                ellipsoidalHeightM = requiredDouble("ellipsoidal height", ellipsoidalHeightM),
                frame = frame.trim(),
                epoch = epoch.trim().takeIf(String::isNotBlank),
                method = method.trim(),
                durationSeconds = optionalLong("duration", durationSeconds),
                horizontalUncertaintyM = optionalDouble("horizontal uncertainty", horizontalUncertaintyM),
                verticalUncertaintyM = optionalDouble("vertical uncertainty", verticalUncertaintyM),
                antennaHeightM = optionalDouble("antenna height", antennaHeightM),
                antennaReferencePoint = antennaReferencePoint.trim().takeIf(String::isNotBlank),
                sourceSessionId = sourceSessionId.trim().takeIf(String::isNotBlank),
                sourceDescription = sourceDescription.trim(),
            )
        }
}

data class BaseCoordinateFormValidation(
    val valid: Boolean,
    val messages: List<String> = emptyList(),
)

private fun requiredDouble(label: String, value: String): Double =
    value.trim().toDoubleOrNull() ?: error("Base coordinate $label is required and must be numeric.")

private fun optionalDouble(label: String, value: String): Double? =
    value.trim().takeIf(String::isNotBlank)?.toDoubleOrNull()
        ?: if (value.trim().isBlank()) null else error("Base coordinate $label must be numeric.")

private fun optionalLong(label: String, value: String): Long? =
    value.trim().takeIf(String::isNotBlank)?.toLongOrNull()
        ?: if (value.trim().isBlank()) null else error("Base coordinate $label must be an integer.")
