package io.github.assworkbench.app.ui

/** Short navigation panes reserve one button; filter editing moves to a dialog. */
internal data class SubtitleFilterLayout(val useDialog: Boolean, val inlineHeightDp: Float) {
    companion object {
        fun resolve(availableHeightDp: Float): SubtitleFilterLayout = if (availableHeightDp < 360f) {
            SubtitleFilterLayout(true, availableHeightDp.coerceIn(0f, 48f))
        } else {
            SubtitleFilterLayout(false, (availableHeightDp * 0.4f).coerceAtMost(260f))
        }
    }
}
