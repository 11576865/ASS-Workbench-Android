package ops

import (
	"bytes"
	"context"
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
