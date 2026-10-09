# Evenlight: I gave the AI the smallest job in the app, and measured why

**Tagline:** one balcony, one sky, one card an evening.
**Repo:** `Burry071/evenlight` (not pushed yet; the link lands when it is)
**Hacktoberfest 2026, Week 1, theme "Touch Grass."**

Every entry in this theme seems to be about getting people outside. Mine takes a photo of the sky from a place you
are already sitting and turns it into a card. It does not know whether you went anywhere. I want to be careful about
that, because it is the most interesting thing about the project: the app claims exactly what it measures and
nothing else.

The other interesting thing is that I ran a language model on the phone - on an emulator standing in for a phone, for
every measurement in this post, which is its own story below - found it was bad at the obvious task, and gave it a
smaller one. That decision is the spine of this post, and every number behind it is either in a file in the
repo or labelled out loud as a measurement that is not.

## What the card is

One photo, taken fresh by the camera. No gallery import, no presets, no saturation slider.

Eight horizontal colour bands, read off the photo. A name, chosen by matching the most colourful band against a
fixed vocabulary of 35 words. A date, and the sun's altitude in degrees at the moment the shutter was pressed. One
line of text.

Then, in the app rather than on the card, every evening you have recorded, oldest first, with a visible gap where an
evening is missing. A streak counter would have hidden the days I did not shoot, and the holes are the honest part
of the data.

The card is 1080x1440 pixels and the colour on it is the colour that was measured: no filter, no preset, and the
renderer's only colour input is the eight values. The reason I wrote a second implementation of the sampler in
Python is so that a change to those numbers cannot pass in one language alone.

## The measurement that decided the architecture

I wanted the model to look at eight colours and name the evening. So I ran it on the inputs the app would actually
hand it, before writing any app code. Five designs, a 1B parameter open-weight model on CPU, the same eighteen calls
on the last two.

| what I asked for | what came back |
|---|---|
| "name the evening, 2-3 words, no digits" | obeyed the format, and copied nouns straight out of my input |
| same, plus a ban list, exactly 2 words, temperature 1.0 | the same echo failure |
| "choose from exactly these words" (the real 35-word vocabulary) | **0/6 on-list.** It answered `sun and stone`, `sun, concrete` |
| "choose one of these three" | **on-list 17/18**, but **stable across reordering 1/6** |
| the identical 18 calls on-device, CPU | **on-list 18/18, single-line 18/18, stable 1/6** |

Read the middle row again. Handed the actual vocabulary and told to pick a word from it, it got the answer
*wrong every single time out of six*, inventing phrases that were not on the list. Handed three choices, it was
almost perfect: 17 of 18 on-list. Then I shuffled the three and it changed its mind in five of six attempts, and the
one stable answer reproduced across two independent runtimes - which rules out one runtime's quantisation being the
whole story, and is the only evidence I have that it is not an artifact of my harness. One case is
worth describing: the same three candidate names in three different orders came back with three different winners.

That is the whole finding, and it flipped my design. The model is perfectly capable of phrasing something it is
handed. It is not capable of deciding. So the matcher owns every choice in this app, meaning every choice: which
band wins, which order the bands go in, which word the evening gets. The model writes one line of prose from a word
it did not pick, and it is not allowed to pick anything.

```text
name: brassy
bands: #1E2438 #2A3350 #3C4A67 #5C6B82 #8A8375 #B08A5E #C2854A #3A322A
sun: 9 degrees
Write one line of four to eight words that contains the word brassy. Do not use any digit and do not add a
colour that is not listed.
```

The system prompt is one sentence about what it may do, plus a ban on digits, brackets and newlines. Then a verifier
reads the reply. It rejects the line if the reply is not lowercase, contains a digit, is more than eight words or
fewer than four, or is missing the word it was given. On rejection, the card uses a template line, `brassy light,
measured from this sky`, and a counter increments. The card still renders.

That was the design. Then I put the weights on a device and ran nineteen shots through the installed app, and both
halves of that paragraph turned out to be wrong about something.

## The model's measured failure rate, which is the actual result

