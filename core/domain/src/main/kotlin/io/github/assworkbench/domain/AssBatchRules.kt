package io.github.assworkbench.domain

import java.math.BigInteger

sealed interface AssBatchFilter {
    fun matches(event: AssEvent, document: AssDocument): Boolean

    data object All : AssBatchFilter { override fun matches(event: AssEvent, document: AssDocument) = true }
    data class StyleIs(val style: String) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = event.style == style
    }
    data class ActorContains(val value: String, val ignoreCase: Boolean = true) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = event.name.contains(value, ignoreCase)
    }
    data class TextContains(val value: String, val ignoreCase: Boolean = true) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = AssInlineSyntax.visibleText(event.text).contains(value, ignoreCase)
    }
    data class VisibleRegex(val pattern: Regex) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = pattern.containsMatchIn(AssInlineSyntax.visibleText(event.text))
    }
    data class RawRegex(val pattern: Regex) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = pattern.containsMatchIn(event.text)
    }
    data class StyleRegex(val pattern: Regex) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = pattern.containsMatchIn(event.style)
    }
    data class DurationRange(val minimumMs: Long, val maximumMs: Long) : AssBatchFilter {
        init {
            require(minimumMs >= 0L) { "最短时长不能为负数。" }
            require(maximumMs >= minimumMs) { "最长时长不能小于最短时长。" }
        }
        override fun matches(event: AssEvent, document: AssDocument): Boolean {
            val duration = (event.end.millis - event.start.millis).coerceAtLeast(0L)
            return duration in minimumMs..maximumMs
        }
    }
    data class LayerIs(val layer: Int) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = event.layer == layer
    }
    data class EventIds(val ids: Set<Long>) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = event.id in ids
    }
    data class CommentIs(val comment: Boolean) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = event.comment == comment
    }
    data class HasTag(val tag: String) : AssBatchFilter {
        private val pattern = Regex(
            """\\${Regex.escape(tag)}(?:[^A-Za-z]|$)""",
            RegexOption.IGNORE_CASE,
        )
        override fun matches(event: AssEvent, document: AssDocument) =
            pattern.containsMatchIn(event.text)
    }
    data class KaraokeRevealCompatible(
        val spec: AssKaraokeRevealFxSpec = AssKaraokeRevealFxSpec(),
    ) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument): Boolean =
            AssKaraokeFxAuthoring.inspectProgressiveRevealCompatibility(
                document = document,
                eventId = event.id,
                spec = spec,
            ).compatible
    }
    data class TimeRange(val startMs: Long, val endMs: Long) : AssBatchFilter {
        init {
            require(startMs >= 0L) { "时间范围起点不能为负数。" }
            require(endMs >= startMs) { "时间范围终点不能早于起点。" }
        }
        override fun matches(event: AssEvent, document: AssDocument) =
            event.end.millis >= startMs && event.start.millis <= endMs
    }
    data class And(val filters: List<AssBatchFilter>) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = filters.all { it.matches(event, document) }
    }
    data class Or(val filters: List<AssBatchFilter>) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = filters.any { it.matches(event, document) }
    }
    data class Not(val filter: AssBatchFilter) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = !filter.matches(event, document)
    }
}

sealed interface AssBatchAction {
    fun apply(event: AssEvent, document: AssDocument): AssEvent

