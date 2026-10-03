package org.rtkcollector.app.profile

import org.rtkcollector.app.base.AcceptedBaseCoordinate

internal fun modelTestBaseCoordinate() = AcceptedBaseCoordinate(
    id = "base", name = "Base", latDeg = 49.4637593130, lonDeg = 15.4512544790,
    ellipsoidalHeightM = null, mslAltitudeM = 707.8, geoidSeparationM = null,
    frame = "ITRF", epoch = null, method = "KNOWN_CONTROL", durationSeconds = null,
    horizontalUncertaintyM = null, verticalUncertaintyM = null, antennaHeightM = null,
    antennaReferencePoint = null, sourceSessionId = null, sourceDescription = "Known control",
)
