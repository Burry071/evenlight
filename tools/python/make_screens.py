"""Draw the screen tour: a contact sheet PNG and an animated GIF, from the five committed screenshots.

The screenshots are full emulator frames, so the status bar and the gesture bar are cropped off before
anything is drawn; what remains is the app's own surface, unaltered. Rounded corners and the hairline are
presentation, applied as a mask over the real pixels.

The sheet carries one honesty line, because the screenshots are what they are: an emulator whose camera
scene is synthetic, which is why the card screen's sun line reads -63 degrees.

    python tools/python/make_screens.py
"""

import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import skycard                                                        # noqa: E402
from skycard import DISPLAY_FONT, DISPLAY_VARIATIONS, TEXT_FONT, BODY_VARIATIONS, Palette, _font, _wrapped  # noqa: E402

REPO = os.path.join(HERE, "..", "..")
IMG = os.path.join(REPO, "docs", "images")

# Status bar above, gesture bar below: both are the emulator's chrome, not the app's surface.
CROP_TOP, CROP_BOTTOM = 130, 90

SCREENS = [
    ("screen-setup.png", "Setup", "a place and a pair of coordinates, once"),
    ("screen-tonight.png", "Tonight", "minutes until sunset, computed on the device"),
    ("screen-shoot.png", "Shoot", "one tap, nothing else to decide"),
    ("screen-card.png", "Card", "eight measured bands, the name, the sun, one line"),
    ("screen-stack.png", "Stack", "every evening, oldest first, holes left in"),
]

FOOTER = ("Emulator screenshots. The camera scene is synthetic, which is why the card's sun line reads "
          "-63 degrees: there is no sun in a test image.")


def load(name):
    im = Image.open(os.path.join(IMG, name)).convert("RGB")
    return im.crop((0, CROP_TOP, im.width, im.height - CROP_BOTTOM))


def framed(screen, height, radius):
    """The screen at the given height, corners rounded by a mask over the real pixels."""
    width = skycard.round_half_up(screen.width * height / screen.height)
    screen = screen.resize((width, height), Image.LANCZOS)
    mask = Image.new("L", (width, height), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, width - 1, height - 1], radius=radius, fill=255)
    out = Image.new("RGB", (width, height), Palette.INK)
    out.paste(screen, (0, 0), mask)
    return out, mask


def make_sheet(out_path):
    phone_h = 860
    gap = 44
    margin = 96
    head_h = 150
    label_h = 190

    screens = [load(n) for n, _, _ in SCREENS]
    phones = [framed(s, phone_h, 40)[0] for s in screens]
    col_w = phones[0].width

    width = margin * 2 + col_w * len(phones) + gap * (len(phones) - 1)
    height = head_h + phone_h + label_h + 90
    sheet = Image.new("RGB", (width, height), Palette.INK)
    draw = ImageDraw.Draw(sheet)

    body_colour = tuple(skycard.round_half_up(Palette.PAPER[k] * 0.62 + Palette.INK[k] * 0.38) for k in range(3))

    title_font = _font(DISPLAY_FONT, 76, DISPLAY_VARIATIONS)
    foot_font = _font(TEXT_FONT, 26, BODY_VARIATIONS)
    label_font = _font(DISPLAY_FONT, 40, DISPLAY_VARIATIONS)
    cap_font = _font(TEXT_FONT, 24, BODY_VARIATIONS)

    draw.text((margin, 62), "Evenlight, screen by screen", font=title_font, fill=Palette.PAPER, anchor="la")

    y = head_h
    for i, (phone, (_, label, caption)) in enumerate(zip(phones, SCREENS)):
        x = margin + i * (col_w + gap)
        sheet.paste(phone, (x, y))
        draw.rounded_rectangle([x - 2, y - 2, x + col_w + 1, y + phone_h + 1], radius=42,
                               outline=body_colour, width=2)
        ly = y + phone_h + 34
        draw.text((x + 4, ly), label, font=label_font, fill=Palette.PAPER, anchor="la")
        ly += label_font.size + 14
        for line in _wrapped(draw, caption, cap_font, col_w - 8):
            draw.text((x + 4, ly), line, font=cap_font, fill=body_colour, anchor="la")
            ly += cap_font.size + 6

    draw.text((margin, height - 62), FOOTER, font=foot_font, fill=body_colour, anchor="la")
    sheet.save(out_path)
    return out_path, sheet.size


def make_gif(out_path):
    phone_h = 780
    label_h = 150
    screens = [load(n) for n, _, _ in SCREENS]
    phones = [framed(s, phone_h, 36)[0] for s in screens]
    col_w = phones[0].width

    width = col_w + 120
    height = 60 + phone_h + label_h
    label_font = _font(DISPLAY_FONT, 44, DISPLAY_VARIATIONS)
    cap_font = _font(TEXT_FONT, 26, BODY_VARIATIONS)
    body_colour = tuple(skycard.round_half_up(Palette.PAPER[k] * 0.62 + Palette.INK[k] * 0.38) for k in range(3))

    frames = []
    for phone, (_, label, caption) in zip(phones, SCREENS):
        frame = Image.new("RGB", (width, height), Palette.INK)
        draw = ImageDraw.Draw(frame)
        x = (width - col_w) // 2
        frame.paste(phone, (x, 60))
        draw.rounded_rectangle([x - 2, 58, x + col_w + 1, 60 + phone_h + 1], radius=38,
                               outline=body_colour, width=2)
        draw.text((x + 2, 60 + phone_h + 26), label, font=label_font, fill=Palette.PAPER, anchor="la")
        cy = 60 + phone_h + 26 + label_font.size + 12
        for line in _wrapped(draw, caption, cap_font, col_w - 4):
            draw.text((x + 2, cy), line, font=cap_font, fill=body_colour, anchor="la")
            cy += cap_font.size + 6
        frames.append(frame)

    frames[0].save(out_path, save_all=True, append_images=frames[1:], duration=1900, loop=0, optimize=True)
    return out_path, len(frames)


if __name__ == "__main__":
    sheet, size = make_sheet(os.path.join(IMG, "screens-sheet.png"))
    print("wrote %s (%dx%d)" % (sheet, size[0], size[1]))
    gif, n = make_gif(os.path.join(IMG, "screens-tour.gif"))
    print("wrote %s (%d frames)" % (gif, n))
