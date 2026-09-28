package io.github.assworkbench.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.em

private data class AssSyntaxPalette(
    val body: Color,
    val block: Color,
    val tag: Color,
    val value: Color,
    val escape: Color,
    val blockBackground: Color,
)

private val overrideBlockRegex = Regex("""\{[^}]*\}""")
private val tagNameRegex = Regex("""\\[A-Za-z]+""")
private val hexValueRegex = Regex("""&H[0-9A-Fa-f]+&?""")
private val numericValueRegex = Regex("""(?<![A-Za-z])[-+]?\d+(?:\.\d+)?""")
private val textEscapeRegex = Regex("""\\[Nnh]""")

@Composable
fun rememberAssSyntaxTransformation(): VisualTransformation {
    val scheme = MaterialTheme.colorScheme
    val palette = AssSyntaxPalette(
        body = scheme.onSurface,
        block = scheme.onSurfaceVariant,
        tag = scheme.primary,
        value = scheme.tertiary,
        escape = scheme.secondary,
        blockBackground = scheme.surfaceVariant.copy(alpha = 0.42f),
    )
    return remember(palette) { AssSyntaxTransformation(palette) }
}

@Composable
fun rememberAssAnnotatedText(text: String): AnnotatedString {
    val scheme = MaterialTheme.colorScheme
    val palette = AssSyntaxPalette(
        body = scheme.onSurface,
        block = scheme.onSurfaceVariant,
        tag = scheme.primary,
        value = scheme.tertiary,
        escape = scheme.secondary,
        blockBackground = scheme.surfaceVariant.copy(alpha = 0.34f),
    )
    return remember(text, palette) { buildAssAnnotatedString(text, palette) }
}

private class AssSyntaxTransformation(
    private val palette: AssSyntaxPalette,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(
            buildAssAnnotatedString(text.text, palette),
            OffsetMapping.Identity,
        )
}

private fun buildAssAnnotatedString(
    text: String,
    palette: AssSyntaxPalette,
): AnnotatedString {
    val out = AnnotatedString.Builder(text)
    if (text.isNotEmpty()) {
        out.addStyle(SpanStyle(color = palette.body), 0, text.length)
    }

    overrideBlockRegex.findAll(text).forEach { block ->
        val start = block.range.first
        val endExclusive = block.range.last + 1
        out.addStyle(
            SpanStyle(
                color = palette.block,
                fontFamily = FontFamily.Monospace,
                fontSize = 0.92.em,
                background = palette.blockBackground,
            ),
            start,
            endExclusive,
        )

        tagNameRegex.findAll(block.value).forEach { match ->
            val s = start + match.range.first
            val e = start + match.range.last + 1
            out.addStyle(
                SpanStyle(
                    color = palette.tag,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                ),
                s,
                e,
            )
        }

        hexValueRegex.findAll(block.value).forEach { match ->
            val s = start + match.range.first
            val e = start + match.range.last + 1
            out.addStyle(
                SpanStyle(
                    color = palette.value,
                    fontFamily = FontFamily.Monospace,
                ),
                s,
                e,
            )
        }

        numericValueRegex.findAll(block.value).forEach { match ->
            val s = start + match.range.first
            val e = start + match.range.last + 1
            out.addStyle(
                SpanStyle(
                    color = palette.value,
                    fontFamily = FontFamily.Monospace,
                ),
                s,
                e,
            )
        }
    }

    textEscapeRegex.findAll(text).forEach { match ->
        out.addStyle(
            SpanStyle(
                color = palette.escape,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            ),
            match.range.first,
            match.range.last + 1,
        )
    }

    return out.toAnnotatedString()
}
