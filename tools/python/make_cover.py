"""Draw the DEV post's cover image: 1280x720, from a record the app filed.

The card on the cover is rendered here from the record JSON rather than pasted from a committed PNG, so the
cover cannot drift from the data. The eight swatches are that record's own bands, in stored order, with the
matcher's winner outlined - the same three facts the card shows.

Type is the card's type: skycard._font with the pinned variable-font instances, so the cover is not wearing a
lookalike of the app's face.

    python tools/python/make_cover.py -o docs/images/cover.png
"""

import argparse
import os
import sys
import tempfile

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import skycard                                                        # noqa: E402
from skycard import DISPLAY_FONT, DISPLAY_VARIATIONS, TEXT_FONT, BODY_VARIATIONS, Palette, _font  # noqa: E402

REPO = os.path.join(HERE, "..", "..")
DEFAULT_RECORD = os.path.join(REPO, "docs", "data", "wording-sample", "2026-10-10-attempt-5.json")

W, H = 1280, 720
TITLE = "Evenlight"
TAGLINE = "one photo  \u00b7  eight measured bands  \u00b7  one card"
CLAIM = "Gemma 3 1B runs on the phone and writes one line."
CLAIM2 = "It does not choose anything."
REPO_LINE = "github.com/Burry071/evenlight"


def blend(a, b, t):
    """a at weight t over b, rounded half up like the rest of this repo."""
    return tuple(skycard.round_half_up(a[k] * t + b[k] * (1 - t)) for k in range(3))


def fit(draw, text, font_path, variations, start, max_width):
    """Largest size at or below start whose text fits max_width."""
    size = start
    while size > 8:
        font = _font(font_path, size, variations)
        if draw.textlength(text, font=font) <= max_width:
            return font, size
        size -= 2
    return _font(font_path, size, variations), size


def make_cover(record_path, out_path):
    tmp = tempfile.NamedTemporaryFile(suffix=".png", delete=False)
    tmp.close()
    try:
        # The render is the source of truth for both halves of the cover: the card image and the swatches
        # come from one call, so they cannot disagree.
        bands, index, date_label, rec = skycard.card_from_record(record_path, tmp.name)
        card = Image.open(tmp.name).convert("RGB")
    finally:
        os.unlink(tmp.name)

    cover = Image.new("RGB", (W, H), Palette.INK)
    draw = ImageDraw.Draw(cover)

    body_colour = blend(Palette.PAPER, Palette.INK, 0.62)

    # The card's colour field only: the rows above the paper section, found by scanning for the first row
    # whose centre pixel is the paper colour rather than guessed at. The cover leads with the measured
    # gradient and the name strip; the sun line and wording live on the card the post shows in full.
    crop_h = card.height
    for yy in range(card.height):
        if card.getpixel((card.width // 2, yy)) == Palette.CARD:
            crop_h = yy
            break
    field = card.crop((0, 0, card.width, crop_h))
    field_w = 440
    field_h = skycard.round_half_up(field.height * field_w / field.width)
    field = field.resize((field_w, field_h), Image.LANCZOS)
    card_x, card_y = 88, (H - field_h) // 2
    cover.paste(field, (card_x, card_y))
    card_h = field_h

    right = card_x + field_w + 72
    avail = W - right - 88

    title_font, _ = fit(draw, TITLE, DISPLAY_FONT, DISPLAY_VARIATIONS, 104, avail)
    tag_font, _ = fit(draw, TAGLINE, TEXT_FONT, BODY_VARIATIONS, 30, avail)
    claim_font, _ = fit(draw, CLAIM, TEXT_FONT, BODY_VARIATIONS, 27, avail)
    claim2_font, _ = fit(draw, CLAIM2, TEXT_FONT, BODY_VARIATIONS, 27, avail)
    name_font, _ = fit(draw, rec["name"], DISPLAY_FONT, DISPLAY_VARIATIONS, 54, avail)
    repo_font, _ = fit(draw, REPO_LINE, TEXT_FONT, BODY_VARIATIONS, 22, avail)

    # anchor="la" throughout: y is the top of the text, so the stack below cannot drift up into the row
    # above it the way a baseline anchor let the title clip off the top edge and the name sit on the swatches.
    # The column's own vertical rhythm, centred in the frame: it no longer hangs off the card block, which
    # is what let the repo line land on top of the name when the card got shorter.
    stack = (title_font.size + 30 + tag_font.size + 46 + 74 + 20 +
             name_font.size + 44 + claim_font.size + 10 + claim2_font.size)
    y = (H - stack) // 2
    draw.text((right, y), TITLE, font=title_font, fill=Palette.PAPER, anchor="la")
    y += title_font.size + 30
    draw.text((right, y), TAGLINE, font=tag_font, fill=body_colour, anchor="la")
    y += tag_font.size + 46

    # The record's own eight bands, stored order, winner outlined in paper.
    gap = 10
    sw = (avail - gap * 7) // 8
    sw_h = 74
    for i, band in enumerate(bands):
        x = right + i * (sw + gap)
        draw.rounded_rectangle([x, y, x + sw, y + sw_h], radius=6, fill=band)
        if i == index:
            draw.rounded_rectangle([x - 3, y - 3, x + sw + 3, y + sw_h + 3], radius=8,
                                   outline=Palette.PAPER, width=3)
    y += sw_h + 20
    draw.text((right, y), rec["name"], font=name_font, fill=Palette.PAPER, anchor="la")
    y += name_font.size + 44

    draw.text((right, y), CLAIM, font=claim_font, fill=body_colour, anchor="la")
    y += claim_font.size + 10
    draw.text((right, y), CLAIM2, font=claim2_font, fill=Palette.PAPER, anchor="la")

    draw.text((right, H - 64), REPO_LINE, font=repo_font, fill=body_colour, anchor="la")

    cover.save(out_path)
    return out_path, rec["name"], date_label, index, len(bands)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--from-record", default=DEFAULT_RECORD, help="the record JSON the app filed")
    ap.add_argument("-o", "--out", default=os.path.join(REPO, "docs", "images", "cover.png"))
    args = ap.parse_args()

    out, name, date_label, index, n = make_cover(args.from_record, args.out)
    print("wrote %s" % out)
    print("  card from: %s" % args.from_record)
    print("  name %s, %s, winner band %d of %d" % (name, date_label, index, n))


if __name__ == "__main__":
    main()
