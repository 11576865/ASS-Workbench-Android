package ops

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/gravity-zero/mkvgo/mkv"
	"github.com/gravity-zero/mkvgo/mkv/reader"
	"github.com/gravity-zero/mkvgo/mkv/writer"
)

func TestReplaceASSPreservesTrackIdentityAndContainerMetadata(t *testing.T) {
	dir := t.TempDir()
	src := filepath.Join(dir, "source.mkv")
	dst := filepath.Join(dir, "updated.mkv")
	assPath := filepath.Join(dir, "edited.ass")
	newFontPath := filepath.Join(dir, "AddedFont.otf")

	oldHeader := "[Script Info]\nScriptType: v4.00+\n\n[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\nStyle: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"
	newASS := oldHeader + "\nDialogue: 0,0:00:00.50,0:00:02.00,Default,,0,0,0,,Edited"
	if err := os.WriteFile(assPath, []byte(newASS), 0644); err != nil {
		t.Fatal(err)
	}
	newFont := append([]byte("OTTO"), bytes.Repeat([]byte{0x42}, 256)...)
	if err := os.WriteFile(newFontPath, newFont, 0644); err != nil {
		t.Fatal(err)
	}

	font := bytes.Repeat([]byte("font-data-"), 128)
	video := mkv.Track{ID: 1, UID: 101, Type: mkv.VideoTrack, Codec: "vp9", IsDefault: true}
	sub := mkv.Track{
		ID: 2, UID: 202, Type: mkv.SubtitleTrack, Codec: "ass",
		Language: "jpn", LanguageBCP47: "ja-JP", Name: "Japanese",
		IsDefault: true, IsForced: true, Commentary: true,
		CodecPrivate: []byte(oldHeader),
	}
	container := &mkv.Container{
		Info: mkv.SegmentInfo{
			Title: "Fixture",
			TimecodeScale: 1_000_000,
			MuxingApp: "fixture",
			WritingApp: "fixture",
			SegmentUID: []byte("0123456789abcdef"),
		},
		Chapters: []mkv.Chapter{{ID: 7, Title: "Intro", StartMs: 0, EndMs: 2000}},
		Attachments: []mkv.Attachment{{
			ID: 9, Name: "Fixture.ttf", MIMEType: "font/ttf",
			Data: font, Size: int64(len(font)),
		}},
		Tags: []mkv.Tag{
			{TargetType: "MOVIE", SimpleTags: []mkv.SimpleTag{{Name: "TITLE", Value: "Fixture tag"}}},
			{TargetID: 202, SimpleTags: []mkv.SimpleTag{{Name: "ASSWB_KEEP", Value: "yes"}}},
		},
	}

	f, err := os.Create(src)
	if err != nil {
		t.Fatal(err)
	}
	mw := writer.NewMKVWriter(f)
	if err := mw.WriteStart(); err != nil {
		t.Fatal(err)
	}
	if err := mw.WriteMetadata(container, []mkv.Track{video, sub}, 3000); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(f, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 1, Timecode: 0, Keyframe: true, Data: []byte{0x01}},
		{TrackNumber: 2, Timecode: 250, Duration: 1000, Data: []byte("0,0,Default,,0,0,0,,Old")},
		{TrackNumber: 1, Timecode: 1000, Keyframe: true, Data: []byte{0x02}},
	}); err != nil {
		t.Fatal(err)
	}
	if err := mw.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := f.Close(); err != nil {
		t.Fatal(err)
	}

	if err := ReplaceASSWithFonts(context.Background(), src, 2, assPath, dst, []string{newFontPath, newFontPath}); err != nil {
		t.Fatal(err)
	}

	got, err := reader.Open(context.Background(), dst)
	if err != nil {
		t.Fatal(err)
	}
	if len(got.Tracks) != 2 {
		t.Fatalf("track count = %d, want 2", len(got.Tracks))
	}
	if got.Tracks[0].ID != 1 || got.Tracks[1].ID != 2 {
		t.Fatalf("track order/number changed: %+v", got.Tracks)
	}
	s := got.Tracks[1]
	if s.UID != 202 || s.Name != "Japanese" || s.Language != "jpn" || s.LanguageBCP47 != "ja-JP" {
		t.Fatalf("subtitle identity metadata changed: %+v", s)
	}
	if !s.IsDefault || !s.IsForced || !s.Commentary {
		t.Fatalf("subtitle flags changed: %+v", s)
	}
	if len(got.Attachments) != 2 {
		t.Fatalf("attachment count = %d, want 2: %+v", len(got.Attachments), got.Attachments)
	}
	if got.Attachments[0].Name != "Fixture.ttf" || !bytes.Equal(got.Attachments[0].Data, font) {
		t.Fatalf("source attachment not preserved: %+v", got.Attachments)
	}
	if got.Attachments[1].Name != "AddedFont.otf" || got.Attachments[1].MIMEType != "font/otf" ||
		!bytes.Equal(got.Attachments[1].Data, newFont) {
		t.Fatalf("selected font not attached once: %+v", got.Attachments)
	}
	if got.Attachments[1].ID == got.Attachments[0].ID {
		t.Fatalf("new attachment reused source UID: %+v", got.Attachments)
	}
	if len(got.Chapters) != 1 || got.Chapters[0].ID != 7 || got.Chapters[0].Title != "Intro" {
		t.Fatalf("chapter not preserved: %+v", got.Chapters)
	}
	var keptGlobal, keptTrack bool
	for _, tag := range got.Tags {
		for _, st := range tag.SimpleTags {
			if tag.TargetID == 0 && st.Name == "TITLE" && st.Value == "Fixture tag" {
				keptGlobal = true
			}
			if tag.TargetID == 202 && st.Name == "ASSWB_KEEP" && st.Value == "yes" {
				keptTrack = true
			}
		}
	}
	if !keptGlobal || !keptTrack {
		t.Fatalf("ordinary tags not preserved: %+v", got.Tags)
	}

	brFile, err := os.Open(dst)
	if err != nil {
		t.Fatal(err)
	}
	defer brFile.Close()
	br, err := reader.NewBlockReader(brFile, got.Info.TimecodeScale)
	if err != nil {
		t.Fatal(err)
	}
	br.KeepTracks(2)
	blk, err := br.Next()
	if err != nil {
		t.Fatal(err)
	}
	if blk.Timecode != 500 || blk.Duration != 1500 || !bytes.Contains(blk.Data, []byte("Edited")) {
		t.Fatalf("replacement subtitle block = %+v %q", blk, string(blk.Data))
	}
}


