"""skycard.py - the reference implementation of Evenlight's band maths and card layout.

Same eight bands, same winner, same name, same 1080x1440 card as the Kotlin in :core, run from a
photo on a laptop. Equivalence is not asserted here: fixtures/*.bands.json is pinned by
core/src/test/kotlin/.../FixtureEquivalenceTest.kt and both languages are tested against it, so
agreement is transitive (tools/python/test_skycard.py).

Usage: python skycard.py PHOTO [-o card.png] [--print-json]
"""
import math
import os

TARGET_WIDTH = 64
BAND_COUNT = 8


def round_half_up(v):
    """Matches Pixels.roundHalfUp. Python's built-in round() is banker's rounding and diverges on .5."""
    return math.floor(v + 0.5)


def relative_luminance(rgb):
    """Pixels.relativeLuminance: WCAG relative luminance, 0..1, binary64 in both languages."""
    def lin(v):
        s = v / 255.0
        return s / 12.92 if s <= 0.03928 else ((s + 0.055) / 1.055) ** 2.4
    r, g, b = rgb
    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)


def to_pixels(img):
    """ARGB-less packed ints, top row first, matching BitmapFactory.getPixels minus alpha."""
    w, h = img.size
    data = img.convert("RGB").tobytes()
    return [(data[i] << 16) | (data[i + 1] << 8) | data[i + 2] for i in range(0, len(data), 3)], w, h


def bilinear(src, sw, sh, dw, dh):
    """BandSampler.bilinear: half-pixel-centre bilinear, per-channel round-half-up.

    Every weight and every channel sum below is a plain Python float, which is binary64, and each
    expression keeps Kotlin's operand order, so it evaluates to the same double as the Double
    arithmetic in BandSampler.kt. Nothing here may pass through numpy.float32, struct.pack('f', ...),
    or Pillow's own resize: a 32-bit ratio moves a channel average across a .5 boundary and the two
    cards stop matching (Task 7's Ruling 36 measured 13,161 such outcomes).
    """
    if (sw, sh) == (dw, dh):
        return list(src)
    out = []
    sx = sw / dw
    sy = sh / dh
    for dy in range(dh):
        fy = min(max((dy + 0.5) * sy - 0.5, 0.0), sh - 1)
        y0 = int(fy); y1 = min(y0 + 1, sh - 1); wy = fy - y0
        for dx in range(dw):
            fx = min(max((dx + 0.5) * sx - 0.5, 0.0), sw - 1)
            x0 = int(fx); x1 = min(x0 + 1, sw - 1); wx = fx - x0
            a = src[y0 * sw + x0]; b = src[y0 * sw + x1]
            c = src[y1 * sw + x0]; d = src[y1 * sw + x1]
            w00 = (1 - wx) * (1 - wy); w01 = wx * (1 - wy)
            w10 = (1 - wx) * wy;       w11 = wx * wy
            chans = []
            for shift in (16, 8, 0):
                v = (((a >> shift) & 0xFF) * w00 + ((b >> shift) & 0xFF) * w01 +
                     ((c >> shift) & 0xFF) * w10 + ((d >> shift) & 0xFF) * w11)
                chans.append(min(255, max(0, round_half_up(v))))
            out.append((chans[0] << 16) | (chans[1] << 8) | chans[2])
    return out


def sample(src, width, height):
    """BandSampler.sample: pixels -> eight ordered bands plus their mean luminance."""
    if width <= 0 or height <= 0:
        raise ValueError("width and height must both be positive, got %dx%d" % (width, height))
    if len(src) != width * height:
        raise ValueError("expected %d pixels, got %d" % (width * height, len(src)))
    dw = TARGET_WIDTH
    # min(height, ...) mirrors BandSampler.kt: without it, width < TARGET_WIDTH makes dh exceed the
    # source row count and the bilinear pass invents rows. Identity for width >= 64; sky_narrow pins it.
    dh = max(1, min(height, round_half_up(height * dw / width)))
    small = bilinear(src, width, height, dw, dh)
    bands = []
    for i in range(BAND_COUNT):
        y0 = i * dh // BAND_COUNT
        y1 = (i + 1) * dh // BAND_COUNT
        frm = min(y0, dh - 1)
        to = min(max(y1, y0 + 1) - 1, dh - 1)
        rs = gs = bs = 0; n = 0
        for y in range(frm, to + 1):
            row = y * dw
            for x in range(dw):
                c = small[row + x]
                rs += (c >> 16) & 0xFF; gs += (c >> 8) & 0xFF; bs += c & 0xFF
                n += 1
        bands.append((round_half_up(rs / n), round_half_up(gs / n), round_half_up(bs / n)))
    lum = sum(relative_luminance(b) for b in bands) / BAND_COUNT
    return bands, lum


