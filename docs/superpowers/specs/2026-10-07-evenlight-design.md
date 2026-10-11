# Evenlight — design spec (v2, sky card)

**Date:** 2026-10-08 · **Status:** approved for implementation (user: "now write plan", 2026-10-08)
**Target:** Hacktoberfest 2026 DEV Challenge Week 1, theme "Touch Grass". Closes 2026-10-11 23:59 PDT =
2026-10-12 06:59 UTC = **2026-10-12 11:59 PKT**. (This line said "06:59 PKT" until 2026-10-11: it mislabelled
UTC as PKT and understated the window by six hours.)
**Author:** built by the user (`Burry071`) with AI-agent assistance.

> This replaces the 2026-10-07 v1 spec, which described a sky-*measuring instrument* with a laptop
> pipeline. The user rejected that framing ("not satisfying me as user, some indie soothing thing") and
> chose a different output shape: **a daily sky card.** v1's architecture is void — its `index.py`,
> `caption.py`, `aqi.py`, composite 0–10 haze index and laptop-in-the-loop are all gone. §12 lists exactly
> what was kept, because two of v1's claims were false and are corrected here.

---

## 1. The idea in one paragraph

One tap on a balcony, and the evening becomes a card: eight colour bands measured from the actual sky in
front of him, ordered top to horizon exactly as they were, with the evening's name printed across them in
its own colour. Evenlight keeps the cards in a stack. Nothing else. It does not score the air, does not
judge the weather, does not remind him, and does not try to get anyone outside — he is already outside. The
finding is the stack itself: four or five evenings from one hostel balcony in Punjab during smog season,
each one produced by pixels that were really there, with the missed days left visible as holes.

A local open-weight model (`Gemma3-1B-IT` q4, on-device, CPU) writes the one line of prose on the card. It
is given the words. It is not allowed to choose them, and the spec explains why with measured numbers (§7).

## 2. Why this shape, decided against the field

Measured 2026-10-07 from the first 200 posts tagged `hf26challenge`: local-model-generates-an-outdoor-quest
≈30, screen-time inversion ≈10, garden planner ≈9, bird-call ID ≈8, **field notebook / walk journal / "field
card" ≈8**, "when is the weather good to go out" ≈7, air-quality window ≈4, photo judged by a local model ≈4.
No entry is from Pakistan.

- **"Card" is the dominant shape of this challenge** — six of the 200 are literally a card (`TrailCard`,
  `fieldcard`, `Paper Trail`, `Pocket Field`, `Lakefront Window`, `Five-Minute Field Notes`), and every one of
  them is a *text* card a model writes. The word stays out of the title. The difference is the mechanism:
  those write sentences, this renders measured colour.
- **Neighbours are sky *enhancers*** (PICNIC, SkyPic) — they make a sky prettier. Evenlight reports what
  colour it actually was, and says so on the card.
- **Originality comes from the place and the unconditional record**, not from the concept.
- **Nothing is invented for a demo.** No streaks, badges, scores, reminders, or confidence percentages. If a
  number appears on a card, the app measured or computed it.

## 3. Facts about the real user (the design's constraints)

- Hostel room on a busy bazaar side; shops close around 21:00.
- Balcony view is **buildings and sky only** — no distant landmark. So the card's bands are sky plus
  building fronts, and the bottom band is usually wall, not horizon. That is a fact about the picture, not
  a defect to hide.
- He is already outside daily; **the phone comes with him**. This is why the design has no persuasion layer.
- Power cuts (~3 h/day) move him to the laptop, not outdoors — an outage-triggered design was dropped as fiction.
- Test device: a Pixel 7 (self-reported "6 GB"; the official spec is 8 GB — **read it off the device before
  writing any performance sentence**). The measured probe numbers in §7 are from an x86_64 API-35 emulator
  with ~2.9 GB `MemTotal`, not from that phone.

## 4. Scope

### In v2
1. Android app (Kotlin, single Activity, Compose): one-time typed coordinates, camera capture, band
   sampling, card render, card stack with visible gaps.