func TestReplaceASSWithFontsRenamesAttachmentNameCollision(t *testing.T) {
	dir := t.TempDir()
	src := filepath.Join(dir, "source.mkv")
	dst := filepath.Join(dir, "updated.mkv")
	assPath := filepath.Join(dir, "edited.ass")
	fontPath := filepath.Join(dir, "Fixture.ttf")

	header := "[Script Info]\nScriptType: v4.00+\n\n[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\nStyle: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"
	if err := os.WriteFile(assPath, []byte(header+"\nDialogue: 0,0:00:00.00,0:00:01.00,Default,,0,0,0,,Edited"), 0644); err != nil {
		t.Fatal(err)
	}
	newFont := append([]byte{0x00, 0x01, 0x00, 0x00}, bytes.Repeat([]byte{0x55}, 128)...)
	if err := os.WriteFile(fontPath, newFont, 0644); err != nil {
		t.Fatal(err)
	}

	existingFont := bytes.Repeat([]byte("different-source-font"), 32)
	container := &mkv.Container{
		Info: mkv.SegmentInfo{TimecodeScale: 1_000_000},
		Attachments: []mkv.Attachment{{
			ID: 7, Name: "Fixture.ttf", MIMEType: "font/ttf",
			Data: existingFont, Size: int64(len(existingFont)),
		}},
	}
	video := mkv.Track{ID: 1, UID: 101, Type: mkv.VideoTrack, Codec: "vp9"}
	sub := mkv.Track{ID: 2, UID: 202, Type: mkv.SubtitleTrack, Codec: "ass", CodecPrivate: []byte(header)}

	f, err := os.Create(src)
	if err != nil {
		t.Fatal(err)
	}
	mw := writer.NewMKVWriter(f)
	if err := mw.WriteStart(); err != nil {
		t.Fatal(err)
	}
	if err := mw.WriteMetadata(container, []mkv.Track{video, sub}, 1000); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(f, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 1, Timecode: 0, Keyframe: true, Data: []byte{0x01}},
		{TrackNumber: 2, Timecode: 0, Duration: 1000, Data: []byte("0,0,Default,,0,0,0,,Old")},
	}); err != nil {
		t.Fatal(err)
	}
	if err := mw.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := f.Close(); err != nil {
		t.Fatal(err)
	}

	if err := ReplaceASSWithFonts(context.Background(), src, 2, assPath, dst, []string{fontPath}); err != nil {
		t.Fatal(err)
	}
	got, err := reader.Open(context.Background(), dst)
	if err != nil {
		t.Fatal(err)
	}
	if len(got.Attachments) != 2 {
		t.Fatalf("attachment count = %d, want 2: %+v", len(got.Attachments), got.Attachments)
	}
	if got.Attachments[0].Name != "Fixture.ttf" || !bytes.Equal(got.Attachments[0].Data, existingFont) {
		t.Fatalf("source attachment changed: %+v", got.Attachments)
	}
	added := got.Attachments[1]
	if added.Name == "Fixture.ttf" || !strings.HasPrefix(added.Name, "Fixture-asswb-") || !strings.HasSuffix(added.Name, ".ttf") {
		t.Fatalf("collision was not renamed deterministically: %+v", added)
	}
	if !bytes.Equal(added.Data, newFont) {
		t.Fatalf("renamed attachment payload changed: %+v", added)
	}
}


func TestAddAttachmentsSupportsCoverAndArbitraryFiles(t *testing.T) {
	dir := t.TempDir()
	src := filepath.Join(dir, "source.mkv")
	dst := filepath.Join(dir, "updated.mkv")
	coverPath := filepath.Join(dir, "cover.png")
	notePath := filepath.Join(dir, "notes.xyz")

	cover := append([]byte{0x89, 0x50, 0x4e, 0x47}, bytes.Repeat([]byte{0x11}, 64)...)
	note := []byte("arbitrary attachment payload")
	if err := os.WriteFile(coverPath, cover, 0644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(notePath, note, 0644); err != nil {
		t.Fatal(err)
	}

	existing := []byte("source attachment")
	container := &mkv.Container{
		Info: mkv.SegmentInfo{TimecodeScale: 1_000_000, Title: "Attachment fixture"},
		Attachments: []mkv.Attachment{{
			ID: 4, Name: "Fixture.ttf", MIMEType: "font/ttf",
			Data: existing, Size: int64(len(existing)),
		}},
		Chapters: []mkv.Chapter{{ID: 1, Title: "Intro", StartMs: 0, EndMs: 1000}},
	}
	video := mkv.Track{ID: 1, UID: 101, Type: mkv.VideoTrack, Codec: "vp9", IsDefault: true}

	f, err := os.Create(src)
	if err != nil {
		t.Fatal(err)
	}
	mw := writer.NewMKVWriter(f)
	if err := mw.WriteStart(); err != nil {
		t.Fatal(err)
	}
	if err := mw.WriteMetadata(container, []mkv.Track{video}, 1000); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(f, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 1, Timecode: 0, Keyframe: true, Data: []byte{0x01}},
	}); err != nil {
		t.Fatal(err)
	}
	if err := mw.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := f.Close(); err != nil {
		t.Fatal(err)
	}

	if err := AddAttachments(context.Background(), src, dst, []string{coverPath, notePath, coverPath}); err != nil {
		t.Fatal(err)
	}
	got, err := reader.Open(context.Background(), dst)
	if err != nil {
		t.Fatal(err)
	}
	if len(got.Tracks) != 1 || got.Tracks[0].ID != 1 || got.Tracks[0].UID != 101 {
		t.Fatalf("track identity changed: %+v", got.Tracks)
	}
	if len(got.Attachments) != 3 {
		t.Fatalf("attachment count = %d, want 3: %+v", len(got.Attachments), got.Attachments)
	}
	byName := make(map[string]mkv.Attachment)
	for _, att := range got.Attachments {
		byName[att.Name] = att
	}
	if att := byName["Fixture.ttf"]; !bytes.Equal(att.Data, existing) {
		t.Fatalf("source attachment changed: %+v", att)
	}
	if att := byName["cover.png"]; att.MIMEType != "image/png" || !bytes.Equal(att.Data, cover) {
		t.Fatalf("cover attachment = %+v", att)
	}
	if att := byName["notes.xyz"]; att.MIMEType != "application/octet-stream" || !bytes.Equal(att.Data, note) {
		t.Fatalf("unknown attachment = %+v", att)
	}
	if len(got.Chapters) != 1 || got.Chapters[0].Title != "Intro" {
		t.Fatalf("chapters changed: %+v", got.Chapters)
	}
}


func TestEditAttachmentsRemovesAndReplacesInOneRemux(t *testing.T) {
	dir := t.TempDir()
	src := filepath.Join(dir, "source.mkv")
	dst := filepath.Join(dir, "updated.mkv")
	replacePath := filepath.Join(dir, "cover.png")
	addPath := filepath.Join(dir, "notes.txt")

	replacementData := append([]byte{0x89, 0x50, 0x4e, 0x47}, bytes.Repeat([]byte{0x33}, 48)...)
	addedData := []byte("container note")
	if err := os.WriteFile(replacePath, replacementData, 0644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(addPath, addedData, 0644); err != nil {
		t.Fatal(err)
	}

	keepData := []byte("keep")
	removeData := []byte("remove")
	replaceOldData := []byte("old-cover")
	container := &mkv.Container{
		Info: mkv.SegmentInfo{TimecodeScale: 1_000_000, Title: "CRUD fixture"},
		Attachments: []mkv.Attachment{
			{ID: 7, Name: "keep.bin", MIMEType: "application/octet-stream", Data: keepData, Size: int64(len(keepData))},
			{ID: 8, Name: "remove.txt", MIMEType: "text/plain", Data: removeData, Size: int64(len(removeData))},
			{ID: 9, Name: "old-cover.jpg", MIMEType: "image/jpeg", Data: replaceOldData, Size: int64(len(replaceOldData))},
		},
		Chapters: []mkv.Chapter{{ID: 2, Title: "Chapter", StartMs: 0, EndMs: 1000}},
	}
	video := mkv.Track{ID: 1, UID: 101, Type: mkv.VideoTrack, Codec: "vp9", IsDefault: true}

	out, err := os.Create(src)
	if err != nil {
		t.Fatal(err)
	}
	mw := writer.NewMKVWriter(out)
	if err := mw.WriteStart(); err != nil {
		t.Fatal(err)
	}
	if err := mw.WriteMetadata(container, []mkv.Track{video}, 1000); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(out, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 1, Timecode: 0, Keyframe: true, Data: []byte{0x01}},
	}); err != nil {
		t.Fatal(err)
	}
	if err := mw.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := out.Close(); err != nil {
		t.Fatal(err)
	}

	if err := EditAttachments(
		context.Background(),
		src,
		dst,
		[]string{addPath},
		[]string{"8"},
		[]AttachmentReplacement{{Target: "9", Path: replacePath}},
		nil,
	); err != nil {
		t.Fatal(err)
	}

	got, err := reader.Open(context.Background(), dst)
	if err != nil {
		t.Fatal(err)
	}
	if len(got.Tracks) != 1 || got.Tracks[0].ID != 1 || got.Tracks[0].UID != 101 {
		t.Fatalf("track identity changed: %+v", got.Tracks)
	}
	if len(got.Chapters) != 1 || got.Chapters[0].Title != "Chapter" {
		t.Fatalf("chapters changed: %+v", got.Chapters)
	}
	if len(got.Attachments) != 3 {
		t.Fatalf("attachment count = %d, want 3: %+v", len(got.Attachments), got.Attachments)
	}
	byID := make(map[uint64]mkv.Attachment)
	byName := make(map[string]mkv.Attachment)
	for _, att := range got.Attachments {
		byID[att.ID] = att
		byName[att.Name] = att
	}
	if att := byID[7]; att.Name != "keep.bin" || !bytes.Equal(att.Data, keepData) {
		t.Fatalf("untouched attachment changed: %+v", att)
	}
	if _, exists := byID[8]; exists {
		t.Fatalf("removed attachment UID 8 still exists: %+v", got.Attachments)
	}
	replaced := byID[9]
	if replaced.Name != "cover.png" || replaced.MIMEType != "image/png" ||
		!bytes.Equal(replaced.Data, replacementData) {
		t.Fatalf("replacement did not preserve identity/content: %+v", replaced)
	}
	added, ok := byName["notes.txt"]
	if !ok || added.MIMEType != "text/plain" || !bytes.Equal(added.Data, addedData) {
		t.Fatalf("added attachment missing or changed: %+v", added)
	}
	if added.ID == 7 || added.ID == 9 {
		t.Fatalf("added attachment reused surviving UID: %+v", added)
	}
}


