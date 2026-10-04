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
    /** Stable mkvgo mutation/extraction target: FileUID when available, otherwise a unique filename. */
    val attachmentTarget: String? = null,
    val attachmentMimeType: String = "",
    val attachmentDescription: String = "",
    val attachmentSizeBytes: Long? = null,
    val attachmentSha256: String? = null,
    val change: ContainerResourceChange = ContainerResourceChange.UNCHANGED,
)

data class ContainerTrackUi(
    val number: Long,
    val name: String,
    val language: String,
    val eventCount: Int,
)

data class PendingContainerAttachmentUi(
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long? = null,
)

data class PendingContainerAttachmentRemovalUi(
    val target: String,
    val name: String,
)

data class PendingContainerAttachmentReplacementUi(
    val target: String,
    val originalName: String,
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long? = null,
)

data class PendingContainerAttachmentMetadataUi(
    val target: String,
    val originalName: String,
    val name: String,
    val description: String,
)

data class ContainerBridgeState(
    val uri: String? = null,
    val name: String = "",
    val loading: Boolean = false,
    val tracks: List<ContainerTrackUi> = emptyList(),
    val resources: List<ContainerResourceUi> = emptyList(),
    val inventoryEvidence: ContainerInventoryEvidence = ContainerInventoryEvidence.BASELINE,
    val selectedTrackNumber: Long? = null,
    val pendingAttachments: List<PendingContainerAttachmentUi> = emptyList(),
    val pendingAttachmentRemovals: List<PendingContainerAttachmentRemovalUi> = emptyList(),
    val pendingAttachmentReplacements: List<PendingContainerAttachmentReplacementUi> = emptyList(),
    val pendingAttachmentMetadataEdits: List<PendingContainerAttachmentMetadataUi> = emptyList(),
    val extractedFontCount: Int = 0,
    val skippedAttachmentCount: Int = 0,
    val writeBackAvailable: Boolean = false,
    val writeBackBusy: Boolean = false,
    val attachmentExtractBusy: Boolean = false,
    val error: String? = null,
)
