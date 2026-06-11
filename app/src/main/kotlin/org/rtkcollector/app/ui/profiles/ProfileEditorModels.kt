package org.rtkcollector.app.ui.profiles

data class NtripMountpointEditorState(
    val mountpointText: String = "",
    val availableMountpoints: List<String> = emptyList(),
) {
    fun withFetchedMountpoints(mountpoints: List<String>): NtripMountpointEditorState =
        copy(availableMountpoints = mountpoints.filter(String::isNotBlank).distinct())

    fun selectMountpoint(mountpoint: String): NtripMountpointEditorState {
        require(mountpoint.isNotBlank()) { "Selected mountpoint must not be blank." }
        require(availableMountpoints.isEmpty() || mountpoint in availableMountpoints) {
            "Selected mountpoint is not in the fetched list."
        }
        return copy(mountpointText = mountpoint)
    }
}

const val PersistentReceiverWriteLabel = "Write init config persistently to device"
const val PersistentReceiverWriteWarningTitle = "Write receiver configuration?"
const val PersistentReceiverWriteWarningBody =
    "This sends the current init script and SAVECONFIG to the receiver. " +
        "It writes receiver non-volatile memory and can affect other apps, tools and future receiver sessions until manually changed again."

fun persistentReceiverWriteAction(
    onClick: () -> Unit = {},
    onClickWithValues: ((Map<String, String>) -> Unit)? = null,
): ProfileEditorAction =
    ProfileEditorAction(
        label = PersistentReceiverWriteLabel,
        onClick = onClick,
        onClickWithValues = onClickWithValues,
        warningTitle = PersistentReceiverWriteWarningTitle,
        warningBody = PersistentReceiverWriteWarningBody,
        confirmLabel = "Write persistently",
    )