func TestAttachmentMetadataEditAndExtractPreservePayloadIdentity(t *testing.T) {
	dir := t.TempDir()
	src := filepath.Join(dir, "source.mkv")
	dst := filepath.Join(dir, "renamed.mkv")
	extracted := filepath.Join(dir, "extracted.bin")

	payload := []byte("attachment payload must survive metadata-only edits")
	container := &mkv.Container{
		Info: mkv.SegmentInfo{TimecodeScale: 1_000_000},
		Attachments: []mkv.Attachment{{
			ID: 12,
			Name: "notes.txt",
			MIMEType: "text/plain",
			Description: "old description",
			Data: payload,
			Size: int64(len(payload)),
		}},
	}
	video := mkv.Track{ID: 1, UID: 101, Type: mkv.VideoTrack, Codec: "vp9"}

	out, err := os.Create(src)
	if err != nil {
		t.Fatal(err)
	}
	mw := writer.NewMKVWriter(out)
	if err := mw.WriteStart(); err != nil {
		t.Fatal(err)
	}
	if err := mw.WriteMetadata(container, []mkv.Track{video}, 1000); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(out, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 1, Timecode: 0, Keyframe: true, Data: []byte{0x01}},
	}); err != nil {
		t.Fatal(err)
	}
	if err := mw.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := out.Close(); err != nil {
		t.Fatal(err)
	}

	if err := ExtractAttachmentTarget(context.Background(), src, "12", extracted); err != nil {
		t.Fatal(err)
	}
	gotExtracted, err := os.ReadFile(extracted)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(gotExtracted, payload) {
		t.Fatalf("extracted payload changed: %q", string(gotExtracted))
	}

	if err := EditAttachments(
		context.Background(),
		src,
		dst,
		nil,
		nil,
		nil,
		[]AttachmentMetadataEdit{{
			Target: "12",
			Name: "translator-notes.txt",
			Description: "translation notes",
		}},
	); err != nil {
		t.Fatal(err)
	}

	got, err := reader.Open(context.Background(), dst)
	if err != nil {
		t.Fatal(err)
	}
	if len(got.Attachments) != 1 {
		t.Fatalf("attachment count = %d, want 1", len(got.Attachments))
	}
	att := got.Attachments[0]
	if att.ID != 12 || att.Name != "translator-notes.txt" ||
		att.Description != "translation notes" || att.MIMEType != "text/plain" {
		t.Fatalf("metadata edit changed identity or unexpected fields: %+v", att)
	}
	if !bytes.Equal(att.Data, payload) {
		t.Fatalf("metadata edit changed payload: %q", string(att.Data))
	}
	if len(got.Tracks) != 1 || got.Tracks[0].UID != 101 {
		t.Fatalf("track identity changed: %+v", got.Tracks)
	}
}

func TestAttachmentMetadataEditRejectsNameCollision(t *testing.T) {
	existing := []mkv.Attachment{
		{ID: 1, Name: "a.txt", MIMEType: "text/plain"},
		{ID: 2, Name: "b.txt", MIMEType: "text/plain"},
	}
	_, err := planAttachmentEdits(
		existing,
		nil,
		nil,
		nil,
		[]AttachmentMetadataEdit{{
			Target: "2",
			Name: "a.txt",
			Description: "",
		}},
	)
	if err == nil || !strings.Contains(err.Error(), "conflicts") {
		t.Fatalf("expected name collision error, got %v", err)
	}
}


