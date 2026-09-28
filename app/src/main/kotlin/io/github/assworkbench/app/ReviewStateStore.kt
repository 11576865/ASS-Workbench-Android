package io.github.assworkbench.app

import android.content.Context
import io.github.assworkbench.domain.ReviewSidecar
import io.github.assworkbench.domain.ReviewSidecarCodec
import java.io.File
import java.security.MessageDigest

class ReviewStateStore(context: Context) {
    private val dir = File(context.filesDir, "review-sidecars").apply { mkdirs() }

    fun load(identity: String): ReviewSidecar? {
        if (identity.isBlank()) return null
        val file = fileFor(identity)
        if (!file.isFile) return null
        return runCatching { ReviewSidecarCodec.decode(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    fun write(identity: String, value: ReviewSidecar) {
        if (identity.isBlank()) return
        val file = fileFor(identity)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(ReviewSidecarCodec.encode(value), Charsets.UTF_8)
        if (file.exists()) file.delete()
        check(tmp.renameTo(file)) { "无法提交 Review sidecar" }
    }

    private fun fileFor(identity: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(dir, digest + ".review")
    }
}
