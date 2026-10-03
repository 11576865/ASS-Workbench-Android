package io.github.assworkbench.app

enum class ContainerResourceKind {
    VIDEO,
    AUDIO,
    SUBTITLE,
    FONT,
    ATTACHMENT,
    CHAPTERS,
    OTHER,
}

enum class ContainerResourceChange {
    UNCHANGED,
    ADDED,
    REMOVED,
    MODIFIED,
    UNRESOLVED,
}

enum class ContainerInventoryEvidence {
    BASELINE,
    CURRENT_SOURCE,
    VERIFIED_OUTPUT,
}

data class ContainerResourceUi(
    val rowKey: String,
    val kind: ContainerResourceKind,
    val title: String,
    val detail: String,
    val trackNumber: Long? = null,
    val editableAss: Boolean = false,
    val change: ContainerResourceChange = ContainerResourceChange.UNCHANGED,
)

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
    val resources: List<ContainerResourceUi> = emptyList(),
    val inventoryEvidence: ContainerInventoryEvidence = ContainerInventoryEvidence.BASELINE,
    val selectedTrackNumber: Long? = null,
    val extractedFontCount: Int = 0,
    val skippedAttachmentCount: Int = 0,
    val writeBackAvailable: Boolean = false,
    val writeBackBusy: Boolean = false,
    val error: String? = null,
)
