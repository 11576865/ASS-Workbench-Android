package io.github.assworkbench.container

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class MatroskaReader(
    private val maxAttachmentBytes: Int = 64 * 1024 * 1024,
    private val maxTotalAttachmentBytes: Int = 256 * 1024 * 1024,
) {
    fun scan(
        input: InputStream,
        retainAttachments: Boolean = true,
        onAttachment: ((MatroskaAttachment) -> Unit)? = null,
    ): MatroskaScanResult {
        val reader = EbmlReader(input)
        var timecodeScaleNs = 1_000_000L
        val trackBuilders = linkedMapOf<Long, TrackBuilder>()
        val attachments = mutableListOf<MatroskaAttachment>()
        val attachmentInfos = mutableListOf<MatroskaAttachmentInfo>()
        var totalAttachmentBytes = 0
        var skippedAttachmentCount = 0
        var chapterCount = 0

        while (true) {
            val h = reader.headerOrNull() ?: break
            when (h.id) {
                ID_SEGMENT -> {
                    val segmentEnd = h.end(reader.position)
                    while (!reader.atEnd(segmentEnd)) {
                        val child = reader.headerOrNull() ?: break
                        when (child.id) {
                            ID_INFO -> {
                                val bytes = reader.readBytesChecked(child.size, 4 * 1024 * 1024)
                                val info = EbmlReader(ByteArrayInputStream(bytes))
                                while (true) {
                                    val e = info.headerOrNull() ?: break
                                    if (e.id == ID_TIMECODE_SCALE) {
                                        timecodeScaleNs = info.readUnsigned(e.size)
                                    } else {
                                        info.skipFully(e.size)
                                    }
                                }
                            }
                            ID_TRACKS -> {
                                val end = child.end(reader.position)
                                while (!reader.atEnd(end)) {
                                    val e = reader.headerOrNull() ?: break
                                    if (e.id == ID_TRACK_ENTRY) {
                                        val bytes = reader.readBytesChecked(e.size, 8 * 1024 * 1024)
                                        parseTrackEntry(bytes)?.let { trackBuilders[it.number] = it }
                                    } else {
                                        reader.skipFully(e.size)
                                    }
                                }
                            }
                            ID_ATTACHMENTS -> {
                                val end = child.end(reader.position)
                                while (!reader.atEnd(end)) {
                                    val e = reader.headerOrNull() ?: break
                                    if (e.id == ID_ATTACHED_FILE) {
                                        val maxRemaining = (maxTotalAttachmentBytes - totalAttachmentBytes).coerceAtLeast(0)
                                        val parsed = parseAttachment(
                                            reader = reader,
                                            size = e.size,
                                            maxDataBytes = maxAttachmentBytes.coerceAtMost(maxRemaining),
                                        )
                                        if (parsed != null) {
                                            attachmentInfos += parsed.info
                                            val attachment = parsed.attachment
                                            if (attachment != null) {
                                                totalAttachmentBytes += attachment.data.size
                                                onAttachment?.invoke(attachment)
                                                if (retainAttachments) attachments += attachment
                                            } else {
                                                skippedAttachmentCount++
                                            }
                                        } else {
                                            skippedAttachmentCount++
                                        }
                                    } else {
                                        reader.skipFully(e.size)
                                    }
                                }
                            }
                            ID_CLUSTER -> parseCluster(
                                reader = reader,
                                clusterSize = child.size,
                                scaleNs = timecodeScaleNs,
                                tracks = trackBuilders,
                            )
                            ID_CHAPTERS -> chapterCount += countChapterAtoms(reader, child.size)
                            else -> reader.skipFully(child.size)
                        }
                    }
                }
                else -> reader.skipFully(h.size)
            }
        }

        return MatroskaScanResult(
            subtitleTracks = trackBuilders.values
                .filter { it.codecId == "S_TEXT/ASS" }
                .map { it.build() },
            attachments = attachments,
            timecodeScaleNs = timecodeScaleNs,
            trackInfos = trackBuilders.values.map { it.info() },
            attachmentInfos = attachmentInfos,
            chapterCount = chapterCount,
            skippedAttachmentCount = skippedAttachmentCount,
        )
    }

    private fun parseTrackEntry(bytes: ByteArray): TrackBuilder? {
        val r = EbmlReader(ByteArrayInputStream(bytes))
        var number: Long? = null
        var uid: Long? = null
        var type: Long? = null
        var name = ""
        var language = ""
        var languageBcp47 = ""
        var codecId = ""
        var codecPrivate = ""
        var isDefault = true
        var isForced = false
        var hearingImpaired = false
        var visualImpaired = false
        var textDescriptions = false
        var original = false
        var commentary = false
        while (true) {
            val h = r.headerOrNull() ?: break
            when (h.id) {
                ID_TRACK_NUMBER -> number = r.readUnsigned(h.size)
                ID_TRACK_UID -> uid = r.readUnsigned(h.size)
                ID_TRACK_TYPE -> type = r.readUnsigned(h.size)
                ID_NAME -> name = r.readString(h.size)
                ID_LANGUAGE -> language = r.readString(h.size)
                ID_LANGUAGE_BCP47 -> languageBcp47 = r.readString(h.size)
                ID_CODEC_ID -> codecId = r.readString(h.size)
                ID_CODEC_PRIVATE -> codecPrivate = r.readString(h.size)
                ID_FLAG_DEFAULT -> isDefault = r.readUnsigned(h.size) != 0L
                ID_FLAG_FORCED -> isForced = r.readUnsigned(h.size) != 0L
                ID_FLAG_HEARING_IMPAIRED -> hearingImpaired = r.readUnsigned(h.size) != 0L
                ID_FLAG_VISUAL_IMPAIRED -> visualImpaired = r.readUnsigned(h.size) != 0L
                ID_FLAG_TEXT_DESCRIPTIONS -> textDescriptions = r.readUnsigned(h.size) != 0L
                ID_FLAG_ORIGINAL -> original = r.readUnsigned(h.size) != 0L
                ID_FLAG_COMMENTARY -> commentary = r.readUnsigned(h.size) != 0L
                else -> r.skipFully(h.size)
            }
        }
        val n = number ?: return null
        val resolvedType = type ?: TRACK_TYPE_UNKNOWN
        return TrackBuilder(
            number = n,
            uid = uid,
            typeCode = resolvedType,
            name = name,
            language = language,
            languageBcp47 = languageBcp47,
            codecId = codecId,
            codecPrivate = codecPrivate,
            isDefault = isDefault,
            isForced = isForced,
            hearingImpaired = hearingImpaired,
            visualImpaired = visualImpaired,
            textDescriptions = textDescriptions,
            original = original,
            commentary = commentary,
        )
    }

    private fun parseAttachment(
        reader: EbmlReader,
        size: Long,
        maxDataBytes: Int,
    ): ParsedAttachment? {
        val end = ElementHeader.end(size, reader.position)
        var uid: Long? = null
        var fileName = ""
        var mime = ""
        var description = ""
        var data = ByteArray(0)
        var dataSize: Long? = null
        var dataSkipped = false

        while (!reader.atEnd(end)) {
            val h = reader.headerOrNull() ?: break
            when (h.id) {
                ID_FILE_UID -> uid = reader.readUnsigned(h.size)
                ID_FILE_NAME -> fileName = reader.readString(h.size)
                ID_FILE_MIME -> mime = reader.readString(h.size)
                ID_FILE_DESCRIPTION -> description = reader.readString(h.size)
                ID_FILE_DATA -> {
                    dataSize = h.size.takeIf { it >= 0 }
                    if (maxDataBytes <= 0 || h.size < 0 || h.size > maxDataBytes.toLong() || h.size > Int.MAX_VALUE) {
                        reader.skipFully(h.size)
                        dataSkipped = true
                    } else {
                        data = reader.readBytesChecked(h.size, maxDataBytes)
                    }
                }
                else -> reader.skipFully(h.size)
            }
        }

        if (fileName.isBlank()) return null
        val available = !dataSkipped && data.isNotEmpty()
        val info = MatroskaAttachmentInfo(
            uid = uid,
            fileName = fileName,
            mimeType = mime,
            description = description,
            sizeBytes = dataSize,
            sha256 = data.takeIf { available }?.let(::sha256),
            dataAvailable = available,
        )
        val attachment = data.takeIf { available }?.let {
            MatroskaAttachment(uid, fileName, mime, description, it)
        }
        return ParsedAttachment(info, attachment)
    }

    private fun countChapterAtoms(reader: EbmlReader, size: Long): Int {
        val end = ElementHeader.end(size, reader.position)
        var count = 0
        while (!reader.atEnd(end)) {
            val h = reader.headerOrNull() ?: break
            when (h.id) {
                ID_EDITION_ENTRY -> count += countChapterAtoms(reader, h.size)
                ID_CHAPTER_ATOM -> {
                    count++
                    count += countChapterAtoms(reader, h.size)
                }
                else -> reader.skipFully(h.size)
            }
        }
        return count
    }

    private fun parseCluster(
        reader: EbmlReader,
        clusterSize: Long,
        scaleNs: Long,
        tracks: Map<Long, TrackBuilder>,
    ) {
        val end = ElementHeader.end(clusterSize, reader.position)
        var clusterTimecode = 0L
        while (!reader.atEnd(end)) {
            val h = reader.headerOrNull() ?: break
            when (h.id) {
                ID_CLUSTER_TIMECODE -> clusterTimecode = reader.readUnsigned(h.size)
                ID_SIMPLE_BLOCK -> {
                    readBlock(reader, h.size, clusterTimecode, scaleNs, null, tracks)
                }
                ID_BLOCK_GROUP -> {
                    val groupEnd = h.end(reader.position)
                    var durationTicks: Long? = null
                    var pending: PendingBlock? = null
                    while (!reader.atEnd(groupEnd)) {
                        val e = reader.headerOrNull() ?: break
                        when (e.id) {
                            ID_BLOCK -> pending = readBlock(reader, e.size, clusterTimecode, scaleNs, null, tracks, emit = false)
                            ID_BLOCK_DURATION -> durationTicks = reader.readUnsigned(e.size)
                            else -> reader.skipFully(e.size)
                        }
                    }
                    if (pending != null) {
                        val durationMs = durationTicks?.let { ticksToMs(it, scaleNs) }
                        tracks[pending.trackNumber]?.packets?.add(
                            MatroskaSubtitlePacket(pending.startMs, durationMs, pending.payload)
                        )
                    }
                }
                else -> reader.skipFully(h.size)
            }
        }
    }

    private fun readBlock(
        reader: EbmlReader,
        size: Long,
        clusterTimecode: Long,
        scaleNs: Long,
        durationTicks: Long?,
        tracks: Map<Long, TrackBuilder>,
        emit: Boolean = true,
    ): PendingBlock? {
        if (size < 4 || size > Int.MAX_VALUE) {
            reader.skipFully(size)
            return null
        }
        val startPos = reader.position
        val first = reader.readByte()
        val vintLen = EbmlReader.vintLength(first)
        if (vintLen <= 0 || vintLen > 8 || vintLen.toLong() + 3 > size) {
            reader.skipFully(size - 1)
            return null
        }
        var trackNumber = (first and (0xFF ushr vintLen)).toLong()
        repeat(vintLen - 1) { trackNumber = (trackNumber shl 8) or reader.readByte().toLong() }
        val hi = reader.readByte()
        val lo = reader.readByte()
        val relative = (((hi shl 8) or lo).toShort()).toInt()
        val flags = reader.readByte()
        val consumed = reader.position - startPos
        val remaining = size - consumed
        val builder = tracks[trackNumber]
        val lacing = (flags ushr 1) and 0x03
        if (builder == null || builder.codecId != "S_TEXT/ASS" || lacing != 0) {
            reader.skipFully(remaining)
            return null
        }
        val payload = String(reader.readBytesChecked(remaining, 8 * 1024 * 1024), StandardCharsets.UTF_8)
        val absoluteTicks = clusterTimecode + relative
        val startMs = ticksToMs(absoluteTicks.coerceAtLeast(0), scaleNs)
        val pending = PendingBlock(trackNumber, startMs, payload)
        if (emit) {
            val durationMs = durationTicks?.let { ticksToMs(it, scaleNs) }
            builder.packets += MatroskaSubtitlePacket(startMs, durationMs, payload)
        }
        return pending
    }

    private fun ticksToMs(ticks: Long, scaleNs: Long): Long =
        ((ticks.toDouble() * scaleNs.toDouble()) / 1_000_000.0).toLong()

    private data class PendingBlock(val trackNumber: Long, val startMs: Long, val payload: String)

    private data class ParsedAttachment(
        val info: MatroskaAttachmentInfo,
        val attachment: MatroskaAttachment?,
    )

    private data class TrackBuilder(
        val number: Long,
        val uid: Long?,
        val typeCode: Long,
        val name: String,
        val language: String,
        val languageBcp47: String,
        val codecId: String,
        val codecPrivate: String,
        val isDefault: Boolean,
        val isForced: Boolean,
        val hearingImpaired: Boolean,
        val visualImpaired: Boolean,
        val textDescriptions: Boolean,
        val original: Boolean,
        val commentary: Boolean,
        val packets: MutableList<MatroskaSubtitlePacket> = mutableListOf(),
    ) {
        fun build() = MatroskaSubtitleTrack(number, uid, name, language, codecId, codecPrivate, packets.toList())

        fun info() = MatroskaTrackInfo(
            number = number,
            uid = uid,
            typeCode = typeCode,
            kind = trackKind(typeCode),
            name = name,
            language = language,
            languageBcp47 = languageBcp47,
            codecId = codecId,
            isDefault = isDefault,
            isForced = isForced,
            hearingImpaired = hearingImpaired,
            visualImpaired = visualImpaired,
            textDescriptions = textDescriptions,
            original = original,
            commentary = commentary,
            contentHash = if (codecId == "S_TEXT/ASS") {
                val bytes = buildString {
                    append(codecPrivate)
                    packets.forEach { packet ->
                        append('\u0000').append(packet.startMs)
                        append(':').append(packet.durationMs ?: -1L)
                        append(':').append(packet.payload)
                    }
                }.toByteArray(StandardCharsets.UTF_8)
                sha256(bytes)
            } else {
                null
            },
        )
    }

    private data class ElementHeader(val id: Long, val size: Long) {
        fun end(contentStart: Long): Long = end(size, contentStart)
        companion object {
            fun end(size: Long, contentStart: Long): Long =
                if (size == UNKNOWN_SIZE) Long.MAX_VALUE else contentStart + size
        }
    }

    private class EbmlReader(private val input: InputStream) {
        var position: Long = 0
            private set

        fun headerOrNull(): ElementHeader? {
            val first = input.read()
            if (first < 0) return null
            position++
            val idLen = vintLength(first)
            if (idLen <= 0 || idLen > 4) throw IllegalArgumentException("Invalid EBML id")
            var id = first.toLong()
            repeat(idLen - 1) {
                id = (id shl 8) or readByte().toLong()
            }

            val sizeFirst = readByte()
            val sizeLen = vintLength(sizeFirst)
            if (sizeLen <= 0 || sizeLen > 8) throw IllegalArgumentException("Invalid EBML size")
            var size = (sizeFirst and (0xFF ushr sizeLen)).toLong()
            repeat(sizeLen - 1) {
                size = (size shl 8) or readByte().toLong()
            }
            val unknownMarker = (1L shl (7 * sizeLen)) - 1L
            if (size == unknownMarker) size = UNKNOWN_SIZE
            return ElementHeader(id, size)
        }

        fun readByte(): Int {
            val value = input.read()
            if (value < 0) throw EOFException()
            position++
            return value
        }

        fun readUnsigned(size: Long): Long {
            require(size in 1..8) { "Unsupported integer size: $size" }
            var result = 0L
            repeat(size.toInt()) { result = (result shl 8) or readByte().toLong() }
            return result
        }

        fun readString(size: Long): String =
            String(readBytesChecked(size, 8 * 1024 * 1024), StandardCharsets.UTF_8).trimEnd('\u0000')

        fun readBytesChecked(size: Long, max: Int): ByteArray {
            require(size >= 0 && size <= max.toLong() && size <= Int.MAX_VALUE) { "Element too large: $size" }
            val out = ByteArray(size.toInt())
            var offset = 0
            while (offset < out.size) {
                val n = input.read(out, offset, out.size - offset)
                if (n < 0) throw EOFException()
                offset += n
                position += n
            }
            return out
        }

        fun skipFully(size: Long) {
            if (size == UNKNOWN_SIZE) {
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    position += n
                }
                return
            }
            var remaining = size
            val buffer = ByteArray(64 * 1024)
            while (remaining > 0) {
                val skipped = input.skip(remaining)
                if (skipped > 0) {
                    position += skipped
                    remaining -= skipped
                } else {
                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (n < 0) throw EOFException()
                    position += n
                    remaining -= n
                }
            }
        }

        fun atEnd(end: Long): Boolean = end != Long.MAX_VALUE && position >= end

        companion object {
            fun vintLength(first: Int): Int {
                var mask = 0x80
                for (length in 1..8) {
                    if ((first and mask) != 0) return length
                    mask = mask ushr 1
                }
                return -1
            }
        }
    }

    companion object {
        private const val UNKNOWN_SIZE = -1L
        private const val TRACK_TYPE_UNKNOWN = -1L

        private fun trackKind(typeCode: Long): MatroskaTrackKind = when (typeCode) {
            0x01L -> MatroskaTrackKind.VIDEO
            0x02L -> MatroskaTrackKind.AUDIO
            0x03L -> MatroskaTrackKind.COMPLEX
            0x10L -> MatroskaTrackKind.LOGO
            0x11L -> MatroskaTrackKind.SUBTITLE
            0x12L -> MatroskaTrackKind.BUTTONS
            0x20L -> MatroskaTrackKind.CONTROL
            0x21L -> MatroskaTrackKind.METADATA
            else -> MatroskaTrackKind.OTHER
        }

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }

        private const val ID_SEGMENT = 0x18538067L
        private const val ID_INFO = 0x1549A966L
        private const val ID_TIMECODE_SCALE = 0x2AD7B1L
        private const val ID_TRACKS = 0x1654AE6BL
        private const val ID_TRACK_ENTRY = 0xAEL
        private const val ID_TRACK_NUMBER = 0xD7L
        private const val ID_TRACK_UID = 0x73C5L
        private const val ID_TRACK_TYPE = 0x83L
        private const val ID_NAME = 0x536EL
        private const val ID_LANGUAGE = 0x22B59CL
        private const val ID_LANGUAGE_BCP47 = 0x22B59DL
        private const val ID_CODEC_ID = 0x86L
        private const val ID_CODEC_PRIVATE = 0x63A2L
        private const val ID_FLAG_DEFAULT = 0x88L
        private const val ID_FLAG_FORCED = 0x55AAL
        private const val ID_FLAG_HEARING_IMPAIRED = 0x55ABL
        private const val ID_FLAG_VISUAL_IMPAIRED = 0x55ACL
        private const val ID_FLAG_TEXT_DESCRIPTIONS = 0x55ADL
        private const val ID_FLAG_ORIGINAL = 0x55AEL
        private const val ID_FLAG_COMMENTARY = 0x55AFL
        private const val ID_ATTACHMENTS = 0x1941A469L
        private const val ID_ATTACHED_FILE = 0x61A7L
        private const val ID_FILE_DESCRIPTION = 0x467EL
        private const val ID_FILE_NAME = 0x466EL
        private const val ID_FILE_MIME = 0x4660L
        private const val ID_FILE_DATA = 0x465CL
        private const val ID_FILE_UID = 0x46AEL
        private const val ID_CHAPTERS = 0x1043A770L
        private const val ID_EDITION_ENTRY = 0x45B9L
        private const val ID_CHAPTER_ATOM = 0xB6L
        private const val ID_CLUSTER = 0x1F43B675L
        private const val ID_CLUSTER_TIMECODE = 0xE7L
        private const val ID_SIMPLE_BLOCK = 0xA3L
        private const val ID_BLOCK_GROUP = 0xA0L
        private const val ID_BLOCK = 0xA1L
        private const val ID_BLOCK_DURATION = 0x9BL
    }
}
