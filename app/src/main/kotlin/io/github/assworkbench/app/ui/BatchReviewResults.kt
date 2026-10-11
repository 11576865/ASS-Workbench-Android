package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.domain.AssBatchReview

@Composable
internal fun BatchReviewResults(review: AssBatchReview, tagPrefix: String, pageState: MutableState<Int>? = null, onLocate: (Long) -> Unit) {
    val preview = review.preview
    var previewPage by (pageState ?: remember(review) { mutableStateOf(0) })
    val changedExamples = remember(review, previewPage) { review.page(previewPage) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("命中 ${preview.affectedEventIds.size} 条 · 实际变化 ${preview.changedEventIds.size} 条")
        val pages = review.pageCount
        if (pages > 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton({ previewPage-- }, enabled = previewPage > 0,
                    modifier = Modifier.testTag("$tagPrefix-previous")) { Text("上一页") }
                Text("${previewPage + 1} / $pages", modifier = Modifier.weight(1f))
                OutlinedButton({ previewPage++ }, enabled = previewPage + 1 < pages,
                    modifier = Modifier.testTag("$tagPrefix-next")) { Text("下一页") }
            }
        }
        changedExamples.forEach { (before, after) ->
            Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.small) {
                Column(Modifier.fillMaxWidth().padding(6.dp)) {
                    TextButton(onClick = { onLocate(before.id) }, modifier = Modifier.testTag("$tagPrefix-locate-${before.id}")) { Text("定位字幕 #${before.id}") }
                    Text("#${before.id} · ${before.start.millis}-${before.end.millis} → ${after.start.millis}-${after.end.millis}",
                        style = MaterialTheme.typography.labelSmall)
                    if (before.style != after.style || before.layer != after.layer) {
                        Text("${before.style}/L${before.layer} → ${after.style}/L${after.layer}",
                            style = MaterialTheme.typography.labelSmall)
                    }
                    if (before.name != after.name) Text("Actor：${before.name} → ${after.name}", style = MaterialTheme.typography.bodySmall)
                    if (before.comment != after.comment) Text("类型：${if (before.comment) "Comment" else "Dialogue"} → ${if (after.comment) "Comment" else "Dialogue"}", style = MaterialTheme.typography.bodySmall)
                    if (before.marginL != after.marginL || before.marginR != after.marginR || before.marginV != after.marginV) {
                        Text("边距 L/R/V：${before.marginL}/${before.marginR}/${before.marginV} → ${after.marginL}/${after.marginR}/${after.marginV}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (before.effect != after.effect) Text("Effect：${before.effect} → ${after.effect}", style = MaterialTheme.typography.bodySmall)
                    if (before.text != after.text) {
                        Text("修改前 ASS：${before.text}", style = MaterialTheme.typography.bodySmall)
                        Text("修改后 ASS：${after.text}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
