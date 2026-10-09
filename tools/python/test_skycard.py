import io, json, os, re, sys, tempfile, unittest
from contextlib import redirect_stdout
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import skycard

FIX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "fixtures")
REC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "docs", "data", "wording-sample")

def pixels_of(name):
    return skycard.to_pixels(Image.open(os.path.join(FIX, name + ".png")))

class Equivalence(unittest.TestCase):
    def test_bands_match_the_kotlin_goldens(self):
        for name in ("sky_ramp", "sky_blocks", "sky_wide", "sky_narrow"):
            with self.subTest(name):
                expected = json.load(open(os.path.join(FIX, name + ".bands.json")))["bands"]
                self.assertEqual([s.upper() for s in expected], [s.upper() for s in skycard.bands_for(os.path.join(FIX, name + ".png"))])

    def test_winner_and_name_follow_the_kotlin_rules(self):
        self.assertEqual("brassy", skycard.name_for((0xC2, 0x85, 0x4A)))
        self.assertEqual("slate", skycard.name_for((0x5C, 0x6B, 0x82)))

    def test_vocabulary_is_the_same_thirty_five_words(self):
        self.assertEqual(35, len(skycard.VOCABULARY))
        self.assertEqual(35, len({w for w, _ in skycard.VOCABULARY}))

    def test_the_vocabulary_is_vocabulary_kt_in_order_and_to_the_last_digit(self):
        # A divergent anchor would make a matcher bug read as a fixture problem, so the copy is not
        # trusted: it is diffed against the Kotlin source it came from, word by word and hex by hex.
        kotlin = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "core", "src",
                              "main", "kotlin", "com", "burry071", "evenlight", "core", "Vocabulary.kt")
        with io.open(kotlin, encoding="utf-8") as source:
            pairs = re.findall(r'VocabWord\("([a-z]+)", "(#[0-9A-F]{6})"\)', source.read())
        self.assertEqual(35, len(pairs))
        self.assertEqual([(w, h) for w, h in pairs],
                         [(w, skycard.hex_of(a)) for w, a in skycard.VOCABULARY])

    def test_the_narrow_fixture_bands_are_repeated_source_rows_not_invented_rows(self):
        # sky_narrow is the only input under TARGET_WIDTH, so it is the only thing that pins the dh clamp
        # in sample(). Unclamped, dh would be 12 for a 6-row source and both languages would interpolate
        # rows that do not exist - while the three >= 64 px fixtures, where the clamp is the identity,
        # would stay green. Hence the rows are asserted here, not only through the golden file.
        src, w, h = pixels_of("sky_narrow")
        bands, _ = skycard.sample(src, w, h)
        rows = [((src[y * w] >> 16) & 0xFF, (src[y * w] >> 8) & 0xFF, src[y * w] & 0xFF) for y in range(h)]
        self.assertEqual([rows[0], rows[0], rows[1], rows[2], rows[3], rows[3], rows[4], rows[5]], bands)

    def test_the_winner_tie_breaks_the_way_matcher_does(self):
        # Matcher.winner: strictly greater chroma range wins, equal range goes to the higher luminance,
        # equal range and equal luminance keeps the lowest index. sky_narrow's bands 0 and 1 are the same
        # colour, so all three arms are reachable on a shipped fixture.
        bands, _ = skycard.sample(*pixels_of("sky_narrow"))
        self.assertEqual(0, skycard.winner(bands))
        self.assertEqual(0, skycard.winner([(10, 250, 20), (10, 250, 20), (0, 0, 240)]))
        # Equal range (120 each) with the later band brighter: index 1 in both languages.
        self.assertEqual(1, skycard.winner([(20, 140, 20), (60, 180, 60)]))

    def test_name_for_keeps_the_earlier_word_on_an_exact_tie(self):
        # No colour in the whole 256^3 cube is exactly tied between two of the 35 real anchors (verified
        # exhaustively), so the tie rule is only reachable through an injected vocabulary - which is what
        # Matcher.nameFor's strict `<` is for, and what keeps a tie from resolving by word or by luck.
        # (155,100,50) is exactly 55 away from each of the two anchors below.
        self.assertEqual("earlier", skycard.name_for((155, 100, 50), vocabulary=[
            ("earlier", (100, 100, 50)), ("later", (210, 100, 50))]))
        self.assertEqual("later", skycard.name_for((155, 100, 50), vocabulary=[
            ("later", (210, 100, 50)), ("earlier", (100, 100, 50))]))

    def test_card_geometry_reproduces_task_sevens_pinned_numbers(self):
        # These are the numbers CardGeometryTest asserts against the Kotlin, so the card layout is
        # transitive through them exactly like the bands are.
        g = skycard.CardGeometry()
        self.assertEqual((1080, 1440), (g.width, g.height))
        self.assertEqual(893, g.field_height)
        self.assertEqual(893, g.paper_top)
        self.assertEqual(893 / 8, g.band_height)
        self.assertEqual(55.8125, g.band_centre_y(0))
        self.assertEqual(837.1875, g.band_centre_y(7))
        self.assertEqual(96, g.strip_height)
        self.assertEqual(797, g.strip_top)
        self.assertEqual(446, skycard.CardGeometry(540, 720).field_height)
        self.assertEqual(0.0, g.field_blend(0))
        self.assertEqual(0.0, g.field_blend(40))
        self.assertEqual(7.0, g.field_blend(892))

    def test_the_blend_stays_in_binary64_and_rounds_half_up(self):
        g = skycard.CardGeometry()
        black = [(0, 0, 0)] * 8
        even = black[:1] + [(200, 100, 40)] + black[2:]
        odd = black[:1] + [(201, 101, 41)] + black[2:]
        self.assertEqual((0, 0, 0), g.blend_of(black, 0.0))
        self.assertEqual((200, 100, 40), g.blend_of(even, 1.0))
        self.assertEqual((100, 50, 20), g.blend_of(even, 0.5))
        # Half-up, not banker's: the same .5 averages one unit lower would round to 100/50/20.
        self.assertEqual((101, 51, 21), g.blend_of(odd, 0.5))
        # Row 57 of a real card: 47 * 19/1786 is exactly .5, and it only survives as 1 if the ratio is
        # kept in binary64 - a Float path gives 0.49999998 and lands on 0.
        blue = black[:1] + [(0, 0, 47)] + black[2:]
        self.assertEqual((0, 0, 1), g.blend_of(blue, g.field_blend(57)))

    def test_the_ink_rule_flips_at_the_pinned_luminance_threshold(self):
        g = skycard.CardGeometry()
        self.assertEqual(skycard.Palette.PAPER, g.ink_on((178, 178, 178)))
        self.assertEqual(skycard.Palette.INK, g.ink_on((179, 179, 179)))
        self.assertEqual(skycard.Palette.PAPER, g.ink_on((0xC2, 0x85, 0x4A)))
        self.assertEqual(skycard.Palette.INK, g.ink_on((0xE3, 0xDE, 0xD4)))

    def test_every_field_row_is_the_measured_blend_and_the_strip_is_the_winner(self):
        # The card's load-bearing claim is that the colour on the card IS the colour that was measured, and
        # until now nothing pinned the reference renderer's pixels outside a handful of hand-picked rows. The
        # shipped Android renderer drew its 893 field rows as anti-aliased hairlines, which spread coverage
        # instead of painting the row: 797 of 893 rows came out as a 25 % wash toward the card surface rather
        # than the blend (final-review finding 1.1, reproduced against docs/images/). This walks every drawn
        # field row and every strip row of a fresh render at the frame's centre column and demands the exact
        # blend_of value - the check that would have caught 1.1 on the Python side. strip_top is the
        # boundary because the strip deliberately paints over the last 96 field rows with the winner band,
        # exactly as CardRenderer does, and the guard below pins that the two are different colours, so the
        # strip assertions cannot pass by the blend happening to equal them.
        bands, _ = skycard.sample(*pixels_of("sky_blocks"))
        index = skycard.winner(bands)
        out = os.path.join(tempfile.mkdtemp(), "field.png")
        skycard.render_card(bands, out, winner_index=index)
        rgb = Image.open(out).convert("RGB")
        g = skycard.CardGeometry()
        self.assertNotEqual(bands[index], g.blend_of(bands, g.field_blend(g.strip_top)))
        for y in range(g.strip_top):
            self.assertEqual((y, g.blend_of(bands, g.field_blend(y))), (y, rgb.getpixel((540, y))))
        for y in range(g.strip_top, g.field_height):
            self.assertEqual((y, bands[index]), (y, rgb.getpixel((540, y))))
        self.assertEqual(skycard.Palette.CARD, rgb.getpixel((540, g.paper_top)))

    def test_a_rendered_card_is_the_artefact_size_and_carries_the_measured_colours(self):
        # The card is the demo's artefact, so its pixels are checked rather than its drawing calls: an
        # alpha channel reaching the canvas, or a band being mixed where it should be shown untouched,
        # fails here. The winner is passed in, never recomputed inside render_card.
        bands, _ = skycard.sample(*pixels_of("sky_blocks"))
        out = os.path.join(tempfile.mkdtemp(), "card.png")
        skycard.render_card(bands, out, winner_index=skycard.winner(bands))
        drawn = Image.open(out)
        self.assertEqual("PNG", drawn.format)
        self.assertEqual((1080, 1440), drawn.size)
        rgb = drawn.convert("RGB")
        # Rows 0 and 55 are at or below band centre 0 (55.8125), so the field starts as band 0 untouched.
        # Row 56 has begun blending but no channel has moved half a step yet, so it still reads as band 0.
        self.assertEqual(bands[0], rgb.getpixel((0, 0)))
        self.assertEqual(bands[0], rgb.getpixel((0, 55)))
        self.assertEqual(bands[0], rgb.getpixel((0, 56)))
        # Row 80 is a hand-derived golden, not a recomputed one: t = (80 - 55.8125) / 111.625 = 0.21669,
        # band 0 is #1E2438 and band 1 is #2A3350 (the two lowest entries of sky_blocks.bands.json), so
        # the channels are 30+12t=32.60, 36+15t=39.25, 56+24t=61.20 and half-up keeps them at 33/39/61.
        # Centre 0 half a band off in either direction moves all three, which is the same mutation Ruling
        # 37 caught in the Kotlin.
        self.assertEqual((33, 39, 61), rgb.getpixel((0, 80)))
        self.assertEqual(bands[6], rgb.getpixel((0, 797)))                # the winner strip, unmixed
        self.assertEqual(bands[6], rgb.getpixel((0, 892)))                # strip to the last field row
        self.assertNotIn(rgb.getpixel((0, 796)), (bands[6], bands[7]))    # still blending above stripTop
        self.assertEqual(skycard.Palette.CARD, rgb.getpixel((0, 893)))    # paper, and no alpha anywhere
        self.assertEqual(skycard.Palette.CARD, rgb.getpixel((0, 1439)))

    def test_the_strip_and_chips_follow_the_winner_that_was_handed_in(self):
        # The strip is drawn from the winner the caller passed, not from one render_card decided for
        # itself - which is the rule a record-driven caller depends on. The chips are the two neighbours
        # recomputed from the same band list, at CardRenderer's positions.
        bands, _ = skycard.sample(*pixels_of("sky_blocks"))
        out = os.path.join(tempfile.mkdtemp(), "card.png")
        skycard.render_card(bands, out, winner_index=2)
        rgb = Image.open(out).convert("RGB")
        self.assertEqual(bands[2], rgb.getpixel((0, 800)))
        self.assertEqual(bands[1], rgb.getpixel((920, 1060)))
        self.assertEqual(bands[3], rgb.getpixel((980, 1060)))

    def test_the_cli_prints_only_what_it_measured(self):
        out = os.path.join(tempfile.mkdtemp(), "card.png")
        buf = io.StringIO()
        with redirect_stdout(buf):
            skycard.main([os.path.join(FIX, "sky_blocks.png"), "-o", out, "--print-json"])
        printed = json.loads(buf.getvalue())
        with open(os.path.join(FIX, "sky_blocks.bands.json")) as golden_file:
            golden = json.load(golden_file)
        self.assertEqual(golden["bands"], printed["bands"])                # parsed values, never float text
        self.assertEqual(6, printed["winner"])
        self.assertEqual("brassy", printed["name"])
        self.assertEqual({"bands", "winner", "name"}, set(printed))
        self.assertTrue(os.path.exists(out))

    def test_from_record_redraws_the_card_from_the_stored_fields(self):
        # Task 10's reproducibility promise, which until --from-record existed was only a docstring claim:
        # a stranger with this repository and a published record can re-derive that evening's card without
        # the photo. Every header part comes from the record's own fields; stdout prints what was read from
        # the file, not what this run would have decided; the date is the record's, never EXIF or mtime.
        src = os.path.join(REC, "2026-10-10.json")
        with io.open(src, encoding="utf-8") as f:
            rec = json.load(f)
        out = os.path.join(tempfile.mkdtemp(), "card.png")
        buf = io.StringIO()
        with redirect_stdout(buf):
            self.assertEqual(0, skycard.main(["--from-record", src, "-o", out, "--print-json"]))
        printed = json.loads(buf.getvalue())
        self.assertEqual({"bands", "winner", "name"}, set(printed))
        self.assertEqual(rec["bands"], printed["bands"])                   # the record's stored strings
        self.assertEqual(rec["winner"], printed["winner"])
        self.assertEqual(rec["name"], printed["name"])
        drawn = Image.open(out)
        self.assertEqual((1080, 1440), drawn.size)
        bands = [skycard._parse_hex(s) for s in rec["bands"]]
        g = skycard.CardGeometry()
        rgb = drawn.convert("RGB")
        # x = 1040 on the strip: the name glyph starts at the margin and must not be what is being sampled.
        for y in range(g.strip_top, g.field_height):
            self.assertEqual((y, bands[rec["winner"]]), (y, rgb.getpixel((1040, y))))
        self.assertEqual("10 October", skycard.card_from_record(src, os.path.join(tempfile.mkdtemp(), "c.png"))[2])

    def test_from_record_never_recomputes_the_winner(self):
        # The drift CardRenderer.kt's and CardHeader.kt's docstrings forbid: a record whose stored `winner`
        # is not winner(bands) must still draw the RECORD's strip, because the strip is a fact about that
        # evening. If --from-record decided for itself, this is the test that dies.
        with io.open(os.path.join(REC, "2026-10-10.json"), encoding="utf-8") as f:
            rec = json.load(f)
        bands = [skycard._parse_hex(s) for s in rec["bands"]]
        drifted = (rec["winner"] + 2) % skycard.BAND_COUNT
        self.assertNotEqual(drifted, skycard.winner(bands))                # teeth: the drift is real
        rec["winner"] = drifted
        path = os.path.join(tempfile.mkdtemp(), "drifted.json")
        with io.open(path, "w", encoding="utf-8") as f:
            json.dump(rec, f)
        out = os.path.join(tempfile.mkdtemp(), "card.png")
        buf = io.StringIO()
        with redirect_stdout(buf):
            skycard.main(["--from-record", path, "-o", out, "--print-json"])
        self.assertEqual(drifted, json.loads(buf.getvalue())["winner"])
        g = skycard.CardGeometry()
        rgb = Image.open(out).convert("RGB")
        for y in range(g.strip_top, g.field_height):
            self.assertEqual((y, bands[drifted]), (y, rgb.getpixel((1040, y))))
        # The chips follow the stored winner too: index 7 has only a lower neighbour, bands[6], drawn at
        # CardRenderer's single-chip position.
        self.assertEqual(bands[6], rgb.getpixel((985, g.paper_top + 173)))

    def test_the_pinned_record_bytes_load_the_way_python_needs_them(self):
        # EveningRecordTest.theWireFileIsExactlyWhatPythonReads pins these exact bytes from the Kotlin side
        # and names the failure this closes: the comment there says "winner":6.0 would load fine and then
        # make bands[record["winner"]] a TypeError. Until --from-record, nothing in Python parsed a record
        # at all, so the contract was asserted on one side only. These are the same bytes, read the way
        # --from-record reads them.
        rec = json.loads("""{"date":"2026-10-08","shutter":"2026-10-08--1759","bands":["#1E2438","#2A3350","#3C4A67","#5C6B82","#8A8375","#B08A5E","#C2854A","#3A322A"],"winner":6,"name":"brassy","sun_altitude_deg":9,"mean_luminance":0.117,"wording":"brassy light holding at the edge of the wall","wording_source":"model","wording_rejected":false,"attempt":1,"clock_suspect":false}""")
        self.assertIsInstance(rec["winner"], int)
        self.assertNotIsInstance(rec["winner"], bool)                      # json never gives bool here, but `True` IS an int
        self.assertEqual(8, len(rec["bands"]))
        self.assertTrue(all(isinstance(s, str) and s.startswith("#") for s in rec["bands"]))
        path = os.path.join(tempfile.mkdtemp(), "pinned.json")
        with io.open(path, "w", encoding="utf-8") as f:
            json.dump(rec, f)
        out = os.path.join(tempfile.mkdtemp(), "card.png")
        skycard.card_from_record(path, out)                                # parses, renders, writes - no float, no TypeError
        self.assertEqual((1080, 1440), Image.open(out).size)

if __name__ == "__main__":
    unittest.main()
