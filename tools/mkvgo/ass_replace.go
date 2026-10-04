package ops

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"os"
	"path/filepath"
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
		fontPaths, attachmentPaths, nil, nil, opts...,
	)
}

func ReplaceASSWithFontsAndAttachmentEdits(
	ctx context.Context,
	srcPath string,
	trackID uint64,
	assPath, dstPath string,
	fontPaths, attachmentPaths, removeTargets []string,
	replacements []AttachmentReplacement,
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
		all, removeTargets, replacements, opts...,
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
		attachmentPaths, nil, nil, opts...,
	)
}

type AttachmentReplacement struct {
	Target string
	Path   string
}

func ReplaceASSWithAttachmentEdits(
	ctx context.Context,
	srcPath string,
	trackID uint64,
	assPath, dstPath string,
	attachmentPaths, removeTargets []string,
	replacements []AttachmentReplacement,
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
	)
	if err != nil {
		return err
	}
	c.Attachments = updatedAttachments

	targetIndex := -1
	for i := range c.Tracks {
		if c.Tracks[i].ID == trackID {
			if c.Tracks[i].Type != mkv.SubtitleTrack || (c.Tracks[i].Codec != "ass" && c.Tracks[i].Codec != "ssa") {
				return fmt.Errorf("track %d is %s/%s, not ASS/SSA", trackID, c.Tracks[i].Type, c.Tracks[i].Codec)
			}
			targetIndex = i
			break
		}
	}
	if targetIndex < 0 {
		return fmt.Errorf("ASS/SSA track %d not found", trackID)
	}

	tracks := append([]mkv.Track(nil), c.Tracks...)
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

	// Source blocks for the target track are omitted; replacement blocks are
	// injected under the exact same TrackNumber.
	remap := identityRemap(c.Tracks)
	delete(remap, trackID)

	// Content hashes/statistics describe payload bytes. Preserve ordinary tags,
	// but recompute these derived families when the source carried them.
	plan := planContentTags(c.Tags)
	digests, stats := plan.digestsFor(), plan.statsFor()
	if digests != nil {
		h := sha256.New()
		for _, b := range subBlocks {
			_, _ = h.Write(b.Data)
		}
		digests[trackID] = h
	}
	if stats != nil {
		s := &trackStats{}
		for i := range subBlocks {
			s.add(&subBlocks[i])
		}
		stats[trackID] = s
	}

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

	meta, durationMs := metaForMergedSubs(c, subBlocks)
	meta.Info.SegmentUID = derivedSegmentUID(&c.Info, srcPath, "replace-ass")
	meta.Tags = nil
	if err := mw.WriteMetadata(&meta, tracks, durationMs); err != nil {
		return err
	}
	if err := mw.ReserveTags(plan.upperBoundTags(tracks)); err != nil {
		return err
	}

	if err := streamToWriter(ctx, mw, srcPath, c.Info.TimecodeScale, fs, streamOpts{
		remap: remap,
		extraSubs: subBlocks,
		contentDigests: digests,
		contentStats: stats,
		progress: mkv.ProgressFrom(opts),
	}); err != nil {
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
	return EditAttachments(ctx, srcPath, dstPath, attachmentPaths, nil, nil, opts...)
}

func EditAttachments(
	ctx context.Context,
	srcPath, dstPath string,
	attachmentPaths, removeTargets []string,
	replacements []AttachmentReplacement,
	opts ...mkv.Options,
) error {
	if len(attachmentPaths) == 0 && len(removeTargets) == 0 && len(replacements) == 0 {
		return fmt.Errorf("no attachment edits selected")
	}
	fs := mkv.FSFrom(opts)
	probe, err := reader.OpenWithFS(ctx, srcPath, fs, reader.WithoutAttachmentData())
	if err != nil {
		return err
	}
	updated, err := planAttachmentEdits(
		probe.Attachments,
		attachmentPaths,
		removeTargets,
		replacements,
	)
	if err != nil {
		return err
	}
	return EditMetadata(ctx, srcPath, dstPath, func(c *mkv.Container) {
		c.Attachments = updated
	}, opts...)
}

func planAttachmentEdits(
	existing []mkv.Attachment,
	addPaths, removeTargets []string,
	replacements []AttachmentReplacement,
) ([]mkv.Attachment, error) {
	removeIndexes := make(map[int]struct{}, len(removeTargets))
	replaceByIndex := make(map[int]mkv.Attachment, len(replacements))

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

	updated := make([]mkv.Attachment, 0, len(existing)-len(removeIndexes)+len(addPaths))
	for index, att := range existing {
		if _, removing := removeIndexes[index]; removing {
			continue
		}
		if replacement, ok := replaceByIndex[index]; ok {
			updated = append(updated, replacement)
		} else {
			updated = append(updated, att)
		}
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
