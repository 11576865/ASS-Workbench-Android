package io.github.assworkbench.app

import android.content.Context
import java.io.File

object StartupProbe {
    private const val FILE_NAME = "startup-probe.txt"
    private const val MAX_HISTORY_CHARS = 32 * 1024
    const val NORMAL_PREVIEW_CORE_STAGE = "normal_preview_core"

    fun mark(context: Context, stage: String, status: String, detail: String = "") {
        runCatching {
            val target = File(context.filesDir, FILE_NAME)
            val entry = buildString {
                appendLine("---")
                appendLine("stage=$stage")
                appendLine("status=$status")
                appendLine("provider=${BuildConfig.ASSWB_RENDERER_FONT_PROVIDER}")
                appendLine("renderer=${BuildConfig.ASSWB_RENDERER_VERSION}")
                if (detail.isNotBlank()) appendLine("detail=$detail")
            }
            val previous = if (target.isFile) target.readText(Charsets.UTF_8) else ""
            val combined = (previous + entry).takeLast(MAX_HISTORY_CHARS)
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            tmp.writeText(combined, Charsets.UTF_8)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }

    fun read(context: Context, maxLines: Int = 80): String = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) {
            "无启动记录"
        } else {
            file.readLines(Charsets.UTF_8).takeLast(maxLines).joinToString("\n")
        }
    }.getOrDefault("无法读取启动记录")

    fun readLatest(context: Context, maxEntries: Int = 4): String = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) {
            "无启动记录"
        } else {
            file.readText(Charsets.UTF_8)
                .split("\n---\n")
                .map(String::trim)
                .filter(String::isNotBlank)
                .takeLast(maxEntries)
                .asReversed()
                .joinToString("\n\n---\n\n")
        }
    }.getOrDefault("无法读取最新启动记录")

    fun rendererCoreCrashSuspected(context: Context): Boolean = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) return@runCatching false
        val latest = file.readText(Charsets.UTF_8)
            .split("\n---\n")
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { entry ->
                val values = entry.lineSequence()
                    .mapNotNull { line ->
                        val split = line.indexOf('=')
                        if (split <= 0) null else line.substring(0, split) to line.substring(split + 1)
                    }
                    .toMap()
                values["stage"] to values["status"]
            }
            .lastOrNull { (stage, _) -> stage == NORMAL_PREVIEW_CORE_STAGE }
        latest?.second == "starting"
    }.getOrDefault(false)

    fun describe(t: Throwable, maxDepth: Int = 8): String {
        val seen = HashSet<Throwable>()
        val parts = ArrayList<String>()
        var current: Throwable? = t
        var depth = 0
        while (current != null && depth < maxDepth && seen.add(current)) {
            parts += buildString {
                append(current::class.java.name)
                current.message?.takeIf { it.isNotBlank() }?.let {
                    append(": ")
                    append(it)
                }
            }
            current = current.cause
            depth++
        }
        return parts.joinToString("\ncaused by → ")
    }

    inline fun <T> stage(context: Context, name: String, block: () -> T): T {
        mark(context, name, "starting")
        return try {
            block().also { mark(context, name, "success") }
        } catch (t: Throwable) {
            mark(context, name, "failure", describe(t))
            throw t
        }
    }
}
