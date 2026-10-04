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
	case "edit-attachments":
		runEditAttachments(os.Args[2:])
	case "edit-container":
		runEditContainer(os.Args[2:])
	case "extract-attachment":
		runExtractAttachment(os.Args[2:])
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
	var fontPaths, attachmentPaths, removeTargets []string
	var replacements []ops.AttachmentReplacement
	var metadataEdits []ops.AttachmentMetadataEdit
	var removeTrackTargets []string
	var trackMetadataEdits []ops.TrackMetadataEdit
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
		case "--remove-attachment":
			i++
			if i >= len(args) {
				fatal("--remove-attachment needs a value")
			}
			removeTargets = append(removeTargets, args[i])
		case "--replace-attachment":
			if i+2 >= len(args) {
				fatal("--replace-attachment needs <target> <file>")
			}
			replacements = append(replacements, ops.AttachmentReplacement{
				Target: args[i+1],
				Path: args[i+2],
			})
			i += 2
		case "--edit-attachment-meta":
			if i+3 >= len(args) {
				fatal("--edit-attachment-meta needs <target> <name> <description>")
			}
			metadataEdits = append(metadataEdits, ops.AttachmentMetadataEdit{
				Target: args[i+1],
				Name: args[i+2],
				Description: args[i+3],
			})
			i += 3
		case "--remove-track":
			i++
			if i >= len(args) {
				fatal("--remove-track needs a value")
			}
			removeTrackTargets = append(removeTrackTargets, args[i])
		case "--edit-track-meta":
			if i+5 >= len(args) {
				fatal("--edit-track-meta needs <target> <name> <language> <default> <forced>")
			}
			trackMetadataEdits = append(trackMetadataEdits, ops.TrackMetadataEdit{
				Target: args[i+1],
				Name: args[i+2],
				Language: args[i+3],
				IsDefault: parseBoolFlag(args[i+4], "--edit-track-meta default"),
				IsForced: parseBoolFlag(args[i+5], "--edit-track-meta forced"),
			})
			i += 5
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
	if err := ops.ReplaceASSWithFontsAndAttachmentEdits(
		context.Background(),
		source,
		trackID,
		assPath,
		output,
		fontPaths,
		attachmentPaths,
		removeTargets,
		replacements,
		metadataEdits,
		removeTrackTargets,
		trackMetadataEdits,
		trackImports,
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


func runEditAttachments(args []string) {
	if len(args) < 4 {
		fatal(usage())
	}
	source := args[0]
	var output string
	var attachmentPaths, removeTargets []string
	var replacements []ops.AttachmentReplacement
	var metadataEdits []ops.AttachmentMetadataEdit
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
		case "--remove-attachment":
			i++
			if i >= len(args) {
				fatal("--remove-attachment needs a value")
			}
			removeTargets = append(removeTargets, args[i])
		case "--replace-attachment":
			if i+2 >= len(args) {
				fatal("--replace-attachment needs <target> <file>")
			}
			replacements = append(replacements, ops.AttachmentReplacement{
				Target: args[i+1],
				Path: args[i+2],
			})
			i += 2
		case "--edit-attachment-meta":
			if i+3 >= len(args) {
				fatal("--edit-attachment-meta needs <target> <name> <description>")
			}
			metadataEdits = append(metadataEdits, ops.AttachmentMetadataEdit{
				Target: args[i+1],
				Name: args[i+2],
				Description: args[i+3],
			})
			i += 3
		default:
			fatal("unknown argument: " + args[i])
		}
	}
	if output == "" || (len(attachmentPaths) == 0 && len(removeTargets) == 0 && len(replacements) == 0 && len(metadataEdits) == 0) {
		fatal(usage())
	}
	ensureOutputAbsent(output)
	if err := ops.EditAttachments(
		context.Background(),
		source,
		output,
		attachmentPaths,
		removeTargets,
		replacements,
		metadataEdits,
	); err != nil {
		fatal(err.Error())
	}
}

