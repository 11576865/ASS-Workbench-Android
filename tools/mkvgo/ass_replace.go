package ops

import (
	"context"
	"crypto/sha256"
	"fmt"
	"os"
	"path/filepath"
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
	return ReplaceASSWithFonts(ctx, srcPath, trackID, assPath, dstPath, nil, opts...)
}

// ReplaceASSWithFonts performs the same same-slot ASS replacement and, in the
// same remux pass, appends selected TTF/OTF files as Matroska attachments.
// Existing attachments are preserved. Attachment names are never overwritten:
// a selected font whose generated attachment name already exists is skipped.
func ReplaceASSWithFonts(
	ctx context.Context,
	srcPath string,
	trackID uint64,
	assPath, dstPath string,
	fontPaths []string,
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
	if err := appendFontAttachments(c, fontPaths); err != nil {
		return err
	}

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

func appendFontAttachments(c *mkv.Container, fontPaths []string) error {
	if len(fontPaths) == 0 {
		return nil
	}

	existingNames := make(map[string]struct{}, len(c.Attachments))
	var nextID uint64 = 1
	for _, att := range c.Attachments {
		existingNames[strings.ToLower(att.Name)] = struct{}{}
		if att.ID >= nextID {
			nextID = att.ID + 1
		}
	}

	seenPaths := make(map[string]struct{}, len(fontPaths))
	for _, path := range fontPaths {
		clean := filepath.Clean(path)
		if _, ok := seenPaths[clean]; ok {
			continue
		}
		seenPaths[clean] = struct{}{}

		ext := strings.ToLower(filepath.Ext(clean))
		if ext != ".ttf" && ext != ".otf" {
			return fmt.Errorf("unsupported font attachment %q", filepath.Base(clean))
		}
		data, err := os.ReadFile(clean)
		if err != nil {
			return fmt.Errorf("read font attachment %q: %w", filepath.Base(clean), err)
		}
		if len(data) == 0 {
			return fmt.Errorf("font attachment %q is empty", filepath.Base(clean))
		}

		name := filepath.Base(clean)
		key := strings.ToLower(name)
		if _, exists := existingNames[key]; exists {
			continue
		}
		mime := "font/ttf"
		if ext == ".otf" {
			mime = "font/otf"
		}
		c.Attachments = append(c.Attachments, mkv.Attachment{
			ID:       nextID,
			Name:     name,
			MIMEType: mime,
			Size:     int64(len(data)),
			Data:     data,
		})
		existingNames[key] = struct{}{}
		nextID++
	}
	return nil
}
