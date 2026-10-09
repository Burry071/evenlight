package com.burry071.evenlight.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.burry071.evenlight.core.EveningRecord
import com.burry071.evenlight.ui.theme.EvenlightColors
import com.burry071.evenlight.ui.theme.EvenlightType
import java.time.LocalDate
import kotlin.math.abs

/**
 * The stack: every evening between the first card and today, gaps included.
 *
 * The rows come from [StackDays], never from `readRecords()`, because the set difference is the only thing that
 * makes a hole knowable - a list built from the cards that exist collapses every gap silently, and the gaps are the
 * product's claim about a week.
 *
 * The motion here is scroll-linked rather than decorative: a card reaches `.97 -> 1` and straightens `1.2° -> 0°` as
 * it comes to the centre, so the evening being read is the only square one. The numbers come from the list's own
 * layout info during composition - no animation is run, nothing to cut when the frame budget is short, which is what
 * the mockup's note asks for.
 * Above 1.15x font scale the rotation is dropped, which is what the mockup's responsive note asks for and the first
 * thing to cut if the frame budget misses 60 fps.
 */
@Composable
fun StackScreen(
    records: List<EveningRecord>,
    held: Set<String>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    detail: @Composable (() -> Unit)? = null
) {
    val today = LocalDate.now()
    val first = firstEvening(records, held)
    val days = remember(today, first) { StackDays.days(first, today) }
    val holes = remember(days, held) { StackDays.holes(days, held) }
    val byDate = remember(records) { records.groupBy { it.date } }
    val largeFont = LocalDensity.current.fontScale > 1.15f

    BoxWithConstraints(
        modifier.fillMaxSize().background(EvenlightColors.NightSurface)
            .statusBarsPadding().navigationBarsPadding()
    ) {
        val expanded = maxWidth >= 840.dp
        val twoUp = !expanded && maxWidth >= 600.dp

        if (expanded && detail != null) {
            // Two panes: the stack at 38 %, the card detail at 62 %, no bottom nav at any width.
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(38f).fillMaxSize().padding(start = 20.dp, end = 8.dp, top = 14.dp)) {
                    StackHead(today, days.size - holes.size, holes.size)
                    StackList(days, holes, byDate, largeFont, twoUp = false, gap = 14.dp, onOpen = onOpen)
                }
                Column(
                    Modifier.weight(62f).fillMaxSize().padding(end = 20.dp, top = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // No cap here: `CardScreen` caps its own card, and a second `widthIn` on the way in composes
                    // against it and wins, so the pane's card would render at 380 dp while the design system's
                    // number said 420. One mechanism, one number.
                    detail()
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 14.dp)
            ) {
                StackHead(today, days.size - holes.size, holes.size)
                StackList(days, holes, byDate, largeFont, twoUp, if (twoUp) 16.dp else 14.dp, onOpen)
            }
        }
    }
}

/**
 * The month and the two counts, from the mockup's `October` / `4 evenings · 1 hole`.
 *
 * A top-level composable rather than a local function in the screen's body, because the composition rules allow no
 * local composable functions - and both panes need the same head, so there is one copy of the numbers either way.
 */
@Composable
private fun StackHead(today: LocalDate, count: Int, holeCount: Int) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(longDate(today).substringAfter(' '), style = EvenlightType.title, color = EvenlightColors.NightText)
        Text(
            "$count ${plural(count, "evening")} · $holeCount ${plural(holeCount, "hole")}",
            style = EvenlightType.caption.copy(letterSpacing = 0.06.em),
            color = EvenlightColors.NightText.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun StackList(
    days: List<LocalDate>,
    holes: Set<LocalDate>,
    byDate: Map<String, List<EveningRecord>>,
    largeFont: Boolean,
    twoUp: Boolean,
    gap: Dp,
    onOpen: (String) -> Unit
) {
    if (twoUp) {
        val grid = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = grid,
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier.fillMaxSize()
        ) {
            items(days.size) { i ->
                val day = days[i]
                // Centred inside the cell, because a 340 dp card in a 410 dp cell otherwise hugs the left edge.
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    StackRow(
                        day = day,
                        record = byDate[day.toString()]?.lastOrNull(),
                        isHole = day in holes,
                        largeFont = largeFont,
                        settle = gridCentreProgress(grid, i),
                        onClick = { onOpen(day.toString()) }
                    )
                }
            }
        }
    } else {
        val list = rememberLazyListState()
        LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxSize()) {
            itemsIndexed(days) { i, day ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    StackRow(
                        day = day,
                        record = byDate[day.toString()]?.lastOrNull(),
                        isHole = day in holes,
                        largeFont = largeFont,
                        settle = listCentreProgress(list, i),
                        onClick = { onOpen(day.toString()) }
                    )
                }
            }
        }
    }
}

/**
 * How square this row is: 1 at the centre of the viewport, 0 at either edge.
 *
 * Read from the list's own layout info rather than from an animation, which is the whole point of the mockup's note
 * - "driven from the scroll offset", zero lines of animation code, nothing to cut when the frame budget is short.
 */
private fun listCentreProgress(state: LazyListState, index: Int): Float {
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return 0f
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    if (viewport <= 0f) return 1f
    return edgeProgress(visible.offset + visible.size / 2f - viewport / 2f, viewport)
}

