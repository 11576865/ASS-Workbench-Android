package io.github.assworkbench.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Debug-only deterministic provider for destructive save regression.
 *
 * Query parameter corruptSecondRead=1 returns a corrupted shadow file only for
 * the second read-open. That models "write succeeded but provider read-back is
 * not what was written" and lets SafeSubtitleSave exercise rollback.
 */
class SafeSaveTestProvider : ContentProvider() {
    companion object {
        private val readCounts = ConcurrentHashMap<String, AtomicInteger>()

        fun reset() {
            readCounts.clear()
        }
    }

    private val root: File
        get() = File(requireNotNull(context).cacheDir, "safe-save-provider").apply { mkdirs() }

    override fun onCreate(): Boolean {
        root.mkdirs()
        return true
    }

    override fun getType(uri: Uri): String = "text/x-ssa"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val target = targetFile(uri)
        target.parentFile?.mkdirs()

        val readOnly = mode == "r"
        if (readOnly) {
            val key = uri.buildUpon().clearQuery().build().toString()
            val count = readCounts.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
            if (uri.getQueryParameter("corruptSecondRead") == "1" && count == 2) {
                val corrupt = File(root, "corrupt-shadow.ass")
                corrupt.writeText(
                    "[Script Info]\nScriptType: v4.00+\n\n[V4+ Styles]\nFormat: Name,Fontname,Fontsize,PrimaryColour,SecondaryColour,OutlineColour,BackColour,Bold,Italic,Underline,StrikeOut,ScaleX,ScaleY,Spacing,Angle,BorderStyle,Outline,Shadow,Alignment,MarginL,MarginR,MarginV,Encoding\nStyle: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,0,2,10,10,10,1\n\n[Events]\nFormat: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text\nDialogue: 0,0:00:00.00,0:00:01.00,Default,,0,0,0,,CORRUPTED\n"
                )
                return ParcelFileDescriptor.open(corrupt, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }

        if (!target.exists() && !readOnly) target.createNewFile()
        if (!target.exists()) throw java.io.FileNotFoundException(uri.toString())

        val flags = when (mode) {
            "r" -> ParcelFileDescriptor.MODE_READ_ONLY
            "w", "wt" -> ParcelFileDescriptor.MODE_WRITE_ONLY or
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
            "wa" -> ParcelFileDescriptor.MODE_WRITE_ONLY or
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_APPEND
            "rw" -> ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
            "rwt" -> ParcelFileDescriptor.MODE_READ_WRITE or
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
            else -> throw java.io.FileNotFoundException("Unsupported mode: " + mode)
        }
        return ParcelFileDescriptor.open(target, flags)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val target = targetFile(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(columns.map { column ->
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> target.name
                    OpenableColumns.SIZE -> target.length()
                    else -> null
                }
            }.toTypedArray())
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun targetFile(uri: Uri): File {
        val name = uri.lastPathSegment?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.ifBlank { "target.ass" } ?: "target.ass"
        return File(root, name)
    }
}