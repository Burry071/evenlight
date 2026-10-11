# Evenlight: I gave the AI the smallest job in the app, and measured why

**Tagline:** one balcony, one sky, one card an evening.
**Repo:** [github.com/Burry071/evenlight](https://github.com/Burry071/evenlight) - public, 92 commits, first one
2026-10-08 01:55 +0500, inside the entry window.
**Hacktoberfest 2026, Week 1, theme "Touch Grass."**

Every entry in this theme seems to be about getting people outside. Mine takes a photo of the sky from a place you
are already sitting and turns it into a card. It does not know whether you went anywhere. I want to be careful about
that, because it is the most interesting thing about the project: the app claims exactly what it measures and
nothing else.

The other interesting thing is that I ran a language model on the phone - on an emulator standing in for a phone, for
every measurement in this post, which is its own story below - found it was bad at the obvious task, and gave it a
smaller one. That decision is the spine of this post, and every number behind it is either in a file in the
repo or labelled out loud as a measurement that is not.

## How it gets anyone outside, and the feature I refused to build

The template asks how this gets people off the screen and into the world, and the honest answer is narrower than
the question. The app's only input is a photo the camera takes while you are holding it. There is no gallery import
and no way to hand it an evening you did not stand under, so the artefact cannot exist unless somebody went outside
and pointed a phone at the sky. That is the entire mechanism. It is a weak one, and I would rather describe it
accurately than dress it up as a behaviour-change intervention.

I was asked for a sunset notification and did not build one. The reason is arithmetic, not principle: a notification
wants `POST_NOTIFICATIONS`, and the runtime permission set here is exactly `{CAMERA}` with a test asserting it.
Evenlight's one distinctive claim is that it can see nothing but the photo you hand it - there is no `INTERNET`
permission in the manifest, so the measurements cannot leave the phone even if the app wanted them to. Trading a
verifiable fact for a nudge I would not personally obey is a bad deal at any time and a worse one the day before a
deadline. The argument for the other side is real, though, so here it is: an app that never asks gets opened only
when you happen to remember, and the stack in this repo has holes in it for exactly that reason.

One claim I checked in the code rather than in the design doc, because it is the one that makes this more than a
sunset app: the shutter is gated on nothing but `enabled = !busy`. No hour check, no sun-altitude check, no scene
check. "Evening" in the UI describes what I use it for, not what it accepts. Point it at a garden at noon, at grass,
at a wall, and it measures eight bands of whatever colour is actually there and names it from the same 35-word
vocabulary. The only hard requirement is that you saved a place and a pair of coordinates once, because the card
prints the sun's altitude - and the app refuses to file a card rather than print an altitude computed for the Gulf
of Guinea.

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

## Demo

The real thing first: three screens off a Pixel 7 on the evening of 10 October, shot from a balcony four minutes
before the sunset the phone had computed for 17:49.

![Shoot, on the phone: the place, the countdown, the shutter](docs/images/phone-shoot.jpg)

Shoot is one tap, under a countdown the device computed itself - in 4 min, sunset 17:49, evening 1 - from NOAA
solar maths and a pair of coordinates typed in once at setup. No location permission, no network to fetch either.

![The card the phone filed: eight bands, the name thin, sun 0 degrees](docs/images/phone-card.jpg)

Four minutes before sunset the sun's altitude rounds to 0 degrees, and that is what the card says: not a forecast,
the altitude at the shutter minute. Eight measured bands, a name the matcher chose from a fixed vocabulary of 35
words, and one line of wording - here in the template's voice, "thin light, measured from this sky", which is what
the app writes when the model's answer is unusable or absent. The record on the phone says which of the two it
was; I have not pulled it off the device yet, so I am not going to guess in print.

![The stack on the phone: one evening, no holes](docs/images/phone-stack.jpg)

And the stack, in dark mode: one evening, zero holes. The hole is the design - miss an evening and the stack shows
the gap instead of pretending.

The five-panel walk below is the emulator's, because setup was never photographed on the phone. It is drawn by
`tools/python/make_screens.py` from five committed screenshots, so it is regenerated rather than collaged, and it
says on its own face that those panels are emulator panels - which is why the card in its fourth frame reads a sun
altitude of -63 degrees, there being no sun in a synthetic scene. Set that frame beside the phone's 0 degrees and
you have the two halves of this post in one picture: what the model phrasing was measured on, and what the
measurement itself looks like on a real sky.

![Setup, Tonight, Shoot, Card and Stack, in that order, on the emulator](docs/images/screens-tour.gif)

Setup asks for a place name and a pair of coordinates, once. The tour's other four panels are the countdown and the
three phone screens above.

Two cards the app actually filed on the emulator, both reproducible from the JSON beside them:

![A card whose line claims stars in a scene that has none](docs/images/card-2026-10-10.png)

![A card using the template fallback](docs/images/card-2026-10-09.png)

The app itself is not a link you can click. It is an APK you build from the repo
(`gradle :app:assembleDebug`, which needs network on a first run for dependencies, then
`adb install -r app/build/outputs/apk/debug/app-debug.apk`),
61,231,992 bytes with no model weights inside it, and the weights are a separate 584 MB gated download whose terms
you accept in your own browser. That is the one part of the demo I cannot hand you, and it is the reason the two
paths below exist: the measurement is demonstrable without any of it.

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

The system prompt started as one sentence about what it may do, plus a ban on digits, brackets and newlines, and the
verifier that read the reply had the same shape; both halves turned out wrong and both were rewritten, the prompt into
an allowlist-shaped ask and the gate into the character set below. Then a verifier reads the reply. It rejects the line if the reply is not lowercase, contains a digit, is more than eight words or
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
evenings: **did it answer** (12 of 19) and **was that answer valid** (7 of the 12 answers pass today's gate - the
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
about a minute apart, each with the time its record first appeared and the time it was written last. The final write
lands between 12.4 s and 24.2 s after the shutter tap, which is 11.3 to 21.4 s after the record already existed. Two
of the three are model lines (24.2 s and 14.3 s after the tap); the 12.4 s one is a rejected line, so the fastest
write in the set was the template, not an answer. So a
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
  belongs here, and no claim about evenings either. The one real sky in this post is the phone's, at the top of the
  demo: one evening, sun 0 degrees, and no weather claim attached to it either.
- **What the gate cannot catch.** Two accepted lines claim stars - `the stars were bright against an ash-colored
  sky.` and `the stars shimmered beneath an ash-lit sky.` - in a scene with no stars and a record that measures
  none. A verifier can only check form, because the only facts this pipeline has are eight colours and one altitude.
  That is the argument for the whole design: the colour on the card never comes from the model. If I had let the
  model choose anything at all, "stars" would have been a plausible-looking lie on a card that claims to be a
  measurement.

What this is not yet: a timing or a memory number from the phone I own. The phone has filed one real evening - the
three screens at the top of this post, 10 October, sun 0 degrees - but I have not pulled its record or read its
memory off the device, so every millisecond and megabyte below is still the emulator's: x86_64 with 3 GB of RAM
and, with the model resident, about 132 MB free and the low-memory killer active. And the emulator's set is
nineteen shots of one synthetic scene, not a season. Two timings, because they are different measurements and
averaging them would be a lie: the bare engine call
in the probe cost 1,080 / 1,247 / 3,502 ms (min / median / max), while inside the app the rewrite lands 11-21 s
after the record is filed. I have not split that difference up, so I am not going to explain it. Init 4,566 ms, peak
RSS 1,269 MB, both from the emulator.

## How the colour is measured, since that is where the bugs were

The photo is resampled to 64 pixels wide with a hand-written bilinear filter, split into eight bands, and each band
is averaged per channel. Mean luminance uses the sRGB linearisation, so a value at or below 0.03928 divides by 12.92 and
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
was a throwaway probe and its script is not in this repository, so this is the one number in the post whose derivation
script you cannot even look at - the emulator timings above are equally yours to take on trust. What you can check is the fix: `fieldBlend` returns a `Double`, and no cast quantises it on the way to a
pixel. Canonical precision is now 64-bit on both sides, and the Python port is told never to touch
`numpy.float32`.

**Name ties.** Two vocabulary words can sit at the same distance from a measured colour. The earlier entry in the
vocabulary wins, and a test asserts that order rather than leaving it to whatever a map iteration does on some
machine later.

## The part anyone can try in twenty seconds

Nobody has to install an Android app, and nobody has to download a gigabyte of weights.

```bash
python tools/python/skycard.py photo.jpg -o card.png --print-json
python tools/python/skycard.py --from-record docs/data/wording-sample/2026-10-10-attempt-5.json -o card.png
```

The first line prints the eight bands, which band won, and the name, then writes a card. The second redraws the card
the app filed from the JSON record it wrote, which is how you can check that the picture says what the measurement
says rather than what I say about it. Timed as whole processes on this box,
two runs each: 0.27 s and 0.33 s for a 128x96 fixture, 4.7 s and 6.1 s for a 4032x3024 JPEG at 2 MB (not committed - that second file
is a synthetic gradient with noise in it at the resolution a common phone shoots at, not a photograph - the one real
sky in this repo is the phone's, photographed for the demo above), and the spread between its two runs is why I give
both numbers instead of a confident
one. The band arithmetic is the same arithmetic as the Kotlin, line for line, and a test on both sides asserts
against the same five PNG fixtures, so agreement is transitive and neither language can drift without a failure.
89 Kotlin core tests, 49 app tests, 20 Python tests.

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
- The debug APK at this commit is 61,231,992 bytes (58.4 MiB) with zero model weights in it. That is the inference
  library's native code, compiled for four ABIs. The build before the last round of fixes was 61,350,388 bytes, and
  two builds of one tree half an hour apart came out 62 bytes apart, so read the figure as "about 58.4 MiB" rather
  than as a checksum.
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

There is one real evening in this repo now: 10 October, a balcony, four minutes before a sunset the phone computed
for 17:49, sun 0 degrees, name thin, filed on a Pixel 7 and photographed at the top of this post. Everything else -
every record behind the measurements above, and the five PNGs under `fixtures/`, which are drawn, not photographed -
is the emulator's. The rest of the stack is still empty, and it will keep showing that: miss an evening and the hole
stays visible. That is the part of the design that survives my own laziness.

One thing I did learn, and it is the reason the model ended up with the smallest job in the app: I spent days
treating the 1/6 as a bug in my own harness. It reproduced across two independent runtimes, which is what the spec
now says plainly, that this is a model limit and not a quantisation artifact. At some point I stopped debugging and
accepted that the model is genuinely that unreliable at picking. Knowing that is worth more than knowing why my
code was right, and the only way I found it was to measure before I built.

## Why open innovation matters here

Three things about this project only work because the pieces are open, and the third one is uncomfortable.

The model is open-weight - Gemma 3 1B IT, q4, 584,417,280 bytes, run through LiteRT-LM 0.16.1 on the CPU backend -
and that is not a licensing nicety. It is why the app can have no `INTERNET` permission at all. A hosted model puts
a network permission in the manifest, and then "nothing leaves the phone" becomes a promise about my intentions.
Open weights turned a privacy claim into a fact about a file, which is the kind of claim a test can hold.

The arithmetic is open, so the central claim of this post is checkable rather than believable. The band sampler
exists twice - Kotlin on the phone, Python in `tools/python/skycard.py` - and a test on each side asserts against
the same five PNG fixtures, so neither language can drift without a failure. The nineteen records behind the model's
failure rate are JSON files in the repo with a recount script beside them. The finding that a 1B model changed its
mind in five of six reorderings of the same three options is a claim about weights you can download and run
yourself. If any of that were closed, you would have to take my word for the number that decided the architecture -
and that number is the only reason the architecture looks the way it does.

The uncomfortable one: openness is also why I could afford to be pessimistic. Those probe runs cost nothing but
time, because nobody was metering the calls. Eighteen calls to discover that a model picks badly is a free
experiment on open weights and an invoice on a hosted API, and an expensive experiment is one you are tempted to
skip and assume your way past instead. Open weights bought the measurement that demoted the model to the smallest
job in the app. I do not think I would have paid to be told I was wrong.

## Code, and how it was built

The repository carries the spec it was built from and the plan that decomposed it, both committed, both written
before the code they describe: `docs/superpowers/specs/2026-10-07-evenlight-design.md` and
`docs/superpowers/plans/2026-10-08-evenlight.md`. Both are wrong in places the implementation later corrected, and
section 12 of the spec is a list of claims from an earlier, discarded design - two of which were simply false and
are named as such. This was built with AI agents over four days. Leaving the arguments in the repo is the only
honest way to show that; a summary of the process would be a story about it instead.

Counts at the commit this post describes: **89 tests in `:core`, 49 in `:app`, 20 in Python**, no skips and no
failures. The debug APK is 61,231,992 bytes. The weights are not committed - GitHub's hard per-file limit is
100 MB and the file is 584,417,280 bytes, so `models/GET_MODEL.md` ships the URL, filename, byte size and SHA-256
instead, and `*.litertlm` is gitignored so the omission cannot be an accident.

## Prize categories

**Overall.**

**Best Use of Gemma.** Gemma 3 1B IT, q4, on-device through LiteRT-LM 0.16.1, CPU backend, no network. I want to be
exact about the size of the role, because the honest version is smaller than the category name suggests: the model
writes one line of prose per card and chooses nothing. Every decision - which band wins, what order the eight go in,
which of 35 words the evening gets - belongs to a deterministic matcher, because I measured the model failing at
exactly that job before I wrote the app. If "best use" means the largest role for the model, this is not that entry.
If it means the most measured one, the eighteen probe calls and nineteen device shots are in the sections above, and
the records are in the repo.

No other partner category. Nothing here is hosted, and I would rather leave a category empty than stretch a claim
to fill it.
