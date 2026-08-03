package com.jackque.solitaire.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.jackque.solitaire.engine.Card

/**
 * Draws the widget board's cards as bitmaps: the same monochrome design
 * language as the app (black-bordered white cards, red suits fully
 * inverted, hatched backs). Selection is a thick dashed border.
 */
object BoardRenderer {

    private fun paint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun textPaint(sizePx: Float, color: Int): Paint = paint().apply {
        this.color = color
        textSize = sizePx
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private fun newBitmap(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(maxOf(w, 8), maxOf(h, 8), Bitmap.Config.RGB_565).apply {
            eraseColor(Color.WHITE)
        }

    private fun drawCardFace(canvas: Canvas, rect: RectF, card: Card, selected: Boolean) {
        val corner = rect.width() * 0.10f
        val border = paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = rect.width() * 0.035f
        }
        val fill = paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        canvas.drawRoundRect(rect, corner, corner, fill)
        canvas.drawRoundRect(rect, corner, corner, border)

        val inverted = card.isRed
        var ink = Color.BLACK
        if (inverted) {
            val inset = rect.width() * 0.07f
            val inner = RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
            canvas.drawRoundRect(inner, corner, corner, paint().apply { color = Color.BLACK })
            ink = Color.WHITE
        }
        val label = "${card.rank.label}${card.suit.symbol}"
        val text = textPaint(rect.width() * 0.34f, ink)
        canvas.drawText(label, rect.centerX(), rect.top + rect.width() * 0.42f, text)

        if (selected) drawSelection(canvas, rect)
    }

    private fun drawCardBack(canvas: Canvas, rect: RectF) {
        val corner = rect.width() * 0.10f
        val border = paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = rect.width() * 0.035f
        }
        canvas.drawRoundRect(rect, corner, corner, border)
        val hatch = paint().apply {
            color = Color.BLACK
            strokeWidth = rect.width() * 0.02f
        }
        val step = rect.width() * 0.16f
        var x = rect.left - rect.height()
        while (x < rect.right) {
            canvas.drawLine(x, rect.bottom, x + rect.height(), rect.top, hatch)
            x += step
        }
    }

    private fun drawEmpty(canvas: Canvas, rect: RectF, label: String, selected: Boolean = false) {
        val corner = rect.width() * 0.10f
        val border = paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = rect.width() * 0.02f
        }
        canvas.drawRoundRect(rect, corner, corner, border)
        if (label.isNotEmpty()) {
            canvas.drawText(
                label,
                rect.centerX(),
                rect.centerY() + rect.width() * 0.12f,
                textPaint(rect.width() * 0.34f, Color.BLACK),
            )
        }
        if (selected) drawSelection(canvas, rect)
    }

    private fun drawSelection(canvas: Canvas, rect: RectF) {
        val stroke = paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = rect.width() * 0.07f
            pathEffect = DashPathEffect(floatArrayOf(rect.width() * 0.14f, rect.width() * 0.10f), 0f)
        }
        canvas.drawRect(rect, stroke)
    }

    /** Stock pile: back + remaining count, or the recycle hint when empty. */
    fun stockBitmap(remaining: Int, wasteNotEmpty: Boolean, w: Int, h: Int): Bitmap {
        val bitmap = newBitmap(w, h)
        val canvas = Canvas(bitmap)
        val rect = RectF(1f, 1f, w - 1f, h - 1f)
        if (remaining > 0) {
            drawCardBack(canvas, rect)
            val badge = textPaint(w * 0.30f, Color.BLACK)
            val bg = paint().apply { color = Color.WHITE }
            canvas.drawRect(
                rect.centerX() - w * 0.24f, rect.centerY() - w * 0.20f,
                rect.centerX() + w * 0.24f, rect.centerY() + w * 0.14f, bg,
            )
            canvas.drawText("$remaining", rect.centerX(), rect.centerY() + w * 0.10f, badge)
        } else {
            drawEmpty(canvas, rect, if (wasteNotEmpty) "R" else "")
        }
        return bitmap
    }

    /** A single-card pile: waste top or a foundation top; empty slot otherwise. */
    fun topCardBitmap(card: Card?, emptyLabel: String, selected: Boolean, w: Int, h: Int): Bitmap {
        val bitmap = newBitmap(w, h)
        val canvas = Canvas(bitmap)
        val rect = RectF(1f, 1f, w - 1f, h - 1f)
        if (card != null) drawCardFace(canvas, rect, card, selected)
        else drawEmpty(canvas, rect, emptyLabel, selected)
        return bitmap
    }

    /** One tableau column with overlapping cards; [selectedFrom] marks the picked-up run. */
    fun columnBitmap(cards: List<Card>, selectedFrom: Int?, w: Int, h: Int): Bitmap {
        val bitmap = newBitmap(w, h)
        val canvas = Canvas(bitmap)
        if (cards.isEmpty()) {
            drawEmpty(canvas, RectF(1f, 1f, w - 1f, (w * 1.42f).coerceAtMost(h - 1f)), "", selectedFrom != null)
            return bitmap
        }
        val cardH = (w * 1.42f).coerceAtMost(h * 0.6f)
        var downStep = cardH * 0.18f
        var upStep = cardH * 0.34f
        val downCount = cards.count { !it.faceUp }
        val upCount = cards.size - downCount
        val needed = cardH + downStep * downCount + upStep * maxOf(0, upCount - 1)
        if (needed > h && cards.size > 1) {
            val scale = (h - cardH) / (needed - cardH)
            downStep *= scale
            upStep *= scale
        }
        var y = 1f
        cards.forEachIndexed { index, card ->
            val rect = RectF(1f, y, w - 1f, y + cardH)
            if (card.faceUp) {
                drawCardFace(canvas, rect, card, selected = selectedFrom != null && index >= selectedFrom)
            } else {
                drawCardBack(canvas, rect)
            }
            y += if (card.faceUp) upStep else downStep
        }
        return bitmap
    }
}
