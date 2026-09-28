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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import io.github.assworkbench.domain.AssInlineSyntax
import io.github.assworkbench.domain.AssInlineTokenKind

private data class AssSyntaxPalette(
    val body: Color,
    val block: Color,
    val tag: Color,
    val value: Color,
    val escape: Color,
    val blockBackground: Color,
)

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

    val analysis = AssInlineSyntax.analyze(text)
    analysis.tokens.forEach { token ->
        val style = when (token.kind) {
            AssInlineTokenKind.TEXT -> null
            AssInlineTokenKind.OVERRIDE_BLOCK -> SpanStyle(
                color = palette.block,
                fontFamily = FontFamily.Monospace,
                fontSize = 0.92.em,
                background = palette.blockBackground,
            )
            AssInlineTokenKind.TAG_NAME -> SpanStyle(
                color = palette.tag,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            AssInlineTokenKind.TAG_VALUE -> SpanStyle(
                color = palette.value,
                fontFamily = FontFamily.Monospace,
            )
            AssInlineTokenKind.ESCAPE -> SpanStyle(
                color = palette.escape,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            AssInlineTokenKind.MALFORMED_BLOCK -> SpanStyle(
                color = MaterialTheme.colorScheme.error,
                fontFamily = FontFamily.Monospace,
                textDecoration = TextDecoration.Underline,
            )
        }
        if (style != null && token.endExclusive > token.start) {
            out.addStyle(style, token.start, token.endExclusive)
        }
    }

    return out.toAnnotatedString()
}