Nineteen shots, weights present, engine already warm. **Seven of nineteen** got a line that belonged on the card.
Every number below is read out of the JSON the app files, and all nineteen records are in the repo at
`docs/data/wording-sample/` with their own recount script beside them, so this is arithmetic you can check rather
than a claim you have to trust.

They are two experiments, not one, because the verifier changed halfway through:

| gate | shots | answered | silent | rejected | accepted | accepted wrongly |
|---|---|---|---|---|---|---|
| denylist (the first 13) | 13 | 6 | 7 | 1 | 5 | 1 |
| allowlist (the last 6) | 6 | 6 | 0 | 3 | 3 | 0 |
| all 19 | 19 | 12 | 7 | 4 | 8 | 1 |

Blending those rows is the dishonest move, and it is tempting. The one wrongly accepted line is a miss the shipped
gate cannot make, so it does not belong in the shipped gate's rate; and a six-shot row cannot carry a rejection
rate at all (3 of 6 rejected reads as "the new gate is worse" and means nothing). So: the denylist's
failure is a finding about denylists, and the allowlist's row is a finding that the sample is too small to publish
a rate from.

That last line - `the **ash** sky glowed softly as dusk fell.` - is the one I would not have found by reading the
code. The verifier banned `#`, `<`, `{`, `(`. It did
not ban `*`. The gate is a denylist, which means it has to be right every time, and a 1B model only has to be
creative once. The card rendered the asterisks, literally, under the name `ash`, and the record said everything was
fine because the line had passed. The denylist became an allowlist: a line may now contain lowercase letters, spaces,
and `. , - '`. An allowlist only has to be right about the punctuation I actually want, and the vocabulary needs
none beyond that. The cost is honest and I would rather publish it than hide it: any odd character now counts as a
rejection, so the rejection rate goes up.

The seven silences are the second one, and none of them were visible until the schema could name them. The app filed
all seven as `wording_source: "none"`, which in my schema meant "there is no model on this device." There was a model
on the device. The field could not tell "never consulted" from "asked and got nothing," so any rate built on it was
uncomputable, and it was wrong in the flattering direction: a model that failed looked like a model that was absent.
There is a fourth value now, `"no-answer"`, and that makes the two failures separable instead of one share of
evenings: **did it answer** (12 of 19) and **was that answer valid** (7 of the 12 under the gate that ships - the
other five are the four the gate rejected plus the markdown line the old gate wrongly passed). One number about
latency, one about judgement. Both are recountable from the record files, and the script in
`docs/data/wording-sample/PROVENANCE.md` prints the 7.

Once the seven had their own value, their timestamps told the rest of the story. Every record carries its shutter
minute, and that alone is enough to see it: all seven silences sit in 23:24, 23:25 and 23:26, three minutes that hold
nine shots, of which two answered. The ten shots before and after that window all answered. Same build, same weights,
same model - the only variable is spacing. I have a sharper version of that sentence from the device's file mtimes at
second resolution, that the fast shots went 5-13 s after their neighbour and the answered ones at least 22 s. It is
not in the repo, because I deleted those records when I put the emulator back the way I found it, so count that half
as my measurement rather than something you can recount.

What is in the repo is `docs/data/wording-sample/three-timed-shots.txt`: three shots on the current build, fired
about a minute apart, each with the time its record first appeared and the time it was written last. The model's line
lands between 12.4 s and 24.2 s after the shutter tap, which is 11.3 to 21.4 s after the record already existed. So a
shot taken twelve seconds later arrives while the previous call is still inside the engine. The seven no-answers are
not a model that refuses. They are one engine and two callers, and the reason the closing path now holds a guard
across the whole native call rather than across the timeout.

The honest half: I fired those shots in a burst because I was testing a code path, not living with an app. One
evening a day is the cadence the thing is for, and in this sample the ten shots that were not fired two-to-four to a
minute all answered. Both numbers are in the table above. The one I would have published without looking is the
flattering one.

Traps I checked before trusting any of it, because a bad sample is worse than no sample:

- **The seed.** `SamplerConfig(topK = 20, topP = 0.9, temperature = 0.7)` sets no seed, so I was afraid consecutive
  replies might come back identical and my "19 shots" would really be one. Seven distinct lines out of eight
  accepted, one repeat (`the evening sky held an ash-like glow.` twice). Not pinned, but not independent either.