func TestEditContainerResourcesPreservesSurvivingTrackIdentity(t *testing.T) {
	dir := t.TempDir()
	src := filepath.Join(dir, "track-source.mkv")
	dst := filepath.Join(dir, "track-updated.mkv")

	attachmentData := []byte("keep attachment")
	container := &mkv.Container{
		Info: mkv.SegmentInfo{
			TimecodeScale: 1_000_000,
			Title: "Track mutation fixture",
			SegmentUID: []byte("track-fixture-001"),
		},
		Chapters: []mkv.Chapter{{ID: 5, Title: "Keep chapter", StartMs: 0, EndMs: 2000}},
		Attachments: []mkv.Attachment{{
			ID: 11,
			Name: "keep.txt",
			MIMEType: "text/plain",
			Data: attachmentData,
			Size: int64(len(attachmentData)),
		}},
		Tags: []mkv.Tag{
			{TargetType: "MOVIE", SimpleTags: []mkv.SimpleTag{{Name: "GLOBAL", Value: "keep"}}},
			{TargetID: 202, SimpleTags: []mkv.SimpleTag{{Name: "AUDIO_ONLY", Value: "drop"}}},
			{TargetID: 505, SimpleTags: []mkv.SimpleTag{{Name: "SUB_KEEP", Value: "yes"}}},
		},
	}
	video := mkv.Track{
		ID: 1, UID: 101, Type: mkv.VideoTrack, Codec: "vp9",
		Name: "Video", Language: "und", IsDefault: true,
	}
	audio := mkv.Track{
		ID: 2, UID: 202, Type: mkv.AudioTrack, Codec: "opus",
		Name: "Audio", Language: "jpn", IsDefault: true,
	}
	sub := mkv.Track{
		ID: 5, UID: 505, Type: mkv.SubtitleTrack, Codec: "ass",
		Name: "Signs", Language: "eng", IsDefault: false, IsForced: true,
		CodecPrivate: []byte("[Script Info]\nScriptType: v4.00+\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"),
	}

	out, err := os.Create(src)
	if err != nil {
		t.Fatal(err)
	}
	mw := writer.NewMKVWriter(out)
	if err := mw.WriteStart(); err != nil {
		t.Fatal(err)
	}
	if err := mw.WriteMetadata(container, []mkv.Track{video, audio, sub}, 2000); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(out, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 1, Timecode: 0, Keyframe: true, Data: []byte{0x01}},
		{TrackNumber: 2, Timecode: 0, Keyframe: true, Data: []byte{0x02}},
		{TrackNumber: 5, Timecode: 100, Duration: 900, Data: []byte("0,0,Default,,0,0,0,,Keep subtitle")},
		{TrackNumber: 1, Timecode: 1000, Keyframe: true, Data: []byte{0x03}},
		{TrackNumber: 2, Timecode: 1000, Keyframe: true, Data: []byte{0x04}},
	}); err != nil {
		t.Fatal(err)
	}
	if err := mw.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := out.Close(); err != nil {
		t.Fatal(err)
	}

	if err := EditContainerResources(
		context.Background(),
		src,
		dst,
		nil,
		nil,
		nil,
		nil,
		[]string{"uid:202"},
		[]TrackMetadataEdit{{
			Target: "uid:101",
			Name: "Main picture",
			Language: "und",
			LanguageBCP47: "ja-JP",
			IsDefault: false,
			IsForced: false,
			HearingImpaired: true,
			VisualImpaired: true,
			TextDescriptions: true,
			Original: true,
			Commentary: true,
		}},
	); err != nil {
		t.Fatal(err)
	}

	got, err := reader.Open(context.Background(), dst)
	if err != nil {
		t.Fatal(err)
	}
	if len(got.Tracks) != 2 {
		t.Fatalf("track count = %d, want 2: %+v", len(got.Tracks), got.Tracks)
	}
	if got.Tracks[0].ID != 1 || got.Tracks[0].UID != 101 {
		t.Fatalf("video identity changed: %+v", got.Tracks[0])
	}
	if got.Tracks[0].Name != "Main picture" || got.Tracks[0].Language != "und" ||
		got.Tracks[0].LanguageBCP47 != "ja-JP" ||
		got.Tracks[0].IsDefault || got.Tracks[0].IsForced ||
		!got.Tracks[0].HearingImpaired || !got.Tracks[0].VisualImpaired ||
		!got.Tracks[0].TextDescriptions || !got.Tracks[0].Original ||
		!got.Tracks[0].Commentary {
		t.Fatalf("video metadata edit not applied: %+v", got.Tracks[0])
	}
	if got.Tracks[1].ID != 5 || got.Tracks[1].UID != 505 ||
		got.Tracks[1].Name != "Signs" || got.Tracks[1].Language != "eng" ||
		got.Tracks[1].IsDefault || !got.Tracks[1].IsForced {
		t.Fatalf("surviving subtitle identity/metadata changed: %+v", got.Tracks[1])
	}
	if len(got.Attachments) != 1 || got.Attachments[0].ID != 11 ||
		!bytes.Equal(got.Attachments[0].Data, attachmentData) {
		t.Fatalf("attachment changed: %+v", got.Attachments)
	}
	if len(got.Chapters) != 1 || got.Chapters[0].ID != 5 || got.Chapters[0].Title != "Keep chapter" {
		t.Fatalf("chapter changed: %+v", got.Chapters)
	}

	var globalKept, removedAudioTag, subtitleTagKept bool
	for _, tag := range got.Tags {
		for _, st := range tag.SimpleTags {
			if tag.TargetID == 0 && st.Name == "GLOBAL" && st.Value == "keep" {
				globalKept = true
			}
			if tag.TargetID == 202 && st.Name == "AUDIO_ONLY" {
				removedAudioTag = true
			}
			if tag.TargetID == 505 && st.Name == "SUB_KEEP" && st.Value == "yes" {
				subtitleTagKept = true
			}
		}
	}
	if !globalKept || removedAudioTag || !subtitleTagKept {
		t.Fatalf("track-targeted tag filtering wrong: %+v", got.Tags)
	}

	blockFile, err := os.Open(dst)
	if err != nil {
		t.Fatal(err)
	}
	defer blockFile.Close()
	br, err := reader.NewBlockReader(blockFile, got.Info.TimecodeScale)
	if err != nil {
		t.Fatal(err)
	}
	var seenVideo, seenSubtitle bool
	for {
		block, err := br.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			t.Fatal(err)
		}
		if block.TrackNumber == 2 {
			t.Fatalf("removed track payload survived: %+v", block)
		}
		if block.TrackNumber == 1 {
			seenVideo = true
		}
		if block.TrackNumber == 5 {
			seenSubtitle = true
		}
	}
	if !seenVideo || !seenSubtitle {
		t.Fatalf("surviving payload missing: video=%v subtitle=%v", seenVideo, seenSubtitle)
	}
}

func TestPlanTrackEditsRejectsRemovingAllTracks(t *testing.T) {
	tracks := []mkv.Track{
		{ID: 1, UID: 101, Type: mkv.VideoTrack, Codec: "vp9"},
		{ID: 2, UID: 202, Type: mkv.AudioTrack, Codec: "opus"},
	}
	_, _, _, err := planTrackEdits(
		tracks,
		[]string{"uid:101", "uid:202"},
		nil,
	)
	if err == nil || !strings.Contains(err.Error(), "cannot remove all tracks") {
		t.Fatalf("expected remove-all rejection, got %v", err)
	}
}


