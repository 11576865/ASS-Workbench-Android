package io.github.assworkbench.app

import android.content.Context
import java.io.File
import kotlin.concurrent.thread

data class AttachmentReplacementInput(
    val target: String,
    val file: File,
)

data class AttachmentMetadataEditInput(
    val target: String,
    val name: String,
    val description: String,
)

data class TrackMetadataEditInput(
    val target: String,
    val name: String,
    val language: String,
    val isDefault: Boolean,
    val isForced: Boolean,
    val languageBcp47: String = "",
    val hearingImpaired: Boolean = false,
    val visualImpaired: Boolean = false,
    val textDescriptions: Boolean = false,
    val original: Boolean = false,
    val commentary: Boolean = false,
)

data class TrackImportInput(
    val sourceKind: ContainerTrackImportSourceKind = ContainerTrackImportSourceKind.MATROSKA_TRACK,
    val source: File,
    val trackNumber: Long? = null,
    val sourceTrackUid: Long? = null,
    val sourceSha256: String? = null,
    val sourceContentSha256: String? = null,
    val sourceCodecId: String? = null,
    val sampleRate: Int? = null,
    val channelCount: Int? = null,
    val name: String,
    val language: String,
    val isDefault: Boolean,
    val isForced: Boolean,
    val languageBcp47: String = "",
    val hearingImpaired: Boolean = false,
    val visualImpaired: Boolean = false,
    val textDescriptions: Boolean = false,
    val original: Boolean = false,
    val commentary: Boolean = false,
)

data class TrackContentDigest(
    val sha256: String,
    val packetCount: Long,
    val firstTimecodeMs: Long?,
    val lastTimecodeMs: Long?,
    val codecPrivateSha256: String? = null,
)

class MkvGoTool(private val context: Context) {
    private val executable: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libmkvgo.so")

    fun isAvailable(): Boolean {
        val file = executable
        if (!file.isFile) return false
        if (!file.canExecute()) runCatching { file.setExecutable(true, true) }
        return file.canExecute()
    }

    fun replaceAss(
        source: File,
        trackNumber: Long,
        editedAss: File,
        output: File,
        fonts: List<File> = emptyList(),
        attachments: List<File> = emptyList(),
        removeAttachments: List<String> = emptyList(),
        replaceAttachments: List<AttachmentReplacementInput> = emptyList(),
        metadataEdits: List<AttachmentMetadataEditInput> = emptyList(),
        removeTracks: List<String> = emptyList(),
        trackMetadataEdits: List<TrackMetadataEditInput> = emptyList(),
    ) {
        require(isAvailable()) { "MKV 写回工具在此 ABI 上不可用" }
        output.parentFile?.mkdirs() ?: error("输出目录不可用")
        output.delete()

        val args = mutableListOf(
            "replace-ass",
            source.absolutePath,
            "-o", output.absolutePath,
            "-t", trackNumber.toString(),
        )
        fonts
            .distinctBy { it.absolutePath }
            .forEach { font ->
                require(font.isFile && font.length() > 0L) { "字体文件不可用：" + font.name }
                args += "--font"
                args += font.absolutePath
            }
        attachments
            .distinctBy { it.absolutePath }
            .filterNot { candidate -> fonts.any { it.absolutePath == candidate.absolutePath } }
            .forEach { attachment ->
                require(attachment.isFile && attachment.length() > 0L) { "附件文件不可用：" + attachment.name }
                args += "--attachment"
                args += attachment.absolutePath
            }
        removeAttachments.distinct().forEach { target ->
            require(target.isNotBlank()) { "附件删除目标不能为空" }
            args += "--remove-attachment"
            args += target
        }
        replaceAttachments
            .distinctBy { it.target }
            .forEach { replacement ->
                require(replacement.target.isNotBlank()) { "附件替换目标不能为空" }
                require(replacement.file.isFile && replacement.file.length() > 0L) {
                    "附件替换文件不可用：" + replacement.file.name
                }
                args += "--replace-attachment"
                args += replacement.target
                args += replacement.file.absolutePath
            }
        metadataEdits
            .distinctBy { it.target }
            .forEach { metadata ->
                require(metadata.target.isNotBlank()) { "附件元数据目标不能为空" }
                require(metadata.name.isNotBlank()) { "附件名称不能为空" }
                args += "--edit-attachment-meta"
                args += metadata.target
                args += metadata.name
                args += metadata.description
            }
        removeTracks.distinct().forEach { target ->
            require(target.isNotBlank()) { "轨道删除目标不能为空" }
            args += "--remove-track"
            args += target
        }
        trackMetadataEdits
            .distinctBy { it.target }
            .forEach { metadata ->
                require(metadata.target.isNotBlank()) { "轨道元数据目标不能为空" }
                args += "--edit-track-meta-v2"
                args += metadata.target
                args += metadata.name
                args += metadata.language
                args += metadata.languageBcp47
                args += if (metadata.isDefault) "1" else "0"
                args += if (metadata.isForced) "1" else "0"
                args += if (metadata.hearingImpaired) "1" else "0"
                args += if (metadata.visualImpaired) "1" else "0"
                args += if (metadata.textDescriptions) "1" else "0"
                args += if (metadata.original) "1" else "0"
                args += if (metadata.commentary) "1" else "0"
            }
        args += editedAss.absolutePath
        run(*args.toTypedArray())
        require(output.isFile && output.length() > 0L) { "MKV 写回未生成输出文件" }
    }


