"""Deterministic fixtures. Run once with the Kotlin test to pin bands, then commit the output."""
from PIL import Image
import os

HERE = os.path.dirname(os.path.abspath(__file__))
BANDS = [(0x1E,0x24,0x38),(0x2A,0x33,0x50),(0x3C,0x4A,0x67),(0x5C,0x6B,0x82),
         (0x8A,0x83,0x75),(0xB0,0x8A,0x5E),(0xC2,0x85,0x4A),(0x3A,0x32,0x2A)]

def ramp():
    img = Image.new("RGB", (64, 48))
    for y in range(48):
        img.paste(BANDS[y // 6], (0, y, 64, y + 1))
    return img

def blocks():
    img = Image.new("RGB", (128, 96))
    for y in range(96):
        img.paste(BANDS[min(7, y * 8 // 96)], (0, y, 128, y + 1))
    return img

def wide():
    img = Image.new("RGB", (100, 100), (30, 32, 40))
    img.paste((200, 150, 60), (0, 80, 100, 100))
    return img

def narrow():
    # 32 x 6: a fixture with width < TARGET_WIDTH (sky_columns is the second one, from below and with real
    # horizontal variation). In BandSampler/sample, dh would be
    # round_half_up(6*64/32) = 12 > the 6 source rows, so without the clamp both languages interpolate
    # rows that do not exist. Clamped to dh = 6, the eight bands repeat source rows as
    # r0,r0,r1,r2,r3,r3,r4,r5. Every other fixture is >= 64 px wide, where the clamp is the identity -
    # which is exactly why this one exists: it is the only thing that pins the clause cross-language.
    img = Image.new("RGB", (32, 6))
    for y in range(6):
        img.paste((10 + y * 40, 250 - y * 35, 20 + y * 15), (0, y, 32, y + 1))
    return img

def columns():
    # Finding 2.2: every fixture above is row-uniform - within a row a == b == c == d - so a reviewer's
    # mutants (x1 = x0, wx = 0.0, dropping the - 0.5 on fx, averaging 1 of 64 columns) all left both
    # suites green. This one is 48 x 16: no row is uniform, every column carries its own colour, and the
    # 64-wide row the bands average is a real interpolation of 48 distinct pixels (sx = 0.75, so the
    # weights are 0.125 / 0.375 / 0.625 - live decisions for round-half-up). 48 < TARGET_WIDTH also
    # repeats sky_narrow's dh-clamp pin from a second direction (dh = min(16, 21) = 16) - and the blue
    # channel walks +3 per source row so the clamp has something to clamp: with row-identical columns the
    # bands would read the same mean at dh = 21 as at dh = 16 and the pin would be decorative.
    img = Image.new("RGB", (48, 16))
    for x in range(48):
        for y in range(16):
            img.paste(((255 - 5 * x) % 256, (3 * x + x * x) % 256, (97 + 11 * x + 3 * y) % 256),
                      (x, y, x + 1, y + 1))
    return img

# The plan writes these under "../../fixtures" because every other tool in Task 10 sits two levels
# below the repo root (tools/python/); this file is already in fixtures/, so HERE is the output dir.
for name, img in (("sky_ramp", ramp()), ("sky_blocks", blocks()), ("sky_wide", wide()),
                  ("sky_narrow", narrow()), ("sky_columns", columns())):
    img.save(os.path.join(HERE, name + ".png"))