func TestEditContainerResourcesImportsExternalTrackWithFreshIdentity(t *testing.T) {
	dir := t.TempDir()
	targetPath := filepath.Join(dir, "target.mkv")
	importPath := filepath.Join(dir, "external.mkv")
	dst := filepath.Join(dir, "imported.mkv")

	targetAttachment := []byte("target-only attachment")
	targetContainer := &mkv.Container{
		Info: mkv.SegmentInfo{TimecodeScale: 1_000_000, Title: "Target"},
		Attachments: []mkv.Attachment{{
			ID: 31,
			Name: "target-note.txt",
			MIMEType: "text/plain",
			Data: targetAttachment,
			Size: int64(len(targetAttachment)),
		}},
		Chapters: []mkv.Chapter{{ID: 9, Title: "Target chapter", StartMs: 0, EndMs: 1500}},
	}
	targetVideo := mkv.Track{
		ID: 1, UID: 101, Type: mkv.VideoTrack, Codec: "vp9",
		Name: "Main", Language: "und", IsDefault: true,
	}
	targetSub := mkv.Track{
		ID: 5, UID: 505, Type: mkv.SubtitleTrack, Codec: "ass",
		Name: "Signs", Language: "eng", IsForced: true,
		CodecPrivate: []byte("[Script Info]\nScriptType: v4.00+\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"),
	}
	targetFile, err := os.Create(targetPath)
	if err != nil {
		t.Fatal(err)
	}
	targetWriter := writer.NewMKVWriter(targetFile)
	if err := targetWriter.WriteStart(); err != nil {
		t.Fatal(err)
	}
	if err := targetWriter.WriteMetadata(targetContainer, []mkv.Track{targetVideo, targetSub}, 1500); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(targetFile, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 1, Timecode: 0, Keyframe: true, Data: []byte{0x11}},
		{TrackNumber: 5, Timecode: 200, Duration: 800, Data: []byte("0,0,Default,,0,0,0,,Target subtitle")},
		{TrackNumber: 1, Timecode: 1000, Keyframe: true, Data: []byte{0x12}},
	}); err != nil {
		t.Fatal(err)
	}
	if err := targetWriter.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := targetFile.Close(); err != nil {
		t.Fatal(err)
	}

	externalAttachment := []byte("must not be copied")
	externalContainer := &mkv.Container{
		Info: mkv.SegmentInfo{TimecodeScale: 1_000_000, Title: "External"},
		Attachments: []mkv.Attachment{{
			ID: 41,
			Name: "external-only.txt",
			MIMEType: "text/plain",
			Data: externalAttachment,
			Size: int64(len(externalAttachment)),
		}},
		Tags: []mkv.Tag{
			{TargetType: "MOVIE", SimpleTags: []mkv.SimpleTag{{Name: "SOURCE_GLOBAL", Value: "do-not-copy"}}},
			{TargetID: 101, SimpleTags: []mkv.SimpleTag{{Name: "TRACK_NOTE", Value: "carry-me"}}},
		},
	}
	// The source UID intentionally collides with the target video. Import must
	// allocate destination identity rather than carrying source identity across.
	externalAudio := mkv.Track{
		ID: 2, UID: 101, Type: mkv.AudioTrack, Codec: "opus",
		Name: "Source commentary", Language: "jpn", IsDefault: true,
		CodecPrivate: []byte("OpusHead\x01\x02"),
	}
	externalFile, err := os.Create(importPath)
	if err != nil {
		t.Fatal(err)
	}
	externalWriter := writer.NewMKVWriter(externalFile)
	if err := externalWriter.WriteStart(); err != nil {
		t.Fatal(err)
	}
	if err := externalWriter.WriteMetadata(externalContainer, []mkv.Track{externalAudio}, 2200); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(externalFile, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 2, Timecode: 0, Keyframe: true, Data: []byte{0x21}},
		{TrackNumber: 2, Timecode: 1000, Keyframe: true, Data: []byte{0x22}},
		{TrackNumber: 2, Timecode: 2000, Keyframe: true, Data: []byte{0x23}},
	}); err != nil {
		t.Fatal(err)
	}
	if err := externalWriter.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := externalFile.Close(); err != nil {
		t.Fatal(err)
	}

	if err := EditContainerResourcesWithTrackImports(
		context.Background(),
		targetPath,
		dst,
		nil,
		nil,
		nil,
		nil,
		nil,
		nil,
		[]TrackImport{{
			SourcePath: importPath,
			TrackID: 2,
			Name: "Commentary",
			Language: "eng",
			LanguageBCP47: "en-GB",
			IsDefault: false,
			IsForced: true,
			HearingImpaired: true,
			Commentary: true,
		}},
	); err != nil {
		t.Fatal(err)
	}

	got, err := reader.Open(context.Background(), dst)
	if err != nil {
		t.Fatal(err)
	}
	if len(got.Tracks) != 3 {
		t.Fatalf("track count = %d, want 3: %+v", len(got.Tracks), got.Tracks)
	}
	if got.Tracks[0].ID != 1 || got.Tracks[0].UID != 101 ||
		got.Tracks[1].ID != 5 || got.Tracks[1].UID != 505 {
		t.Fatalf("destination survivor identity changed: %+v", got.Tracks)
	}
	imported := got.Tracks[2]
	if imported.ID != 6 {
		t.Fatalf("imported TrackNumber = %d, want 6 (append after max destination ID)", imported.ID)
	}
	if imported.UID == 0 || imported.UID == 101 || imported.UID == 505 {
		t.Fatalf("imported track did not get fresh destination UID: %+v", imported)
	}
	if imported.Type != mkv.AudioTrack || imported.Codec != "opus" ||
		imported.Name != "Commentary" || imported.Language != "eng" ||
		imported.LanguageBCP47 != "en-GB" ||
		imported.IsDefault || !imported.IsForced ||
		!imported.HearingImpaired || !imported.Commentary {
		t.Fatalf("imported track metadata/codec wrong: %+v", imported)
	}
	if len(got.Attachments) != 1 || got.Attachments[0].Name != "target-note.txt" ||
		!bytes.Equal(got.Attachments[0].Data, targetAttachment) {
		t.Fatalf("target attachments changed or source attachment leaked in: %+v", got.Attachments)
	}
	if len(got.Chapters) != 1 || got.Chapters[0].ID != 9 {
		t.Fatalf("target chapters changed: %+v", got.Chapters)
	}

	var importedTagFound, sourceGlobalLeaked bool
	for _, tag := range got.Tags {
		for _, st := range tag.SimpleTags {
			if tag.TargetID == imported.UID && st.Name == "TRACK_NOTE" && st.Value == "carry-me" {
				importedTagFound = true
			}
			if st.Name == "SOURCE_GLOBAL" {
				sourceGlobalLeaked = true
			}
		}
	}
	if !importedTagFound || sourceGlobalLeaked {
		t.Fatalf(
			"imported track tags wrong: carried=%v sourceGlobalLeaked=%v tags=%+v",
			importedTagFound,
			sourceGlobalLeaked,
			got.Tags,
		)
	}
	if got.DurationMs < 2000 {
		t.Fatalf("output duration = %dms, want imported longer timeline reflected", got.DurationMs)
	}

	blockFile, err := os.Open(dst)
	if err != nil {
		t.Fatal(err)
	}
	defer blockFile.Close()
	br, err := reader.NewBlockReader(blockFile, got.Info.TimecodeScale)
	if err != nil {
		t.Fatal(err)
	}
	var seenTargetVideo, seenTargetSub, seenImportedAudio bool
	for {
		block, err := br.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			t.Fatal(err)
		}
		switch block.TrackNumber {
		case 1:
			seenTargetVideo = true
		case 5:
			seenTargetSub = true
		case 6:
			seenImportedAudio = true
		case 2:
			t.Fatalf("source TrackNumber leaked instead of destination remap: %+v", block)
		}
	}
	if !seenTargetVideo || !seenTargetSub || !seenImportedAudio {
		t.Fatalf(
			"payload missing: video=%v subtitle=%v importedAudio=%v",
			seenTargetVideo,
			seenTargetSub,
			seenImportedAudio,
		)
	}
}


func TestTrackImportDoesNotReuseIdentityRemovedInSameTransaction(t *testing.T) {
	dir := t.TempDir()
	targetPath := filepath.Join(dir, "target-remove-import.mkv")
	importPath := filepath.Join(dir, "external-remove-import.mkv")
	dst := filepath.Join(dir, "updated-remove-import.mkv")

	target := &mkv.Container{Info: mkv.SegmentInfo{TimecodeScale: 1_000_000}}
	targetVideo := mkv.Track{ID: 1, UID: 101, Type: mkv.VideoTrack, Codec: "vp9"}
	targetAudio := mkv.Track{ID: 5, UID: 505, Type: mkv.AudioTrack, Codec: "opus"}

	f, err := os.Create(targetPath)
	if err != nil {
		t.Fatal(err)
	}
	mw := writer.NewMKVWriter(f)
	if err := mw.WriteStart(); err != nil {
		t.Fatal(err)
	}
	if err := mw.WriteMetadata(target, []mkv.Track{targetVideo, targetAudio}, 1000); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(f, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 1, Timecode: 0, Keyframe: true, Data: []byte{0x01}},
		{TrackNumber: 5, Timecode: 0, Keyframe: true, Data: []byte{0x05}},
	}); err != nil {
		t.Fatal(err)
	}
	if err := mw.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := f.Close(); err != nil {
		t.Fatal(err)
	}

	external := &mkv.Container{Info: mkv.SegmentInfo{TimecodeScale: 1_000_000}}
	externalSub := mkv.Track{
		ID: 2, UID: 202, Type: mkv.SubtitleTrack, Codec: "ass",
		CodecPrivate: []byte("[Script Info]\nScriptType: v4.00+\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"),
	}
	g, err := os.Create(importPath)
	if err != nil {
		t.Fatal(err)
	}
	mw2 := writer.NewMKVWriter(g)
	if err := mw2.WriteStart(); err != nil {
		t.Fatal(err)
	}
	if err := mw2.WriteMetadata(external, []mkv.Track{externalSub}, 1000); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(g, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 2, Timecode: 100, Duration: 500, Data: []byte("0,0,Default,,0,0,0,,Imported")},
	}); err != nil {
		t.Fatal(err)
	}
	if err := mw2.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := g.Close(); err != nil {
		t.Fatal(err)
	}

	if err := EditContainerResourcesWithTrackImports(
		context.Background(),
		targetPath,
		dst,
		nil,
		nil,
		nil,
		nil,
		[]string{"uid:505"},
		nil,
		[]TrackImport{{
			SourcePath: importPath,
			TrackID: 2,
			Name: "Imported subtitles",
			Language: "eng",
		}},
	); err != nil {
		t.Fatal(err)
	}

	got, err := reader.Open(context.Background(), dst)
	if err != nil {
		t.Fatal(err)
	}
	if len(got.Tracks) != 2 {
		t.Fatalf("track count = %d, want 2: %+v", len(got.Tracks), got.Tracks)
	}
	if got.Tracks[0].ID != 1 || got.Tracks[0].UID != 101 {
		t.Fatalf("surviving target identity changed: %+v", got.Tracks[0])
	}
	imported := got.Tracks[1]
	if imported.ID == 5 || imported.UID == 505 {
		t.Fatalf("import reused identity removed in same transaction: %+v", imported)
	}
	if imported.ID <= 5 || imported.UID <= 505 {
		t.Fatalf("import identity should be allocated above source reservation floor: %+v", imported)
	}
	if imported.Type != mkv.SubtitleTrack || imported.Codec != "ass" {
		t.Fatalf("wrong imported resource: %+v", imported)
	}
}

