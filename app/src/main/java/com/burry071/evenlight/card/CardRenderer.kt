package com.burry071.evenlight.card

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.burry071.evenlight.core.CardGeometry
import com.burry071.evenlight.core.Palette

class CardRenderer(private val displayFont: Typeface, private val textFont: Typeface) {

    data class Header(
        val name: String,
        val dateLabel: String,
        val sunAltitudeDeg: Int,
        val wording: String,
        val winnerIndex: Int,
        val neighbours: List<Int>,
        val night: Boolean = false
    )

    private val geo = CardGeometry()

    fun render(bands: List<Int>, header: Header): Bitmap {
        val bmp = Bitmap.createBitmap(geo.width, geo.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val paper = if (header.night) Palette.NIGHT_SURFACE else Palette.CARD
        val text = if (header.night) Palette.NIGHT_TEXT else Palette.INK
        val quiet = if (header.night) Palette.NIGHT_HAIRLINE else Palette.MUTED
        c.drawColor(paper)

        for (y in 0 until geo.fieldHeight) {
            p.color = geo.blendOf(bands, geo.fieldBlend(y))
            c.drawLine(0f, y.toFloat(), geo.width.toFloat(), y.toFloat(), p)
        }

        // The winner comes in on the header rather than being recomputed here, because the record stores
        // `winner` and Task 10 re-derives cards in Python from that stored field. A renderer that decided for
        // itself could draw a strip that disagrees with the record it was built from - which is the one drift
        // this file exists to prevent, and "it draws, it does not decide" means exactly this line.
        val winner = Palette.opaque(bands[header.winnerIndex])
        p.color = winner
        c.drawRect(0f, geo.stripTop.toFloat(), geo.width.toFloat(), geo.fieldHeight.toFloat(), p)
        p.color = geo.inkOn(winner)
        p.typeface = displayFont
        p.textSize = 116f
        c.drawText(header.name, 56f, geo.stripTop + geo.stripHeight * 0.72f, p)

        p.color = text; p.typeface = displayFont; p.textSize = 62f
        c.drawText(header.dateLabel, 56f, geo.paperTop + 120f, p)
        p.color = quiet; p.typeface = textFont; p.textSize = 40f
        c.drawText("sun ${header.sunAltitudeDeg}\u00B0", 56f, geo.paperTop + 186f, p)

        var x = geo.width - 56f - 60f * header.neighbours.size
        for (n in header.neighbours) {
            p.color = Palette.opaque(n)          // measured anchors arrive as 0x00RRGGBB (T3 packing)
            c.drawRoundRect(x, geo.paperTop + 150f, x + 46f, geo.paperTop + 196f, 8f, 8f, p)
            x += 60f
        }

        p.color = text; p.typeface = textFont; p.textSize = 44f
        wrap(c, p, header.wording, 56f, geo.paperTop + 290f, geo.width - 112f, 60f)

        p.color = quiet; p.textSize = 32f
        c.drawText("measured, not adjusted \u00B7 8 bands", 56f, geo.height - 56f, p)
        return bmp
    }

    private fun wrap(c: Canvas, p: Paint, text: String, x: Float, y0: Float, maxW: Float, lineH: Float) {
        var y = y0
        var line = ""
        for (word in text.split(' ')) {
            val trial = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(trial) > maxW) { c.drawText(line, x, y, p); y += lineH; line = word }
            else line = trial
        }
        if (line.isNotEmpty()) c.drawText(line, x, y, p)
    }
}
