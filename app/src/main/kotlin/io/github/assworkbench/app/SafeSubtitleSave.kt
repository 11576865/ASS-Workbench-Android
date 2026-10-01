package io.github.assworkbench.app

import android.content.ContentResolver
import android.net.Uri
import io.github.assworkbench.domain.AssCodec
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssRoundTripVerifier
import io.github.assworkbench.domain.AssTextDecoder
import io.github.assworkbench.domain.AssTextEncoding
import java.io.File
import java.io.FileOutputStream

data class SafeSaveResult(
    val bytesWritten: Long,
    val verifiedEncoding: AssTextEncoding,
    val restoredPreviousTarget: Boolean,
)

internal object SafeSubtitleSave {
    private const val MAX_BACKUP_BYTES = 64 * 1024 * 1024

    fun write(
        resolver: ContentResolver,
        cacheDir: File,
        uri: Uri,
        document: AssDocument,
        encoding: AssTextEncoding,
    ): SafeSaveResult {
        val serialized = AssCodec.write(document)
        AssRoundTripVerifier.requireEquivalent(document, serialized)
        val bytes = encoding.encode(serialized)

        val stageDir = File(cacheDir, "safe-save").apply { mkdirs() }
        val stage = File.createTempFile("ass-", ".stage", stageDir)
        try {
            FileOutputStream(stage).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            val stagedBytes = stage.readBytes()
            require(stagedBytes.contentEquals(bytes)) { "staging file verification failed" }
            verifyBytes(document, stagedBytes, encoding)

            val previous = readExistingBounded(resolver, uri)
            var writeStarted = false
            try {
                writeStarted = true
                writeTarget(resolver, uri, stagedBytes)
                val readBack = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("无法回读刚保存的字幕")
                val verified = verifyBytes(document, readBack, encoding)
                require(readBack.contentEquals(stagedBytes)) {
                    "目标提供程序回读字节与写入内容不一致"
                }
                return SafeSaveResult(
                    bytesWritten = readBack.size.toLong(),
                    verifiedEncoding = verified,
                    restoredPreviousTarget = false,
                )
            } catch (failure: Throwable) {
                var restored = false
                if (writeStarted && previous != null) {
                    restored = runCatching {
                        writeTarget(resolver, uri, previous)
                        val restoredBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error("回滚后无法回读目标")
                        require(restoredBytes.contentEquals(previous)) { "回滚校验失败" }
                    }.isSuccess
                }
                val suffix = if (restored) {
                    "；已恢复保存前目标内容"
                } else if (writeStarted) {
                    "；无法确认目标已恢复，请保留恢复记录并使用“另存为”"
                } else ""
                throw IllegalStateException((failure.message ?: failure::class.java.simpleName) + suffix, failure)
            }
        } finally {
            stage.delete()
        }
    }

    private fun verifyBytes(
        document: AssDocument,
        bytes: ByteArray,
        expectedEncoding: AssTextEncoding,
    ): AssTextEncoding {
        val decoded = AssTextDecoder.decode(bytes)
        require(decoded.encoding == expectedEncoding) {
            "编码回读不一致：期望 ${expectedEncoding.displayName}，实际 ${decoded.encoding.displayName}"
        }
        val report = AssRoundTripVerifier.verify(document, decoded.text)
        require(report.equivalent) {
            "保存后语义校验失败：" + report.summary
        }
        return decoded.encoding
    }

    private fun readExistingBounded(resolver: ContentResolver, uri: Uri): ByteArray? {
        val input = resolver.openInputStream(uri) ?: return null
        input.use {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_BACKUP_BYTES) {
                    "现有目标超过 64 MiB；为避免不可回滚覆盖，请使用“另存为”"
                }
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    private fun writeTarget(resolver: ContentResolver, uri: Uri, bytes: ByteArray) {
        val descriptor = runCatching { resolver.openFileDescriptor(uri, "rwt") }.getOrNull()
        if (descriptor != null) {
            descriptor.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { output ->
                    output.write(bytes)
                    output.flush()
                    output.fd.sync()
                }
            }
            return
        }

        resolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(bytes)
            output.flush()
        } ?: error("无法写入字幕目标")
    }
}