2. On-device Gemma wording of one line per card, gated by a verifier, with a defined no-model fallback.
3. A Python reference implementation of the sampler and renderer, so a reviewer can turn their own sky
   photo into a card in under a minute without installing anything.
4. A cross-language equivalence test proving the Kotlin and Python paths produce identical bands (§10).
5. The user's own evenings, gaps included, and a DEV write-up.

### Out of v2, with reasons
- **The laptop in the nightly loop.** It was never needed; it existed because writing Python was easier for
  the agent than writing Kotlin. Capture, sampling, rendering and wording all happen on the phone.
- **Air-quality comparison and the composite haze index.** Once the laptop left the loop there was no
  honest place for a 0–10 "index" derived from four points, and an in-app AQI call needs both a network
  permission and a key. The app makes no claim about air.
- **Balcony-session interruption counts** (`left_app` / `put_down`). Dropped: the chosen artefact is the
  card, and the counter was a second instrument bolted to it. Flagged here as a deliberate change from v1
  so the user can object.
- **Any model judgement.** Measured in §7 — a 1B model cannot pick between options reliably.
- Notifications, widgets, streaks, accounts, cloud sync, gallery import.

## 5. Architecture

```
[ Android phone — no INTERNET permission in the manifest ]
 MainActivity (Compose)
  ├ SunWindow.kt   NOAA solar geometry: sunset time + current sun altitude. Pure computation.
  ├ Capture.kt     CameraX still → evenlight/raw/YYYY-MM-DD--HHMM.jpg (EXIF rotation applied)
  ├ Sample.kt      pure JVM over an IntArray pixel buffer → 8 ordered band RGBs   ← no Android deps
  ├ Match.kt       winner band + nearest vocabulary word. Pure, deterministic.    ← no Android deps
  ├ Wording.kt     LiteRT-LM Engine(CPU) → one line, gated by Verifier.kt
  ├ Card.kt        Canvas render → evenlight/cards/YYYY-MM-DD.png
  └ Stack.kt       the evenings, oldest first, holes rendered as blank cards
 files: raw/*.jpg, cards/*.png, records/YYYY-MM-DD.json, settings.json

[ reference tooling, laptop, not in the nightly path ]
 tools/python/skycard.py   same band math + same card layout, from a JPEG file
 fixtures/                 input photo + expected bands JSON + expected card PNG
```

`Sample.kt` and `Match.kt` deliberately take an `IntArray` of pixels and hold no Android or camera types.
That is what makes them testable in a plain JVM test and what lets the Python port be checked against them
line for line. The model is a separate optional component: the app is complete and correct without it.

## 6. Card design (the visual identity the user picked)

- **Top ~62%:** the eight measured bands, top of frame at the top, drawn as a full-bleed linear blend
  between band centres. **No saturation boost, no white balance, no presets, no filters.**
- **Bottom ~38%:** warm off-white paper.
- **The winning colour is a solid strip carrying the name in the paper colour** — not coloured *text*.
  Printing the name in a measured pale hue onto off-white fails contrast; filling a strip with that exact
  measured hue and reversing the type keeps every displayed colour real and untouched. This is the
  resolution of the "winner hex too pale" problem, and it changes no measured value.
- **Header:** the evening's name, lowercase, two words.
- **Sub-line:** date, then `sun 9°` — the altitude actually computed at the shutter instant.
- **Footer, one quiet line:** `measured, not adjusted · 8 bands`. Provenance is on the artefact, not buried
  in a README.