func TestEditContainerResourcesCanReplaceEntireTrackSetWithImport(t *testing.T) {
	dir := t.TempDir()
	base := filepath.Join(dir, "base-one-track.mkv")
	external := filepath.Join(dir, "external-one-track.mkv")
	dst := filepath.Join(dir, "replaced-track-set.mkv")

	baseTrack := mkv.Track{ID: 4, UID: 404, Type: mkv.VideoTrack, Codec: "vp9"}
	out, err := os.Create(base)
	if err != nil {
		t.Fatal(err)
	}
	mw := writer.NewMKVWriter(out)
	if err := mw.WriteStart(); err != nil {
		t.Fatal(err)
	}
	if err := mw.WriteMetadata(
		&mkv.Container{Info: mkv.SegmentInfo{TimecodeScale: 1_000_000}},
		[]mkv.Track{baseTrack},
		1000,
	); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(out, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 4, Timecode: 0, Keyframe: true, Data: []byte("old-video")},
	}); err != nil {
		t.Fatal(err)
	}
	if err := mw.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := out.Close(); err != nil {
		t.Fatal(err)
	}

	newTrack := mkv.Track{ID: 2, UID: 22, Type: mkv.AudioTrack, Codec: "opus"}
	out, err = os.Create(external)
	if err != nil {
		t.Fatal(err)
	}
	mw = writer.NewMKVWriter(out)
	if err := mw.WriteStart(); err != nil {
		t.Fatal(err)
	}
	if err := mw.WriteMetadata(
		&mkv.Container{Info: mkv.SegmentInfo{TimecodeScale: 1_000_000}},
		[]mkv.Track{newTrack},
		1200,
	); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(out, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 2, Timecode: 0, Keyframe: true, Data: []byte("new-audio")},
	}); err != nil {
		t.Fatal(err)
	}
	if err := mw.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := out.Close(); err != nil {
		t.Fatal(err)
	}

	if err := EditContainerResourcesWithTrackImports(
		context.Background(),
		base,
		dst,
		nil,
		nil,
		nil,
		nil,
		[]string{"uid:404"},
		nil,
		[]TrackImport{{
			SourcePath: external,
			TrackID: 2,
			SourceTrackUID: 22,
			Name: "Only audio",
			Language: "und",
		}},
	); err != nil {
		t.Fatal(err)
	}

	got, err := reader.Open(context.Background(), dst)
	if err != nil {
		t.Fatal(err)
	}
	if len(got.Tracks) != 1 {
		t.Fatalf("track count = %d, want 1: %+v", len(got.Tracks), got.Tracks)
	}
	if got.Tracks[0].ID != 5 || got.Tracks[0].UID <= 404 ||
		got.Tracks[0].Type != mkv.AudioTrack || got.Tracks[0].Codec != "opus" {
		t.Fatalf("replacement track set identity/type wrong: %+v", got.Tracks[0])
	}

	blockFile, err := os.Open(dst)
	if err != nil {
		t.Fatal(err)
	}
	defer blockFile.Close()
	br, err := reader.NewBlockReader(blockFile, got.Info.TimecodeScale)
	if err != nil {
		t.Fatal(err)
	}
	block, err := br.Next()
	if err != nil {
		t.Fatal(err)
	}
	if block.TrackNumber != 5 || string(block.Data) != "new-audio" {
		t.Fatalf("replacement track payload = track %d %q", block.TrackNumber, string(block.Data))
	}
	if _, err := br.Next(); err != io.EOF {
		t.Fatalf("expected exactly one imported block, got %v", err)
	}
}

func TestPlanTrackImportsRejectsSourceUIDDrift(t *testing.T) {
	dir := t.TempDir()
	source := filepath.Join(dir, "source-uid-drift.mkv")
	track := mkv.Track{ID: 2, UID: 222, Type: mkv.AudioTrack, Codec: "opus"}

	out, err := os.Create(source)
	if err != nil {
		t.Fatal(err)
	}
	mw := writer.NewMKVWriter(out)
	if err := mw.WriteStart(); err != nil {
		t.Fatal(err)
	}
	if err := mw.WriteMetadata(
		&mkv.Container{Info: mkv.SegmentInfo{TimecodeScale: 1_000_000}},
		[]mkv.Track{track},
		1000,
	); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(out, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 2, Timecode: 0, Keyframe: true, Data: []byte("payload")},
	}); err != nil {
		t.Fatal(err)
	}
	if err := mw.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := out.Close(); err != nil {
		t.Fatal(err)
	}

	_, _, _, _, _, _, err = planTrackImports(
		context.Background(),
		mkv.FSFrom(nil),
		nil,
		nil,
		[]TrackImport{{
			SourcePath: source,
			TrackID: 2,
			SourceTrackUID: 999,
		}},
	)
	if err == nil || !strings.Contains(err.Error(), "changed identity") {
		t.Fatalf("expected source TrackUID drift rejection, got %v", err)
	}
}

func TestPlanTrackImportsRejectsIdentityOverflowBeforeSecondImport(t *testing.T) {
	dir := t.TempDir()
	source := filepath.Join(dir, "source-identity-overflow.mkv")
	first := mkv.Track{ID: 1, UID: 11, Type: mkv.AudioTrack, Codec: "opus"}
	second := mkv.Track{ID: 2, UID: 22, Type: mkv.SubtitleTrack, Codec: "ass"}

	out, err := os.Create(source)
	if err != nil {
		t.Fatal(err)
	}
	mw := writer.NewMKVWriter(out)
	if err := mw.WriteStart(); err != nil {
		t.Fatal(err)
	}
	if err := mw.WriteMetadata(
		&mkv.Container{Info: mkv.SegmentInfo{TimecodeScale: 1_000_000}},
		[]mkv.Track{first, second},
		1000,
	); err != nil {
		t.Fatal(err)
	}
	if err := mw.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := out.Close(); err != nil {
		t.Fatal(err)
	}

	maxMinusOne := ^uint64(0) - 1
	_, _, _, _, _, _, err = planTrackImports(
		context.Background(),
		mkv.FSFrom(nil),
		nil,
		[]mkv.Track{{
			ID: maxMinusOne,
			UID: maxMinusOne,
			Type: mkv.VideoTrack,
			Codec: "vp9",
		}},
		[]TrackImport{
			{SourcePath: source, TrackID: 1, SourceTrackUID: 11},
			{SourcePath: source, TrackID: 2, SourceTrackUID: 22},
		},
	)
	if err == nil || !strings.Contains(err.Error(), "cannot allocate another track identity") {
		t.Fatalf("expected track identity overflow rejection, got %v", err)
	}
}