def hex_of(rgb):
    """Pixels.hex: uppercase ASCII, locale-free - Python's %X never resolves against a locale either."""
    return "#%02X%02X%02X" % rgb


def chroma_range(rgb):
    """Matcher.chromaRange."""
    return max(rgb) - min(rgb)


def winner(bands):
    """Matcher.winner: max chroma range, tie -> higher relative luminance, tie -> lowest index."""
    if not bands:
        raise ValueError("bands must not be empty")
    best = 0
    for i in range(1, len(bands)):
        ra, rl = chroma_range(bands[i]), relative_luminance(bands[i])
        ba, bl = chroma_range(bands[best]), relative_luminance(bands[best])
        if ra > ba or (ra == ba and rl > bl):
            best = i
    return best


def name_for(rgb, vocabulary=None):
    """Matcher.nameFor: nearest anchor in RGB, unweighted. Ties keep the earlier vocabulary entry."""
    # `is None`, not falsiness: an injected empty vocabulary is a caller bug, and `or VOCABULARY` would
    # answer it by silently naming against the real 35 words.
    vocab = VOCABULARY if vocabulary is None else vocabulary
    best_word, best_d = vocab[0][0], None
    for word, anchor in vocab:
        d = sum((a - b) ** 2 for a, b in zip(rgb, anchor)) ** 0.5
        if best_d is None or d < best_d:
            best_d, best_word = d, word
    return best_word


def bands_for(path):
    from PIL import Image
    src, w, h = to_pixels(Image.open(path))
    return [hex_of(b) for b in sample(src, w, h)[0]]


# Copied from core/src/main/kotlin/com/burry071/evenlight/core/Vocabulary.kt: same 35 words, same order,
# same anchor hexes. test_skycard.py parses that file and diffs it against this list, so the copy cannot
# drift silently when the vocabulary changes.
_WORDS = [
    ("still", "#4A5560"), ("smothered", "#5A5148"),
    ("clearing", "#A8C0CC"), ("brassy", "#C2854A"),
    ("tarnished", "#6B5D4A"), ("milky", "#CFC9BE"),
    ("flint", "#7E7A72"), ("ember", "#B4552C"),
    ("copper", "#A96A3C"), ("chalk", "#E3DED4"),
    ("slate", "#5C6B82"), ("ash", "#9B968D"),
    ("wax", "#D8B878"), ("honey", "#C89A46"),
    ("bruised", "#5A4560"), ("waited", "#6E5F52"),
    ("low", "#3A322A"), ("thin", "#BFC6CB"),
    ("wide", "#7C93A8"), ("close", "#44403A"),
    ("dusty", "#A08A72"), ("wet", "#2E3A42"),
    ("salt", "#D5D2C8"), ("iron", "#4C4A49"),
    ("paper", "#EDE8DE"), ("linen", "#D9CFC0"),
    ("wool", "#B8A894"), ("amber", "#B08A5E"),
    ("smoke", "#6A6A6A"), ("held", "#7A6A55"),
    ("lifted", "#9FB6C4"), ("sunken", "#262A33"),
    ("gathering", "#55606B"), ("fading", "#C79A7A"),
    ("bright", "#E8D9B8"),
]


def _parse_hex(s):
    """VocabWord.anchor via Pixels.parseHex, returning the (r, g, b) triple Pillow draws."""
    t = s.lstrip("#")
    if len(t) != 6:
        raise ValueError("not a hex colour: %s" % s)
    return tuple(int(t[i:i + 2], 16) for i in (0, 2, 4))


VOCABULARY = [(word, _parse_hex(anchor)) for word, anchor in _WORDS]


class Palette:
    """CardGeometry.kt's Palette minus the alpha byte, and minus the four this tool never draws.

    Palette.opaque exists because Android's Canvas reads the high byte of a colour int; Pillow has no
    such byte on an RGB image, so adding one here would put the two languages at odds by construction.
    Every value below is the Kotlin constant's low three bytes. NIGHT_* and HAIRLINE stay in Kotlin:
    the night surface is a screen state, and this tool only ever files a day card.
    """
    PAPER = _parse_hex("#F4EFE6")
    CARD = _parse_hex("#FBF8F2")
    INK = _parse_hex("#2A2622")
    MUTED = _parse_hex("#6E6656")


