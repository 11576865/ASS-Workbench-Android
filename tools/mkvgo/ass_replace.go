package ops

import (
	"context"
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"hash"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"

	"github.com/gravity-zero/mkvgo/mkv"
	"github.com/gravity-zero/mkvgo/mkv/reader"
	"github.com/gravity-zero/mkvgo/mkv/subtitle"
	"github.com/gravity-zero/mkvgo/mkv/writer"
)

// ReplaceASS replaces one existing ASS/SSA track in place at the Matroska
// track-identity level. TrackNumber, TrackUID, ordering, language/name,
// disposition flags and other track metadata are inherited from the source.
// Only the subtitle codec/header and blocks are replaced.
func ReplaceASS(ctx context.Context, srcPath string, trackID uint64, assPath, dstPath string, opts ...mkv.Options) error {
	return ReplaceASSWithAttachments(ctx, srcPath, trackID, assPath, dstPath, nil, opts...)
}

// ReplaceASSWithFonts keeps the historical font-only API while routing the
// actual remux through the generic attachment implementation.
func ReplaceASSWithFonts(
	ctx context.Context,
	srcPath string,
	trackID uint64,
	assPath, dstPath string,
	fontPaths []string,
	opts ...mkv.Options,
) error {
	for _, path := range fontPaths {
		if !isFontAttachmentPath(path) {
			return fmt.Errorf("unsupported font attachment %q", filepath.Base(path))
		}
	}
	return ReplaceASSWithAttachments(ctx, srcPath, trackID, assPath, dstPath, fontPaths, opts...)
}

// ReplaceASSWithFontsAndAttachments preserves the legacy font validation while
// allowing arbitrary Matroska attachments in the same transactional remux.
func ReplaceASSWithFontsAndAttachments(
	ctx context.Context,
	srcPath string,
	trackID uint64,
	assPath, dstPath string,
	fontPaths, attachmentPaths []string,
	opts ...mkv.Options,
) error {
	return ReplaceASSWithFontsAndAttachmentEdits(
		ctx, srcPath, trackID, assPath, dstPath,
		fontPaths, attachmentPaths, nil, nil, nil, nil, nil, nil, opts...,
	)
}

func ReplaceASSWithFontsAndAttachmentEdits(
	ctx context.Context,
	srcPath string,
	trackID uint64,
	assPath, dstPath string,
	fontPaths, attachmentPaths, removeTargets []string,
	replacements []AttachmentReplacement,
	metadataEdits []AttachmentMetadataEdit,
	removeTrackTargets []string,
	trackMetadataEdits []TrackMetadataEdit,
	trackAdditions []TrackAddition,
	opts ...mkv.Options,
) error {
	for _, path := range fontPaths {
		if !isFontAttachmentPath(path) {
			return fmt.Errorf("unsupported font attachment %q", filepath.Base(path))
		}
	}
	all := append(append([]string(nil), fontPaths...), attachmentPaths...)
	return ReplaceASSWithAttachmentEdits(
		ctx, srcPath, trackID, assPath, dstPath,
		all, removeTargets, replacements, metadataEdits,
		removeTrackTargets, trackMetadataEdits, trackAdditions, opts...,
	)
}

// ReplaceASSWithAttachments performs the same same-slot ASS replacement and,
// in the same remux pass, appends arbitrary files as Matroska attachments.
// Existing attachments are preserved. Name collisions are resolved
// deterministically without overwriting the source attachment.
func ReplaceASSWithAttachments(
	ctx context.Context,
	srcPath string,
	trackID uint64,
	assPath, dstPath string,
	attachmentPaths []string,
	opts ...mkv.Options,
) error {
	return ReplaceASSWithAttachmentEdits(
		ctx, srcPath, trackID, assPath, dstPath,
		attachmentPaths, nil, nil, nil, nil, nil, nil, opts...,
	)
}

type AttachmentReplacement struct {
	Target string
	Path   string
}

type AttachmentMetadataEdit struct {
	Target      string
	Name        string
	Description string
}

type TrackMetadataEdit struct {
	Target    string
	Name      string
	Language  string
	IsDefault bool
	IsForced  bool
}

const (
	trackAdditionSourceMatroska    = "matroska"
	trackAdditionSourceASS         = "ass"
	trackAdditionSourcePacketAudio = "packet-audio"
)

type TrackAddition struct {
	SourceKind     string
	SourcePath     string
	SourceTrackID  uint64
	SourceTrackUID uint64
	SourceSHA256   string
	SourceCodec    string
	SampleRate     uint32
	Channels       uint8
	Name           string
	Language       string
	IsDefault      bool
	IsForced       bool
}