func writeNormalizedPacketFixture(
	t *testing.T,
	path string,
	durationUs int64,
	packets []struct {
		ptsUs int64
		data  []byte
	},
) (bundleSHA string, contentSHA string) {
	t.Helper()
	out, err := os.Create(path)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := out.Write([]byte(packetBundleMagic)); err != nil {
		t.Fatal(err)
	}
	if err := binary.Write(out, binary.BigEndian, durationUs); err != nil {
		t.Fatal(err)
	}
	if err := binary.Write(out, binary.BigEndian, uint64(len(packets))); err != nil {
		t.Fatal(err)
	}
	content := sha256.New()
	var header [12]byte
	for _, packet := range packets {
		if err := binary.Write(out, binary.BigEndian, packet.ptsUs); err != nil {
			t.Fatal(err)
		}
		if err := binary.Write(out, binary.BigEndian, uint32(0)); err != nil {
			t.Fatal(err)
		}
		if err := binary.Write(out, binary.BigEndian, uint32(len(packet.data))); err != nil {
			t.Fatal(err)
		}
		if _, err := out.Write(packet.data); err != nil {
			t.Fatal(err)
		}
		binary.BigEndian.PutUint64(header[:8], uint64(packet.ptsUs/1000))
		binary.BigEndian.PutUint32(header[8:], uint32(len(packet.data)))
		_, _ = content.Write(header[:])
		_, _ = content.Write(packet.data)
	}
	if err := out.Close(); err != nil {
		t.Fatal(err)
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(raw)
	return hex.EncodeToString(sum[:]), hex.EncodeToString(content.Sum(nil))
}

func TestTrackImportAddsNormalizedASSWithFreshIdentity(t *testing.T) {
	dir := t.TempDir()
	base := filepath.Join(dir, "base.mkv")
	assPath := filepath.Join(dir, "normalized.ass")
	dst := filepath.Join(dir, "out.mkv")

	out, err := os.Create(base)
	if err != nil {
		t.Fatal(err)
	}
	mw := writer.NewMKVWriter(out)
	if err := mw.WriteStart(); err != nil {
		t.Fatal(err)
	}
	baseTrack := mkv.Track{ID: 4, UID: 404, Type: mkv.VideoTrack, Codec: "vp9"}
	if err := mw.WriteMetadata(
		&mkv.Container{Info: mkv.SegmentInfo{TimecodeScale: 1_000_000}},
		[]mkv.Track{baseTrack},
		1000,
	); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(out, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 4, Timecode: 0, Keyframe: true, Data: []byte("video")},
	}); err != nil {
		t.Fatal(err)
	}
	if err := mw.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := out.Close(); err != nil {
		t.Fatal(err)
	}

	ass := []byte("[Script Info]\nScriptType: v4.00+\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\nDialogue: 0,0:00:00.25,0:00:02.25,Default,,0,0,0,,Normalized import")
	if err := os.WriteFile(assPath, ass, 0644); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(ass)

	if err := EditContainerResourcesWithTrackImports(
		context.Background(),
		base,
		dst,
		nil, nil, nil, nil, nil, nil,
		[]TrackImport{{
			SourceKind: trackImportSourceASS,
			SourcePath: assPath,
			SourceSHA256: hex.EncodeToString(sum[:]),
			Name: "Imported ASS",
			Language: "eng",
			LanguageBCP47: "en-US",
			IsDefault: false,
			IsForced: true,
			HearingImpaired: true,
		}},
	); err != nil {
		t.Fatal(err)
	}

	got, err := reader.Open(context.Background(), dst)
	if err != nil {
		t.Fatal(err)
	}
	if len(got.Tracks) != 2 {
		t.Fatalf("track count = %d, want 2: %+v", len(got.Tracks), got.Tracks)
	}
	added := got.Tracks[1]
	if added.ID == 4 || added.UID == 0 || added.UID == 404 {
		t.Fatalf("normalized ASS did not receive fresh identity: %+v", added)
	}
	if added.Type != mkv.SubtitleTrack || added.Codec != "ass" ||
		added.Name != "Imported ASS" || added.Language != "eng" ||
		added.LanguageBCP47 != "en-US" || !added.IsForced || !added.HearingImpaired {
		t.Fatalf("normalized ASS metadata wrong: %+v", added)
	}

	file, err := os.Open(dst)
	if err != nil {
		t.Fatal(err)
	}
	defer file.Close()
	br, err := reader.NewBlockReader(file, got.Info.TimecodeScale)
	if err != nil {
		t.Fatal(err)
	}
	var seen bool
	for {
		block, err := br.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			t.Fatal(err)
		}
		if block.TrackNumber == added.ID && bytes.Contains(block.Data, []byte("Normalized import")) {
			seen = true
		}
	}
	if !seen {
		t.Fatal("normalized ASS payload missing from destination Track")
	}
}

func TestTrackImportAddsPacketAudioAndDigestMatches(t *testing.T) {
	dir := t.TempDir()
	base := filepath.Join(dir, "base.mkv")
	packetPath := filepath.Join(dir, "audio.awpkt")
	dst := filepath.Join(dir, "out.mkv")

	out, err := os.Create(base)
	if err != nil {
		t.Fatal(err)
	}
	mw := writer.NewMKVWriter(out)
	if err := mw.WriteStart(); err != nil {
		t.Fatal(err)
	}
	baseTrack := mkv.Track{ID: 4, UID: 404, Type: mkv.VideoTrack, Codec: "vp9"}
	if err := mw.WriteMetadata(
		&mkv.Container{Info: mkv.SegmentInfo{TimecodeScale: 1_000_000}},
		[]mkv.Track{baseTrack},
		1000,
	); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(out, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 4, Timecode: 0, Keyframe: true, Data: []byte("video")},
	}); err != nil {
		t.Fatal(err)
	}
	if err := mw.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := out.Close(); err != nil {
		t.Fatal(err)
	}

	packets := []struct {
		ptsUs int64
		data  []byte
	}{
		{0, []byte{0xff, 0xfb, 0x10, 0x01}},
		{26_000, []byte{0xff, 0xfb, 0x10, 0x02}},
		{52_000, []byte{0xff, 0xfb, 0x10, 0x03}},
	}
	bundleSHA, contentSHA := writeNormalizedPacketFixture(t, packetPath, 80_000, packets)

	if err := EditContainerResourcesWithTrackImports(
		context.Background(),
		base,
		dst,
		nil, nil, nil, nil, nil, nil,
		[]TrackImport{{
			SourceKind: trackImportSourcePacketAudio,
			SourcePath: packetPath,
			SourceSHA256: bundleSHA,
			SourceCodec: "A_MPEG/L3",
			SampleRate: 44_100,
			Channels: 2,
			Name: "Imported MP3",
			Language: "und",
			LanguageBCP47: "en",
			Commentary: true,
		}},
	); err != nil {
		t.Fatal(err)
	}

	got, err := reader.Open(context.Background(), dst)
	if err != nil {
		t.Fatal(err)
	}
	if len(got.Tracks) != 2 {
		t.Fatalf("track count = %d, want 2: %+v", len(got.Tracks), got.Tracks)
	}
	added := got.Tracks[1]
	if added.ID == 4 || added.UID == 0 || added.UID == 404 {
		t.Fatalf("packet audio did not receive fresh identity: %+v", added)
	}
	if added.Type != mkv.AudioTrack || added.Codec != "A_MPEG/L3" ||
		added.Name != "Imported MP3" || added.LanguageBCP47 != "en" ||
		!added.Commentary {
		t.Fatalf("packet audio metadata wrong: %+v", added)
	}
	digest, err := DigestTrackContent(context.Background(), dst, added.ID)
	if err != nil {
		t.Fatal(err)
	}
	if digest.SHA256 != contentSHA {
		t.Fatalf("output digest = %s, want %s", digest.SHA256, contentSHA)
	}
	if digest.PacketCount != uint64(len(packets)) || digest.FirstMs != 0 || digest.LastMs != 52 {
		t.Fatalf("output packet evidence wrong: %+v", digest)
	}
}


