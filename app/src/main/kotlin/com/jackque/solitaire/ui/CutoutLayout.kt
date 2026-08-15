package com.jackque.solitaire.ui

/**
 * A display cutout attached to the top edge of the window (e.g. the
 * camera region of the Motorola Razr cover display), in dp.
 */
data class TopCutout(val leftDp: Float, val rightDp: Float, val heightDp: Float)

/**
 * Keeps the status bar on the true top row of the screen: instead of
 * pushing the whole board below the cutout, the bar reserves blank
 * space on the cutout's side and the board starts below whatever part
 * of the cutout the bar itself doesn't cover.
 */
object CutoutLayout {

    /** (startPaddingDp, endPaddingDp) for the status bar row. */
    fun barPadding(cutout: TopCutout?, screenWidthDp: Float): Pair<Float, Float> {
        cutout ?: return 0f to 0f
        if (cutout.rightDp <= 0f || cutout.leftDp >= screenWidthDp) return 0f to 0f
        val center = (cutout.leftDp + cutout.rightDp) / 2f
        return if (center > screenWidthDp / 2f) {
            // Cameras toward the right edge: keep the bar's content left.
            0f to (screenWidthDp - cutout.leftDp).coerceAtLeast(0f)
        } else {
            cutout.rightDp.coerceAtLeast(0f) to 0f
        }
    }

    /** Extra space below the bar so the board clears the cutout's tail. */
    fun spacerBelowBar(cutout: TopCutout?, barHeightDp: Float): Float =
        ((cutout?.heightDp ?: 0f) - barHeightDp).coerceAtLeast(0f)
}