class CardGeometry:
    """CardGeometry.kt, line for line, in the same binary64 arithmetic."""

    def __init__(self, width=1080, height=1440):
        self.width = width
        self.height = height
        self.field_height = round_half_up(height * 0.62)
        self.strip_height = round_half_up(height * 0.0667)

    @property
    def paper_top(self):
        return self.field_height

    @property
    def band_height(self):
        return self.field_height / 8.0

    @property
    def strip_top(self):
        return self.field_height - self.strip_height

    def band_centre_y(self, i):
        return (i + 0.5) * self.band_height

    def field_blend(self, y):
        """Integer part = the lower band index; fraction = how far toward the next one."""
        if y <= self.band_centre_y(0):
            return 0.0
        if y >= self.band_centre_y(7):
            return 7.0
        for i in range(7):
            a = self.band_centre_y(i); b = self.band_centre_y(i + 1)
            if y < b:
                return i + (y - a) / (b - a)
        return 7.0

    def ink_on(self, band):
        """Paper on a dark band, ink on a pale one: the measured hue is displayed untouched."""
        return Palette.PAPER if relative_luminance(band) < 0.45 else Palette.INK

    def blend_of(self, bands, blend):
        i = max(0, min(int(blend), len(bands) - 2))
        t = min(max(blend - i, 0.0), 1.0)
        a = bands[i]; b = bands[i + 1]
        return tuple(min(255, max(0, round_half_up(a[k] * (1 - t) + b[k] * t))) for k in range(3))


MONTHS = ["January", "February", "March", "April", "May", "June",
          "July", "August", "September", "October", "November", "December"]

HERE = os.path.dirname(os.path.abspath(__file__))
FONT_DIR = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "font")
DISPLAY_FONT = os.path.join(FONT_DIR, "fraunces_variable.ttf")
TEXT_FONT = os.path.join(FONT_DIR, "instrument_sans_variable.ttf")

# The card's typography sizes, as CardRenderer.kt sets them (116f on the strip, 62f on the date,
# 44f on the wording, 32f on the footer), all measured from the top-left of a 1080-wide artefact.
STRIP_SIZE, DATE_SIZE, WORDING_SIZE, FOOTER_SIZE = 116, 62, 44, 32
MARGIN, CHIP_W, CHIP_GAP, CHIP_R = 56, 46, 60, 8


def _font(path, size):
    if not os.path.isfile(path):
        raise FileNotFoundError("bundled font missing: %s (the card draws no system-face stand-in)" % path)
    from PIL import ImageFont
    return ImageFont.truetype(path, size)


def _wrapped(draw, text, font, max_width):
    """CardRenderer.wrap: greedy by word, breaking on the width the font reports for the trial line."""
    lines, line = [], ""
    for word in text.split(" "):
        trial = word if not line else line + " " + word
        if draw.textlength(trial, font=font) > max_width:
            lines.append(line); line = word
        else:
            line = trial
    if line:
        lines.append(line)
    return lines