private fun gridCentreProgress(state: LazyGridState, index: Int): Float {
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return 0f
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    if (viewport <= 0f) return 1f
    // A grid item's offset and size are an `IntOffset`/`IntSize` (a cell has both dimensions); the row's centre is
    // measured down the scroll axis, same as the list.
    return edgeProgress(visible.offset.y + visible.size.height / 2f - viewport / 2f, viewport)
}

private fun edgeProgress(centreOffset: Float, viewport: Float): Float =
    (1f - 2f * abs(centreOffset) / viewport).coerceIn(0f, 1f)

/**
 * One evening: the card if there is one, the outlined empty card if there is not.
 *
 * The row surface is the mockup's `#1E1B14` - a fourth night tone that `Palette` does not have, and a screen may
 * not restate a chrome colour as a literal or invent a new one. So the night card is outlined with `NightHairline`
 * on `NightSurface` instead of filled, which keeps the eighth palette constant honest. Chrome moves, bands do not.
 */
@Composable
private fun StackRow(
    day: LocalDate,
    record: EveningRecord?,
    isHole: Boolean,
    largeFont: Boolean,
    settle: Float,
    onClick: () -> Unit
) {
    val bands = record?.let { bandColors(it.bandsHex) }

    Column(
        Modifier
            // The brief's card cap: 340 dp, and nothing in the stack is wider than that. At 600-840 dp two-up a cell
            // reaches ~410 dp at the top of the range, and a card that wide stops being the object the mockup drew.
            // Compact phones are already under it (360 dp - 40 dp padding), so the cap only bites on a foldable.
            // `widthIn` before `fillMaxWidth`: a fill first takes the whole cell and the cap only shrinks the
            // content, leaving the card's border and tap area wide while its insides sit at 340 dp.
            .widthIn(max = 340.dp)
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = 0.97f + 0.03f * settle
                scaleY = 0.97f + 0.03f * settle
                if (!largeFont) rotationZ = 1.2f * (1f - settle)
            }
            .clip(cardShape())
            .border(1.dp, EvenlightColors.NightHairline, cardShape())
            .background(EvenlightColors.NightSurface)
            .clickable(onClick = onClick)
    ) {
        if (!isHole && record != null && bands != null) {
            BandField(bands, Modifier.fillMaxWidth().height(88.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    record.name,
                    style = EvenlightType.title.copy(fontSize = 17.sp, letterSpacing = (-0.015).em),
                    color = EvenlightColors.NightText
                )
                Text(
                    "${shortDate(day)} · sun ${record.sunAltitudeDeg}°",
                    style = EvenlightType.caption.copy(fontSize = 10.5.sp, letterSpacing = 0.06.em),
                    color = EvenlightColors.NightText.copy(alpha = 0.72f)
                )
            }
        } else {
            Box(Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.Center) {
                DashedOutline(EvenlightColors.NightHairline, Modifier.fillMaxSize())
                Text(
                    "${shortDate(day)} — no photo",
                    style = EvenlightType.caption.copy(fontSize = 11.sp, letterSpacing = 0.06.em),
                    color = EvenlightColors.NightText.copy(alpha = 0.72f)
                )
            }
        }
    }
}

/**
 * The hole's dashed border. Compose's `Modifier.border` is a solid stroke, and the dashed outline is the one thing
 * that distinguishes "no photo" from "photo too dark to see", so it is drawn here with dashes rather than filled in.
 * The corners are square because a dashed squircle would need the path and the dash pattern to agree; Step 10 can
 * say whether that reads as drift next to the mockup.
 */
@Composable
private fun DashedOutline(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val t = 1.dp.toPx()
        val dash = 12.dp.toPx()
        val gap = 8.dp.toPx()
        val w = size.width
        val h = size.height
        var x = 0f
        while (x < w) {
            val end = (x + dash).coerceAtMost(w)
            drawLine(color, Offset(x, t / 2f), Offset(end, t / 2f), t)
            drawLine(color, Offset(x, h - t / 2f), Offset(end, h - t / 2f), t)
            x += dash + gap
        }
        var y = 0f
        while (y < h) {
            val end = (y + dash).coerceAtMost(h)
            drawLine(color, Offset(t / 2f, y), Offset(t / 2f, end), t)
            drawLine(color, Offset(w - t / 2f, y), Offset(w - t / 2f, end), t)
            y += dash + gap
        }
    }
}

/**
 * The first evening the stack may claim.
 *
 * [StackDays] says `first` is the earliest card's date, not the install date, because an evening that predates the
 * app was never an evening and inventing one puts a hole in the record for a day nobody stood on a balcony. Two
 * fallbacks, in order: the earliest record date when a record exists whose card failed to render (the observation
 * is real even when the artefact is missing, and dropping it would collapse that gap), and today when there is
 * nothing at all, so a first run shows one evening with no photo rather than an empty list.
 */
private fun firstEvening(records: List<EveningRecord>, held: Set<String>): LocalDate {
    val card = held.minOrNull()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (card != null) return card
    val record = records.minByOrNull { it.date }?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    return record ?: LocalDate.now()
}

private fun plural(count: Int, noun: String): String = if (count == 1) noun else "${noun}s"