func runEditContainer(args []string) {
	if len(args) < 4 {
		fatal(usage())
	}
	source := args[0]
	var output string
	var attachmentPaths, removeTargets, removeTrackTargets []string
	var replacements []ops.AttachmentReplacement
	var metadataEdits []ops.AttachmentMetadataEdit
	var trackMetadataEdits []ops.TrackMetadataEdit
	var trackImports []ops.TrackImport
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
		case "--remove-attachment":
			i++
			if i >= len(args) {
				fatal("--remove-attachment needs a value")
			}
			removeTargets = append(removeTargets, args[i])
		case "--replace-attachment":
			if i+2 >= len(args) {
				fatal("--replace-attachment needs <target> <file>")
			}
			replacements = append(replacements, ops.AttachmentReplacement{
				Target: args[i+1],
				Path: args[i+2],
			})
			i += 2
		case "--edit-attachment-meta":
			if i+3 >= len(args) {
				fatal("--edit-attachment-meta needs <target> <name> <description>")
			}
			metadataEdits = append(metadataEdits, ops.AttachmentMetadataEdit{
				Target: args[i+1],
				Name: args[i+2],
				Description: args[i+3],
			})
			i += 3
		case "--remove-track":
			i++
			if i >= len(args) {
				fatal("--remove-track needs a value")
			}
			removeTrackTargets = append(removeTrackTargets, args[i])
		case "--edit-track-meta":
			if i+5 >= len(args) {
				fatal("--edit-track-meta needs <target> <name> <language> <default> <forced>")
			}
			trackMetadataEdits = append(trackMetadataEdits, ops.TrackMetadataEdit{
				Target: args[i+1],
				Name: args[i+2],
				Language: args[i+3],
				IsDefault: parseBoolFlag(args[i+4], "--edit-track-meta default"),
				IsForced: parseBoolFlag(args[i+5], "--edit-track-meta forced"),
			})
			i += 5
		case "--add-track":
			if i+6 >= len(args) {
				fatal("--add-track needs <file> <trackID> <name> <language> <default> <forced>")
			}
			id, err := strconv.ParseUint(args[i+2], 10, 64)
			if err != nil || id == 0 {
				fatal("--add-track trackID must be a non-zero integer")
			}
			trackImports = append(trackImports, ops.TrackImport{
				SourcePath: args[i+1],
				TrackID: id,
				Name: args[i+3],
				Language: args[i+4],
				IsDefault: parseBoolFlag(args[i+5], "--add-track default"),
				IsForced: parseBoolFlag(args[i+6], "--add-track forced"),
			})
			i += 6
		default:
			fatal("unknown argument: " + args[i])
		}
	}
	if output == "" || (
		len(attachmentPaths) == 0 &&
			len(removeTargets) == 0 &&
			len(replacements) == 0 &&
			len(metadataEdits) == 0 &&
			len(removeTrackTargets) == 0 &&
			len(trackMetadataEdits) == 0 &&
			len(trackImports) == 0) {
		fatal(usage())
	}
	ensureOutputAbsent(output)
	if err := ops.EditContainerResources(
		context.Background(),
		source,
		output,
		attachmentPaths,
		removeTargets,
		replacements,
		metadataEdits,
		removeTrackTargets,
		trackMetadataEdits,
	); err != nil {
		fatal(err.Error())
	}
}

func runExtractAttachment(args []string) {
	if len(args) < 5 {
		fatal(usage())
	}
	source := args[0]
	var output, target string
	for i := 1; i < len(args); i++ {
		switch args[i] {
		case "-o":
			i++
			if i >= len(args) {
				fatal("-o needs a value")
			}
			output = args[i]
		case "--target":
			i++
			if i >= len(args) {
				fatal("--target needs a value")
			}
			target = args[i]
		default:
			fatal("unknown argument: " + args[i])
		}
	}
	if output == "" || target == "" {
		fatal(usage())
	}
	ensureOutputAbsent(output)
	if err := ops.ExtractAttachmentTarget(
		context.Background(),
		source,
		target,
		output,
	); err != nil {
		fatal(err.Error())
	}
}

func parseBoolFlag(value, label string) bool {
	switch value {
	case "1", "true":
		return true
	case "0", "false":
		return false
	default:
		fatal(label + " must be 0/1 or false/true")
		return false
	}
}

func ensureOutputAbsent(path string) {
	if _, err := os.Stat(path); err == nil {
		fatal("output already exists: " + path)
	}
}

func usage() string {
	return "usage:\n" +
		"  asswb-mkvgo replace-ass <file.mkv> -o <out.mkv> -t <trackID> [--font <font.ttf>]... [--attachment <file>]... [--remove-attachment <uid-or-name>]... [--replace-attachment <uid-or-name> <file>]... [--edit-attachment-meta <uid-or-name> <name> <description>]... [--remove-track <uid:id|number:id>]... [--edit-track-meta <uid:id|number:id> <name> <language> <default> <forced>]... <edited.ass>\n" +
		"  asswb-mkvgo add-attachments <file.mkv> -o <out.mkv> --attachment <file> [--attachment <file>]...\n" +
		"  asswb-mkvgo edit-attachments <file.mkv> -o <out.mkv> [--attachment <file>]... [--remove-attachment <uid-or-name>]... [--replace-attachment <uid-or-name> <file>]... [--edit-attachment-meta <uid-or-name> <name> <description>]...\n" +
		"  asswb-mkvgo edit-container <file.mkv> -o <out.mkv> [attachment edits] [--remove-track <uid:id|number:id>]... [--edit-track-meta <uid:id|number:id> <name> <language> <default> <forced>]... [--add-track <source.mkv> <trackID> <name> <language> <default> <forced>]...\n" +
		"  asswb-mkvgo extract-attachment <file.mkv> -o <file> --target <uid-or-name>"
}

func fatal(message string) {
	fmt.Fprintln(os.Stderr, message)
	os.Exit(1)
}
