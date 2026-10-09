# Getting the model (optional)

The app is complete and fully functional without this file. No download is required to install Evenlight, take a
card, name an evening, or build the stack. Without weights the card carries the template line
(`<name> light, measured from this sky`) and the record says `wording_source: "none"`. Nothing else changes, and
the path that makes that true is `app/src/main/java/com/burry071/evenlight/wording/EngineHolder.kt`: `ensure()`
returns `false` the moment `modelFile.exists()` is `false`, and every failure route lands back on the template.

There is also no download inside the app. The manifest carries no `INTERNET` permission
(`app/src/main/AndroidManifest.xml`, asserted by `app/src/test/java/com/burry071/evenlight/ManifestTest.kt`), so
the only way weights get on the device is a person putting them there. One limit, because it is the kind a test can
hide: that test reads the manifest I wrote, not the merged artifact the build installs, so the check you can trust
is `grep uses-permission -A1 app/build/intermediates/merged_manifests/debug/*/AndroidManifest.xml` after a build.

## Why it is not in this repo

GitHub's hard per-file limit is 100 MB. The model is 584,417,280 bytes. It cannot be committed here at all, which
is why `.gitignore` carries `*.litertlm` and this file exists instead.

## The file, exactly

| | |
|---|---|
| Host | `https://huggingface.co/litert-community/Gemma3-1B-IT-ONNX` |
| Repository | `litert-community/Gemma3-1B-IT-ONNX` |
| Filename | `Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm` |
| Size | 584,417,280 bytes |
| sha256 | `1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be` |

## What you have to do yourself

That repository is `gated=auto` under the Gemma Terms of Use. You accept those terms in your own browser, with your
own account, and only then does the file become downloadable. I cannot script that for you, and I have not
redistributed the weights in this repo, so nothing here needs a licence grant from Google.

```bash
# 1. Open https://huggingface.co/litert-community/Gemma3-1B-IT-ONNX in a browser and accept the terms.
# 2. Download Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm from the Files tab.
# 3. Check what you got before moving 584 MB anywhere:
sha256sum Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm
# must print 1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be
```

The hash is the one claim here you can actually execute, so run it. A truncated download and a wrong file look
identical to a missing model from the app's side: the card just keeps using the template line.

## Where it goes

`files/models/` inside the app's private storage, under that exact filename. `EngineHolder.modelFile` builds the
path as `File(context.filesDir, "models/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm")`, so a name that
differs by one character reads as "no model installed". On a debug build that directory is
`/data/data/com.burry071.evenlight/files/models/`, and the route I used on an emulator was:

```bash
adb push Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm /data/local/tmp/
adb shell "run-as com.burry071.evenlight mkdir -p files/models"
adb shell "run-as com.burry071.evenlight cp /data/local/tmp/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm files/models/"
```

The app never reads anything outside its own directory, so a release build without `run-as` needs a different route
to the same path. Placing the file is the whole setup: the engine is built lazily on the first shot after the file
appears, and the holder is rebuilt when you save Settings.

## What it costs on disk

**1,092,957,056 bytes ≈ 1.02 GiB**, not 557 MiB. The weights are 584,417,280 bytes and the engine writes a
508,539,776-byte XNNPack cache beside them on first use. That cache is measured on the emulator, not estimated, and
it is roughly 87% of the model file, which is the part everybody gets wrong when they quote "about half a gig".

## What it costs in time and memory

Every number in this section is from an x86_64 API-35 emulator (1080x2340 @ 440 dpi, 3,048,052 kB `MemTotal`), CPU
backend, engine already warm. The target device is unmeasured, so these are not "on a phone" numbers.

| | |
|---|---|
| Model init | 4,566 ms |
| Peak RSS during a call | 1,269 MB |
| One line, in isolation | 1,080 / 1,247 / 3,502 ms (min / median / max of three calls) |
| Free RAM with the model resident | ~132 MB, low-memory killer active |

Two consequences worth knowing before you bother. Inside the app the line arrives 11-21 s after the record is
filed, not the one-to-three-and-a-half seconds the same call takes on its own, which is why the record is written
first: an evening on disk in 0.13-2.8 s does not depend on the model surviving. And the 10 s give-up marker around
the call bounds nothing, because
`withTimeoutOrNull` cannot interrupt a blocking native call - measured on the same emulator, a call that ran 10.5 s
still delivered its line past the marker.

If you would rather not spend a gigabyte and twenty seconds to get one adjective into a sentence you did not ask
for: leave the file off. The card is the measurement, and the measurement does not need the model.