- **The scene.** The emulator's back camera is a synthetic scene, so those nineteen shots are not nineteen skies.
  They sample the model's phrasing and the gate's behaviour, which is what this section is about. The bands did move
  between shots (14 distinct band sets out of 19, names `ash` 13 times and `flint` 6), but the sun altitude read
  `-63°`, `-64°` or `-65°` on every card, because there is no sun in the test scene. So: no claim about weather
  belongs here, and no claim about evenings either.
- **What the gate cannot catch.** Two accepted lines claim stars - `the stars were bright against an ash-colored
  sky.` and `the stars shimmered beneath an ash-lit sky.` - in a scene with no stars and a record that measures
  none. A verifier can only check form, because the only facts this pipeline has are eight colours and one altitude.
  That is the argument for the whole design: the colour on the card never comes from the model. If I had let the
  model choose anything at all, "stars" would have been a plausible-looking lie on a card that claims to be a
  measurement.

What this is not yet: a number from the phone I own. The emulator is x86_64 with 3 GB of RAM and, with the model
resident, about 132 MB free and the low-memory killer active. And it is nineteen shots of one synthetic scene, not a
season. Two timings, because they are different measurements and averaging them would be a lie: the bare engine call
in the probe cost 1,080 / 1,247 / 3,502 ms (min / median / max), while inside the app the rewrite lands 11-21 s
after the record is filed. I have not split that difference up, so I am not going to explain it. Init 4,566 ms, peak
RSS 1,269 MB, both from the emulator.

## How the colour is measured, since that is where the bugs were

The photo is resampled to 64 pixels wide with a hand-written bilinear filter, split into eight bands, and each band
is averaged per channel. Mean luminance uses the sRGB linearisation, so a value below 0.03928 divides by 12.92 and
anything above it goes through a power of 2.4. The winning band is the one with the largest gap between its biggest
and smallest channel; a tie goes to the brighter band.

Four details cost real time, and they are all in the code with comments:

**Rounding.** Python's built-in `round` is banker's rounding, so it sends `.5` to the even neighbour. Kotlin's
`Math.round` sends it up. The Python port needed its own `round_half_up`, or the two implementations disagree at
every half-pixel boundary and the equivalence test fails for a reason neither algorithm caused.

**A clamp that only one fixture can see.** The resample height is derived from the source aspect ratio. When the
source is narrower than 64 pixels, the derived height exceeds the number of rows the photo actually has, and the
bilinear pass cheerfully interpolates rows that do not exist. Clamped, and one fixture is 32x6 so that no future
change to that line can pass silently. Every other fixture is at least 64 pixels wide, where the clamp is the
identity. That is why the odd-sized fixture exists.

**Precision across the border.** One side was doing channel arithmetic in 32-bit floats and the other in 64-bit.
That disagreement showed up as 13,161 differing `(row, band-pair, channel)` outcomes when the blend formula was
brute-forced instead of rendered - the kind of thing that looks like a rendering bug until you notice the two sides
round exact `.5` ties opposite ways (row 57, the pair 0 and 47: one language says 0, the other says 1). That sweep
was a throwaway probe and its script is not in this repository, so this is the one number in this post you cannot
recompute. What you can check is the fix: `fieldBlend` returns a `Double`, and no cast quantises it on the way to a
pixel. Canonical precision is now 64-bit on both sides, and the Python port is told never to touch
`numpy.float32`.

**Name ties.** Two vocabulary words can sit at the same distance from a measured colour. The earlier entry in the
vocabulary wins, and a test asserts that order rather than leaving it to whatever a map iteration does on some
machine later.

## The part anyone can try in twenty seconds

Nobody has to install an Android app, and nobody has to download a gigabyte of weights.

```bash
python tools/python/skycard.py photo.jpg -o card.png --print-json
```

It prints the eight bands, which band won, and the name, then writes a card. Timed as whole processes on this box,
two runs each: 0.27 s and 0.33 s for a 128x96 fixture, 4.7 s and 6.1 s for a 4032x3024 JPEG at 2 MB. That second file
is a synthetic gradient with noise in it at the resolution a common phone shoots at, not a photograph - nothing in
this repo is a real sky yet - and the spread between its two runs is why I give both numbers instead of a confident
one. The band arithmetic is the same arithmetic as the Kotlin, line for line, and a test on both sides asserts
against the same four PNG fixtures, so agreement is transitive and neither language can drift without a failure.
87 Kotlin core tests, 39 app tests, 13 Python tests.