func writeNormalizedPacketFixtureV2(
	t *testing.T,
	path string,
	durationUs int64,
	codecPrivate []byte,
	packets []struct {
		ptsUs int64
		data  []byte
	},
) (bundleSHA string, contentSHA string, codecPrivateSHA string) {
	t.Helper()
	if len(codecPrivate) == 0 {
		t.Fatal("CodecPrivate fixture must not be empty")
	}
	out, err := os.Create(path)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := out.Write([]byte(packetBundleMagicV2)); err != nil {
		t.Fatal(err)
	}
	if err := binary.Write(out, binary.BigEndian, durationUs); err != nil {
		t.Fatal(err)
	}
	if err := binary.Write(out, binary.BigEndian, uint64(len(packets))); err != nil {
		t.Fatal(err)
	}
	if err := binary.Write(out, binary.BigEndian, uint32(len(codecPrivate))); err != nil {
		t.Fatal(err)
	}
	if _, err := out.Write(codecPrivate); err != nil {
		t.Fatal(err)
	}

	content := sha256.New()
	var header [12]byte
	for _, packet := range packets {
		if err := binary.Write(out, binary.BigEndian, packet.ptsUs); err != nil {
			t.Fatal(err)
		}
		if err := binary.Write(out, binary.BigEndian, uint32(0)); err != nil {
			t.Fatal(err)
		}
		if err := binary.Write(out, binary.BigEndian, uint32(len(packet.data))); err != nil {
			t.Fatal(err)
		}
		if _, err := out.Write(packet.data); err != nil {
			t.Fatal(err)
		}
		binary.BigEndian.PutUint64(header[:8], uint64(packet.ptsUs/1000))
		binary.BigEndian.PutUint32(header[8:], uint32(len(packet.data)))
		_, _ = content.Write(header[:])
		_, _ = content.Write(packet.data)
	}
	if err := out.Close(); err != nil {
		t.Fatal(err)
	}

	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	bundle := sha256.Sum256(raw)
	private := sha256.Sum256(codecPrivate)
	return hex.EncodeToString(bundle[:]),
		hex.EncodeToString(content.Sum(nil)),
		hex.EncodeToString(private[:])
}

func TestTrackImportAddsAacPacketAudioAndCodecPrivateDigestMatches(t *testing.T) {
	dir := t.TempDir()
	base := filepath.Join(dir, "base.mkv")
	packetPath := filepath.Join(dir, "audio-aac.awpkt")
	dst := filepath.Join(dir, "out.mkv")

	out, err := os.Create(base)
	if err != nil {
		t.Fatal(err)
	}
	mw := writer.NewMKVWriter(out)
	if err := mw.WriteStart(); err != nil {
		t.Fatal(err)
	}
	baseTrack := mkv.Track{ID: 4, UID: 404, Type: mkv.VideoTrack, Codec: "vp9"}
	if err := mw.WriteMetadata(
		&mkv.Container{Info: mkv.SegmentInfo{TimecodeScale: 1_000_000}},
		[]mkv.Track{baseTrack},
		1000,
	); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(out, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 4, Timecode: 0, Keyframe: true, Data: []byte("video")},
	}); err != nil {
		t.Fatal(err)
	}
	if err := mw.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := out.Close(); err != nil {
		t.Fatal(err)
	}

	// AAC-LC, 44.1 kHz, stereo AudioSpecificConfig. The packet payload bytes
	// are protocol fixtures; playable AAC framing is covered by the Android
	// MediaExtractor regression.
	codecPrivate := []byte{0x12, 0x10}
	packets := []struct {
		ptsUs int64
		data  []byte
	}{
		{0, []byte{0x21, 0x10, 0x04, 0x60}},
		{23_220, []byte{0x21, 0x10, 0x04, 0x61}},
		{46_440, []byte{0x21, 0x10, 0x04, 0x62}},
	}
	bundleSHA, contentSHA, codecPrivateSHA := writeNormalizedPacketFixtureV2(
		t,
		packetPath,
		70_000,
		codecPrivate,
		packets,
	)

	if err := EditContainerResourcesWithTrackImports(
		context.Background(),
		base,
		dst,
		nil, nil, nil, nil, nil, nil,
		[]TrackImport{{
			SourceKind: trackImportSourcePacketAudio,
			SourcePath: packetPath,
			SourceSHA256: bundleSHA,
			SourceCodec: "A_AAC",
			SampleRate: 44_100,
			Channels: 2,
			Name: "Imported AAC",
			Language: "und",
		}},
	); err != nil {
		t.Fatal(err)
	}

	got, err := reader.Open(context.Background(), dst)
	if err != nil {
		t.Fatal(err)
	}
	if len(got.Tracks) != 2 {
		t.Fatalf("track count = %d, want 2: %+v", len(got.Tracks), got.Tracks)
	}
	added := got.Tracks[1]
	if added.Type != mkv.AudioTrack || added.Codec != "A_AAC" {
		t.Fatalf("AAC track metadata wrong: %+v", added)
	}
	if !bytes.Equal(added.CodecPrivate, codecPrivate) {
		t.Fatalf("CodecPrivate = %x, want %x", added.CodecPrivate, codecPrivate)
	}

	digest, err := DigestTrackContent(context.Background(), dst, added.ID)
	if err != nil {
		t.Fatal(err)
	}
	if digest.SHA256 != contentSHA {
		t.Fatalf("output packet digest = %s, want %s", digest.SHA256, contentSHA)
	}
	if digest.CodecPrivateSHA256 != codecPrivateSHA {
		t.Fatalf(
			"output CodecPrivate digest = %s, want %s",
			digest.CodecPrivateSHA256,
			codecPrivateSHA,
		)
	}
	if digest.PacketCount != uint64(len(packets)) {
		t.Fatalf("output packet count = %d, want %d", digest.PacketCount, len(packets))
	}
}

func TestTrackImportRejectsAacPacketAudioWithoutCodecPrivate(t *testing.T) {
	dir := t.TempDir()
	base := filepath.Join(dir, "base.mkv")
	packetPath := filepath.Join(dir, "audio-v1.awpkt")
	dst := filepath.Join(dir, "out.mkv")

	out, err := os.Create(base)
	if err != nil {
		t.Fatal(err)
	}
	mw := writer.NewMKVWriter(out)
	if err := mw.WriteStart(); err != nil {
		t.Fatal(err)
	}
	baseTrack := mkv.Track{ID: 4, UID: 404, Type: mkv.VideoTrack, Codec: "vp9"}
	if err := mw.WriteMetadata(
		&mkv.Container{Info: mkv.SegmentInfo{TimecodeScale: 1_000_000}},
		[]mkv.Track{baseTrack},
		1000,
	); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(out, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 4, Timecode: 0, Keyframe: true, Data: []byte("video")},
	}); err != nil {
		t.Fatal(err)
	}
	if err := mw.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := out.Close(); err != nil {
		t.Fatal(err)
	}

	packets := []struct {
		ptsUs int64
		data  []byte
	}{
		{0, []byte{0x21, 0x10, 0x04, 0x60}},
	}
	bundleSHA, _ := writeNormalizedPacketFixture(t, packetPath, 30_000, packets)

	err = EditContainerResourcesWithTrackImports(
		context.Background(),
		base,
		dst,
		nil, nil, nil, nil, nil, nil,
		[]TrackImport{{
			SourceKind: trackImportSourcePacketAudio,
			SourcePath: packetPath,
			SourceSHA256: bundleSHA,
			SourceCodec: "A_AAC",
			SampleRate: 44_100,
			Channels: 2,
		}},
	)
	if err == nil || !strings.Contains(err.Error(), "missing CodecPrivate") {
		t.Fatalf("expected missing CodecPrivate rejection, got %v", err)
	}
}
