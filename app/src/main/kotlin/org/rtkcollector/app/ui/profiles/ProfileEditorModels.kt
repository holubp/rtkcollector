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
