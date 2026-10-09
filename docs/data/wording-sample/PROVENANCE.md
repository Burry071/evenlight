# The 19 shots behind the wording numbers

These are real `EveningRecord` files, dumped from the app's own data directory with
`adb pull /data/com.burry071.evenlight/files/evenlight/records`. Every failure rate the README and the DEV post
publishes is arithmetic over exactly these files, so a reader can recount them instead of taking my word:

```
python - <<'PY'
import json, glob, re
r = [json.load(open(f, encoding='utf-8')) for f in glob.glob('docs/data/wording-sample/*.json')]
print(len(r), 'shots')
print('answered :', sum(1 for d in r if d['wording_source'] in ('model', 'template', 'no-answer')))
print('accepted :', sum(1 for d in r if d['wording_source'] == 'model'))
print('rejected :', sum(1 for d in r if d['wording_rejected']))
print('silent   :', sum(1 for d in r if d['wording_source'] in ('none', 'no-answer')))
chars = re.compile(r"[a-z .,'-]+")   # the shipped allowlist's character set, Verifier.kt:15
print('would pass today :', sum(1 for d in r if d['wording_source'] == 'model' and chars.fullmatch(d['wording'])))
PY
```

Expected: 19 shots, 12 answered, 8 accepted, 4 rejected, 7 silent, 7 would pass today.

The last line is the one that matters for the published rate: seven of the twelve answers a model actually gave
would survive the gate that ships, and the eighth is `the **ash** sky glowed softly as dusk fell.`, which the
denylist let through. So the post says **7 of 12 answered-and-valid**, never 8 of 12.

**What this is not.** The images came from the emulator's synthetic camera input, not from a balcony. Every card in
this set measured `sun_altitude_deg` of -63 or -64 (-65 on four of them) because that is what the stopgap scene
produces with no sun in it, and every one of the nineteen has `winner: 5` - the bands move, the winning band index
never does. So this sample tests the wording path - prompt, gate, provenance, fallback - and says nothing about
weather, locations, or evenings. Five of the nineteen are dated 2026-10-10 only because the emulator's clock crossed
midnight while the shots were being taken.

**Two gates, two regimes, never blended.** The 13 files from 2026-10-09 23:23-23:29 (`2026-10-09.json` plus
`attempt-2` through `attempt-13`) were written by the build whose verifier used a denylist of banned characters.
That gate let `the **ash** sky glowed softly as dusk fell.` onto a real card, which is the bug that produced the
allowlist. The 6 files from 23:57 onward (`attempt-14` and everything dated 2026-10-10) were written by the
allowlist build. Counting the two together would put a miss the shipped gate cannot make into the shipped gate's
denominator, so the README reports them as separate rows.

**What is not here.** A rejected answer is counted but not kept: `wording_rejected` is `true` and the file stores
the template line that replaced it, never the line the model produced or which rule rejected it. Two of the six
allowlist answers are therefore unexplainable from this data, and the README says so. Storing the rejected line
would mean a new field on `EveningRecord`, and `:core` is frozen. No photo, no position, and no `place` name is in
these files - the coordinates and the place live only in `settings.json` on the device, which is not committed.

## The cadence, and what the timestamps can and cannot show

`three-timed-shots.txt` is the console output of three shots fired about a minute apart on the build that ships,
read off the device while it ran. Each line gives the record's first write and its final write after the wording
pass, so the model's line lands 21.4 s, 11.3 s and 14.2 s after the record existed, and 24.2 s, 12.4 s and 14.3 s
after the tap. Nothing else in this set has second resolution.

What a reader *can* recount from the JSON is the minute-resolution pattern behind the seven silences, because every
record carries `shutter` to the minute:

```
python - <<'PY'
import json, glob
from collections import defaultdict
by = defaultdict(list)
for f in glob.glob('docs/data/wording-sample/*.json'):
    d = json.load(open(f, encoding='utf-8'))
    by[d['shutter']].append(d['wording_source'])
for minute in sorted(by):
    print(minute, len(by[minute]), 'shots:', ' '.join(s[:4] for s in sorted(by[minute])))
PY
```

Every `none` in the set sits in the three minutes 23:24, 23:25 and 23:26, and those three minutes hold nine shots:
two of them answered, seven did not. The other ten shots, before and after that window, all answered. That is the
spacing finding at the resolution the committed data actually carries. The sharper version - the rapid shots fired
5-13 s after their neighbour, the answered ones at least 22 s apart - came from the device's file mtimes read with
`stat`, and I deleted those records when I put the emulator back the way I found it, so that sentence is my
measurement and not yours to check.
