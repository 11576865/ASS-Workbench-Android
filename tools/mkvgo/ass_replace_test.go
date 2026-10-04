package ops

import (
	"bytes"
	"context"
	"crypto/sha256"
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
			IsDefault: false,
			IsForced: false,
		}},
		nil,
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
		got.Tracks[0].IsDefault || got.Tracks[0].IsForced {
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


func TestEditContainerResourcesAddsExternalTrackWithFreshIdentity(t *testing.T) {
	dir := t.TempDir()
	base := filepath.Join(dir, "base.mkv")
	external := filepath.Join(dir, "external.mkv")
	dst := filepath.Join(dir, "with-audio.mkv")

	keepAttachment := []byte("keep attachment")
	baseContainer := &mkv.Container{
		Info: mkv.SegmentInfo{
			TimecodeScale: 1_000_000,
			Title: "Base",
			SegmentUID: []byte("base-segment-0001"),
		},
		Chapters: []mkv.Chapter{{ID: 4, Title: "Base chapter", StartMs: 0, EndMs: 1800}},
		Attachments: []mkv.Attachment{{
			ID: 11,
			Name: "keep.txt",
			MIMEType: "text/plain",
			Data: keepAttachment,
			Size: int64(len(keepAttachment)),
		}},
		Tags: []mkv.Tag{{
			TargetType: "MOVIE",
			SimpleTags: []mkv.SimpleTag{{Name: "BASE_GLOBAL", Value: "keep"}},
		}},
	}
	baseVideo := mkv.Track{
		ID: 1, UID: 101, Type: mkv.VideoTrack, Codec: "vp9",
		Name: "Video", Language: "und", IsDefault: true,
	}
	baseSub := mkv.Track{
		ID: 5, UID: 505, Type: mkv.SubtitleTrack, Codec: "ass",
		Name: "Signs", Language: "eng",
		CodecPrivate: []byte("[Script Info]\nScriptType: v4.00+\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"),
	}
	out, err := os.Create(base)
	if err != nil {
		t.Fatal(err)
	}
	mw := writer.NewMKVWriter(out)
	if err := mw.WriteStart(); err != nil {
		t.Fatal(err)
	}
	if err := mw.WriteMetadata(baseContainer, []mkv.Track{baseVideo, baseSub}, 2000); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(out, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 1, Timecode: 0, Keyframe: true, Data: []byte{0x01}},
		{TrackNumber: 5, Timecode: 100, Duration: 700, Data: []byte("0,0,Default,,0,0,0,,Base subtitle")},
		{TrackNumber: 1, Timecode: 1000, Keyframe: true, Data: []byte{0x02}},
	}); err != nil {
		t.Fatal(err)
	}
	if err := mw.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := out.Close(); err != nil {
		t.Fatal(err)
	}

	externalContainer := &mkv.Container{
		Info: mkv.SegmentInfo{
			TimecodeScale: 1_000_000,
			Title: "External audio",
		},
		Tags: []mkv.Tag{{
			TargetID: 202,
			SimpleTags: []mkv.SimpleTag{{Name: "SOURCE_TRACK_NOTE", Value: "retarget-me"}},
		}},
	}
	externalAudio := mkv.Track{
		ID: 2, UID: 202, Type: mkv.AudioTrack, Codec: "opus",
		Name: "Source audio", Language: "jpn", IsDefault: true,
	}
	out, err = os.Create(external)
	if err != nil {
		t.Fatal(err)
	}
	mw = writer.NewMKVWriter(out)
	if err := mw.WriteStart(); err != nil {
		t.Fatal(err)
	}
	if err := mw.WriteMetadata(externalContainer, []mkv.Track{externalAudio}, 3500); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(out, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 2, Timecode: 0, Keyframe: true, Data: []byte("audio-0")},
		{TrackNumber: 2, Timecode: 1500, Keyframe: true, Data: []byte("audio-1")},
		{TrackNumber: 2, Timecode: 3000, Keyframe: true, Data: []byte("audio-2")},
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
		base,
		dst,
		nil,
		nil,
		nil,
		nil,
		nil,
		nil,
		[]TrackAddition{{
			SourcePath: external,
			SourceTrackID: 2,
			SourceTrackUID: 202,
			Name: "Japanese commentary",
			Language: "jpn",
			IsDefault: false,
			IsForced: false,
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
		t.Fatalf("surviving destination identities changed: %+v", got.Tracks)
	}
	added := got.Tracks[2]
	if added.ID != 6 {
		t.Fatalf("added TrackNumber = %d, want 6 (above original max 5)", added.ID)
	}
	if added.UID <= 505 || added.UID == 202 {
		t.Fatalf("added TrackUID = %d, want fresh destination identity above original range", added.UID)
	}
	if added.Type != mkv.AudioTrack || added.Codec != "opus" ||
		added.Name != "Japanese commentary" || added.Language != "jpn" ||
		added.IsDefault || added.IsForced {
		t.Fatalf("added track metadata/codec changed: %+v", added)
	}
	if len(got.Attachments) != 1 || got.Attachments[0].ID != 11 ||
		!bytes.Equal(got.Attachments[0].Data, keepAttachment) {
		t.Fatalf("base attachment changed: %+v", got.Attachments)
	}
	if len(got.Chapters) != 1 || got.Chapters[0].Title != "Base chapter" {
		t.Fatalf("base chapter changed: %+v", got.Chapters)
	}
	if got.DurationMs < 3500 {
		t.Fatalf("output duration = %dms, want >= 3500ms from longer added source", got.DurationMs)
	}

	var retargeted bool
	for _, tag := range got.Tags {
		if tag.TargetID != added.UID {
			continue
		}
		for _, st := range tag.SimpleTags {
			if st.Name == "SOURCE_TRACK_NOTE" && st.Value == "retarget-me" {
				retargeted = true
			}
		}
	}
	if !retargeted {
		t.Fatalf("source track-targeted tag was not retargeted to new TrackUID %d: %+v", added.UID, got.Tags)
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
	var addedPayloads []string
	lastTime := int64(-1)
	for {
		block, err := br.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			t.Fatal(err)
		}
		if block.Timecode < lastTime {
			t.Fatalf("output blocks not time-ordered: %d after %d", block.Timecode, lastTime)
		}
		lastTime = block.Timecode
		if block.TrackNumber == 6 {
			addedPayloads = append(addedPayloads, string(block.Data))
		}
	}
	if strings.Join(addedPayloads, ",") != "audio-0,audio-1,audio-2" {
		t.Fatalf("added track payloads = %v", addedPayloads)
	}
}