func ReplaceASSWithAttachmentEdits(
	ctx context.Context,
	srcPath string,
	trackID uint64,
	assPath, dstPath string,
	attachmentPaths, removeTargets []string,
	replacements []AttachmentReplacement,
	metadataEdits []AttachmentMetadataEdit,
	removeTrackTargets []string,
	trackMetadataEdits []TrackMetadataEdit,
	trackAdditions []TrackAddition,
	opts ...mkv.Options,
) (err error) {
	ass, err := subtitle.ParseASS(assPath)
	if err != nil {
		return fmt.Errorf("parse ASS: %w", err)
	}
	if len(ass.Events) == 0 {
		return fmt.Errorf("ASS file has no dialogue events")
	}

	fs := mkv.FSFrom(opts)
	c, err := reader.OpenWithFS(ctx, srcPath, fs, reader.WithoutAttachmentData())
	if err != nil {
		return err
	}
	updatedAttachments, err := planAttachmentEdits(
		c.Attachments,
		attachmentPaths,
		removeTargets,
		replacements,
		metadataEdits,
	)
	if err != nil {
		return err
	}
	c.Attachments = updatedAttachments

	baseSourceTracks := append([]mkv.Track(nil), c.Tracks...)
	tracks, removedTrackIDs, removedTrackUIDs, err := planTrackEdits(
		baseSourceTracks,
		removeTrackTargets,
		trackMetadataEdits,
	)
	if err != nil {
		return err
	}
	addedTracks, addedSources, addedPacketSources, addedExtraBlocks, addedTags, addedDurationMs, err := planTrackAdditions(
		ctx,
		baseSourceTracks,
		trackAdditions,
		fs,
	)
	if err != nil {
		return err
	}
	tracks = append(tracks, addedTracks...)
	c.Tags = append(filterTagsForRemovedTrackUIDs(c.Tags, removedTrackUIDs), addedTags...)
	c.Tracks = tracks

	targetIndex := -1
	for i := range tracks {
		if tracks[i].ID == trackID {
			if tracks[i].Type != mkv.SubtitleTrack || (tracks[i].Codec != "ass" && tracks[i].Codec != "ssa") {
				return fmt.Errorf("track %d is %s/%s, not ASS/SSA", trackID, tracks[i].Type, tracks[i].Codec)
			}
			targetIndex = i
			break
		}
	}
	if targetIndex < 0 {
		return fmt.Errorf("ASS/SSA track %d not found", trackID)
	}

	replacement := tracks[targetIndex]
	replacement.Codec = "ass"
	replacement.CodecPrivate = []byte(ass.Header)
	// The new payload is written uncompressed. Carrying a source track's
	// ContentCompression declaration would make readers decompress bytes that
	// were never compressed.
	replacement.Compression = mkv.CompressionNone
	replacement.HeaderStripping = nil
	tracks[targetIndex] = replacement

	subBlocks := make([]mkv.Block, len(ass.Events))
	for i, ev := range ass.Events {
		var dur int64
		if ev.EndMs > ev.StartMs {
			dur = ev.EndMs - ev.StartMs
		}
		subBlocks[i] = mkv.Block{
			TrackNumber: trackID,
			Timecode: ev.StartMs,
			Duration: dur,
			Data: []byte(fmt.Sprintf("%d,0,%s", i, ev.Fields)),
		}
	}

	allExtraBlocks := append(append([]mkv.Block(nil), subBlocks...), addedExtraBlocks...)
	sort.SliceStable(allExtraBlocks, func(i, j int) bool {
		return allExtraBlocks[i].Timecode < allExtraBlocks[j].Timecode
	})

	// Source blocks for the target track are omitted; replacement blocks are
	// injected under the exact same TrackNumber.
	remap := identityRemap(baseSourceTracks)
	delete(remap, trackID)
	for removedID := range removedTrackIDs {
		delete(remap, removedID)
	}

	// Content hashes/statistics describe payload bytes. Preserve ordinary tags,
	// but recompute derived families across the complete merged output.
	plan := planContentTags(c.Tags)

	out, err := fs.DoCreate(dstPath)
	if err != nil {
		return err
	}
	defer closeWithErr(out, &err)

	mw := writer.NewMKVWriter(out)
	mw.SetAttachmentSource(attachmentSource(fs))
	if err := mw.WriteStart(); err != nil {
		return err
	}

	meta, durationMs := metaForMergedSubs(c, allExtraBlocks)
	if addedDurationMs > durationMs {
		durationMs = addedDurationMs
		meta = metaForNewDuration(&meta)
	}
	meta.Info.SegmentUID = derivedSegmentUID(&c.Info, srcPath, "replace-ass")
	meta.Tags = nil
	if err := mw.WriteMetadata(&meta, tracks, durationMs); err != nil {
		return err
	}
	if err := mw.ReserveTags(plan.upperBoundTags(tracks)); err != nil {
		return err
	}

	sources := []mergeSource{{
		path: srcPath,
		scale: c.Info.TimecodeScale,
		remap: remap,
	}}
	sources = append(sources, addedSources...)
	stats, digests, err := streamMergeWithExtraBlocks(
		ctx,
		mw,
		c.Info.TimecodeScale,
		fs,
		sources,
		addedPacketSources,
		allExtraBlocks,
		mkv.ProgressFrom(opts),
		plan.wantHashes,
	)
	if err != nil {
		return err
	}

	if err := mw.WriteReservedTags(plan.tagsForOutput(tracks, digests, stats)); err != nil {
		return err
	}
	return mw.Finalize()
}

// AddAttachments rewrites an MKV once and appends arbitrary files as Matroska
// attachments without changing any track payload. It is the attachment-only
// path used when no ASS track edit is part of the user's mutation plan.
func AddAttachments(
	ctx context.Context,
	srcPath, dstPath string,
	attachmentPaths []string,
	opts ...mkv.Options,
) error {
	return EditAttachments(ctx, srcPath, dstPath, attachmentPaths, nil, nil, nil, opts...)
}

func EditAttachments(
	ctx context.Context,
	srcPath, dstPath string,
	attachmentPaths, removeTargets []string,
	replacements []AttachmentReplacement,
	metadataEdits []AttachmentMetadataEdit,
	opts ...mkv.Options,
) error {
	return EditContainerResources(
		ctx,
		srcPath,
		dstPath,
		attachmentPaths,
		removeTargets,
		replacements,
		metadataEdits,
		nil,
		nil,
		nil,
		opts...,
	)
}

