# The 19 shots behind the wording numbers

These are real `EveningRecord` files, dumped from the app's own data directory with
`adb pull /data/com.burry071.evenlight/files/evenlight/records`. Every failure rate the README and the DEV post
publishes is arithmetic over exactly these files, so a reader can recount them instead of taking my word:

```
python - <<'PY'
import json, glob
r = [json.load(open(f, encoding='utf-8')) for f in glob.glob('docs/data/wording-sample/*.json')]
print(len(r), 'shots')
print('answered :', sum(1 for d in r if d['wording_source'] in ('model', 'template', 'no-answer')))
print('accepted :', sum(1 for d in r if d['wording_source'] == 'model'))
print('rejected :', sum(1 for d in r if d['wording_rejected']))
print('silent   :', sum(1 for d in r if d['wording_source'] in ('none', 'no-answer')))
PY
```

Expected: 19 shots, 12 answered, 8 accepted, 4 rejected, 7 silent.

**What this is not.** The images came from the emulator's synthetic camera input, not from a balcony. Every card in
this set measured `sun_altitude_deg` of -65 or -64 because that is what the stopgap scene produces with no sun in
it. So this sample tests the wording path - prompt, gate, provenance, fallback - and says nothing about weather,
locations, or evenings. Five of the nineteen are dated 2026-10-10 only because the emulator's clock crossed
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
