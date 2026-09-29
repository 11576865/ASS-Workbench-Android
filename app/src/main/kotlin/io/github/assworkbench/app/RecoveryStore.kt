package io.github.assworkbench.app

import android.content.Context
import io.github.assworkbench.domain.AssCodec
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssTextEncoding
import io.github.assworkbench.domain.SubtitleProject
import java.io.File
import java.util.Base64

data class RecoverySnapshot(
    val project: SubtitleProject,
    val document: AssDocument,
    val textEncoding: AssTextEncoding,
)

class RecoveryStore(context: Context) {
    private val dir = File(context.filesDir, "recovery").apply { mkdirs() }
    private val assFile = File(dir, "latest.ass")
    private val metaFile = File(dir, "latest.meta")

    @Synchronized
    fun exists(): Boolean = assFile.isFile && metaFile.isFile

    @Synchronized
    fun label(): String = readMeta().getOrNull(0).orEmpty().ifBlank { "未保存字幕工程" }

    @Synchronized
    fun write(project: SubtitleProject, document: AssDocument, textEncoding: AssTextEncoding) {
        val tmpAss = File(dir, "latest.ass.tmp")
        val tmpMeta = File(dir, "latest.meta.tmp")
        tmpAss.writeText(AssCodec.write(document), Charsets.UTF_8)
        val lines = listOf(
            encode(project.title),
            encode(project.subtitleUri.orEmpty()),
            encode(project.videoUri.orEmpty()),
            project.splitRatio.toString(),
            textEncoding.storageValue(),
        )
        tmpMeta.writeText(lines.joinToString("\n"), Charsets.UTF_8)
        if (assFile.exists()) assFile.delete()
        if (metaFile.exists()) metaFile.delete()
        tmpAss.renameTo(assFile)
        tmpMeta.renameTo(metaFile)
    }

    @Synchronized
    fun read(): RecoverySnapshot? {
        if (!exists()) return null
        return runCatching {
            val meta = readMeta()
            val project = SubtitleProject(
                title = meta.getOrNull(0).orEmpty().ifBlank { "Recovered ASS" },
                subtitleUri = meta.getOrNull(1)?.ifBlank { null },
                videoUri = meta.getOrNull(2)?.ifBlank { null },
                splitRatio = meta.getOrNull(3)?.toFloatOrNull()?.coerceIn(0.28f, 0.78f) ?: 0.56f,
            )
            RecoverySnapshot(
                project = project,
                document = AssCodec.parse(assFile.readText(Charsets.UTF_8)),
                textEncoding = parseEncoding(meta.getOrNull(4)),
            )
        }.getOrNull()
    }

    @Synchronized
    fun clear() {
        assFile.delete()
        metaFile.delete()
        File(dir, "latest.ass.tmp").delete()
        File(dir, "latest.meta.tmp").delete()
    }

    private fun parseEncoding(value: String?): AssTextEncoding = when (value) {
        "utf8-bom" -> AssTextEncoding.UTF8_BOM
        "utf16-le" -> AssTextEncoding.UTF16_LE
        "utf16-be" -> AssTextEncoding.UTF16_BE
        else -> AssTextEncoding.UTF8
    }

    private fun AssTextEncoding.storageValue(): String = when (this) {
        AssTextEncoding.UTF8 -> "utf8"
        AssTextEncoding.UTF8_BOM -> "utf8-bom"
        AssTextEncoding.UTF16_LE -> "utf16-le"
        AssTextEncoding.UTF16_BE -> "utf16-be"
    }

    private fun readMeta(): List<String> {
        if (!metaFile.isFile) return emptyList()
        return metaFile.readLines(Charsets.UTF_8).mapIndexed { index, line ->
            if (index < 3) decode(line) else line
        }
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decode(value: String): String =
        runCatching { String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8) }.getOrDefault("")
}
