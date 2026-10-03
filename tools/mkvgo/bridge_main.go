package main

import (
	"context"
	"fmt"
	"os"
	"strconv"

	"github.com/gravity-zero/mkvgo/mkv/ops"
)

func main() {
	if len(os.Args) < 2 {
		fatal(usage())
	}
	switch os.Args[1] {
	case "replace-ass":
		runReplaceASS(os.Args[2:])
	case "add-attachments":
		runAddAttachments(os.Args[2:])
	default:
		fatal(usage())
	}
}

func runReplaceASS(args []string) {
	if len(args) < 6 {
		fatal(usage())
	}
	source := args[0]
	var output, assPath string
	var fontPaths, attachmentPaths []string
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
		case "--attachment":
			i++
			if i >= len(args) {
				fatal("--attachment needs a value")
			}
			attachmentPaths = append(attachmentPaths, args[i])
		default:
			if len(args[i]) > 0 && args[i][0] == '-' {
				fatal("unknown flag: " + args[i])
			}
			assPath = args[i]
		}
	}

	if output == "" || assPath == "" || trackID == 0 {
		fatal(usage())
	}
	ensureOutputAbsent(output)
	if err := ops.ReplaceASSWithFontsAndAttachments(
		context.Background(),
		source,
		trackID,
		assPath,
		output,
		fontPaths,
		attachmentPaths,
	); err != nil {
		fatal(err.Error())
	}
}

func runAddAttachments(args []string) {
	if len(args) < 4 {
		fatal(usage())
	}
	source := args[0]
	var output string
	var attachmentPaths []string
	for i := 1; i < len(args); i++ {
		switch args[i] {
		case "-o":
			i++
			if i >= len(args) {
				fatal("-o needs a value")
			}
			output = args[i]
		case "--attachment":
			i++
			if i >= len(args) {
				fatal("--attachment needs a value")
			}
			attachmentPaths = append(attachmentPaths, args[i])
		default:
			fatal("unknown argument: " + args[i])
		}
	}
	if output == "" || len(attachmentPaths) == 0 {
		fatal(usage())
	}
	ensureOutputAbsent(output)
	if err := ops.AddAttachments(context.Background(), source, output, attachmentPaths); err != nil {
		fatal(err.Error())
	}
}

func ensureOutputAbsent(path string) {
	if _, err := os.Stat(path); err == nil {
		fatal("output already exists: " + path)
	}
}

func usage() string {
	return "usage:\n" +
		"  asswb-mkvgo replace-ass <file.mkv> -o <out.mkv> -t <trackID> [--font <font.ttf>]... [--attachment <file>]... <edited.ass>\n" +
		"  asswb-mkvgo add-attachments <file.mkv> -o <out.mkv> --attachment <file> [--attachment <file>]..."
}

func fatal(message string) {
	fmt.Fprintln(os.Stderr, message)
	os.Exit(1)
}