def render_card(bands, out_path, winner_index=None, name=None, date_label=None, wording=None):
    """Draw the 1080x1440 card for these eight measured bands and write it to out_path as a PNG.

    `winner_index` is the band the strip shows, and it is named the way `EveningRecord.winnerIndex` and
    `CardRenderer.Header.winnerIndex` are. On a fresh photo it is winner(bands) - the same function the
    record's stored value came from - and a caller re-deriving a card from a record must pass that
    record's stored `winner` instead, because the strip is a fact about the record and recomputing it
    here could draw a card that disagrees with the file it was built from.
    """
    from PIL import Image, ImageDraw

    if len(bands) != BAND_COUNT:
        raise ValueError("a card needs %d bands, got %d" % (BAND_COUNT, len(bands)))
    if winner_index is None:
        winner_index = winner(bands)
    index = winner_index
    if name is None:
        name = name_for(bands[index])
    if wording is None:
        # Wording.template from app/.../wording/Wording.kt, which is also what the app files when no
        # model is installed. The model's own line is Task 11's, and this tool cannot produce it.
        wording = "%s light, measured from this sky" % name

    geo = CardGeometry()
    img = Image.new("RGB", (geo.width, geo.height), Palette.CARD)
    draw = ImageDraw.Draw(img)

    for y in range(geo.field_height):
        draw.line([(0, y), (geo.width - 1, y)], fill=geo.blend_of(bands, geo.field_blend(y)))

    strip = bands[index]
    draw.rectangle([0, geo.strip_top, geo.width - 1, geo.field_height - 1], fill=strip)
    # "ls" anchors on the baseline, which is the coordinate Android's drawText is given.
    draw.text((MARGIN, geo.strip_top + geo.strip_height * 0.72), name,
              font=_font(DISPLAY_FONT, STRIP_SIZE), fill=geo.ink_on(strip), anchor="ls")

    if date_label:
        draw.text((MARGIN, geo.paper_top + 120), date_label,
                  font=_font(DISPLAY_FONT, DATE_SIZE), fill=Palette.INK, anchor="ls")

    # No sun-altitude line: it needs a place and a time, and a photo alone carries neither. Printing one
    # would put an unmeasured number on a card whose whole claim is that it has none.

    neighbours = [bands[i] for i in (index - 1, index + 1) if 0 <= i < len(bands)]
    x = geo.width - MARGIN - CHIP_GAP * len(neighbours)
    for n in neighbours:
        draw.rounded_rectangle([x, geo.paper_top + 150, x + CHIP_W, geo.paper_top + 196],
                               radius=CHIP_R, fill=n)
        x += CHIP_GAP

    body = _font(TEXT_FONT, WORDING_SIZE)
    y = geo.paper_top + 290
    for line in _wrapped(draw, wording, body, geo.width - 2 * MARGIN):
        draw.text((MARGIN, y), line, font=body, fill=Palette.INK, anchor="ls")
        y += 60

    draw.text((MARGIN, geo.height - MARGIN),
              "measured, not adjusted \u00B7 %d bands" % BAND_COUNT,
              font=_font(TEXT_FONT, FOOTER_SIZE), fill=Palette.MUTED, anchor="ls")

    img.save(out_path, "PNG")
    return img


def _date_label_of(path):
    """The evening the card is dated from, and where that date came from - never a guessed one.

    EXIF's capture timestamp when the photo carries one, otherwise the file's own modification time.
    The CLI prints which of the two it used, because a card that silently claims the wrong evening is
    the failure this project refuses everywhere else.
    """
    from PIL import Image
    exif = Image.open(path).getexif() or {}
    for tag in (36867, 306):                      # DateTimeOriginal, then DateTime
        raw = exif.get(tag)
        if isinstance(raw, str) and len(raw) >= 10 and raw[4] == ":" and raw[7] == ":":
            day, month = int(raw[8:10]), int(raw[5:7])
            return "%d %s" % (day, MONTHS[month - 1]), "EXIF %s" % ("DateTimeOriginal" if tag == 36867 else "DateTime")
    import datetime
    local = datetime.datetime.fromtimestamp(os.path.getmtime(path))
    return "%d %s" % (local.day, MONTHS[local.month - 1]), "file mtime (the photo carries no EXIF date)"


def main(argv=None):
    import argparse, json, sys, time
    from PIL import Image
    started = time.monotonic()
    ap = argparse.ArgumentParser(prog="skycard.py", description="Turn a sky photo into an Evenlight card.")
    ap.add_argument("photo", help="the sky photograph to file as a card")
    ap.add_argument("-o", "--out", help="card PNG to write (default: <photo>-card.png beside the photo)")
    ap.add_argument("--print-json", action="store_true", help="print bands, winner and name as JSON")
    args = ap.parse_args(argv)

    src, w, h = to_pixels(Image.open(args.photo))
    bands, _ = sample(src, w, h)
    index = winner(bands)
    name = name_for(bands[index])
    out = args.out or os.path.splitext(args.photo)[0] + "-card.png"
    date_label, date_source = _date_label_of(args.photo)
    render_card(bands, out, winner_index=index, name=name, date_label=date_label)

    # stdout carries only the three measured values; everything else about the run goes to stderr so
    # --print-json stays pipeable.
    if args.print_json:
        print(json.dumps({"bands": [hex_of(b) for b in bands], "winner": index, "name": name}))
    else:
        print("bands  " + " ".join(hex_of(b) for b in bands))
        print("winner %d" % index)
        print("name   %s" % name)
    print("photo  %s (%dx%d)" % (args.photo, w, h), file=sys.stderr)
    print("date   %s - from %s" % (date_label, date_source), file=sys.stderr)
    print("card   %s (%dx%d PNG)" % (out, CardGeometry().width, CardGeometry().height), file=sys.stderr)
    print("elapsed %.1f s" % (time.monotonic() - started), file=sys.stderr)
    return 0


if __name__ == "__main__":
    import sys
    sys.exit(main())
