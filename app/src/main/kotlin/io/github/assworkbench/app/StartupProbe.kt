package io.github.assworkbench.app

import android.content.Context
import java.io.File

object StartupProbe {
    private const val FILE_NAME = "startup-probe.txt"

    fun mark(context: Context, stage: String, status: String, detail: String = "") {
        runCatching {
            val target = File(context.filesDir, FILE_NAME)
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            val body = buildString {
                appendLine("stage=$stage")
                appendLine("status=$status")
                appendLine("provider=${BuildConfig.ASSWB_RENDERER_FONT_PROVIDER}")
                appendLine("renderer=${BuildConfig.ASSWB_RENDERER_VERSION}")
                if (detail.isNotBlank()) appendLine("detail=$detail")
            }
            tmp.writeText(body, Charsets.UTF_8)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }

    fun read(context: Context): String = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (file.isFile) file.readText(Charsets.UTF_8) else "无启动记录"
    }.getOrDefault("无法读取启动记录")

    inline fun <T> stage(context: Context, name: String, block: () -> T): T {
        mark(context, name, "starting")
        return try {
            block().also { mark(context, name, "success") }
        } catch (t: Throwable) {
            mark(context, name, "failure", "${t::class.java.simpleName}: ${t.message ?: "无消息"}")
            throw t
        }
    }
}
