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
)

data class TrackAdditionInput(
    val sourceKind: ContainerTrackImportSourceKind = ContainerTrackImportSourceKind.MATROSKA_TRACK,
    val source: File,
    val sourceTrackNumber: Long? = null,
    val sourceTrackUid: Long? = null,
    val sourceSha256: String? = null,
    val name: String,
    val language: String,
    val isDefault: Boolean,
    val isForced: Boolean,
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
        addTracks: List<TrackAdditionInput> = emptyList(),
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
                args += "--edit-track-meta"
                args += metadata.target
                args += metadata.name
                args += metadata.language
                args += if (metadata.isDefault) "1" else "0"
                args += if (metadata.isForced) "1" else "0"
            }
        appendTrackAdditions(args, addTracks)
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
        addTracks: List<TrackAdditionInput> = emptyList(),
    ) {
        require(isAvailable()) { "MKV 写回工具在此 ABI 上不可用" }
        require(
            additions.isNotEmpty() ||
                removals.isNotEmpty() ||
                replacements.isNotEmpty() ||
                metadataEdits.isNotEmpty() ||
                removeTracks.isNotEmpty() ||
                trackMetadataEdits.isNotEmpty() ||
                addTracks.isNotEmpty()
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
                args += "--edit-track-meta"
                args += metadata.target
                args += metadata.name
                args += metadata.language
                args += if (metadata.isDefault) "1" else "0"
                args += if (metadata.isForced) "1" else "0"
            }
        appendTrackAdditions(args, addTracks)
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

    private fun appendTrackAdditions(
        args: MutableList<String>,
        addTracks: List<TrackAdditionInput>,
    ) {
        addTracks
            .distinctBy {
                buildString {
                    append(it.sourceKind.name)
                    append('\u0000').append(it.source.absolutePath)
                    append('\u0000').append(it.sourceTrackNumber ?: 0L)
                }
            }
            .forEach { addition ->
                require(addition.source.isFile && addition.source.length() > 0L) {
                    "轨道来源文件不可用：" + addition.source.name
                }
                when (addition.sourceKind) {
                    ContainerTrackImportSourceKind.MATROSKA_TRACK -> {
                        val trackNumber = requireNotNull(addition.sourceTrackNumber) {
                            "Matroska 轨道来源缺少 TrackNumber"
                        }
                        require(trackNumber > 0L) { "轨道来源 TrackNumber 无效" }
                        args += "--add-track"
                        args += addition.source.absolutePath
                        args += trackNumber.toString()
                        args += (addition.sourceTrackUid ?: 0L).toString()
                        args += addition.name
                        args += addition.language
                        args += if (addition.isDefault) "1" else "0"
                        args += if (addition.isForced) "1" else "0"
                    }
                    ContainerTrackImportSourceKind.STANDALONE_ASS,
                    ContainerTrackImportSourceKind.STANDALONE_SRT -> {
                        val sha256 = addition.sourceSha256.orEmpty()
                        require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) {
                            "独立 ASS 来源缺少有效 SHA-256 证据"
                        }
                        args += "--add-ass-track"
                        args += addition.source.absolutePath
                        args += sha256.lowercase()
                        args += addition.name
                        args += addition.language
                        args += if (addition.isDefault) "1" else "0"
                        args += if (addition.isForced) "1" else "0"
                    }
                }
            }
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