func EditContainerResources(
	ctx context.Context,
	srcPath, dstPath string,
	attachmentPaths, removeTargets []string,
	replacements []AttachmentReplacement,
	metadataEdits []AttachmentMetadataEdit,
	removeTrackTargets []string,
	trackMetadataEdits []TrackMetadataEdit,
	trackAdditions []TrackAddition,
	opts ...mkv.Options,
) (err error) {
	if len(attachmentPaths) == 0 &&
		len(removeTargets) == 0 &&
		len(replacements) == 0 &&
		len(metadataEdits) == 0 &&
		len(removeTrackTargets) == 0 &&
		len(trackMetadataEdits) == 0 &&
		len(trackAdditions) == 0 {
		return fmt.Errorf("no container edits selected")
	}

	fs := mkv.FSFrom(opts)
	probe, err := reader.OpenWithFS(ctx, srcPath, fs, reader.WithoutAttachmentData())
	if err != nil {
		return err
	}
	updatedAttachments, err := planAttachmentEdits(
		probe.Attachments,
		attachmentPaths,
		removeTargets,
		replacements,
		metadataEdits,
	)
	if err != nil {
		return err
	}
	addedTracks, addedSources, addedPacketSources, addedExtraBlocks, addedTags, addedDurationMs, err := planTrackAdditions(
		ctx,
		probe.Tracks,
		trackAdditions,
		fs,
	)
	if err != nil {
		return err
	}
	updatedTracks, removedTrackIDs, removedTrackUIDs, err := planTrackEditsInternal(
		probe.Tracks,
		removeTrackTargets,
		trackMetadataEdits,
		len(addedTracks) > 0,
	)
	if err != nil {
		return err
	}
	outputTracks := append(append([]mkv.Track(nil), updatedTracks...), addedTracks...)

	// Metadata-only track edits and attachment edits can use mkvgo's fast
	// metadata rewrite. Structural removal/addition streams clusters so the
	// data plane matches the declared TrackEntries.
	if len(removedTrackIDs) == 0 && len(addedTracks) == 0 {
		return EditMetadata(ctx, srcPath, dstPath, func(c *mkv.Container) {
			c.Attachments = updatedAttachments
			c.Tracks = outputTracks
		}, opts...)
	}

	meta := *probe
	meta.Attachments = updatedAttachments
	meta.Tracks = outputTracks
	meta.Tags = append(filterTagsForRemovedTrackUIDs(probe.Tags, removedTrackUIDs), addedTags...)
	if addedDurationMs > meta.DurationMs {
		meta.DurationMs = addedDurationMs
		meta = metaForNewDuration(&meta)
		meta.DurationMs = addedDurationMs
	}
	meta.Info.SegmentUID = derivedSegmentUID(&probe.Info, srcPath, "edit-container-tracks")

	out, err := fs.DoCreate(dstPath)
	if err != nil {
		return err
	}
	defer closeWithErr(out, &err)

	mw := writer.NewMKVWriter(out)
	mw.SetAttachmentSource(attachmentSource(fs))
	if err := mw.WriteStart(); err != nil {
		return err
	}
	if err := mw.WriteMetadata(&meta, outputTracks, maxInt64(probe.DurationMs, addedDurationMs)); err != nil {
		return err
	}
	remap := identityRemap(probe.Tracks)
	for removedID := range removedTrackIDs {
		delete(remap, removedID)
	}
	sources := []mergeSource{{
		path: srcPath,
		scale: probe.Info.TimecodeScale,
		remap: remap,
	}}
	sources = append(sources, addedSources...)
	if len(addedExtraBlocks) > 0 || len(addedPacketSources) > 0 {
		if _, _, err := streamMergeWithExtraBlocks(
			ctx,
			mw,
			probe.Info.TimecodeScale,
			fs,
			sources,
			addedPacketSources,
			addedExtraBlocks,
			mkv.ProgressFrom(opts),
			false,
		); err != nil {
			return err
		}
	} else if _, err := streamMergeToWriter(
		ctx,
		mw,
		probe.Info.TimecodeScale,
		fs,
		sources,
		mkv.ProgressFrom(opts),
	); err != nil {
		return err
	}
	return mw.Finalize()
}

func planAttachmentEdits(
	existing []mkv.Attachment,
	addPaths, removeTargets []string,
	replacements []AttachmentReplacement,
	metadataEdits []AttachmentMetadataEdit,
) ([]mkv.Attachment, error) {
	removeIndexes := make(map[int]struct{}, len(removeTargets))
	replaceByIndex := make(map[int]mkv.Attachment, len(replacements))
	metadataByIndex := make(map[int]mkv.Attachment, len(metadataEdits))

	for _, target := range removeTargets {
		index, err := resolveAttachmentIndex(existing, target)
		if err != nil {
			return nil, err
		}
		removeIndexes[index] = struct{}{}
	}

	for _, replacement := range replacements {
		index, err := resolveAttachmentIndex(existing, replacement.Target)
		if err != nil {
			return nil, err
		}
		if _, removing := removeIndexes[index]; removing {
			return nil, fmt.Errorf("attachment %q cannot be removed and replaced in the same edit", replacement.Target)
		}
		if _, duplicate := replaceByIndex[index]; duplicate {
			return nil, fmt.Errorf("attachment %q has more than one replacement", replacement.Target)
		}
		att, err := fileAttachment(replacement.Path, existing[index].ID)
		if err != nil {
			return nil, err
		}
		att.Description = existing[index].Description
		for otherIndex, other := range existing {
			if otherIndex == index {
				continue
			}
			if _, removing := removeIndexes[otherIndex]; removing {
				continue
			}
			if strings.EqualFold(other.Name, att.Name) {
				return nil, fmt.Errorf(
					"replacement attachment name %q conflicts with existing attachment",
					att.Name,
				)
			}
		}
		replaceByIndex[index] = att
	}

	for _, metadata := range metadataEdits {
		index, err := resolveAttachmentIndex(existing, metadata.Target)
		if err != nil {
			return nil, err
		}
		if _, removing := removeIndexes[index]; removing {
			return nil, fmt.Errorf("attachment %q cannot be removed and metadata-edited in the same edit", metadata.Target)
		}
		if _, replacing := replaceByIndex[index]; replacing {
			return nil, fmt.Errorf("attachment %q cannot be replaced and metadata-edited in the same edit", metadata.Target)
		}
		if _, duplicate := metadataByIndex[index]; duplicate {
			return nil, fmt.Errorf("attachment %q has more than one metadata edit", metadata.Target)
		}
		name := strings.TrimSpace(metadata.Name)
		if name == "" {
			return nil, fmt.Errorf("attachment %q metadata edit has empty name", metadata.Target)
		}
		if filepath.Base(name) != name || strings.ContainsAny(name, "/\\") {
			return nil, fmt.Errorf("attachment name %q must be a file name, not a path", name)
		}
		att := existing[index]
		att.Name = name
		att.Description = metadata.Description
		metadataByIndex[index] = att
	}

	updated := make([]mkv.Attachment, 0, len(existing)-len(removeIndexes)+len(addPaths))
	for index, att := range existing {
		if _, removing := removeIndexes[index]; removing {
			continue
		}
		if replacement, ok := replaceByIndex[index]; ok {
			updated = append(updated, replacement)
		} else if metadata, ok := metadataByIndex[index]; ok {
			updated = append(updated, metadata)
		} else {
			updated = append(updated, att)
		}
	}
	if err := validateUniqueAttachmentNames(updated); err != nil {
		return nil, err
	}

	var nextIDFloor uint64 = 1
	for _, att := range existing {
		if att.ID >= nextIDFloor {
			nextIDFloor = att.ID + 1
		}
	}
	additions, err := buildFileAttachmentsFrom(updated, addPaths, nextIDFloor)
	if err != nil {
		return nil, err
	}
	return append(updated, additions...), nil
}

func validateUniqueAttachmentNames(attachments []mkv.Attachment) error {
	seen := make(map[string]string, len(attachments))
	for _, att := range attachments {
		key := strings.ToLower(att.Name)
		if prior, exists := seen[key]; exists {
			return fmt.Errorf("attachment name %q conflicts with %q", att.Name, prior)
		}
		seen[key] = att.Name
	}
	return nil
}