func TestReplaceASSCanAddExternalTrackInSameRemux(t *testing.T) {
	dir := t.TempDir()
	base := filepath.Join(dir, "base-ass.mkv")
	external := filepath.Join(dir, "external-audio.mkv")
	assPath := filepath.Join(dir, "edited.ass")
	dst := filepath.Join(dir, "combined.mkv")

	header := "[Script Info]\nScriptType: v4.00+\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"
	if err := os.WriteFile(
		assPath,
		[]byte(header+"\nDialogue: 0,0:00:00.25,0:00:01.75,Default,,0,0,0,,Edited plus imported audio"),
		0644,
	); err != nil {
		t.Fatal(err)
	}

	video := mkv.Track{ID: 1, UID: 101, Type: mkv.VideoTrack, Codec: "vp9"}
	sub := mkv.Track{
		ID: 2, UID: 202, Type: mkv.SubtitleTrack, Codec: "ass",
		Name: "Main ASS", Language: "eng", CodecPrivate: []byte(header),
	}
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
		[]mkv.Track{video, sub},
		2000,
	); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(out, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 1, Timecode: 0, Keyframe: true, Data: []byte{0x01}},
		{TrackNumber: 2, Timecode: 0, Duration: 1000, Data: []byte("0,0,Default,,0,0,0,,Old line")},
	}); err != nil {
		t.Fatal(err)
	}
	if err := mw.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := out.Close(); err != nil {
		t.Fatal(err)
	}

	audio := mkv.Track{
		ID: 1, UID: 901, Type: mkv.AudioTrack, Codec: "opus",
		Name: "External", Language: "jpn",
	}
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
		[]mkv.Track{audio},
		1800,
	); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(out, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 1, Timecode: 100, Keyframe: true, Data: []byte("external-audio")},
	}); err != nil {
		t.Fatal(err)
	}
	if err := mw.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := out.Close(); err != nil {
		t.Fatal(err)
	}

	if err := ReplaceASSWithAttachmentEdits(
		context.Background(),
		base,
		2,
		assPath,
		dst,
		nil,
		nil,
		nil,
		nil,
		nil,
		nil,
		[]TrackAddition{{
			SourcePath: external,
			SourceTrackID: 1,
			SourceTrackUID: 901,
			Name: "Imported audio",
			Language: "jpn",
			IsDefault: false,
			IsForced: false,
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
		got.Tracks[1].ID != 2 || got.Tracks[1].UID != 202 {
		t.Fatalf("base track identities changed: %+v", got.Tracks)
	}
	if got.Tracks[2].ID != 3 || got.Tracks[2].UID <= 202 || got.Tracks[2].Codec != "opus" {
		t.Fatalf("imported track identity/codec wrong: %+v", got.Tracks[2])
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
	var editedSeen, oldSeen, audioSeen bool
	for {
		block, err := br.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			t.Fatal(err)
		}
		switch block.TrackNumber {
		case 2:
			if bytes.Contains(block.Data, []byte("Edited plus imported audio")) {
				editedSeen = true
			}
			if bytes.Contains(block.Data, []byte("Old line")) {
				oldSeen = true
			}
		case 3:
			if bytes.Equal(block.Data, []byte("external-audio")) {
				audioSeen = true
			}
		}
	}
	if !editedSeen || oldSeen || !audioSeen {
		t.Fatalf("combined ASS/import payload result: edited=%v old=%v audio=%v", editedSeen, oldSeen, audioSeen)
	}
}

func TestPlanTrackAdditionsRejectsDuplicateSourceTrack(t *testing.T) {
	dir := t.TempDir()
	source := filepath.Join(dir, "source.mkv")
	track := mkv.Track{ID: 1, UID: 77, Type: mkv.AudioTrack, Codec: "opus"}

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
		{TrackNumber: 1, Timecode: 0, Keyframe: true, Data: []byte("payload")},
	}); err != nil {
		t.Fatal(err)
	}
	if err := mw.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := out.Close(); err != nil {
		t.Fatal(err)
	}

	addition := TrackAddition{SourcePath: source, SourceTrackID: 1}
	_, _, _, _, _, _, err = planTrackAdditions(
		context.Background(),
		nil,
		[]TrackAddition{addition, addition},
		mkv.FSFrom(nil),
	)
	if err == nil || !strings.Contains(err.Error(), "more than once") {
		t.Fatalf("expected duplicate source-track rejection, got %v", err)
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

	if err := EditContainerResources(
		context.Background(),
		base,
		dst,
		nil,
		nil,
		nil,
		nil,
		[]string{"uid:404"},
		nil,
		[]TrackAddition{{
			SourcePath: external,
			SourceTrackID: 2,
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


func TestPlanTrackAdditionsRejectsSourceUIDDrift(t *testing.T) {
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

	_, _, _, _, _, _, err = planTrackAdditions(
		context.Background(),
		nil,
		[]TrackAddition{{
			SourcePath: source,
			SourceTrackID: 2,
			SourceTrackUID: 999,
		}},
		mkv.FSFrom(nil),
	)
	if err == nil || !strings.Contains(err.Error(), "changed identity") {
		t.Fatalf("expected source TrackUID drift rejection, got %v", err)
	}
}


func TestEditContainerResourcesAddsStandaloneASSWithFreshIdentity(t *testing.T) {
	dir := t.TempDir()
	base := filepath.Join(dir, "base.mkv")
	assPath := filepath.Join(dir, "imported.ass")
	dst := filepath.Join(dir, "with-standalone-ass.mkv")

	container := &mkv.Container{
		Info: mkv.SegmentInfo{TimecodeScale: 1_000_000, Title: "Base"},
		Chapters: []mkv.Chapter{{ID: 7, Title: "Keep", StartMs: 0, EndMs: 1000}},
		Attachments: []mkv.Attachment{{
			ID: 9, Name: "keep.txt", MIMEType: "text/plain",
			Data: []byte("keep"), Size: 4,
		}},
	}
	video := mkv.Track{ID: 4, UID: 404, Type: mkv.VideoTrack, Codec: "vp9", Name: "Base video"}

	out, err := os.Create(base)
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

	header := "[Script Info]\nScriptType: v4.00+\n\n[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\nStyle: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"
	assBytes := []byte(header + "\nDialogue: 0,0:00:00.25,0:00:02.25,Default,,0,0,0,,Standalone import")
	if err := os.WriteFile(assPath, assBytes, 0644); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(assBytes)

	if err := EditContainerResources(
		context.Background(),
		base,
		dst,
		nil,
		nil,
		nil,
		nil,
		nil,
		nil,
		[]TrackAddition{{
			SourceKind: trackAdditionSourceASS,
			SourcePath: assPath,
			SourceSHA256: hex.EncodeToString(sum[:]),
			Name: "Standalone ASS",
			Language: "eng",
			IsDefault: false,
			IsForced: false,
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
	if got.Tracks[0].ID != 4 || got.Tracks[0].UID != 404 {
		t.Fatalf("surviving destination identity changed: %+v", got.Tracks[0])
	}
	added := got.Tracks[1]
	if added.ID != 5 || added.UID <= 404 {
		t.Fatalf("standalone ASS did not get fresh destination identity: %+v", added)
	}
	if added.Type != mkv.SubtitleTrack || added.Codec != "ass" ||
		added.Name != "Standalone ASS" || added.Language != "eng" ||
		added.IsDefault || added.IsForced {
		t.Fatalf("standalone ASS track metadata wrong: %+v", added)
	}
	if got.DurationMs < 2250 {
		t.Fatalf("duration = %dms, want >= 2250ms", got.DurationMs)
	}
	if len(got.Chapters) != 1 || got.Chapters[0].ID != 7 {
		t.Fatalf("chapters changed: %+v", got.Chapters)
	}
	if len(got.Attachments) != 1 || got.Attachments[0].ID != 9 ||
		!bytes.Equal(got.Attachments[0].Data, []byte("keep")) {
		t.Fatalf("untouched attachment changed: %+v", got.Attachments)
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
	var subtitleSeen bool
	for {
		block, err := br.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			t.Fatal(err)
		}
		if block.TrackNumber == 5 && bytes.Contains(block.Data, []byte("Standalone import")) {
			subtitleSeen = true
		}
	}
	if !subtitleSeen {
		t.Fatal("standalone ASS payload missing from imported track")
	}
}

func TestPlanTrackAdditionsRejectsStandaloneASSHashDrift(t *testing.T) {
	dir := t.TempDir()
	assPath := filepath.Join(dir, "drift.ass")
	header := "[Script Info]\nScriptType: v4.00+\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"
	if err := os.WriteFile(
		assPath,
		[]byte(header+"\nDialogue: 0,0:00:00.00,0:00:01.00,Default,,0,0,0,,Changed"),
		0644,
	); err != nil {
		t.Fatal(err)
	}

	_, _, _, _, _, err := planTrackAdditions(
		context.Background(),
		nil,
		[]TrackAddition{{
			SourceKind: trackAdditionSourceASS,
			SourcePath: assPath,
			SourceSHA256: strings.Repeat("0", 64),
			Name: "Drift",
			Language: "und",
		}},
		mkv.FSFrom(nil),
	)
	if err == nil || !strings.Contains(err.Error(), "changed identity") {
		t.Fatalf("expected standalone ASS hash drift rejection, got %v", err)
	}
}

func TestReplaceASSCanAddStandaloneASSInSameRemux(t *testing.T) {
	dir := t.TempDir()
	base := filepath.Join(dir, "base.mkv")
	editedPath := filepath.Join(dir, "edited.ass")
	importPath := filepath.Join(dir, "import.ass")
	dst := filepath.Join(dir, "combined.mkv")

	header := "[Script Info]\nScriptType: v4.00+\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"
	video := mkv.Track{ID: 1, UID: 101, Type: mkv.VideoTrack, Codec: "vp9"}
	sub := mkv.Track{ID: 2, UID: 202, Type: mkv.SubtitleTrack, Codec: "ass", CodecPrivate: []byte(header)}

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
		[]mkv.Track{video, sub},
		1500,
	); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteCluster(out, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 1, Timecode: 0, Keyframe: true, Data: []byte("video")},
		{TrackNumber: 2, Timecode: 0, Duration: 700, Data: []byte("0,0,Default,,0,0,0,,Old")},
	}); err != nil {
		t.Fatal(err)
	}
	if err := mw.Finalize(); err != nil {
		t.Fatal(err)
	}
	if err := out.Close(); err != nil {
		t.Fatal(err)
	}

	if err := os.WriteFile(
		editedPath,
		[]byte(header+"\nDialogue: 0,0:00:00.10,0:00:01.00,Default,,0,0,0,,Edited existing"),
		0644,
	); err != nil {
		t.Fatal(err)
	}
	importBytes := []byte(header+"\nDialogue: 0,0:00:00.20,0:00:01.20,Default,,0,0,0,,Imported standalone")
	if err := os.WriteFile(importPath, importBytes, 0644); err != nil {
		t.Fatal(err)
	}
	importSum := sha256.Sum256(importBytes)

	if err := ReplaceASSWithAttachmentEdits(
		context.Background(),
		base,
		2,
		editedPath,
		dst,
		nil,
		nil,
		nil,
		nil,
		nil,
		nil,
		[]TrackAddition{{
			SourceKind: trackAdditionSourceASS,
			SourcePath: importPath,
			SourceSHA256: hex.EncodeToString(importSum[:]),
			Name: "Imported",
			Language: "eng",
		}},
	); err != nil {
		t.Fatal(err)
	}

	got, err := reader.Open(context.Background(), dst)
	if err != nil {
		t.Fatal(err)
	}
	if len(got.Tracks) != 3 || got.Tracks[2].ID != 3 || got.Tracks[2].UID <= 202 {
		t.Fatalf("combined track identities wrong: %+v", got.Tracks)
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
	var editedSeen, importedSeen, oldSeen bool
	for {
		block, err := br.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			t.Fatal(err)
		}
		switch block.TrackNumber {
		case 2:
			editedSeen = editedSeen || bytes.Contains(block.Data, []byte("Edited existing"))
			oldSeen = oldSeen || bytes.Contains(block.Data, []byte("Old"))
		case 3:
			importedSeen = importedSeen || bytes.Contains(block.Data, []byte("Imported standalone"))
		}
	}
	if !editedSeen || !importedSeen || oldSeen {
		t.Fatalf("combined synthetic remux wrong: edited=%v imported=%v old=%v", editedSeen, importedSeen, oldSeen)
	}
}
