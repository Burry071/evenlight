package com.burry071.evenlight.card

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.burry071.evenlight.core.BandSampler
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
        // Two paints, split by what they draw. `fill` covers the field, the strip and the chips and carries no
        // ANTI_ALIAS_FLAG on purpose: an anti-aliased hairline stroke spreads coverage across the rows either side
        // of the line it names instead of painting that row, so the 893-row field would come out a blend of the
        // blend rather than the blend itself - and this file's whole job is that it draws, it does not decide.
        // The measured value leaves :core untouched; the primitive here must simply deliver it to the pixel.
        // `text` keeps anti-aliasing: glyph edges are the one place smoothing is the correct drawing.
        val fill = Paint()
        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        val paper = if (header.night) Palette.NIGHT_SURFACE else Palette.CARD
        val inked = if (header.night) Palette.NIGHT_TEXT else Palette.INK
        val quiet = if (header.night) Palette.NIGHT_HAIRLINE else Palette.MUTED
        c.drawColor(paper)

        for (y in 0 until geo.fieldHeight) {
            fill.color = geo.blendOf(bands, geo.fieldBlend(y))
            c.drawRect(0f, y.toFloat(), geo.width.toFloat(), (y + 1).toFloat(), fill)
        }

        // The winner comes in on the header rather than being recomputed here, because the record stores
        // `winner` and Task 10 re-derives cards in Python from that stored field. A renderer that decided for
        // itself could draw a strip that disagrees with the record it was built from - which is the one drift
        // this file exists to prevent, and "it draws, it does not decide" means exactly this line.
        val winner = Palette.opaque(bands[header.winnerIndex])
        fill.color = winner
        c.drawRect(0f, geo.stripTop.toFloat(), geo.width.toFloat(), geo.fieldHeight.toFloat(), fill)
        text.color = geo.inkOn(winner)
        text.typeface = displayFont
        text.textSize = 116f
        c.drawText(header.name, 56f, geo.stripTop + geo.stripHeight * 0.72f, text)

        text.color = inked; text.typeface = displayFont; text.textSize = 62f
        c.drawText(header.dateLabel, 56f, geo.paperTop + 120f, text)
        text.color = quiet; text.typeface = textFont; text.textSize = 40f
        c.drawText("sun ${header.sunAltitudeDeg}\u00B0", 56f, geo.paperTop + 186f, text)

        var x = geo.width - 56f - 60f * header.neighbours.size
        for (n in header.neighbours) {
            fill.color = Palette.opaque(n)          // measured anchors arrive as 0x00RRGGBB (T3 packing)
            c.drawRoundRect(x, geo.paperTop + 150f, x + 46f, geo.paperTop + 196f, 8f, 8f, fill)
            x += 60f
        }

        text.color = inked; text.typeface = textFont; text.textSize = 44f
        wrap(c, text, header.wording, 56f, geo.paperTop + 290f, geo.width - 112f, 60f)

        text.color = quiet; text.textSize = 32f
        // "8 bands" is a fact about the sampler, so it is read from the sampler rather than typed into display
        // copy - a literal here would become an unmeasured number the moment BAND_COUNT moves, and the project's
        // rule is that no number appears on a card the app did not measure or compute.
        c.drawText("measured, not adjusted \u00B7 ${BandSampler.BAND_COUNT} bands", 56f, geo.height - 56f, text)
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