func ExtractAttachmentTarget(
	ctx context.Context,
	srcPath, target, outPath string,
	opts ...mkv.Options,
) (err error) {
	fs := mkv.FSFrom(opts)
	probe, err := reader.OpenWithFS(ctx, srcPath, fs, reader.WithoutAttachmentData())
	if err != nil {
		return err
	}
	index, err := resolveAttachmentIndex(probe.Attachments, target)
	if err != nil {
		return err
	}
	att := probe.Attachments[index]
	src, err := attachmentSource(fs)(&att)
	if err != nil {
		return err
	}
	if closer, ok := src.(io.Closer); ok {
		defer closer.Close()
	}
	out, err := fs.DoCreate(outPath)
	if err != nil {
		return err
	}
	defer closeWithErr(out, &err)
	if att.Size <= 0 {
		return nil
	}
	_, err = io.CopyN(out, src, att.Size)
	return err
}

func planTrackAdditions(
	ctx context.Context,
	existing []mkv.Track,
	additions []TrackAddition,
	fs *mkv.FS,
) ([]mkv.Track, []mergeSource, []packetMergeSource, []mkv.Block, []mkv.Tag, int64, error) {
	if len(additions) == 0 {
		return nil, nil, nil, nil, nil, 0, nil
	}

	var nextID uint64 = 1
	var nextUID uint64 = 1
	for _, track := range existing {
		if track.ID >= nextID {
			if track.ID == ^uint64(0) {
				return nil, nil, nil, nil, nil, 0, fmt.Errorf("cannot allocate a new TrackNumber")
			}
			nextID = track.ID + 1
		}
		uid := track.UID
		if uid == 0 {
			uid = track.ID
		}
		if uid >= nextUID {
			if uid == ^uint64(0) {
				return nil, nil, nil, nil, nil, 0, fmt.Errorf("cannot allocate a new TrackUID")
			}
			nextUID = uid + 1
		}
	}

	out := make([]mkv.Track, 0, len(additions))
	sourcesByPath := make(map[string]*mergeSource)
	order := make([]string, 0, len(additions))
	var packetSources []packetMergeSource
	var extraBlocks []mkv.Block
	var tags []mkv.Tag
	var durationMs int64
	seen := make(map[string]struct{}, len(additions))

	for _, addition := range additions {
		if addition.SourcePath == "" {
			return nil, nil, nil, nil, nil, 0, fmt.Errorf("invalid track addition source")
		}
		kind := addition.SourceKind
		if kind == "" {
			kind = trackAdditionSourceMatroska
		}
		var key string
		switch kind {
		case trackAdditionSourceMatroska:
			if addition.SourceTrackID == 0 {
				return nil, nil, nil, nil, nil, 0, fmt.Errorf("invalid Matroska track addition source")
			}
			key = kind + "\x00" + addition.SourcePath + "\x00" + strconv.FormatUint(addition.SourceTrackID, 10)
		case trackAdditionSourceASS, trackAdditionSourcePacketAudio:
			key = kind + "\x00" + addition.SourcePath
		default:
			return nil, nil, nil, nil, nil, 0, fmt.Errorf("unsupported track addition source kind %q", kind)
		}
		if _, duplicate := seen[key]; duplicate {
			return nil, nil, nil, nil, nil, 0, fmt.Errorf(
				"track source %s was added more than once",
				filepath.Base(addition.SourcePath),
			)
		}
		seen[key] = struct{}{}

		switch kind {
		case trackAdditionSourceMatroska:
			source, err := reader.OpenWithFS(ctx, addition.SourcePath, fs, reader.WithoutAttachmentData())
			if err != nil {
				return nil, nil, nil, nil, nil, 0, fmt.Errorf("open track source %s: %w", filepath.Base(addition.SourcePath), err)
			}
			var srcTrack *mkv.Track
			for i := range source.Tracks {
				if source.Tracks[i].ID == addition.SourceTrackID {
					srcTrack = &source.Tracks[i]
					break
				}
			}
			if srcTrack == nil {
				return nil, nil, nil, nil, nil, 0, fmt.Errorf(
					"track %d not found in %s",
					addition.SourceTrackID,
					filepath.Base(addition.SourcePath),
				)
			}
			if addition.SourceTrackUID != 0 && srcTrack.UID != addition.SourceTrackUID {
				return nil, nil, nil, nil, nil, 0, fmt.Errorf(
					"track %d in %s changed identity: expected TrackUID %d, got %d",
					addition.SourceTrackID,
					filepath.Base(addition.SourcePath),
					addition.SourceTrackUID,
					srcTrack.UID,
				)
			}
			if srcTrack.Type != mkv.VideoTrack &&
				srcTrack.Type != mkv.AudioTrack &&
				srcTrack.Type != mkv.SubtitleTrack {
				return nil, nil, nil, nil, nil, 0, fmt.Errorf(
					"track %d in %s has unsupported type %s",
					addition.SourceTrackID,
					filepath.Base(addition.SourcePath),
					srcTrack.Type,
				)
			}

			track := *srcTrack
			track.ID = nextID
			track.UID = nextUID
			track.Name = addition.Name
			track.Language = addition.Language
			track.IsDefault = addition.IsDefault
			track.IsForced = addition.IsForced
			out = append(out, track)

			sourceUID := srcTrack.UID
			if sourceUID == 0 {
				sourceUID = srcTrack.ID
			}
			for _, tag := range source.Tags {
				if tag.TargetID != sourceUID {
					continue
				}
				tag.TargetID = nextUID
				tags = append(tags, tag)
			}

			if source.DurationMs > durationMs {
				durationMs = source.DurationMs
			}
			state := sourcesByPath[addition.SourcePath]
			if state == nil {
				state = &mergeSource{
					path: addition.SourcePath,
					scale: source.Info.TimecodeScale,
					remap: make(map[uint64]uint64),
				}
				sourcesByPath[addition.SourcePath] = state
				order = append(order, addition.SourcePath)
			}
			state.remap[addition.SourceTrackID] = nextID

		case trackAdditionSourceASS:
			raw, err := os.ReadFile(addition.SourcePath)
			if err != nil {
				return nil, nil, nil, nil, nil, 0, fmt.Errorf("read ASS source %s: %w", filepath.Base(addition.SourcePath), err)
			}
			if addition.SourceSHA256 != "" {
				sum := sha256.Sum256(raw)
				actual := hex.EncodeToString(sum[:])
				if !strings.EqualFold(actual, addition.SourceSHA256) {
					return nil, nil, nil, nil, nil, 0, fmt.Errorf(
						"ASS source %s changed identity: expected sha256 %s, got %s",
						filepath.Base(addition.SourcePath),
						addition.SourceSHA256,
						actual,
					)
				}
			}
			ass, err := subtitle.ParseASS(addition.SourcePath)
			if err != nil {
				return nil, nil, nil, nil, nil, 0, fmt.Errorf("parse ASS source %s: %w", filepath.Base(addition.SourcePath), err)
			}
			if len(ass.Events) == 0 {
				return nil, nil, nil, nil, nil, 0, fmt.Errorf("ASS source %s has no dialogue events", filepath.Base(addition.SourcePath))
			}
			track := mkv.Track{
				ID: nextID,
				UID: nextUID,
				Type: mkv.SubtitleTrack,
				Codec: "ass",
				CodecPrivate: []byte(ass.Header),
				Name: addition.Name,
				Language: addition.Language,
				IsDefault: addition.IsDefault,
				IsForced: addition.IsForced,
			}
			out = append(out, track)
			for i, ev := range ass.Events {
				var dur int64
				if ev.EndMs > ev.StartMs {
					dur = ev.EndMs - ev.StartMs
				}
				extraBlocks = append(extraBlocks, mkv.Block{
					TrackNumber: nextID,
					Timecode: ev.StartMs,
					Duration: dur,
					Data: []byte(fmt.Sprintf("%d,0,%s", i, ev.Fields)),
				})
				if ev.EndMs > durationMs {
					durationMs = ev.EndMs
				}
			}

		case trackAdditionSourcePacketAudio:
			if !strings.EqualFold(addition.SourceCodec, "A_MPEG/L3") {
				return nil, nil, nil, nil, nil, 0, fmt.Errorf(
					"packet audio source %s has unsupported codec %q",
					filepath.Base(addition.SourcePath),
					addition.SourceCodec,
				)
			}
			if addition.SampleRate == 0 || addition.Channels == 0 {
				return nil, nil, nil, nil, nil, 0, fmt.Errorf("packet audio source has invalid audio metadata")
			}
			if addition.SourceSHA256 == "" {
				return nil, nil, nil, nil, nil, 0, fmt.Errorf("packet audio source is missing sha256 evidence")
			}
			if err := verifyFileSHA256(fs, addition.SourcePath, addition.SourceSHA256); err != nil {
				return nil, nil, nil, nil, nil, 0, err
			}
			header, err := readPacketBundleHeader(fs, addition.SourcePath)
			if err != nil {
				return nil, nil, nil, nil, nil, 0, err
			}
			sampleRate := float64(addition.SampleRate)
			channels := addition.Channels
			track := mkv.Track{
				ID: nextID,
				UID: nextUID,
				Type: mkv.AudioTrack,
				Codec: "A_MPEG/L3",
				SampleRate: &sampleRate,
				Channels: &channels,
				Name: addition.Name,
				Language: addition.Language,
				IsDefault: addition.IsDefault,
				IsForced: addition.IsForced,
			}
			out = append(out, track)
			packetSources = append(packetSources, packetMergeSource{
				path: addition.SourcePath,
				trackID: nextID,
				expectedPackets: header.packetCount,
			})
			packetDurationMs := (header.durationUs + 999) / 1000
			if packetDurationMs > durationMs {
				durationMs = packetDurationMs
			}
		}

		if nextID == ^uint64(0) || nextUID == ^uint64(0) {
			if len(out) < len(additions) {
				return nil, nil, nil, nil, nil, 0, fmt.Errorf("cannot allocate another track identity")
			}
		} else {
			nextID++
			nextUID++
		}
	}

	sources := make([]mergeSource, 0, len(order))
	for _, path := range order {
		sources = append(sources, *sourcesByPath[path])
	}
	sort.SliceStable(extraBlocks, func(i, j int) bool {
		return extraBlocks[i].Timecode < extraBlocks[j].Timecode
	})
	return out, sources, packetSources, extraBlocks, tags, durationMs, nil
}