    fun addAttachments(
        source: File,
        output: File,
        attachments: List<File>,
    ) {
        require(isAvailable()) { "MKV 写回工具在此 ABI 上不可用" }
        require(attachments.isNotEmpty()) { "没有待封入附件" }
        output.parentFile?.mkdirs() ?: error("输出目录不可用")
        output.delete()

        val args = mutableListOf(
            "add-attachments",
            source.absolutePath,
            "-o", output.absolutePath,
        )
        attachments
            .distinctBy { it.absolutePath }
            .forEach { attachment ->
                require(attachment.isFile && attachment.length() > 0L) { "附件文件不可用：" + attachment.name }
                args += "--attachment"
                args += attachment.absolutePath
            }
        run(*args.toTypedArray())
        require(output.isFile && output.length() > 0L) { "MKV 写回未生成输出文件" }
    }


    fun editContainer(
        source: File,
        output: File,
        additions: List<File> = emptyList(),
        removals: List<String> = emptyList(),
        replacements: List<AttachmentReplacementInput> = emptyList(),
        metadataEdits: List<AttachmentMetadataEditInput> = emptyList(),
        removeTracks: List<String> = emptyList(),
        trackMetadataEdits: List<TrackMetadataEditInput> = emptyList(),
        trackImports: List<TrackImportInput> = emptyList(),
    ) {
        require(isAvailable()) { "MKV 写回工具在此 ABI 上不可用" }
        require(
            additions.isNotEmpty() ||
                removals.isNotEmpty() ||
                replacements.isNotEmpty() ||
                metadataEdits.isNotEmpty() ||
                removeTracks.isNotEmpty() ||
                trackMetadataEdits.isNotEmpty() ||
                trackImports.isNotEmpty()
        ) { "没有待执行的容器修改" }
        output.parentFile?.mkdirs() ?: error("输出目录不可用")
        output.delete()

        val args = mutableListOf(
            "edit-container",
            source.absolutePath,
            "-o", output.absolutePath,
        )
        additions
            .distinctBy { it.absolutePath }
            .forEach { attachment ->
                require(attachment.isFile && attachment.length() > 0L) { "附件文件不可用：" + attachment.name }
                args += "--attachment"
                args += attachment.absolutePath
            }
        removals.distinct().forEach { target ->
            require(target.isNotBlank()) { "附件删除目标不能为空" }
            args += "--remove-attachment"
            args += target
        }
        replacements
            .distinctBy { it.target }
            .forEach { replacement ->
                require(replacement.target.isNotBlank()) { "附件替换目标不能为空" }
                require(replacement.file.isFile && replacement.file.length() > 0L) {
                    "附件替换文件不可用：" + replacement.file.name
                }
                args += "--replace-attachment"
                args += replacement.target
                args += replacement.file.absolutePath
            }
        metadataEdits
            .distinctBy { it.target }
            .forEach { metadata ->
                require(metadata.target.isNotBlank()) { "附件元数据目标不能为空" }
                require(metadata.name.isNotBlank()) { "附件名称不能为空" }
                args += "--edit-attachment-meta"
                args += metadata.target
                args += metadata.name
                args += metadata.description
            }
        removeTracks.distinct().forEach { target ->
            require(target.isNotBlank()) { "轨道删除目标不能为空" }
            args += "--remove-track"
            args += target
        }
        trackMetadataEdits
            .distinctBy { it.target }
            .forEach { metadata ->
                require(metadata.target.isNotBlank()) { "轨道元数据目标不能为空" }
                args += "--edit-track-meta-v2"
                args += metadata.target
                args += metadata.name
                args += metadata.language
                args += metadata.languageBcp47
                args += if (metadata.isDefault) "1" else "0"
                args += if (metadata.isForced) "1" else "0"
                args += if (metadata.hearingImpaired) "1" else "0"
                args += if (metadata.visualImpaired) "1" else "0"
                args += if (metadata.textDescriptions) "1" else "0"
                args += if (metadata.original) "1" else "0"
                args += if (metadata.commentary) "1" else "0"
            }
        trackImports
            .distinctBy {
                Triple(
                    it.sourceKind,
                    it.source.absolutePath,
                    if (it.sourceKind == ContainerTrackImportSourceKind.NORMALIZED_MEDIA_PACKETS) {
                        it.sourceContentSha256.orEmpty()
                    } else {
                        it.trackNumber.toString()
                    },
                )
            }
            .forEach { import ->
                require(import.source.isFile && import.source.length() > 0L) {
                    "轨道来源文件不可用：" + import.source.name
                }
                when (import.sourceKind) {
                    ContainerTrackImportSourceKind.MATROSKA_TRACK -> {
                        val trackNumber = requireNotNull(import.trackNumber) {
                            "Matroska 轨道来源缺少 TrackNumber"
                        }
                        require(trackNumber > 0L) { "来源 TrackNumber 必须大于 0" }
                        args += "--add-track-v3"
                        args += import.source.absolutePath
                        args += trackNumber.toString()
                        args += (import.sourceTrackUid ?: 0L).toString()
                        appendTrackMetadataArgs(args, import)
                    }

                    ContainerTrackImportSourceKind.STANDALONE_ASS,
                    ContainerTrackImportSourceKind.STANDALONE_SRT -> {
                        val sha256 = import.sourceSha256.orEmpty()
                        require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) {
                            "独立字幕规范化来源缺少有效 SHA-256 证据"
                        }
                        args += "--add-ass-track-v2"
                        args += import.source.absolutePath
                        args += sha256.lowercase()
                        appendTrackMetadataArgs(args, import)
                    }

                    ContainerTrackImportSourceKind.NORMALIZED_MEDIA_PACKETS -> {
                        val sha256 = import.sourceSha256.orEmpty()
                        require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) {
                            "媒体 packet bundle 缺少有效 SHA-256 证据"
                        }
                        val codec = import.sourceCodecId.orEmpty()
                        require(codec in setOf("A_MPEG/L3", "A_AAC")) {
                            "当前 packet audio adapter 只允许 A_MPEG/L3 或 A_AAC"
                        }
                        val sampleRate = requireNotNull(import.sampleRate) {
                            "packet audio 缺少 sample rate"
                        }
                        val channels = requireNotNull(import.channelCount) {
                            "packet audio 缺少 channel count"
                        }
                        require(sampleRate > 0) { "packet audio sample rate 无效" }
                        require(channels in 1..255) { "packet audio channel count 无效" }
                        args += "--add-packet-audio-v2"
                        args += import.source.absolutePath
                        args += sha256.lowercase()
                        args += codec
                        args += sampleRate.toString()
                        args += channels.toString()
                        appendTrackMetadataArgs(args, import)
                    }
                }
            }
        run(*args.toTypedArray())
        require(output.isFile && output.length() > 0L) { "MKV 写回未生成输出文件" }
    }

    fun editAttachments(
        source: File,
        output: File,
        additions: List<File> = emptyList(),
        removals: List<String> = emptyList(),
        replacements: List<AttachmentReplacementInput> = emptyList(),
        metadataEdits: List<AttachmentMetadataEditInput> = emptyList(),
    ) = editContainer(
        source = source,
        output = output,
        additions = additions,
        removals = removals,
        replacements = replacements,
        metadataEdits = metadataEdits,
    )

    private fun appendTrackMetadataArgs(
        args: MutableList<String>,
        import: TrackImportInput,
    ) {
        args += import.name
        args += import.language
        args += import.languageBcp47
        args += if (import.isDefault) "1" else "0"
        args += if (import.isForced) "1" else "0"
        args += if (import.hearingImpaired) "1" else "0"
        args += if (import.visualImpaired) "1" else "0"
        args += if (import.textDescriptions) "1" else "0"
        args += if (import.original) "1" else "0"
        args += if (import.commentary) "1" else "0"
    }

    fun digestTrackContent(
        source: File,
        trackNumber: Long,
    ): TrackContentDigest {
        require(isAvailable()) { "MKV 写回工具在此 ABI 上不可用" }
        require(source.isFile && source.length() > 0L) { "待验证 MKV 不可用" }
        require(trackNumber > 0L) { "待验证 TrackNumber 无效" }
        val text = run(
            "digest-track",
            source.absolutePath,
            "--track", trackNumber.toString(),
        )
        val values = text.lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }
            .toMap()
        val sha256 = values["sha256"].orEmpty()
        require(sha256.matches(Regex("[0-9a-f]{64}"))) {
            "mkvgo digest-track 未返回有效 SHA-256"
        }
        return TrackContentDigest(
            sha256 = sha256,
            packetCount = values["count"]?.toLongOrNull()
                ?: error("mkvgo digest-track 未返回 packet count"),
            firstTimecodeMs = values["first_ms"]?.takeIf { it != "-" }?.toLongOrNull(),
            lastTimecodeMs = values["last_ms"]?.takeIf { it != "-" }?.toLongOrNull(),
            codecPrivateSha256 = values["codec_private_sha256"]
                ?.takeIf { it != "-" && it.matches(Regex("[0-9a-f]{64}")) },
        )
    }

    fun extractAttachment(
        source: File,
        target: String,
        output: File,
    ) {
        require(isAvailable()) { "MKV 附件提取工具在此 ABI 上不可用" }
        require(target.isNotBlank()) { "附件提取目标不能为空" }
        output.parentFile?.mkdirs() ?: error("输出目录不可用")
        output.delete()
        run(
            "extract-attachment",
            source.absolutePath,
            "-o", output.absolutePath,
            "--target", target,
        )
        require(output.isFile) { "MKV 附件提取未生成输出文件" }
    }

    private fun run(vararg args: String): String {
        val command = ArrayList<String>(args.size + 1)
        command += executable.absolutePath
        command += args
        val process = ProcessBuilder(command)
            .directory(context.cacheDir)
            .start()

        var stdout = ""
        var stderr = ""
        val outThread = thread(name = "mkvgo-stdout") {
            stdout = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        val errThread = thread(name = "mkvgo-stderr") {
            stderr = process.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        val exit = process.waitFor()
        outThread.join()
        errThread.join()
        if (exit != 0) {
            error(
                buildString {
                    append("mkvgo 失败 (exit ").append(exit).append(')')
                    val detail = stderr.trim().ifBlank { stdout.trim() }
                    if (detail.isNotBlank()) append(": ").append(detail.takeLast(4000))
                }
            )
        }
        return stdout
    }
}
