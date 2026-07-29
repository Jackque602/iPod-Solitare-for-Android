package com.jackque.solitaire.ui

/**
 * Screen shapes the board adapts to:
 * - TALL: classic portrait phone layout (status bar / board / bottom bar)
 * - COMPACT: near-square or wide screens - e.g. the Motorola Razr cover
 *   display - with a slim status line and a vertical action rail beside
 *   the board so the tableau gets maximum height.
 */
enum class LayoutMode { TALL, COMPACT }

/** Pure sizing policy so it stays unit-testable on the JVM. */
fun layoutModeFor(widthDp: Float, heightDp: Float): LayoutMode =
    if (heightDp >= widthDp * 1.15f) LayoutMode.TALL else LayoutMode.COMPACT