const (
	packetBundleMagic      = "AWPKT001"
	packetBundleHeaderSize = int64(24)
	maxPacketPayloadBytes  = uint32(4 * 1024 * 1024)
	maxPacketCount         = uint64(100_000_000)
)

type packetBundleHeader struct {
	durationUs  int64
	packetCount uint64
}

type packetMergeSource struct {
	path            string
	trackID         uint64
	expectedPackets uint64
}

type packetBundleReader struct {
	r         io.Reader
	trackID   uint64
	remaining uint64
	position  int64
	previous  int64
	started   bool
	checkedEnd bool
}

func readPacketBundleHeaderFrom(r io.Reader) (packetBundleHeader, error) {
	magic := make([]byte, len(packetBundleMagic))
	if _, err := io.ReadFull(r, magic); err != nil {
		return packetBundleHeader{}, fmt.Errorf("read packet bundle magic: %w", err)
	}
	if string(magic) != packetBundleMagic {
		return packetBundleHeader{}, fmt.Errorf("invalid packet bundle magic %q", string(magic))
	}
	var durationUs int64
	if err := binary.Read(r, binary.BigEndian, &durationUs); err != nil {
		return packetBundleHeader{}, fmt.Errorf("read packet bundle duration: %w", err)
	}
	var packetCount uint64
	if err := binary.Read(r, binary.BigEndian, &packetCount); err != nil {
		return packetBundleHeader{}, fmt.Errorf("read packet bundle count: %w", err)
	}
	if durationUs <= 0 {
		return packetBundleHeader{}, fmt.Errorf("packet bundle duration must be positive")
	}
	if packetCount == 0 || packetCount > maxPacketCount {
		return packetBundleHeader{}, fmt.Errorf("packet bundle count %d is out of bounds", packetCount)
	}
	return packetBundleHeader{durationUs: durationUs, packetCount: packetCount}, nil
}

func readPacketBundleHeader(fs *mkv.FS, path string) (packetBundleHeader, error) {
	f, err := fs.DoOpen(path)
	if err != nil {
		return packetBundleHeader{}, err
	}
	defer f.Close()
	return readPacketBundleHeaderFrom(f)
}

func verifyFileSHA256(fs *mkv.FS, path, expected string) error {
	f, err := fs.DoOpen(path)
	if err != nil {
		return err
	}
	defer f.Close()
	h := sha256.New()
	if _, err := io.Copy(h, f); err != nil {
		return fmt.Errorf("hash packet source %s: %w", filepath.Base(path), err)
	}
	actual := hex.EncodeToString(h.Sum(nil))
	if !strings.EqualFold(actual, expected) {
		return fmt.Errorf(
			"packet source %s changed identity: expected sha256 %s, got %s",
			filepath.Base(path),
			expected,
			actual,
		)
	}
	return nil
}

