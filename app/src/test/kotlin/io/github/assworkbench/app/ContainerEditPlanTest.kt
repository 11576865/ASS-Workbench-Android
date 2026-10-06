package io.github.assworkbench.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerEditPlanTest {
    @Test
    fun genericAttachmentIsExecutableWhileDownstreamBehaviorRemainsUnknown() {
        val state = EditorState(
            container = ContainerBridgeState(
                uri = "fixture-source",
                writeBackAvailable = true,
                pendingAttachments = listOf(
                    PendingContainerAttachmentUi(
                        uri = "fixture-cover",
                        name = "cover.png",
                        mimeType = "image/png",
                        sizeBytes = 4096L,
                    )
                ),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertTrue(plan.executable)
        assertEquals(1, plan.mutations.size)
        assertEquals(ContainerMutationKind.ADD_ATTACHMENT, plan.mutations.single().kind)
        assertEquals(ContainerMutationSource.GENERIC_ATTACHMENT, plan.mutations.single().source)
        assertEquals(
            ContainerCompatibilityStatus.UNKNOWN,
            plan.checks.single { it.dimension == ContainerCompatibilityDimension.DOWNSTREAM }.status,
        )
    }

    @Test
    fun missingLocalWriterBlocksOtherwiseValidAttachmentPlan() {
        val state = EditorState(
            container = ContainerBridgeState(
                uri = "fixture-source",
                writeBackAvailable = false,
                pendingAttachments = listOf(
                    PendingContainerAttachmentUi(
                        uri = "fixture-note",
                        name = "notes.txt",
                        mimeType = "text/plain",
                    )
                ),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertFalse(plan.executable)
        assertEquals(
            ContainerCompatibilityStatus.UNSUPPORTED,
            plan.checks.single { it.dimension == ContainerCompatibilityDimension.LOCAL_WRITER }.status,
        )
    }

    @Test
    fun assReplacementOnlyExistsWhenSelectedContainerTrackIsDirty() {
        val clean = EditorState(
            subtitleLoaded = true,
            dirty = false,
            container = ContainerBridgeState(
                uri = "fixture-source",
                selectedTrackNumber = 2L,
                writeBackAvailable = true,
            ),
        )
        val dirty = clean.copy(dirty = true)

        assertTrue(buildContainerEditPlan(clean).mutations.isEmpty())
        val mutation = buildContainerEditPlan(dirty).mutations.single()
        assertEquals(ContainerMutationKind.REPLACE_ASS_TRACK, mutation.kind)
        assertEquals("replace-ass:2", mutation.id)
    }

    @Test
    fun attachmentRemoveAndReplaceBecomeExplicitMutations() {
        val state = EditorState(
            container = ContainerBridgeState(
                uri = "fixture-source",
                writeBackAvailable = true,
                resources = listOf(
                    ContainerResourceUi(
                        rowKey = "attachment:uid:8",
                        kind = ContainerResourceKind.ATTACHMENT,
                        title = "old.txt",
                        detail = "text/plain",
                        attachmentTarget = "8",
                    ),
                    ContainerResourceUi(
                        rowKey = "attachment:uid:9",
                        kind = ContainerResourceKind.ATTACHMENT,
                        title = "old-cover.jpg",
                        detail = "image/jpeg",
                        attachmentTarget = "9",
                    ),
                ),
                pendingAttachmentRemovals = listOf(
                    PendingContainerAttachmentRemovalUi(target = "8", name = "old.txt")
                ),
                pendingAttachmentReplacements = listOf(
                    PendingContainerAttachmentReplacementUi(
                        target = "9",
                        originalName = "old-cover.jpg",
                        uri = "fixture-cover",
                        name = "cover.png",
                        mimeType = "image/png",
                        sizeBytes = 8192L,
                    )
                ),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertTrue(plan.executable)
        assertEquals(
            setOf(ContainerMutationKind.REMOVE_ATTACHMENT, ContainerMutationKind.REPLACE_ATTACHMENT),
            plan.mutations.map { it.kind }.toSet(),
        )
    }

    @Test
    fun staleAttachmentMutationTargetBlocksPreflight() {
        val state = EditorState(
            container = ContainerBridgeState(
                uri = "fixture-source",
                writeBackAvailable = true,
                resources = emptyList(),
                pendingAttachmentRemovals = listOf(
                    PendingContainerAttachmentRemovalUi(target = "7", name = "missing.bin")
                ),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertFalse(plan.executable)
        assertEquals(
            ContainerCompatibilityStatus.UNSUPPORTED,
            plan.checks.single { it.dimension == ContainerCompatibilityDimension.CONTAINER_STRUCTURE }.status,
        )
    }

    @Test
    fun attachmentMetadataEditIsExplicitAndConflictsFailClosed() {
        val resource = ContainerResourceUi(
            rowKey = "attachment:uid:12",
            kind = ContainerResourceKind.ATTACHMENT,
            title = "notes.txt",
            detail = "text/plain",
            attachmentTarget = "12",
            attachmentMimeType = "text/plain",
            attachmentDescription = "old",
            attachmentSizeBytes = 42L,
        )
        val metadataOnly = EditorState(
            container = ContainerBridgeState(
                uri = "fixture-source",
                writeBackAvailable = true,
                resources = listOf(resource),
                pendingAttachmentMetadataEdits = listOf(
                    PendingContainerAttachmentMetadataUi(
                        target = "12",
                        originalName = "notes.txt",
                        name = "translator-notes.txt",
                        description = "translation notes",
                    )
                ),
            ),
        )

        val plan = buildContainerEditPlan(metadataOnly)
        assertTrue(plan.executable)
        assertEquals(ContainerMutationKind.EDIT_ATTACHMENT_METADATA, plan.mutations.single().kind)

        val conflict = metadataOnly.copy(
            container = metadataOnly.container.copy(
                pendingAttachmentRemovals = listOf(
                    PendingContainerAttachmentRemovalUi(target = "12", name = "notes.txt")
                ),
            ),
        )
        val conflictPlan = buildContainerEditPlan(conflict)
        assertFalse(conflictPlan.executable)
        assertEquals(
            ContainerCompatibilityStatus.UNSUPPORTED,
            conflictPlan.checks.single {
                it.dimension == ContainerCompatibilityDimension.CONTAINER_STRUCTURE
            }.status,
        )
    }

    @Test
    fun trackMetadataEditIsExplicitMutation() {
        val resource = ContainerResourceUi(
            rowKey = "track:uid:101",
            kind = ContainerResourceKind.VIDEO,
            title = "Video",
            detail = "V_VP9 · Track #1",
            trackNumber = 1L,
            trackTarget = "uid:101",
            trackUid = 101L,
            trackCodecId = "V_VP9",
            trackName = "Video",
            trackLanguage = "und",
            trackIsDefault = true,
            trackIsForced = false,
        )
        val state = EditorState(
            container = ContainerBridgeState(
                uri = "fixture-source",
                writeBackAvailable = true,
                resources = listOf(resource),
                pendingTrackMetadataEdits = listOf(
                    PendingContainerTrackMetadataUi(
                        target = "uid:101",
                        number = 1L,
                        originalName = "Video",
                        name = "Main picture",
                        language = "jpn",
                        isDefault = false,
                        isForced = true,
                    )
                ),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertTrue(plan.executable)
        assertEquals(ContainerMutationKind.EDIT_TRACK_METADATA, plan.mutations.single().kind)
        assertEquals(ContainerMutationSource.EXISTING_TRACK, plan.mutations.single().source)
        assertEquals(
            ContainerCompatibilityStatus.WARNING,
            plan.checks.single { it.dimension == ContainerCompatibilityDimension.DOWNSTREAM }.status,
        )
    }

    @Test
    fun trackMetadataV2AppearsInMutationDetail() {
        val resource = ContainerResourceUi(
            rowKey = "track:uid:101",
            kind = ContainerResourceKind.AUDIO,
            title = "Commentary",
            detail = "A_OPUS · Track #1",
            trackNumber = 1L,
            trackTarget = "uid:101",
            trackLanguage = "eng",
            trackLanguageBcp47 = "en-US",
        )
        val state = EditorState(
            container = ContainerBridgeState(
                uri = "fixture-source",
                writeBackAvailable = true,
                resources = listOf(resource),
                pendingTrackMetadataEdits = listOf(
                    PendingContainerTrackMetadataUi(
                        target = "uid:101",
                        number = 1L,
                        originalName = "Commentary",
                        name = "Director commentary",
                        language = "eng",
                        isDefault = false,
                        isForced = false,
                        languageBcp47 = "en-GB",
                        hearingImpaired = true,
                        commentary = true,
                    )
                ),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertTrue(plan.executable)
        val mutation = plan.mutations.single()
        assertEquals(ContainerMutationKind.EDIT_TRACK_METADATA, mutation.kind)
        assertTrue(mutation.detail.contains("BCP 47 en-GB"))
        assertTrue(mutation.detail.contains("Hearing impaired"))
        assertTrue(mutation.detail.contains("Commentary"))
    }

    @Test
    fun invalidBcp47BlocksPreflight() {
        val state = EditorState(
            container = ContainerBridgeState(
                uri = "fixture-source",
                writeBackAvailable = true,
                pendingTrackImports = listOf(
                    PendingContainerTrackImportUi(
                        sourceUri = "content://fixture/external.mkv",
                        sourceName = "external.mkv",
                        sourceTrackNumber = 3L,
                        sourceTrackUid = 303L,
                        kind = ContainerResourceKind.AUDIO,
                        codecId = "A_OPUS",
                        name = "Commentary",
                        language = "eng",
                        isDefault = false,
                        isForced = false,
                        sourceAttachmentCount = 0,
                        languageBcp47 = "en--US",
                    )
                ),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertFalse(plan.executable)
        assertEquals(
            ContainerCompatibilityStatus.UNSUPPORTED,
            plan.checks.single { it.dimension == ContainerCompatibilityDimension.CONTAINER_STRUCTURE }.status,
        )
    }

    @Test
    fun removingAllTracksBlocksPreflight() {
        val resources = listOf(
            ContainerResourceUi(
                rowKey = "track:uid:101",
                kind = ContainerResourceKind.VIDEO,
                title = "Video",
                detail = "V_VP9 · Track #1",
                trackNumber = 1L,
                trackTarget = "uid:101",
            ),
            ContainerResourceUi(
                rowKey = "track:uid:202",
                kind = ContainerResourceKind.AUDIO,
                title = "Audio",
                detail = "A_OPUS · Track #2",
                trackNumber = 2L,
                trackTarget = "uid:202",
            ),
        )
        val state = EditorState(
            container = ContainerBridgeState(
                uri = "fixture-source",
                writeBackAvailable = true,
                resources = resources,
                pendingTrackRemovals = listOf(
                    PendingContainerTrackRemovalUi("uid:101", 1L, "Video"),
                    PendingContainerTrackRemovalUi("uid:202", 2L, "Audio"),
                ),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertFalse(plan.executable)
        assertEquals(
            ContainerCompatibilityStatus.UNSUPPORTED,
            plan.checks.single { it.dimension == ContainerCompatibilityDimension.CONTAINER_STRUCTURE }.status,
        )
    }

    @Test
    fun replacingEntireTrackSetWithExternalImportPassesPreflight() {
        val resources = listOf(
            ContainerResourceUi(
                rowKey = "track:uid:101",
                kind = ContainerResourceKind.VIDEO,
                title = "Video",
                detail = "V_VP9 · Track #1",
                trackNumber = 1L,
                trackTarget = "uid:101",
            ),
            ContainerResourceUi(
                rowKey = "track:uid:202",
                kind = ContainerResourceKind.AUDIO,
                title = "Audio",
                detail = "A_OPUS · Track #2",
                trackNumber = 2L,
                trackTarget = "uid:202",
            ),
        )
        val state = EditorState(
            container = ContainerBridgeState(
                uri = "fixture-source",
                writeBackAvailable = true,
                resources = resources,
                pendingTrackRemovals = listOf(
                    PendingContainerTrackRemovalUi("uid:101", 1L, "Video"),
                    PendingContainerTrackRemovalUi("uid:202", 2L, "Audio"),
                ),
                pendingTrackImports = listOf(
                    PendingContainerTrackImportUi(
                        sourceUri = "content://fixture/replacement.mkv",
                        sourceName = "replacement.mkv",
                        sourceTrackNumber = 7L,
                        sourceTrackUid = 707L,
                        kind = ContainerResourceKind.AUDIO,
                        codecId = "A_OPUS",
                        name = "Replacement",
                        language = "und",
                        isDefault = false,
                        isForced = false,
                        sourceAttachmentCount = 0,
                    )
                ),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertTrue(plan.executable)
        assertEquals(
            ContainerCompatibilityStatus.SUPPORTED,
            plan.checks.single {
                it.dimension == ContainerCompatibilityDimension.CONTAINER_STRUCTURE
            }.status,
        )
        assertEquals(
            setOf(
                ContainerMutationKind.REMOVE_TRACK,
                ContainerMutationKind.ADD_TRACK,
            ),
            plan.mutations.map { it.kind }.toSet(),
        )
    }

    @Test
    fun removingDirtySelectedAssBlocksPreflight() {
        val resources = listOf(
            ContainerResourceUi(
                rowKey = "track:uid:101",
                kind = ContainerResourceKind.VIDEO,
                title = "Video",
                detail = "V_VP9 · Track #1",
                trackNumber = 1L,
                trackTarget = "uid:101",
            ),
            ContainerResourceUi(
                rowKey = "track:uid:202",
                kind = ContainerResourceKind.SUBTITLE,
                title = "ASS",
                detail = "S_TEXT/ASS · Track #2",
                trackNumber = 2L,
                trackTarget = "uid:202",
                editableAss = true,
            ),
        )
        val state = EditorState(
            subtitleLoaded = true,
            dirty = true,
            container = ContainerBridgeState(
                uri = "fixture-source",
                writeBackAvailable = true,
                resources = resources,
                selectedTrackNumber = 2L,
                pendingTrackRemovals = listOf(
                    PendingContainerTrackRemovalUi("uid:202", 2L, "ASS")
                ),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertFalse(plan.executable)
        assertEquals(
            ContainerCompatibilityStatus.UNSUPPORTED,
            plan.checks.single { it.dimension == ContainerCompatibilityDimension.CONTAINER_STRUCTURE }.status,
        )
    }

    @Test
    fun externalTrackImportIsExplicitMutation() {
        val state = EditorState(
            container = ContainerBridgeState(
                uri = "fixture-source",
                writeBackAvailable = true,
                pendingTrackImports = listOf(
                    PendingContainerTrackImportUi(
                        sourceUri = "content://fixture/external.mkv",
                        sourceName = "external.mkv",
                        sourceTrackNumber = 3L,
                        sourceTrackUid = 303L,
                        kind = ContainerResourceKind.AUDIO,
                        codecId = "A_OPUS",
                        name = "Commentary",
                        language = "eng",
                        isDefault = false,
                        isForced = false,
                        sourceAttachmentCount = 0,
                    )
                ),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertTrue(plan.executable)
        assertEquals(ContainerMutationKind.ADD_TRACK, plan.mutations.single().kind)
        assertEquals(ContainerMutationSource.EXTERNAL_TRACK, plan.mutations.single().source)
        assertTrue(plan.mutations.single().detail.contains("external.mkv"))
        assertEquals(
            ContainerCompatibilityStatus.WARNING,
            plan.checks.single { it.dimension == ContainerCompatibilityDimension.DOWNSTREAM }.status,
        )
    }

    @Test
    fun duplicateTrackImportBlocksPreflight() {
        val imported = PendingContainerTrackImportUi(
            sourceUri = "content://fixture/external.mkv",
            sourceName = "external.mkv",
            sourceTrackNumber = 3L,
            sourceTrackUid = 303L,
            kind = ContainerResourceKind.AUDIO,
            codecId = "A_OPUS",
            name = "Commentary",
            language = "eng",
            isDefault = false,
            isForced = false,
            sourceAttachmentCount = 0,
        )
        val state = EditorState(
            container = ContainerBridgeState(
                uri = "fixture-source",
                writeBackAvailable = true,
                pendingTrackImports = listOf(imported, imported.copy(name = "Duplicate")),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertFalse(plan.executable)
        assertEquals(
            ContainerCompatibilityStatus.UNSUPPORTED,
            plan.checks.single { it.dimension == ContainerCompatibilityDimension.CONTAINER_STRUCTURE }.status,
        )
    }

    @Test
    fun subtitleTrackImportWithSourceAttachmentsProducesExplicitWarning() {
        val state = EditorState(
            container = ContainerBridgeState(
                uri = "fixture-source",
                writeBackAvailable = true,
                pendingTrackImports = listOf(
                    PendingContainerTrackImportUi(
                        sourceUri = "content://fixture/subtitles.mkv",
                        sourceName = "subtitles.mkv",
                        sourceTrackNumber = 4L,
                        sourceTrackUid = 404L,
                        kind = ContainerResourceKind.SUBTITLE,
                        codecId = "S_TEXT/ASS",
                        name = "Signs",
                        language = "eng",
                        isDefault = false,
                        isForced = false,
                        sourceAttachmentCount = 6,
                    )
                ),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertTrue(plan.executable)
        assertTrue(
            plan.checks.any {
                it.dimension == ContainerCompatibilityDimension.SOURCE_INVENTORY &&
                    it.status == ContainerCompatibilityStatus.WARNING &&
                    it.title.contains("不自动复制来源附件")
            }
        )
    }

    @Test
    fun standaloneSrtNormalizedImportIsExecutable() {
        val import = PendingContainerTrackImportUi(
            sourceKind = ContainerTrackImportSourceKind.STANDALONE_SRT,
            sourceUri = "content://external/subtitles.srt",
            sourceName = "subtitles.srt",
            sourceSha256 = "a".repeat(64),
            sourceAttachmentCount = 0,
            kind = ContainerResourceKind.SUBTITLE,
            codecId = "S_TEXT/ASS",
            name = "Imported subtitles",
            language = "eng",
            languageBcp47 = "en-US",
            isDefault = false,
            isForced = false,
        )
        val state = EditorState(
            container = ContainerBridgeState(
                uri = "fixture-source",
                writeBackAvailable = true,
                resources = listOf(
                    ContainerResourceUi(
                        rowKey = "track:uid:101",
                        kind = ContainerResourceKind.VIDEO,
                        title = "Video",
                        detail = "V_VP9 · Track #1",
                        trackNumber = 1L,
                        trackTarget = "uid:101",
                    )
                ),
                pendingTrackImports = listOf(import),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertTrue(plan.executable)
        assertEquals(ContainerMutationKind.ADD_TRACK, plan.mutations.single().kind)
        assertTrue(plan.mutations.single().detail.contains("standalone SRT"))
        assertTrue(
            plan.checks.any {
                it.dimension == ContainerCompatibilityDimension.SOURCE_INVENTORY &&
                    it.title.contains("独立字幕规范化证据")
            }
        )
    }

    @Test
    fun standaloneSubtitleWithoutNormalizedShaBlocksPreflight() {
        val import = PendingContainerTrackImportUi(
            sourceKind = ContainerTrackImportSourceKind.STANDALONE_ASS,
            sourceUri = "content://external/subtitles.ass",
            sourceName = "subtitles.ass",
            sourceSha256 = null,
            sourceAttachmentCount = 0,
            kind = ContainerResourceKind.SUBTITLE,
            codecId = "S_TEXT/ASS",
            name = "Imported ASS",
            language = "und",
            isDefault = false,
            isForced = false,
        )
        val state = EditorState(
            container = ContainerBridgeState(
                uri = "fixture-source",
                writeBackAvailable = true,
                pendingTrackImports = listOf(import),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertFalse(plan.executable)
        assertEquals(
            ContainerCompatibilityStatus.UNSUPPORTED,
            plan.checks.single {
                it.dimension == ContainerCompatibilityDimension.CONTAINER_STRUCTURE
            }.status,
        )
    }

    @Test
    fun normalizedMp3ImportIsExecutableAndKeepsCurrentMetadataPolicy() {
        val import = PendingContainerTrackImportUi(
            sourceKind = ContainerTrackImportSourceKind.NORMALIZED_MEDIA_PACKETS,
            sourceUri = "content://external/audio.mp3",
            sourceName = "audio.mp3",
            sourceExtractorIndex = 0,
            sourceAttachmentCount = 0,
            sourceSha256 = "b".repeat(64),
            sourceContentSha256 = "c".repeat(64),
            sampleRate = 44_100,
            channelCount = 2,
            packetCount = 42L,
            kind = ContainerResourceKind.AUDIO,
            codecId = "A_MPEG/L3",
            name = "Commentary",
            language = "eng",
            languageBcp47 = "en-GB",
            isDefault = false,
            isForced = false,
            hearingImpaired = true,
            commentary = true,
        )
        val state = EditorState(
            container = ContainerBridgeState(
                uri = "fixture-source",
                writeBackAvailable = true,
                resources = listOf(
                    ContainerResourceUi(
                        rowKey = "track:uid:101",
                        kind = ContainerResourceKind.VIDEO,
                        title = "Video",
                        detail = "V_VP9 · Track #1",
                        trackNumber = 1L,
                        trackTarget = "uid:101",
                    )
                ),
                pendingTrackImports = listOf(import),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertTrue(plan.executable)
        val mutation = plan.mutations.single()
        assertEquals(ContainerMutationSource.EXTERNAL_TRACK, mutation.source)
        assertTrue(mutation.detail.contains("packet stream-copy"))
        assertTrue(mutation.detail.contains("BCP 47 en-GB"))
        assertTrue(mutation.detail.contains("Hearing impaired"))
        assertTrue(mutation.detail.contains("Commentary"))
        assertTrue(
            plan.checks.single {
                it.dimension == ContainerCompatibilityDimension.OUTPUT_VERIFICATION
            }.detail.contains("payload digest")
        )
    }

    @Test
    fun normalizedMp3WithoutLogicalContentEvidenceBlocksPreflight() {
        val import = PendingContainerTrackImportUi(
            sourceKind = ContainerTrackImportSourceKind.NORMALIZED_MEDIA_PACKETS,
            sourceUri = "content://external/audio.mp3",
            sourceName = "audio.mp3",
            sourceExtractorIndex = 0,
            sourceAttachmentCount = 0,
            sourceSha256 = "b".repeat(64),
            sourceContentSha256 = null,
            sampleRate = 44_100,
            channelCount = 2,
            packetCount = 42L,
            kind = ContainerResourceKind.AUDIO,
            codecId = "A_MPEG/L3",
            name = "Audio",
            language = "und",
            isDefault = false,
            isForced = false,
        )
        val state = EditorState(
            container = ContainerBridgeState(
                uri = "fixture-source",
                writeBackAvailable = true,
                pendingTrackImports = listOf(import),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertFalse(plan.executable)
        assertEquals(
            ContainerCompatibilityStatus.UNSUPPORTED,
            plan.checks.single {
                it.dimension == ContainerCompatibilityDimension.CONTAINER_STRUCTURE
            }.status,
        )
    }
}
