package main

import (
	"context"
	"fmt"
	"os"
	"strconv"

	"github.com/gravity-zero/mkvgo/mkv/ops"
)

func main() {
	if len(os.Args) < 2 || os.Args[1] != "replace-ass" {
		fatal("usage: asswb-mkvgo replace-ass <file.mkv> -o <out.mkv> -t <trackID> [--font <font.ttf>]... <edited.ass>")
	}

	args := os.Args[2:]
	if len(args) < 6 {
		fatal("usage: asswb-mkvgo replace-ass <file.mkv> -o <out.mkv> -t <trackID> [--font <font.ttf>]... <edited.ass>")
	}
	source := args[0]
	var output, assPath string
	var fontPaths []string
	var trackID uint64
	for i := 1; i < len(args); i++ {
		switch args[i] {
		case "-o":
			i++
			if i >= len(args) {
				fatal("-o needs a value")
			}
			output = args[i]
		case "-t":
			i++
			if i >= len(args) {
				fatal("-t needs a value")
			}
			id, err := strconv.ParseUint(args[i], 10, 64)
			if err != nil || id == 0 {
				fatal("invalid track ID")
			}
			trackID = id
		case "--font":
			i++
			if i >= len(args) {
				fatal("--font needs a value")
			}
			fontPaths = append(fontPaths, args[i])
		default:
			if len(args[i]) > 0 && args[i][0] == '-' {
				fatal("unknown flag: " + args[i])
			}
			assPath = args[i]
		}
	}

	if output == "" || assPath == "" || trackID == 0 {
		fatal("usage: asswb-mkvgo replace-ass <file.mkv> -o <out.mkv> -t <trackID> [--font <font.ttf>]... <edited.ass>")
	}
	if _, err := os.Stat(output); err == nil {
		fatal("output already exists: " + output)
	}
	if err := ops.ReplaceASSWithFonts(context.Background(), source, trackID, assPath, output, fontPaths); err != nil {
		fatal(err.Error())
	}
}

func fatal(message string) {
	fmt.Fprintln(os.Stderr, message)
	os.Exit(1)
}