func newPacketBundleReader(r io.Reader, trackID uint64, expectedPackets uint64) (*packetBundleReader, error) {
	header, err := readPacketBundleHeaderFrom(r)
	if err != nil {
		return nil, err
	}
	if expectedPackets != 0 && header.packetCount != expectedPackets {
		return nil, fmt.Errorf(
			"packet bundle count changed: expected %d, got %d",
			expectedPackets,
			header.packetCount,
		)
	}
	return &packetBundleReader{
		r: r,
		trackID: trackID,
		remaining: header.packetCount,
		position: packetBundleHeaderSize,
	}, nil
}

func (p *packetBundleReader) Next() (mkv.Block, error) {
	if p.remaining == 0 {
		if !p.checkedEnd {
			p.checkedEnd = true
			var one [1]byte
			n, err := p.r.Read(one[:])
			if n != 0 || (err != nil && err != io.EOF) {
				if err != nil {
					return mkv.Block{}, err
				}
				return mkv.Block{}, fmt.Errorf("packet bundle has trailing bytes")
			}
		}
		return mkv.Block{}, io.EOF
	}

	var ptsUs int64
	if err := binary.Read(p.r, binary.BigEndian, &ptsUs); err != nil {
		return mkv.Block{}, fmt.Errorf("read packet timestamp: %w", err)
	}
	var flags uint32
	if err := binary.Read(p.r, binary.BigEndian, &flags); err != nil {
		return mkv.Block{}, fmt.Errorf("read packet flags: %w", err)
	}
	var size uint32
	if err := binary.Read(p.r, binary.BigEndian, &size); err != nil {
		return mkv.Block{}, fmt.Errorf("read packet size: %w", err)
	}
	if ptsUs < 0 {
		return mkv.Block{}, fmt.Errorf("packet timestamp is negative")
	}
	if p.started && ptsUs < p.previous {
		return mkv.Block{}, fmt.Errorf("packet timestamps are not monotonic")
	}
	if size == 0 || size > maxPacketPayloadBytes {
		return mkv.Block{}, fmt.Errorf("packet payload size %d is out of bounds", size)
	}
	data := make([]byte, int(size))
	if _, err := io.ReadFull(p.r, data); err != nil {
		return mkv.Block{}, fmt.Errorf("read packet payload: %w", err)
	}
	p.position += 16 + int64(size)
	p.previous = ptsUs
	p.started = true
	p.remaining--
	_ = flags // reserved for future video adapters; MP3 audio blocks are seekable.
	return mkv.Block{
		TrackNumber: p.trackID,
		Timecode: ptsUs / 1000,
		Keyframe: true,
		Data: data,
	}, nil
}

type TrackContentDigest struct {
	SHA256      string
	PacketCount uint64
	FirstMs     int64
	LastMs      int64
	Seen        bool
}

func DigestTrackContent(
	ctx context.Context,
	srcPath string,
	trackID uint64,
	opts ...mkv.Options,
) (TrackContentDigest, error) {
	fs := mkv.FSFrom(opts)
	probe, err := reader.OpenWithFS(ctx, srcPath, fs, reader.WithoutAttachmentData())
	if err != nil {
		return TrackContentDigest{}, err
	}
	var exists bool
	for _, track := range probe.Tracks {
		if track.ID == trackID {
			exists = true
			break
		}
	}
	if !exists {
		return TrackContentDigest{}, fmt.Errorf("track %d not found", trackID)
	}

	f, err := fs.DoOpen(srcPath)
	if err != nil {
		return TrackContentDigest{}, err
	}
	defer f.Close()
	br, err := reader.NewBlockReader(f, probe.Info.TimecodeScale)
	if err != nil {
		return TrackContentDigest{}, err
	}
	h := sha256.New()
	var count uint64
	var firstMs, lastMs int64
	var seen bool
	var header [12]byte
	for {
		if ctx.Err() != nil {
			return TrackContentDigest{}, ctx.Err()
		}
		block, err := br.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return TrackContentDigest{}, err
		}
		if block.TrackNumber != trackID {
			continue
		}
		if block.Timecode < 0 {
			return TrackContentDigest{}, fmt.Errorf("track %d has negative timecode", trackID)
		}
		binary.BigEndian.PutUint64(header[:8], uint64(block.Timecode))
		binary.BigEndian.PutUint32(header[8:], uint32(len(block.Data)))
		_, _ = h.Write(header[:])
		_, _ = h.Write(block.Data)
		if !seen {
			firstMs = block.Timecode
			seen = true
		}
		lastMs = block.Timecode
		count++
	}
	if !seen {
		return TrackContentDigest{}, fmt.Errorf("track %d has no blocks", trackID)
	}
	return TrackContentDigest{
		SHA256: hex.EncodeToString(h.Sum(nil)),
		PacketCount: count,
		FirstMs: firstMs,
		LastMs: lastMs,
		Seen: true,
	}, nil
}

func maxInt64(a, b int64) int64 {
	if a > b {
		return a
	}
	return b
}