    data class ShiftTime(val deltaMs: Long) : AssBatchAction {
        override fun apply(event: AssEvent, document: AssDocument): AssEvent {
            val duration = BigInteger.valueOf(event.end.millis)
                .subtract(BigInteger.valueOf(event.start.millis))
            val shiftedStart = BigInteger.valueOf(event.start.millis)
                .add(BigInteger.valueOf(deltaMs))
            val start = if (shiftedStart.signum() < 0) BigInteger.ZERO else shiftedStart
            val end = start.add(duration)
            require(end <= LONG_MAX_BIG_INTEGER) {
                "时间平移结果超出可表示的字幕时间范围。"
            }
            return event.copy(
                start = SubTime(start.longValueExact()),
                end = SubTime(end.longValueExact()),
            )
        }
    }
    data class SetStyle(val style: String) : AssBatchAction {
        init {
            require(style.isNotBlank()) { "目标 Style 不能为空。" }
        }
        override fun apply(event: AssEvent, document: AssDocument): AssEvent {
            require(document.styles.any { it.name == style }) { "目标 Style 不存在：$style" }
            return event.copy(style = style)
        }
    }
    data class SetLayer(val layer: Int) : AssBatchAction {
        override fun apply(event: AssEvent, document: AssDocument) = event.copy(layer = layer)
    }
    data class SetActor(val actor: String) : AssBatchAction {
        override fun apply(event: AssEvent, document: AssDocument) = event.copy(name = actor)
    }
    data class SetComment(val comment: Boolean) : AssBatchAction {
        override fun apply(event: AssEvent, document: AssDocument) = event.copy(comment = comment)
    }
    data class ReplacePlainText(val find: String, val replacement: String) : AssBatchAction {
        override fun apply(event: AssEvent, document: AssDocument): AssEvent {
            if (find.isEmpty()) return event
            val (text, changed) = AssDocumentEditing.replacePlainDialogueText(event.text, find, replacement)
            return if (changed) event.copy(text = text) else event
        }
    }
    data class ReplaceVisibleRegex(val pattern: Regex, val replacement: String) : AssBatchAction {
        override fun apply(event: AssEvent, document: AssDocument) =
            event.copy(text = AssSearchReplace.replaceVisibleSegments(event.text, pattern, replacement))
    }
    data class ReplaceRawRegex(val pattern: Regex, val replacement: String) : AssBatchAction {
        override fun apply(event: AssEvent, document: AssDocument) =
            event.copy(text = pattern.replace(event.text, replacement))
    }
    data class ScaleTiming(
        val originMs: Long,
        val numerator: Long,
        val denominator: Long,
    ) : AssBatchAction {
        init { require(numerator > 0 && denominator > 0) }
        override fun apply(event: AssEvent, document: AssDocument): AssEvent {
            val origin = BigInteger.valueOf(originMs)
            val ratioNumerator = BigInteger.valueOf(numerator)
            val ratioDenominator = BigInteger.valueOf(denominator)
            fun scale(value: Long): Long {
                val delta = BigInteger.valueOf(value).subtract(origin)
                val scaled = origin.add(delta.multiply(ratioNumerator).divide(ratioDenominator))
                val nonNegative = if (scaled.signum() < 0) BigInteger.ZERO else scaled
                require(nonNegative <= LONG_MAX_BIG_INTEGER) {
                    "时间缩放结果超出可表示的字幕时间范围。"
                }
                return nonNegative.longValueExact()
            }
            val start = scale(event.start.millis)
            val end = scale(event.end.millis).coerceAtLeast(start)
            return event.copy(start = SubTime(start), end = SubTime(end))
        }
    }
    data class SetNumericOverride(
        val property: AssTransformVisualProperty,
        val value: Double?,
    ) : AssBatchAction {
        init {
            if (value != null) {
                require(value.isFinite()) { "数值 override 必须是有限数字。" }
                property.minimum?.let { minimum ->
                    require(value >= minimum) { "${property.name} 不能小于 $minimum。" }
                }
            }
        }
        override fun apply(event: AssEvent, document: AssDocument): AssEvent {
            val leading = LEADING_OVERRIDE_BLOCKS.find(event.text)?.value.orEmpty()
            val body = event.text.removePrefix(leading)
            val block = leading.takeIf { it.contains('\\') } ?: "{}"
            val content = block.removePrefix("{").removeSuffix("}")
            val next = AssTransformVisualSemantic.patchNumeric(content, property, value)
            val replacement = if (next.isBlank()) "" else "{$next}"
            return event.copy(text = if (block == "{}") replacement + event.text else leading.replaceFirst(block, replacement) + body)
        }
    }
    data class SetMargins(val left: Int? = null, val right: Int? = null, val vertical: Int? = null) : AssBatchAction {
        init {
            require(listOfNotNull(left, right, vertical).all { it >= 0 }) {
                "Event Margin 不能为负数。"
            }
        }
        override fun apply(event: AssEvent, document: AssDocument) = event.copy(
            marginL = left ?: event.marginL, marginR = right ?: event.marginR, marginV = vertical ?: event.marginV)
    }
    data class ApplyKaraokeRevealFx(
        val spec: AssKaraokeRevealFxSpec = AssKaraokeRevealFxSpec(),
    ) : AssBatchAction {
        override fun apply(event: AssEvent, document: AssDocument): AssEvent {
            val workingDocument = document.copy(events = document.events.map { existing ->
                if (existing.id == event.id) event else existing
            })
            val plan = try {
                AssKaraokeFxAuthoring.planProgressiveReveal(
                    document = workingDocument,
                    eventId = event.id,
                    spec = spec,
                )
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Karaoke FX 在批处理动作阶段失去兼容性：" +
                        (error.message ?: error::class.java.simpleName),
                    error,
                )
            } catch (error: IllegalStateException) {
                throw IllegalArgumentException(
                    "Karaoke FX 在批处理动作阶段失去兼容性：" +
                        (error.message ?: error::class.java.simpleName),
                    error,
                )
            }
            return event.copy(text = plan.generatedText)
        }
    }
}

data class AssBatchRecipe(val id: String, val filter: AssBatchFilter = AssBatchFilter.All, val actions: List<AssBatchAction>)
data class AssBatchPreview(val document: AssDocument, val affectedEventIds: List<Long>, val changedEventIds: List<Long>)

private val LONG_MAX_BIG_INTEGER: BigInteger = BigInteger.valueOf(Long.MAX_VALUE)
private val LEADING_OVERRIDE_BLOCKS = Regex("""^(?:\{[^}]*\})*""")

object AssBatchEngine {
    fun preview(document: AssDocument, recipe: AssBatchRecipe): AssBatchPreview {
        val affected = mutableListOf<Long>()
        val changed = mutableListOf<Long>()
        val events = document.events.map { original ->
            if (!recipe.filter.matches(original, document)) return@map original
            affected += original.id
            val updated = recipe.actions.fold(original) { event, action -> action.apply(event, document) }
            if (updated != original) changed += original.id
            updated
        }
        return AssBatchPreview(document.copy(events = events), affected, changed)
    }
}
