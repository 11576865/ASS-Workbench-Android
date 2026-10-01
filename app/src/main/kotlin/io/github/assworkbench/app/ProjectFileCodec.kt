package io.github.assworkbench.app

import io.github.assworkbench.domain.AssCodec
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssTextEncoding
import org.json.JSONArray
import org.json.JSONObject

enum class SubtitleSourceFormat { ASS, SRT, PROJECT }

data class AssWorkbenchProjectSnapshot(
    val title: String,
    val document: AssDocument,
    val videoUri: String?,
    val subtitleUri: String?,
    val containerUri: String?,
    val containerTrackNumber: Long?,
    val textEncoding: AssTextEncoding,
    val sourceFormat: SubtitleSourceFormat,
    val focusedEventId: Long?,
    val selectedEventIds: Set<Long>,
    val workspaceMode: String,
    val workspaceState: List<String>,
    val surfaceState: List<String>,
)

object ProjectFileCodec {
    const val EXTENSION = ".asswb"
    private const val SCHEMA_VERSION = 2

    fun encode(snapshot: AssWorkbenchProjectSnapshot): String = JSONObject().apply {
        put("schema_version", SCHEMA_VERSION)
        put("title", snapshot.title)
        put("ass", AssCodec.write(snapshot.document))
        put("event_ids", JSONArray(snapshot.document.events.map { it.id }))
        put("video_uri", snapshot.videoUri ?: JSONObject.NULL)
        put("subtitle_uri", snapshot.subtitleUri ?: JSONObject.NULL)
        put("container_uri", snapshot.containerUri ?: JSONObject.NULL)
        put("container_track_number", snapshot.containerTrackNumber ?: JSONObject.NULL)
        put("text_encoding", snapshot.textEncoding.name)
        put("source_format", snapshot.sourceFormat.name)
        put("focused_event_id", snapshot.focusedEventId ?: JSONObject.NULL)
        put("selected_event_ids", JSONArray(snapshot.selectedEventIds.toList()))
        put("workspace_mode", snapshot.workspaceMode)
        put("workspace_state", JSONArray(snapshot.workspaceState))
        put("surface_state", JSONArray(snapshot.surfaceState))
    }.toString(2)

    fun decode(text: String): AssWorkbenchProjectSnapshot {
        val json = JSONObject(text)
        val schemaVersion = json.getInt("schema_version")
        require(schemaVersion in 1..SCHEMA_VERSION) {
            "不支持的 ASS Workbench Project schema"
        }
        fun nullableString(key: String): String? =
            if (!json.has(key) || json.isNull(key)) null else json.getString(key)
        fun strings(key: String): List<String> {
            val array = json.optJSONArray(key) ?: return emptyList()
            return buildList { repeat(array.length()) { add(array.getString(it)) } }
        }
        val parsedDocument = AssCodec.parse(json.getString("ass"))
        val document = if (schemaVersion >= 2) {
            val ids = buildList {
                val array = json.optJSONArray("event_ids") ?: error("Project 缺少 event_ids")
                repeat(array.length()) { add(array.getLong(it)) }
            }
            require(ids.size == parsedDocument.events.size && ids.distinct().size == ids.size) {
                "Project event_ids 与 ASS Events 不一致"
            }
            parsedDocument.copy(
                events = parsedDocument.events.mapIndexed { index, event -> event.copy(id = ids[index]) },
            )
        } else {
            parsedDocument
        }
        val selected = if (schemaVersion >= 2) buildSet {
            val array = json.optJSONArray("selected_event_ids")
            if (array != null) repeat(array.length()) { add(array.getLong(it)) }
        } else {
            emptySet()
        }
        return AssWorkbenchProjectSnapshot(
            title = json.optString("title", "Project"),
            document = document,
            videoUri = nullableString("video_uri"),
            subtitleUri = nullableString("subtitle_uri"),
            containerUri = nullableString("container_uri"),
            containerTrackNumber = if (json.has("container_track_number") && !json.isNull("container_track_number"))
                json.getLong("container_track_number") else null,
            textEncoding = runCatching { AssTextEncoding.valueOf(json.optString("text_encoding")) }
                .getOrDefault(AssTextEncoding.UTF8),
            sourceFormat = runCatching { SubtitleSourceFormat.valueOf(json.optString("source_format")) }
                .getOrDefault(SubtitleSourceFormat.PROJECT),
            focusedEventId = if (schemaVersion >= 2 && json.has("focused_event_id") && !json.isNull("focused_event_id"))
                json.getLong("focused_event_id") else null,
            selectedEventIds = selected,
            workspaceMode = json.optString("workspace_mode", "FIXED"),
            workspaceState = if (schemaVersion >= 2) strings("workspace_state") else emptyList(),
            surfaceState = strings("surface_state"),
        )
    }
}
