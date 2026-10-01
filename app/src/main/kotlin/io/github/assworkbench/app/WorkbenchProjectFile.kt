package io.github.assworkbench.app

import io.github.assworkbench.domain.SubtitleDocumentFormat
import org.json.JSONArray
import org.json.JSONObject

data class WorkbenchProjectFile(
    val title: String,
    val canonicalAss: String,
    val subtitleFormat: SubtitleDocumentFormat,
    val sourceSubtitleUri: String? = null,
    val referenceVideoUri: String? = null,
    val sourceContainerUri: String? = null,
    val sourceContainerTrack: Long? = null,
    val workspaceMode: String = "FIXED",
    val workspaceState: List<String> = emptyList(),
    val surfaceState: List<String> = emptyList(),
    val fontPackagingSelection: List<String> = emptyList(),
)

object WorkbenchProjectCodec {
    const val MIME = "application/vnd.assworkbench.project+json"
    const val EXTENSION = ".asswbproj"
    private const val SCHEMA = 1

    fun encode(project: WorkbenchProjectFile): String = JSONObject().apply {
        put("schema_version", SCHEMA)
        put("title", project.title)
        put("canonical_ass", project.canonicalAss)
        put("subtitle_format", project.subtitleFormat.name)
        putNullable("source_subtitle_uri", project.sourceSubtitleUri)
        putNullable("reference_video_uri", project.referenceVideoUri)
        putNullable("source_container_uri", project.sourceContainerUri)
        if (project.sourceContainerTrack != null) put("source_container_track", project.sourceContainerTrack)
        put("workspace_mode", project.workspaceMode)
        put("workspace_state", JSONArray(project.workspaceState))
        put("surface_state", JSONArray(project.surfaceState))
        put("font_packaging_selection", JSONArray(project.fontPackagingSelection))
    }.toString(2)

    fun decode(text: String): WorkbenchProjectFile {
        val json = JSONObject(text)
        require(json.optInt("schema_version", -1) == SCHEMA) {
            "不支持的 ASS Workbench Project schema"
        }
        val ass = json.getString("canonical_ass")
        val title = json.optString("title").ifBlank { "Untitled project" }
        val format = runCatching {
            SubtitleDocumentFormat.valueOf(json.optString("subtitle_format", "ASS"))
        }.getOrDefault(SubtitleDocumentFormat.ASS)
        return WorkbenchProjectFile(
            title = title,
            canonicalAss = ass,
            subtitleFormat = format,
            sourceSubtitleUri = json.nullableString("source_subtitle_uri"),
            referenceVideoUri = json.nullableString("reference_video_uri"),
            sourceContainerUri = json.nullableString("source_container_uri"),
            sourceContainerTrack = if (json.has("source_container_track")) json.optLong("source_container_track") else null,
            workspaceMode = json.optString("workspace_mode", "FIXED"),
            workspaceState = json.stringList("workspace_state"),
            surfaceState = json.stringList("surface_state"),
            fontPackagingSelection = json.stringList("font_packaging_selection"),
        )
    }

    private fun JSONObject.putNullable(key: String, value: String?) {
        if (value != null) put(key, value) else put(key, JSONObject.NULL)
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.stringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) add(array.optString(i))
        }
    }
}