## The costs, stated before you discover them

- The model file is 584,417,280 bytes and it is gated: you accept Gemma's terms in your own browser to download it.
  I am not allowed to commit it, and GitHub's 100 MB per-file limit means I could not.
- Total on-disk cost is about 1.02 GiB, not 557 MiB, because the engine writes a 508,539,776-byte (485 MiB) XNNPack
  cache beside the model. Both figures came from `stat` on the emulator's filesystem, not estimated - but they are
  emulator figures, and I have not measured a phone yet, so an ARM cache of a different size is possible.
- Model init measured 4,566 ms and peak RSS during a call 1,269 MB, both on the emulator; the timing a user actually
  feels is in the failure-rate section above - the line lands 11-21 s after the record is filed - and every one of
  those numbers is x86_64, not ARM. The phone I actually use is the missing measurement, and I would rather name that
  gap than round it away: it needs `getprop` and `/proc/meminfo` read off the device, not my memory of this box. The
  10 s give-up marker belongs re-checked there too, because on the emulator it turned out to bound nothing:
  `withTimeoutOrNull` cannot interrupt a blocking native call, and a call that ran 10.5 s still delivered its line
  past the marker.
- The debug APK is 61,350,388 bytes (58.5 MiB) with zero model weights in it. That is the inference library's native
  code, compiled for four ABIs. Two builds of the same tree half an hour apart came out 62 bytes apart, so read the
  figure as "about 58.5 MiB".
- Two variable fonts are bundled, 554 KB. Android cannot address a weight axis through the API people expect
  (`fontFeatureSettings` is for OpenType features, and `wght` is not one), so the variation is pinned in a
  `<font-family>` XML and loaded through `ResourcesCompat`, because `Resources.getFont` throws on a family file.
- The app asks for one runtime permission, `CAMERA`, and a test asserts the manifest has no `INTERNET`. Read the next
  sentence before you trust that bullet: the test opens `src/main/AndroidManifest.xml`, the file I wrote, not the
  *merged* manifest the build actually installs, so a dependency that smuggled `INTERNET` in through manifest merging
  would pass the test and break the promise. I did check the merged output by hand, debug and release: `CAMERA`, plus
  the local-receiver permission androidx generates for itself, and no `INTERNET` in either - but that check is a
  command you have to re-run after `assembleDebug`, not a test that fails for you.
  `grep uses-permission -A1 app/build/intermediates/merged_manifests/debug/*/AndroidManifest.xml`.
  Knowing the guarantee is weaker than the test makes it look is the point; a test can hide exactly that from its own
  author.

## What this app does not do

It does not measure air quality, pollution, UV, temperature, or your mood. It does not know if you left the
balcony. It has no reminders, no streak, no cloud sync, and no account. It cannot tell you the sky
looked better yesterday, because yesterday's photo was taken from a different angle of the same view and the app is
not that dishonest.

It does have a `share` link, and I would rather overstate that than let a reader find it later: it is an
`ACTION_SEND` of the card PNG through a `FileProvider`, with a per-URI read-only grant, and the runtime permission
set is still exactly `{CAMERA}`. Nothing leaves the phone by itself, which the manifest test is the guarantee of -
not my intention.

There is no real evening in this repo yet. Every card and every record I published came from an emulator pointed at
its own synthetic scene, and the four PNGs under `fixtures/` are drawn, not photographed. The first sky this app
measures is the one outside my window, and if I do not shoot it, the stack will show that too - the holes are the
part of the design that survives my own laziness.

One thing I did learn, and it is the reason the model ended up with the smallest job in the app: I spent days
treating the 1/6 as a bug in my own harness. It reproduced across two independent runtimes, which is what the spec
now says plainly, that this is a model limit and not a quantisation artifact. At some point I stopped debugging and
accepted that the model is genuinely that unreliable at picking. Knowing that is worth more than knowing why my
code was right, and the only way I found it was to measure before I built.
