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
    fun externalTrackAdditionIsExplicitMutation() {
        val addition = PendingContainerTrackAdditionUi(
            sourceUri = "content://external/source.mkv",
            sourceName = "source.mkv",
            sourceTrackNumber = 2L,
            sourceTrackUid = 202L,
            kind = ContainerResourceKind.AUDIO,
            typeCode = 2L,
            codecId = "A_OPUS",
            name = "Japanese audio",
            language = "jpn",
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
                pendingTrackAdditions = listOf(addition),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertTrue(plan.executable)
        assertEquals(ContainerMutationKind.ADD_TRACK, plan.mutations.single().kind)
        assertEquals(ContainerMutationSource.EXTERNAL_TRACK, plan.mutations.single().source)
        assertEquals(
            ContainerCompatibilityStatus.WARNING,
            plan.checks.single { it.dimension == ContainerCompatibilityDimension.DOWNSTREAM }.status,
        )
    }

    @Test
    fun duplicateExternalSourceTrackBlocksPreflight() {
        val addition = PendingContainerTrackAdditionUi(
            sourceUri = "content://external/source.mkv",
            sourceName = "source.mkv",
            sourceTrackNumber = 2L,
            sourceTrackUid = 202L,
            kind = ContainerResourceKind.AUDIO,
            typeCode = 2L,
            codecId = "A_OPUS",
            name = "Audio",
            language = "jpn",
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
                pendingTrackAdditions = listOf(addition, addition),
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
    fun removeAllExistingTracksIsAllowedWhenExternalTrackReplacesStructure() {
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
        val addition = PendingContainerTrackAdditionUi(
            sourceUri = "content://external/source.mkv",
            sourceName = "source.mkv",
            sourceTrackNumber = 1L,
            sourceTrackUid = 901L,
            kind = ContainerResourceKind.VIDEO,
            typeCode = 1L,
            codecId = "V_VP9",
            name = "Replacement video",
            language = "und",
            isDefault = true,
            isForced = false,
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
                pendingTrackAdditions = listOf(addition),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertTrue(plan.executable)
        assertEquals(
            setOf(ContainerMutationKind.REMOVE_TRACK, ContainerMutationKind.ADD_TRACK),
            plan.mutations.map { it.kind }.toSet(),
        )
    }

    @Test
    fun importedSubtitleWarnsThatSourceAttachmentsDoNotFollowAutomatically() {
        val addition = PendingContainerTrackAdditionUi(
            sourceUri = "content://external/subs.mkv",
            sourceName = "subs.mkv",
            sourceTrackNumber = 3L,
            sourceTrackUid = 303L,
            kind = ContainerResourceKind.SUBTITLE,
            typeCode = 17L,
            codecId = "S_TEXT/ASS",
            name = "Signs",
            language = "eng",
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
                pendingTrackAdditions = listOf(addition),
            ),
        )

        val plan = buildContainerEditPlan(state)
        val downstream = plan.checks.single { it.dimension == ContainerCompatibilityDimension.DOWNSTREAM }

        assertTrue(plan.executable)
        assertEquals(ContainerCompatibilityStatus.WARNING, downstream.status)
        assertTrue(downstream.detail.contains("字体/其他 Attachment 不会自动随轨导入"))
    }

    @Test
    fun standaloneAssAdditionIsExplicitAndExecutable() {
        val addition = PendingContainerTrackAdditionUi(
            sourceKind = ContainerTrackImportSourceKind.STANDALONE_ASS,
            sourceUri = "content://external/imported.ass",
            sourceName = "imported.ass",
            sourceSha256 = "a".repeat(64),
            kind = ContainerResourceKind.SUBTITLE,
            typeCode = 17L,
            codecId = "S_TEXT/ASS",
            name = "Imported ASS",
            language = "eng",
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
                pendingTrackAdditions = listOf(addition),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertTrue(plan.executable)
        assertEquals(ContainerMutationKind.ADD_TRACK, plan.mutations.single().kind)
        assertEquals(ContainerMutationSource.EXTERNAL_TRACK, plan.mutations.single().source)
        assertTrue(plan.mutations.single().detail.contains("standalone ASS"))
        assertEquals(
            ContainerCompatibilityStatus.WARNING,
            plan.checks.single { it.dimension == ContainerCompatibilityDimension.DOWNSTREAM }.status,
        )
        assertTrue(
            plan.checks.single { it.dimension == ContainerCompatibilityDimension.DOWNSTREAM }
                .detail.contains("SHA-256")
        )
    }

    @Test
    fun standaloneAssMissingShaBlocksPreflight() {
        val addition = PendingContainerTrackAdditionUi(
            sourceKind = ContainerTrackImportSourceKind.STANDALONE_ASS,
            sourceUri = "content://external/imported.ass",
            sourceName = "imported.ass",
            sourceSha256 = null,
            kind = ContainerResourceKind.SUBTITLE,
            typeCode = 17L,
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
                pendingTrackAdditions = listOf(addition),
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
    fun standaloneSrtAdditionIsExplicitAndExecutable() {
        val addition = PendingContainerTrackAdditionUi(
            sourceKind = ContainerTrackImportSourceKind.STANDALONE_SRT,
            sourceUri = "content://external/imported.srt",
            sourceName = "imported.srt",
            sourceSha256 = "b".repeat(64),
            kind = ContainerResourceKind.SUBTITLE,
            typeCode = 17L,
            codecId = "S_TEXT/ASS",
            name = "Imported SRT",
            language = "eng",
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
                pendingTrackAdditions = listOf(addition),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertTrue(plan.executable)
        assertEquals(ContainerMutationKind.ADD_TRACK, plan.mutations.single().kind)
        assertTrue(plan.mutations.single().detail.contains("standalone SRT"))
        val downstream = plan.checks.single {
            it.dimension == ContainerCompatibilityDimension.DOWNSTREAM
        }
        assertEquals(ContainerCompatibilityStatus.WARNING, downstream.status)
        assertTrue(downstream.detail.contains("ASS / SRT"))
        assertTrue(downstream.detail.contains("SHA-256"))
    }

    @Test
    fun standaloneSrtWithoutNormalizedHashBlocksPreflight() {
        val addition = PendingContainerTrackAdditionUi(
            sourceKind = ContainerTrackImportSourceKind.STANDALONE_SRT,
            sourceUri = "content://external/imported.srt",
            sourceName = "imported.srt",
            sourceSha256 = null,
            kind = ContainerResourceKind.SUBTITLE,
            typeCode = 17L,
            codecId = "S_TEXT/ASS",
            name = "Imported SRT",
            language = "und",
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
                pendingTrackAdditions = listOf(addition),
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
