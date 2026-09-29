package io.github.assworkbench.app

import android.content.Context
import java.io.File
import kotlin.concurrent.thread

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
        args += editedAss.absolutePath
        run(*args.toTypedArray())
        require(output.isFile && output.length() > 0L) { "MKV 写回未生成输出文件" }
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