// streamMergeWithExtraBlocks performs the same bounded k-way merge as
// streamMergeToWriter while also treating an in-memory subtitle replacement as
// another time-sorted source. This keeps ASS replacement + external Track
// addition in a single remux.
func streamMergeWithExtraBlocks(
	ctx context.Context,
	mw *writer.MKVWriter,
	outScale int64,
	fs *mkv.FS,
	sources []mergeSource,
	packetSources []packetMergeSource,
	extra []mkv.Block,
	progress mkv.ProgressFunc,
	wantHashes bool,
) (map[uint64]*trackStats, map[uint64]hash.Hash, error) {
	type state struct {
		next  func() (mkv.Block, error)
		close func() error
		head  mkv.Block
		ok    bool
	}
	states := make([]*state, 0, len(sources)+len(packetSources))
	defer func() {
		for _, state := range states {
			if state != nil && state.close != nil {
				_ = state.close()
			}
		}
	}()

	allPaths := make([]string, 0, len(sources)+len(packetSources))
	for _, source := range sources {
		allPaths = append(allPaths, source.path)
	}
	for _, source := range packetSources {
		allPaths = append(allPaths, source.path)
	}

	var progTotal int64
	var progPositions []int64
	if progress != nil {
		progPositions = make([]int64, len(allPaths))
		for _, path := range allPaths {
			if stat, _ := fs.DoStat(path); stat != nil {
				progTotal += stat.Size()
			}
		}
	}
	reportProgress := func(index int, position int64) {
		if progress == nil || index < 0 || index >= len(progPositions) {
			return
		}
		progPositions[index] = position
		var sum int64
		for _, value := range progPositions {
			sum += value
		}
		progress(sum, progTotal)
	}

	for index, source := range sources {
		file, err := fs.DoOpen(source.path)
		if err != nil {
			return nil, nil, err
		}
		br, err := reader.NewBlockReader(file, source.scale)
		if err != nil {
			file.Close()
			return nil, nil, err
		}
		if progress != nil {
			i := index
			br.SetProgress(func(position, _ int64) {
				reportProgress(i, position)
			}, progTotal)
		}
		remap := source.remap
		states = append(states, &state{
			close: file.Close,
			next: func() (mkv.Block, error) {
				for {
					block, err := br.Next()
					if err != nil {
						return mkv.Block{}, err
					}
					newID, ok := remap[block.TrackNumber]
					if !ok {
						continue
					}
					block.TrackNumber = newID
					return block, nil
				}
			},
		})
	}

	for packetIndex, source := range packetSources {
		file, err := fs.DoOpen(source.path)
		if err != nil {
			return nil, nil, err
		}
		pr, err := newPacketBundleReader(file, source.trackID, source.expectedPackets)
		if err != nil {
			file.Close()
			return nil, nil, err
		}
		progressIndex := len(sources) + packetIndex
		states = append(states, &state{
			close: file.Close,
			next: func() (mkv.Block, error) {
				block, err := pr.Next()
				reportProgress(progressIndex, pr.position)
				return block, err
			},
		})
	}

	advance := func(index int) error {
		state := states[index]
		block, err := state.next()
		if err == io.EOF {
			state.ok = false
			return nil
		}
		if err != nil {
			return err
		}
		state.head, state.ok = block, true
		return nil
	}
	for index := range states {
		if err := advance(index); err != nil {
			return nil, nil, err
		}
	}

	stats := make(map[uint64]*trackStats)
	var digests map[uint64]hash.Hash
	if wantHashes {
		digests = make(map[uint64]hash.Hash)
	}
	record := func(block *mkv.Block) {
		stat := stats[block.TrackNumber]
		if stat == nil {
			stat = &trackStats{}
			stats[block.TrackNumber] = stat
		}
		stat.add(block)
		if digests != nil {
			h := digests[block.TrackNumber]
			if h == nil {
				h = sha256.New()
				digests[block.TrackNumber] = h
			}
			_, _ = h.Write(block.Data)
		}
	}

	var cluster []mkv.Block
	clusterTS := int64(-1)
	flush := func() error {
		if len(cluster) == 0 {
			return nil
		}
		err := mw.WriteClusterWithCues(clusterTS, outScale, cluster)
		cluster = cluster[:0]
		return err
	}

	extraIndex := 0
	for {
		if ctx.Err() != nil {
			return nil, nil, ctx.Err()
		}
		sourceIndex := -1
		for i, state := range states {
			if !state.ok {
				continue
			}
			if sourceIndex < 0 || state.head.Timecode < states[sourceIndex].head.Timecode {
				sourceIndex = i
			}
		}
		useExtra := extraIndex < len(extra) &&
			(sourceIndex < 0 || extra[extraIndex].Timecode <= states[sourceIndex].head.Timecode)
		if sourceIndex < 0 && !useExtra {
			break
		}

		var block mkv.Block
		if useExtra {
			block = extra[extraIndex]
			extraIndex++
		} else {
			block = states[sourceIndex].head
			if err := advance(sourceIndex); err != nil {
				return nil, nil, err
			}
		}
		record(&block)

		if clusterTS < 0 {
			clusterTS = block.Timecode
		}
		if block.Timecode-clusterTS >= clusterSpanMs(outScale) && len(cluster) > 0 {
			if err := flush(); err != nil {
				return nil, nil, err
			}
			clusterTS = block.Timecode
		}
		cluster = append(cluster, block)
	}
	if err := flush(); err != nil {
		return nil, nil, err
	}
	return stats, digests, nil
}

func planTrackEdits(
	existing []mkv.Track,
	removeTargets []string,
	metadataEdits []TrackMetadataEdit,
) ([]mkv.Track, map[uint64]struct{}, map[uint64]struct{}, error) {
	return planTrackEditsInternal(existing, removeTargets, metadataEdits, false)
}

func planTrackEditsInternal(
	existing []mkv.Track,
	removeTargets []string,
	metadataEdits []TrackMetadataEdit,
	allowEmpty bool,
) ([]mkv.Track, map[uint64]struct{}, map[uint64]struct{}, error) {
	removeIndexes := make(map[int]struct{}, len(removeTargets))
	metadataByIndex := make(map[int]mkv.Track, len(metadataEdits))

	for _, target := range removeTargets {
		index, err := resolveTrackIndex(existing, target)
		if err != nil {
			return nil, nil, nil, err
		}
		removeIndexes[index] = struct{}{}
	}
	if len(removeIndexes) >= len(existing) && !allowEmpty {
		return nil, nil, nil, fmt.Errorf("cannot remove all tracks")
	}

	for _, edit := range metadataEdits {
		index, err := resolveTrackIndex(existing, edit.Target)
		if err != nil {
			return nil, nil, nil, err
		}
		if _, removing := removeIndexes[index]; removing {
			return nil, nil, nil, fmt.Errorf(
				"track %q cannot be removed and metadata-edited in the same edit",
				edit.Target,
			)
		}
		if _, duplicate := metadataByIndex[index]; duplicate {
			return nil, nil, nil, fmt.Errorf("track %q has more than one metadata edit", edit.Target)
		}
		track := existing[index]
		track.Name = edit.Name
		track.Language = edit.Language
		track.IsDefault = edit.IsDefault
		track.IsForced = edit.IsForced
		metadataByIndex[index] = track
	}

	removedIDs := make(map[uint64]struct{}, len(removeIndexes))
	removedUIDs := make(map[uint64]struct{}, len(removeIndexes))
	out := make([]mkv.Track, 0, len(existing)-len(removeIndexes))
	for index, track := range existing {
		if _, removing := removeIndexes[index]; removing {
			removedIDs[track.ID] = struct{}{}
			uid := track.UID
			if uid == 0 {
				uid = track.ID
			}
			removedUIDs[uid] = struct{}{}
			continue
		}
		if edited, ok := metadataByIndex[index]; ok {
			out = append(out, edited)
		} else {
			out = append(out, track)
		}
	}
	return out, removedIDs, removedUIDs, nil
}

