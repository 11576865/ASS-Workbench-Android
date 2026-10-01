package io.github.assworkbench.domain

enum class WorkspacePresentationMode { FIXED, CANVAS_EXPERIMENTAL }

data class AssWorkbenchProjectManifest(
    val schemaVersion: Int = 1,
    val title: String,
    val subtitleUri: String? = null,
    val sourceSubtitleUri: String? = null,
    val sourceFormat: SubtitleSourceFormat = SubtitleSourceFormat.UNKNOWN,
    val videoUri: String? = null,
    val mkvUri: String? = null,
    val mkvTrackNumber: Long? = null,
    val workspaceMode: WorkspacePresentationMode = WorkspacePresentationMode.FIXED,
    val importedFontUris: List<String> = emptyList(),
    val reviewIdentity: String? = null,
)

object AssWorkbenchProjectCodec {
    fun write(project: AssWorkbenchProjectManifest): String = buildString {
        append("ASSWB_PROJECT=").append(project.schemaVersion).append('\n')
        field("title", project.title)
        field("subtitle", project.subtitleUri)
        field("sourceSubtitle", project.sourceSubtitleUri)
        field("sourceFormat", project.sourceFormat.name)
        field("video", project.videoUri)
        field("mkv", project.mkvUri)
        field("mkvTrack", project.mkvTrackNumber?.toString())
        field("workspace", project.workspaceMode.name)
        project.importedFontUris.forEach { field("font", it) }
        field("review", project.reviewIdentity)
    }

    fun parse(text: String): AssWorkbenchProjectManifest {
        val rows = text.lineSequence().mapNotNull { line ->
            val split = line.indexOf('=')
            if (split <= 0) null else line.substring(0, split) to decode(line.substring(split + 1))
        }.toList()
        val version = rows.firstOrNull { it.first == "ASSWB_PROJECT" }?.second?.toIntOrNull()
            ?: error("Not an ASS Workbench project.")
        require(version == 1) { "Unsupported project schema: " + version }
        fun one(key: String) = rows.lastOrNull { it.first == key }?.second?.takeIf { it.isNotEmpty() }
        return AssWorkbenchProjectManifest(
            schemaVersion = version,
            title = one("title") ?: "Untitled",
            subtitleUri = one("subtitle"),
            sourceSubtitleUri = one("sourceSubtitle"),
            sourceFormat = one("sourceFormat")?.let { runCatching { SubtitleSourceFormat.valueOf(it) }.getOrNull() }
                ?: SubtitleSourceFormat.UNKNOWN,
            videoUri = one("video"),
            mkvUri = one("mkv"),
            mkvTrackNumber = one("mkvTrack")?.toLongOrNull(),
            workspaceMode = one("workspace")?.let { runCatching { WorkspacePresentationMode.valueOf(it) }.getOrNull() }
                ?: WorkspacePresentationMode.FIXED,
            importedFontUris = rows.filter { it.first == "font" }.map { it.second },
            reviewIdentity = one("review"),
        )
    }

    private fun StringBuilder.field(key: String, value: String?) {
        if (value != null) append(key).append('=').append(encode(value)).append('\n')
    }

    private fun encode(value: String): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            val v = byte.toInt() and 0xff
            if ((v in 'a'.code..'z'.code) || (v in 'A'.code..'Z'.code) || (v in '0'.code..'9'.code) ||
                v == '-'.code || v == '_'.code || v == '.'.code || v == ':'.code || v == '/'.code) {
                append(v.toChar())
            } else {
                append('%').append(v.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }

    private fun decode(value: String): String {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < value.length) {
            if (value[i] == '%' && i + 2 < value.length) {
                val decoded = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (decoded != null) {
                    bytes += decoded.toByte()
                    i += 3
                    continue
                }
            }
            bytes += value[i].code.toByte()
            i++
        }
        return bytes.toByteArray().decodeToString()
    }
}
