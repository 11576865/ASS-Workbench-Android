package io.github.assworkbench.app

import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream

/** Deterministic still media; native preview liveness does not depend on H.264 decoding. */
internal object NativePreviewFixture {
    fun create(directory: File, name: String): File {
        val file = File(directory, name)
        val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(24, 32, 48))
            FileOutputStream(file).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            bitmap.recycle()
        }
        check(file.isFile && file.length() > 0L)
        return file
    }
}
