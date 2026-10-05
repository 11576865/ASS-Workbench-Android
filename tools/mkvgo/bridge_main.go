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
	case "digest-track":
		runDigestTrack(os.Args[2:])
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
		case "--edit-track-meta-v2":
			if i+11 >= len(args) {
				fatal("--edit-track-meta-v2 needs <target> <name> <language> <bcp47> <default> <forced> <hearing> <visual> <descriptions> <original> <commentary>")
			}
			trackMetadataEdits = append(trackMetadataEdits, ops.TrackMetadataEdit{
				Target: args[i+1],
				Name: args[i+2],
				Language: args[i+3],
				LanguageBCP47: args[i+4],
				IsDefault: parseBoolFlag(args[i+5], "--edit-track-meta-v2 default"),
				IsForced: parseBoolFlag(args[i+6], "--edit-track-meta-v2 forced"),
				HearingImpaired: parseBoolFlag(args[i+7], "--edit-track-meta-v2 hearing"),
				VisualImpaired: parseBoolFlag(args[i+8], "--edit-track-meta-v2 visual"),
				TextDescriptions: parseBoolFlag(args[i+9], "--edit-track-meta-v2 descriptions"),
				Original: parseBoolFlag(args[i+10], "--edit-track-meta-v2 original"),
				Commentary: parseBoolFlag(args[i+11], "--edit-track-meta-v2 commentary"),
			})
			i += 11
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
		case "--edit-track-meta-v2":
			if i+11 >= len(args) {
				fatal("--edit-track-meta-v2 needs <target> <name> <language> <bcp47> <default> <forced> <hearing> <visual> <descriptions> <original> <commentary>")
			}
			trackMetadataEdits = append(trackMetadataEdits, ops.TrackMetadataEdit{
				Target: args[i+1],
				Name: args[i+2],
				Language: args[i+3],
				LanguageBCP47: args[i+4],
				IsDefault: parseBoolFlag(args[i+5], "--edit-track-meta-v2 default"),
				IsForced: parseBoolFlag(args[i+6], "--edit-track-meta-v2 forced"),
				HearingImpaired: parseBoolFlag(args[i+7], "--edit-track-meta-v2 hearing"),
				VisualImpaired: parseBoolFlag(args[i+8], "--edit-track-meta-v2 visual"),
				TextDescriptions: parseBoolFlag(args[i+9], "--edit-track-meta-v2 descriptions"),
				Original: parseBoolFlag(args[i+10], "--edit-track-meta-v2 original"),
				Commentary: parseBoolFlag(args[i+11], "--edit-track-meta-v2 commentary"),
			})
			i += 11
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
		case "--add-track-v2":
			if i+12 >= len(args) {
				fatal("--add-track-v2 needs <file> <trackID> <name> <language> <bcp47> <default> <forced> <hearing> <visual> <descriptions> <original> <commentary>")
			}
			id, err := strconv.ParseUint(args[i+2], 10, 64)
			if err != nil || id == 0 {
				fatal("--add-track-v2 trackID must be a non-zero integer")
			}
			trackImports = append(trackImports, ops.TrackImport{
				SourcePath: args[i+1],
				TrackID: id,
				Name: args[i+3],
				Language: args[i+4],
				LanguageBCP47: args[i+5],
				IsDefault: parseBoolFlag(args[i+6], "--add-track-v2 default"),
				IsForced: parseBoolFlag(args[i+7], "--add-track-v2 forced"),
				HearingImpaired: parseBoolFlag(args[i+8], "--add-track-v2 hearing"),
				VisualImpaired: parseBoolFlag(args[i+9], "--add-track-v2 visual"),
				TextDescriptions: parseBoolFlag(args[i+10], "--add-track-v2 descriptions"),
				Original: parseBoolFlag(args[i+11], "--add-track-v2 original"),
				Commentary: parseBoolFlag(args[i+12], "--add-track-v2 commentary"),
			})
			i += 12
		case "--add-track-v3":
			if i+13 >= len(args) {
				fatal("--add-track-v3 needs <file> <trackID> <trackUID-or-0> <name> <language> <bcp47> <default> <forced> <hearing> <visual> <descriptions> <original> <commentary>")
			}
			id, err := strconv.ParseUint(args[i+2], 10, 64)
			if err != nil || id == 0 {
				fatal("--add-track-v3 trackID must be a non-zero integer")
			}
			uid, err := strconv.ParseUint(args[i+3], 10, 64)
			if err != nil {
				fatal("--add-track-v3 trackUID must be an integer")
			}
			trackImports = append(trackImports, ops.TrackImport{
				SourcePath: args[i+1],
				TrackID: id,
				SourceTrackUID: uid,
				Name: args[i+4],
				Language: args[i+5],
				LanguageBCP47: args[i+6],
				IsDefault: parseBoolFlag(args[i+7], "--add-track-v3 default"),
				IsForced: parseBoolFlag(args[i+8], "--add-track-v3 forced"),
				HearingImpaired: parseBoolFlag(args[i+9], "--add-track-v3 hearing"),
				VisualImpaired: parseBoolFlag(args[i+10], "--add-track-v3 visual"),
				TextDescriptions: parseBoolFlag(args[i+11], "--add-track-v3 descriptions"),
				Original: parseBoolFlag(args[i+12], "--add-track-v3 original"),
				Commentary: parseBoolFlag(args[i+13], "--add-track-v3 commentary"),
			})
			i += 13
		case "--add-ass-track-v2":
			if i+12 >= len(args) {
				fatal("--add-ass-track-v2 needs <source.ass> <sha256> <name> <language> <bcp47> <default> <forced> <hearing> <visual> <descriptions> <original> <commentary>")
			}
			trackImports = append(trackImports, ops.TrackImport{
				SourceKind: "ass",
				SourcePath: args[i+1],
				SourceSHA256: args[i+2],
				Name: args[i+3],
				Language: args[i+4],
				LanguageBCP47: args[i+5],
				IsDefault: parseBoolFlag(args[i+6], "--add-ass-track-v2 default"),
				IsForced: parseBoolFlag(args[i+7], "--add-ass-track-v2 forced"),
				HearingImpaired: parseBoolFlag(args[i+8], "--add-ass-track-v2 hearing"),
				VisualImpaired: parseBoolFlag(args[i+9], "--add-ass-track-v2 visual"),
				TextDescriptions: parseBoolFlag(args[i+10], "--add-ass-track-v2 descriptions"),
				Original: parseBoolFlag(args[i+11], "--add-ass-track-v2 original"),
				Commentary: parseBoolFlag(args[i+12], "--add-ass-track-v2 commentary"),
			})
			i += 12
		case "--add-packet-audio-v2":
			if i+15 >= len(args) {
				fatal("--add-packet-audio-v2 needs <source.awpkt> <sha256> <codec> <sampleRate> <channels> <name> <language> <bcp47> <default> <forced> <hearing> <visual> <descriptions> <original> <commentary>")
			}
			sampleRate, err := strconv.ParseUint(args[i+4], 10, 32)
			if err != nil || sampleRate == 0 {
				fatal("--add-packet-audio-v2 sampleRate must be positive")
			}
			channels, err := strconv.ParseUint(args[i+5], 10, 8)
			if err != nil || channels == 0 {
				fatal("--add-packet-audio-v2 channels must be positive")
			}
			trackImports = append(trackImports, ops.TrackImport{
				SourceKind: "packet-audio",
				SourcePath: args[i+1],
				SourceSHA256: args[i+2],
				SourceCodec: args[i+3],
				SampleRate: uint32(sampleRate),
				Channels: uint8(channels),
				Name: args[i+6],
				Language: args[i+7],
				LanguageBCP47: args[i+8],
				IsDefault: parseBoolFlag(args[i+9], "--add-packet-audio-v2 default"),
				IsForced: parseBoolFlag(args[i+10], "--add-packet-audio-v2 forced"),
				HearingImpaired: parseBoolFlag(args[i+11], "--add-packet-audio-v2 hearing"),
				VisualImpaired: parseBoolFlag(args[i+12], "--add-packet-audio-v2 visual"),
				TextDescriptions: parseBoolFlag(args[i+13], "--add-packet-audio-v2 descriptions"),
				Original: parseBoolFlag(args[i+14], "--add-packet-audio-v2 original"),
				Commentary: parseBoolFlag(args[i+15], "--add-packet-audio-v2 commentary"),
			})
			i += 15
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
	if err := ops.EditContainerResourcesWithTrackImports(
		context.Background(),
		source,
		output,
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


func runDigestTrack(args []string) {
	if len(args) != 3 || args[1] != "--track" {
		fatal("digest-track needs <file.mkv> --track <trackID>")
	}
	trackID, err := strconv.ParseUint(args[2], 10, 64)
	if err != nil || trackID == 0 {
		fatal("invalid digest-track TrackNumber")
	}
	digest, err := ops.DigestTrackContent(context.Background(), args[0], trackID)
	if err != nil {
		fatal(err.Error())
	}
	fmt.Println("sha256=" + digest.SHA256)
	fmt.Printf("count=%d\n", digest.PacketCount)
	if digest.Seen {
		fmt.Printf("first_ms=%d\n", digest.FirstMs)
		fmt.Printf("last_ms=%d\n", digest.LastMs)
	} else {
		fmt.Println("first_ms=-")
		fmt.Println("last_ms=-")
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
		"  asswb-mkvgo replace-ass <file.mkv> -o <out.mkv> -t <trackID> [--font <font.ttf>]... [--attachment <file>]... [--remove-attachment <uid-or-name>]... [--replace-attachment <uid-or-name> <file>]... [--edit-attachment-meta <uid-or-name> <name> <description>]... [--remove-track <uid:id|number:id>]... [--edit-track-meta <uid:id|number:id> <name> <language> <default> <forced>]... [--edit-track-meta-v2 <uid:id|number:id> <name> <language> <bcp47> <default> <forced> <hearing> <visual> <descriptions> <original> <commentary>]... <edited.ass>\n" +
		"  asswb-mkvgo add-attachments <file.mkv> -o <out.mkv> --attachment <file> [--attachment <file>]...\n" +
		"  asswb-mkvgo edit-attachments <file.mkv> -o <out.mkv> [--attachment <file>]... [--remove-attachment <uid-or-name>]... [--replace-attachment <uid-or-name> <file>]... [--edit-attachment-meta <uid-or-name> <name> <description>]...\n" +
		"  asswb-mkvgo edit-container <file.mkv> -o <out.mkv> [attachment edits] [--remove-track <uid:id|number:id>]... [--edit-track-meta <uid:id|number:id> <name> <language> <default> <forced>]... [--edit-track-meta-v2 <uid:id|number:id> <name> <language> <bcp47> <default> <forced> <hearing> <visual> <descriptions> <original> <commentary>]... [--add-track <source.mkv> <trackID> <name> <language> <default> <forced>]... [--add-track-v2 <source.mkv> <trackID> <name> <language> <bcp47> <default> <forced> <hearing> <visual> <descriptions> <original> <commentary>]... [--add-track-v3 <source.mkv> <trackID> <trackUID-or-0> <name> <language> <bcp47> <default> <forced> <hearing> <visual> <descriptions> <original> <commentary>]... [--add-ass-track-v2 <source.ass> <sha256> <name> <language> <bcp47> <default> <forced> <hearing> <visual> <descriptions> <original> <commentary>]... [--add-packet-audio-v2 <source.awpkt> <sha256> <codec> <sampleRate> <channels> <name> <language> <bcp47> <default> <forced> <hearing> <visual> <descriptions> <original> <commentary>]...\n" +
		"  asswb-mkvgo extract-attachment <file.mkv> -o <file> --target <uid-or-name>\n" +
		"  asswb-mkvgo digest-track <file.mkv> --track <trackID>"
}

func fatal(message string) {
	fmt.Fprintln(os.Stderr, message)
	os.Exit(1)
}