- Two neighbouring bands (the winner's immediate above and below) are printed as small squares beside the
  sub-line, because the ordering is the information and one band alone loses it.
- **Stack:** vertical scroll, one card per evening, oldest first. An evening with no photo renders as an
  outlined empty card with the date. Gaps are never collapsed.

### 6.1 Design system, as approved

Pinned in `design/evenlight-mockups.html` (four screens + motion filmstrip + responsive wires); the file is
the source of truth and the plan quotes its values rather than restating them all.

- **Chrome is paper and ink only — zero brand hue.** Every saturated colour on screen is a measured band.
  `paper #F4EFE6 · card #FBF8F2 · ink #2A2622 · muted #6E6656 · hairline #D9D0BF`, night theme
  `#17150F / #E9E3D6 / #332F26`. A fine grain sits on chrome surfaces only, never on a measured band.
- **Type:** Fraunces variable (`opsz`/`SOFT`/`WONK`) for display, Instrument Sans for body and captions.
  Both SIL OFL, so both are **bundled in `res/font/`** — the app has no network and cannot use
  Google Fonts' downloadable-font provider.
- **Shape lock:** actions are pills, cards 20, chips 12, the colour field 0 — full bleed, because rounding a
  measured band would crop data. Spacing on a 4 grid, 20 gutter, 16 between stack cards.
- **Contrast rule:** the name sits on a solid strip of the winner band in paper or ink, chosen by that band's
  WCAG relative luminance (< 0.45 → paper). This is why the winner is a strip and not coloured text, and it
  is also a limit: that pairing clears WCAG's 3:1 large-text bar but not the 4.5:1 body bar, so the strip
  carries display type only, never body copy.
- **Motion:** one delight, shutter→card, 450 ms total. Bands reveal by clip height, never by animating
  height. Haptics one per action, on the same frame as the visual, and none while the model writes.
  Reduce Motion collapses it to a 120 ms cross-fade.
- **Responsive:** three width classes (1-up < 600 dp, 2-up centred 600–840, two panes ≥ 840). The card has a
  fixed aspect and never stretches. Display type stops scaling above 1.15× font scale. Phone landscape is
  unsupported in v2, stated as a decision.

## 7. The model — what it is for, decided from measurement

Five probes, all run before any product code.

| design | mechanism | result |
|---|---|---|
| free generation | "name the evening, 2–3 words, no digits" from band + altitude text | obeys format; **copies nouns straight out of the input** instead of naming |
| forced variety | + ban list, exactly 2 words, temp 1.0 | same echo failure |
| closed vocabulary | + "choose from exactly these words" (the draft vocabulary) | **0/6 on-list** — answered `sun and stone`, `sun, concrete` |
| **top-3 selection** | "choose one of these three" | **on-list 17/18**, but **stable across reordering 1/6** |
| **same test, on-device `.litertlm`** | identical 18 calls, CPU | **on-list 18/18, single-line 18/18, stable 1/6** |

On-device numbers, 2026-10-08, x86_64 API-35 AVD, CPU backend, `Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm`
(584,417,280 B): **init 4,566 ms · peak RSS 1,269 MB · 1,080/1,247/3,502 ms per call (min/med/max)**.
The engine also writes an XNNPack cache of 508,539,776 B next to the model, so **real disk cost is
1,092,957,056 B ≈ 1.02 GiB**, and the README must state that, not 557 MB.

**Reading.** The model is fast and perfectly obedient about *format*, and it is a coin flip about
*judgement* — the same 1/6 across two independent runtimes, so this is a model limit, not a quantisation or
harness artifact. Case 6 returned three different names for three orderings of the same three options.
Therefore: **the matcher owns every choice (winner band, band order, vocabulary word); Gemma only words a
line from a name it is handed.** It may not pick, rank, order, or measure.

**Wording task, precisely.** Input: the chosen name, the eight bands as hex, the sun altitude. Output: one
lowercase line of 4–8 words that must contain the chosen name verbatim.
**Verifier, in order:** reject if the line contains `[0-9]`, `#`, `<`, `{`, `(`, a newline, more than 8
words, or if the chosen name is absent from it. On rejection → template line, and `wording_rejected++`.
The card still renders. **The README reports the wording rates as a result, not a footnote** —
(v2.1 note, 2026-10-10: the shipped README reports the counts split by provenance rather than as one
`wording_rejected/wording_total` fraction, and says why at its own §on the wording gate. This instruction is
superseded by that; the decision it was protecting — that the rate is published, not buried — stands.)
a rejection rate on a 1B model is a real finding about small models and is the honest version of "AI writes
your journal".

**The model is optional by construction.** Absent file, failed `initialize`, or RAM pressure → the card uses
the matcher's word as the header and the record marks `wording: template`. The app must never be bricked by
a 1.02 GB download, and a reviewer who skips the download still gets working cards.

## 8. App behaviour, precisely

### 8.1 Setup (once)
Type a place label and lat/lon to two decimals. Timezone from the device. **No location permission.**
Stored in `settings.json`. Coordinates exist for one purpose: computing the sun's altitude honestly.

### 8.2 When to shoot
`SunWindow.sunsetToday(zone, lat, lon)` and `SunWindow.altitudeAt(instant, …)` from the standard NOAA
solar-position approximation — pure device computation, which is what keeps the no-network claim true.
The home screen shows one line: **"sunset 17:40 — in 14 min"**, and inside ±20 min the shutter button.
**He can shoot at any time; the app tells him when the light is interesting, never whether to do anything.**
No notification, no widget (exact alarms need `SCHEDULE_EXACT_ALARM`, notifications need
`POST_NOTIFICATIONS` — two prompts for a nudge, in an app whose argument is asking for nothing).

**Probe #1 is now done (2026-10-08).** The NOAA solar series implemented as specified returns
**12:39:33Z on 2026-10-08 and 12:35:55Z on 2026-10-11** for 31.52, 74.35, against sunrise-sunset.org's
**12:40:48Z and 12:37:11Z** — a consistent **−1.2 min** bias, inside the ±2 min the plan allows and
irrelevant to a ±20-minute window. Two consequences: the maths needs no fixing, and the mockup's original
"sunset 17:52" copy was wrong for this address — real sunset this week is **17:40 and 17:37 PKT**. That copy
is corrected in `design/evenlight-mockups.html` (2026-10-08); the rendered PNG is **visual reference only**,
not a spec, and is not pixel-authoritative.

### 8.3 Capture
One tap. CameraX `ImageCapture`, back camera, EXIF rotation applied, JPEG to `raw/`, and immediately:
sample, match, render, file. **No gallery import, ever** — an imported photo makes this a filter app and
kills the only claim it has. The original JPEG is kept, because a card nobody can re-derive is decoration.

**Framing aid.** v1's fixed-alpha ghost of a reference image is not in v2 and could not have worked: one
alpha value is invisible over a dark band and a smear over a bright one. What replaces it is three hairlines
drawn with `BlendMode.Difference` in white, which renders as the inverse of whatever is under them and so
stays visible against any sky without sampling the preview. It is a registration aid, not an instrument —
the bands are whole-frame averages, so small shifts between evenings move the card a little and nothing more.

### 8.4 Sampling — the actual algorithm
- Downscale to width 64 preserving aspect ratio (after rotation), so ~64×48 for 4:3.
- Split into **8 equal horizontal bands**; per band take the arithmetic mean R, G, B over its pixels.
- Output is **ordered** top → bottom. This ordering *is* the image, and it is why `androidx.palette` is
  not used: Palette clusters colours globally and throws the vertical sequence away.
- **Winner band** = maximum chroma range `max(c)−min(c)`; ties broken by higher luminance. Deterministic.
- **Name** = nearest neighbour in RGB (Euclidean, no weighting) over a **35-word vocabulary with one anchor
  hex per word** (the list was described as 36 words in the first draft; counted, it is 35, and the count is
  now fixed everywhere rather than padded with a word nobody chose — still, smothered, clearing, brassy, tarnished, milky, flint, ember, copper, chalk, slate,
  ash, wax, honey, bruised, waited, low, thin, wide, close, dusty, wet, salt, iron, paper, linen, wool,
  amber, smoke, held, lifted, sunken, gathering, fading, bright). Sub-millisecond, reproducible, and
  structurally incapable of inventing a word. The dropped 36th was `glass` — named here so the count can be
  audited instead of re-litigated; it never had an anchor hex, so it was never matchable in the first place.
- Record: eight hex values, winner index, name, sun altitude, shutter time, device, wording source.

### 8.5 What "low light" does
Night or a blacked-out sky produces eight near-zero bands and the matcher lands on `ash` or `smoke`. That is
honest output for an honest picture. The record stores the mean luminance so a reviewer can see it, and the
card still shows the date. No day is dropped and none is silently resampled.

## 9. Data and files

| Path | Written by | Contents |
|---|---|---|
| `evenlight/settings.json` | phone | place label, lat/lon |
| `evenlight/raw/YYYY-MM-DD--HHMM.jpg` | phone | the photograph, unedited |
| `evenlight/cards/YYYY-MM-DD.png` | phone | the rendered card |
| `evenlight/records/YYYY-MM-DD.json` | phone | 8 bands, winner index, name, sun altitude, wording line, `wording` source, rejection flag |
| `tools/python/skycard.py` | repo | reference implementation |
| `fixtures/` | repo | input photos + expected bands + expected card |

No database, no account, no analytics, no identifiers, nothing uploaded. **A card is re-derivable from its
raw photo by running `skycard.py` on it — and that equivalence is tested, not asserted (§10).**

## 10. Testing

- **JVM, no device:** `SunWindowTest` — sunset and altitude against published times for his coordinates on
  two dates, tolerance ±2 min. `SampleTest` — golden band values on synthetic gradient bitmaps (a known
  8-band ramp must return those eight colours) plus ordering and tie-break cases. `MatchTest` — winner
  selection and nearest-word behaviour, including a tie. `VerifierTest` — a line containing a digit, a `#`,
  markup, or missing the given name must each be rejected.
- **Cross-language equivalence (the technical showpiece):** `skycard.py` and `Sample.kt` run on the same
  fixture JPEGs must produce **identical** 8-band hex arrays. This test also proves the downscale filter is
  specified rather than implied (the risky part: Kotlin `Bitmap.createScaledBitmap` defaults to bilinear, so
  the Python port pins `Image.Resampling.BILINEAR` and the test fails loudly if they diverge).
- **Emulator:** one scripted pass — grant camera, capture, assert a card PNG and a record JSON exist,
  assert the stack shows a hole for a date with no photo.
- **Real device:** tonight's actual evening. Not substitutable by any test.

## 11. Error handling, named now

- **No camera permission** → app opens, stack works, capture disabled with one line of reason.
- **No model file / init failure / RAM pressure** → template wording path (§7). Never a crash, never a
  blocked card.
- **Model outputs junk** → verifier rejects, template used, counter incremented, visible in the report.
- **Clock moved backwards / timezone changed** → record stores device time and elapsed realtime; a
  mismatch marks the day `clock_suspect` rather than silently reordering the stack.
- **Two shots the same evening** → the later one wins the card, both stay in `records` as `attempts`. n on
  the card is evenings with a card, not shots taken, and the post states it that way.
- **Card render fails** → the record JSON is still written before the render, so the evening is not lost.

## 12. Corrections to v1's claims (do not repeat these)

1. **v1 said "no `INTERNET` permission" while its pipeline made a network call.** It was false as written.
   v2 has no network at all, so the claim is now literally checkable in `AndroidManifest.xml`.
2. **v1 quoted "~1.1 GB" for model storage.** Measured: **1.02 GiB** (1,092,957,056 B), because the XNNPack
   cache is ~87% of the weights file, not "roughly equal". Quote the measured number.
3. **v1's target device was assumed.** The probe numbers are emulator numbers. Any sentence about his phone
   must come from his phone.

## 13. Build order and time budget (Oct 8 → Oct 12, ~4 days)

Every day of slippage costs one evening from the record, so the capture path is built first and used
tonight.

| # | Step | Budget |
|---|---|---|
| 1 | Repo + `SunWindow` with its test | 1.5 h |
| 2 | `Sample.kt` + `Match.kt` with golden tests (pure JVM, no UI) | 2 h |
| 3 | `Capture.kt` + `Card.kt` → **first real card tonight, even ugly** | 4 h |
| 4 | `Stack.kt`, gaps, records | 2 h |
| 5 | `skycard.py` + fixtures + cross-language equivalence test | 3 h |
| 6 | `Wording.kt` + `Verifier.kt` + fallback | 2.5 h |
| 7 | README, `GET_MODEL.md` with the checksum, screenshots | 2 h |
| 8 | Write-up and publish (must land before Oct 11 23:59 PDT) | 6–8 h |

**Pre-named cut order if Oct 10 arrives unfinished** (decided now so it is not a panic choice):
1. `Wording.kt` → drop Gemma entirely, cards use the matcher's word, and the post explains the 1/6
   consistency measurement as the reason — the failure becomes the content;
2. cross-language Python port → ship the Kotlin-only app plus a published golden band table;
3. neighbour-band squares and the two-word name → bare band stack + date;
4. ghost framing (not in v2 scope anyway).

**Never cut:** the fresh-capture-only rule, measured-unmodified colour, the visible gaps, the no-`INTERNET`
manifest, or the exact evening count with holes stated.

## 14. Risks, plainly

- **n is 4 or 5.** Four evenings left in the window. This is a week of one window at one address, not a
  finding. The post states the count exactly, including holes, and never rounds up.
- **A card can look like a filter app.** The defence is behavioural and it is load-bearing: no presets, no
  gallery import, and the footer says what it did. If the user tweaks saturation "to look better", the
  project's premise dies — so colour handling is frozen in §6 and covered by the equivalence test.
- **1.02 GB is a real install barrier** and most reviewers will not download it. Mitigated by the app
  working fully without the model, and by `skycard.py` being the actual demo.
- **The AI contribution is deliberately small and the reason is on paper:** it measured 18/18 obedient on
  format and 1/6 reliable on judgement, so it was moved off judgement. If that reads as thin for "Best Use
  of Gemma", the fix is not to inflate it — the user can decline the category instead.
- **He might stop shooting after two evenings.** The answer is not a reminder system; it is that the trigger
  is a place he already sits, plus a pre-committed statement of whatever data exists.

## 15. Naming, repo, license

- **Evenlight** — "the evening light", and the plain word for the window the app runs in. Earlier working
  name *Aasman* (آسمان) dropped so the name reads outside Pakistan. Tagline: *one balcony, one sky, one card
  an evening.*
- Name checks done: no DEV entry in the 200 census uses it; every obvious dusk word is owned
  (`Duskline` = GitHub weather app, `Afterglow` = 738 repos, `Gloam` = an on-device AI agent, `Half-light` =
  a CSS library), and **"-hour" names are unusable in this challenge** — `golden-hour` and `Golden Hour
  Catcher` are both already submitted. `shadow-hour` is taken by `dfwgj/shadow-hour`, which is why the
  competing Shadow Hour concept was rejected.
- Repo: `Burry071/evenlight` (public). **Creating it is a shared-state action needing explicit go-ahead**,
  and must happen inside Oct 5–11.
- License MIT for code. **Weights are not redistributed** — GitHub's hard per-file limit is 100 MB, so the
  584 MB file cannot be committed at all. `models/GET_MODEL.md` carries the HF URL, exact filename,
  byte size 584,417,280 and sha256 `1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be`,
  under Gemma Terms of Use.

## 16. Success criteria

1. An installed, used app and a public repo with a reproducible pipeline.
2. Four or five real cards, one per evening, each re-derivable from its raw photo.
3. A cross-language test showing Kotlin and Python agree on the same pixels.
4. `skycard.py myphoto.jpg` producing a card for a stranger in under a minute.
5. A write-up whose every number traces to a file in the repo, including the model's measured failure rate.
6. Nothing claiming to measure air quality, and nothing claiming the app got anyone outside.