func resolveTrackIndex(existing []mkv.Track, target string) (int, error) {
	var matches []int
	switch {
	case strings.HasPrefix(target, "uid:"):
		uid, err := strconv.ParseUint(strings.TrimPrefix(target, "uid:"), 10, 64)
		if err != nil || uid == 0 {
			return -1, fmt.Errorf("invalid track UID target %q", target)
		}
		for index, track := range existing {
			if track.UID == uid {
				matches = append(matches, index)
			}
		}
	case strings.HasPrefix(target, "number:"):
		id, err := strconv.ParseUint(strings.TrimPrefix(target, "number:"), 10, 64)
		if err != nil || id == 0 {
			return -1, fmt.Errorf("invalid track number target %q", target)
		}
		for index, track := range existing {
			if track.ID == id {
				matches = append(matches, index)
			}
		}
	default:
		return -1, fmt.Errorf("track target %q must use uid:<id> or number:<id>", target)
	}
	if len(matches) == 0 {
		return -1, fmt.Errorf("no track matching %q", target)
	}
	if len(matches) > 1 {
		return -1, fmt.Errorf("track target %q is ambiguous", target)
	}
	return matches[0], nil
}

func filterTagsForRemovedTrackUIDs(tags []mkv.Tag, removedUIDs map[uint64]struct{}) []mkv.Tag {
	if len(removedUIDs) == 0 {
		return tags
	}
	out := make([]mkv.Tag, 0, len(tags))
	for _, tag := range tags {
		if tag.TargetID != 0 {
			if _, removed := removedUIDs[tag.TargetID]; removed {
				continue
			}
		}
		out = append(out, tag)
	}
	return out
}

func resolveAttachmentIndex(existing []mkv.Attachment, target string) (int, error) {
	id, idErr := strconv.ParseUint(target, 10, 64)
	matches := make([]int, 0, 1)
	for index, att := range existing {
		if (idErr == nil && att.ID == id) || att.Name == target {
			matches = append(matches, index)
		}
	}
	if len(matches) == 0 {
		return -1, fmt.Errorf("no attachment matching %q", target)
	}
	if len(matches) > 1 {
		return -1, fmt.Errorf("attachment target %q is ambiguous", target)
	}
	return matches[0], nil
}

func fileAttachment(path string, id uint64) (mkv.Attachment, error) {
	clean := filepath.Clean(path)
	data, err := os.ReadFile(clean)
	if err != nil {
		return mkv.Attachment{}, fmt.Errorf("read attachment %q: %w", filepath.Base(clean), err)
	}
	if len(data) == 0 {
		return mkv.Attachment{}, fmt.Errorf("attachment %q is empty", filepath.Base(clean))
	}
	name := filepath.Base(clean)
	return mkv.Attachment{
		ID:       id,
		Name:     name,
		MIMEType: attachmentMIME(name),
		Size:     int64(len(data)),
		Data:     data,
	}, nil
}

func buildFileAttachments(existing []mkv.Attachment, paths []string) ([]mkv.Attachment, error) {
	return buildFileAttachmentsFrom(existing, paths, 1)
}

func buildFileAttachmentsFrom(
	existing []mkv.Attachment,
	paths []string,
	nextIDFloor uint64,
) ([]mkv.Attachment, error) {
	if len(paths) == 0 {
		return nil, nil
	}

	existingNames := make(map[string]struct{}, len(existing)+len(paths))
	nextID := nextIDFloor
	if nextID == 0 {
		nextID = 1
	}
	for _, att := range existing {
		existingNames[strings.ToLower(att.Name)] = struct{}{}
		if att.ID >= nextID {
			nextID = att.ID + 1
		}
	}

	seenPaths := make(map[string]struct{}, len(paths))
	out := make([]mkv.Attachment, 0, len(paths))
	for _, path := range paths {
		clean := filepath.Clean(path)
		if _, ok := seenPaths[clean]; ok {
			continue
		}
		seenPaths[clean] = struct{}{}

		data, err := os.ReadFile(clean)
		if err != nil {
			return nil, fmt.Errorf("read attachment %q: %w", filepath.Base(clean), err)
		}
		if len(data) == 0 {
			return nil, fmt.Errorf("attachment %q is empty", filepath.Base(clean))
		}

		name := filepath.Base(clean)
		key := strings.ToLower(name)
		if _, exists := existingNames[key]; exists {
			sum := sha256.Sum256(data)
			digest := hex.EncodeToString(sum[:4])
			ext := filepath.Ext(name)
			stem := strings.TrimSuffix(name, ext)
			candidate := stem + "-asswb-" + digest + ext
			candidateKey := strings.ToLower(candidate)
			for suffix := 2; ; suffix++ {
				if _, taken := existingNames[candidateKey]; !taken {
					break
				}
				candidate = stem + "-asswb-" + digest + "-" + fmt.Sprint(suffix) + ext
				candidateKey = strings.ToLower(candidate)
			}
			name = candidate
			key = candidateKey
		}
		out = append(out, mkv.Attachment{
			ID:       nextID,
			Name:     name,
			MIMEType: attachmentMIME(name),
			Size:     int64(len(data)),
			Data:     data,
		})
		existingNames[key] = struct{}{}
		nextID++
	}
	return out, nil
}

func isFontAttachmentPath(path string) bool {
	switch strings.ToLower(filepath.Ext(path)) {
	case ".ttf", ".otf", ".ttc", ".otc":
		return true
	default:
		return false
	}
}

func attachmentMIME(name string) string {
	switch strings.ToLower(filepath.Ext(name)) {
	case ".ttf":
		return "font/ttf"
	case ".otf":
		return "font/otf"
	case ".ttc", ".otc":
		return "font/collection"
	case ".png":
		return "image/png"
	case ".jpg", ".jpeg":
		return "image/jpeg"
	case ".webp":
		return "image/webp"
	case ".gif":
		return "image/gif"
	case ".svg":
		return "image/svg+xml"
	case ".txt":
		return "text/plain"
	case ".md":
		return "text/markdown"
	case ".pdf":
		return "application/pdf"
	case ".json":
		return "application/json"
	case ".xml":
		return "application/xml"
	case ".zip":
		return "application/zip"
	default:
		return "application/octet-stream"
	}
}
