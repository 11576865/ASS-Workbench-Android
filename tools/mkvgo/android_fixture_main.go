package main

import (
	"bytes"
	"fmt"
	"os"
	"path/filepath"

	"github.com/gravity-zero/mkvgo/mkv"
	"github.com/gravity-zero/mkvgo/mkv/writer"
)

func main() {
	if len(os.Args) != 2 {
		panic("usage: asswb-fixture <output.mkv>")
	}
	outPath := os.Args[1]
	if err := os.MkdirAll(filepath.Dir(outPath), 0o755); err != nil {
		panic(err)
	}

	header := "[Script Info]\nScriptType: v4.00+\n\n[V4+ Styles]\n" +
		"Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n" +
		"Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1\n\n[Events]\n" +
		"Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"

	video := mkv.Track{ID: 1, UID: 101, Type: mkv.VideoTrack, Codec: "vp9", IsDefault: true}
	sub := mkv.Track{
		ID: 2, UID: 202, Type: mkv.SubtitleTrack, Codec: "ass",
		Language: "eng", Name: "Regression ASS", IsDefault: true,
		CodecPrivate: []byte(header),
	}
	font := bytes.Repeat([]byte("fixture-font-data-"), 32)
	container := &mkv.Container{
		Info: mkv.SegmentInfo{
			Title: "ASS Workbench Android MKV regression fixture",
			TimecodeScale: 1_000_000,
			MuxingApp: "asswb-fixture",
			WritingApp: "asswb-fixture",
			SegmentUID: []byte("asswb-fixture-01"),
		},
		Attachments: []mkv.Attachment{{
			ID: 9,
			Name: "Fixture.ttf",
			MIMEType: "font/ttf",
			Data: font,
			Size: int64(len(font)),
		}},
	}

	f, err := os.Create(outPath)
	if err != nil {
		panic(err)
	}
	mw := writer.NewMKVWriter(f)
	if err := mw.WriteStart(); err != nil {
		panic(err)
	}
	if err := mw.WriteMetadata(container, []mkv.Track{video, sub}, 3_000); err != nil {
		panic(err)
	}
	if err := writer.WriteCluster(f, 0, 1_000_000, []mkv.Block{
		{TrackNumber: 1, Timecode: 0, Keyframe: true, Data: []byte{0x01}},
		{TrackNumber: 2, Timecode: 250, Duration: 1_000, Data: []byte("0,0,Default,,0,0,0,,Old line")},
		{TrackNumber: 1, Timecode: 1_500, Keyframe: true, Data: []byte{0x02}},
	}); err != nil {
		panic(err)
	}
	if err := mw.Finalize(); err != nil {
		panic(err)
	}
	if err := f.Close(); err != nil {
		panic(err)
	}
	fmt.Printf("wrote %s\n", outPath)
}
