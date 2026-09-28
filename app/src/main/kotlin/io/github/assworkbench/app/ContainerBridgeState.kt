package io.github.assworkbench.app

data class ContainerTrackUi(
    val number: Long,
    val name: String,
    val language: String,
    val eventCount: Int,
)

data class ContainerBridgeState(
    val uri: String? = null,
    val name: String = "",
    val loading: Boolean = false,
    val tracks: List<ContainerTrackUi> = emptyList(),
    val selectedTrackNumber: Long? = null,
    val extractedFontCount: Int = 0,
    val skippedAttachmentCount: Int = 0,
    val writeBackAvailable: Boolean = false,
    val writeBackBusy: Boolean = false,
    val error: String? = null,
)
