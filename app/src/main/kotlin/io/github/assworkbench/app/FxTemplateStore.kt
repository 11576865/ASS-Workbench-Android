package io.github.assworkbench.app

import android.content.Context
import io.github.assworkbench.domain.AssFxTemplate
import io.github.assworkbench.domain.AssFxTemplateCodec
import java.io.File
import java.util.UUID

data class SavedFxTemplate(
    val id: String,
    val template: AssFxTemplate,
)

data class FxTemplateStoreSnapshot(
    val templates: List<SavedFxTemplate>,
    val corruptFileCount: Int,
)

class FxTemplateStore(context: Context) {
    private val dir = File(context.filesDir, "fx-templates").apply { mkdirs() }

    @Synchronized
    fun load(): FxTemplateStoreSnapshot {
        var corrupt = 0
        val templates = dir.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension == EXTENSION }
            .mapNotNull { file ->
                runCatching {
                    SavedFxTemplate(
                        id = file.nameWithoutExtension,
                        template = AssFxTemplateCodec.decode(file.readText(Charsets.UTF_8)),
                    )
                }.getOrElse {
                    corrupt++
                    null
                }
            }
            .sortedWith(
                compareBy<SavedFxTemplate> { it.template.name.lowercase() }
                    .thenBy { it.id }
            )
            .toList()
        return FxTemplateStoreSnapshot(templates, corrupt)
    }

    @Synchronized
    fun save(template: AssFxTemplate): FxTemplateStoreSnapshot {
        AssFxTemplateCodec.validate(template)
        val current = load()
        val existing = current.templates.firstOrNull {
            it.template.name.equals(template.name, ignoreCase = true)
        }
        val id = existing?.id ?: UUID.randomUUID().toString()
        val target = File(dir, "$id.$EXTENSION")
        val staging = File(dir, ".$id.$EXTENSION.tmp")
        val payload = AssFxTemplateCodec.encode(template)

        staging.writeText(payload, Charsets.UTF_8)
        if (target.exists() && !target.delete()) {
            staging.delete()
            error("无法替换已有 FX 模板。")
        }
        if (!staging.renameTo(target)) {
            runCatching {
                target.writeText(payload, Charsets.UTF_8)
            }.getOrElse { error ->
                staging.delete()
                throw error
            }
            staging.delete()
        }
        return load()
    }

    @Synchronized
    fun delete(id: String): FxTemplateStoreSnapshot {
        require(ID_PATTERN.matches(id)) { "FX 模板 ID 无效。" }
        val target = File(dir, "$id.$EXTENSION")
        if (target.exists() && !target.delete()) error("无法删除 FX 模板。")
        return load()
    }

    companion object {
        private const val EXTENSION = "asswbfx"
        private val ID_PATTERN = Regex("""[0-9a-fA-F-]{36}""")
    }
}
