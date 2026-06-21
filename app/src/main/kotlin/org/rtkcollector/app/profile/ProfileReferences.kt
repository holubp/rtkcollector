package org.rtkcollector.app.profile

internal inline fun <reified T> List<T>.requireProfileReference(id: String, label: String): T =
    firstOrNull { profile ->
        when (profile) {
            is CommandProfile -> profile.id == id
            is UsbBaudProfile -> profile.id == id
            is NtripCasterProfile -> profile.id == id
            is NtripMountpointProfile -> profile.id == id
            is RecordingPolicyProfile -> profile.id == id
            is RtklibProfile -> profile.id == id
            is SolutionPolicyProfile -> profile.id == id
            is StorageProfile -> profile.id == id
            else -> false
        }
    } ?: throw IllegalArgumentException("Missing $label '$id'.")
