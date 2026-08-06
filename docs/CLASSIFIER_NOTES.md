# Classifier Capability Notes — read before extending detection

These findings come from inspecting the TFLite metadata actually bundled in
`android/app/src/main/assets/`. They are not assumptions.

## 1. `bert_classifier.tflite` — binary sentiment only

- Size: 25,707,538 bytes. Valid TFLite flatbuffer (`TFL3` identifier at offset 4).
- Metadata zip appended at offset 25,707,347 containing `vocab.txt` (BERT WordPiece)
  and `labels.txt`.
- **`labels.txt` contains exactly two labels, lowercase:**

  ```
  negative
  positive
  ```

This is an SST-2 (movie-review polarity) MobileBERT classifier.

### What this means

It **cannot** emit any of the following, no matter how the Kotlin is written:

`toxic`, `hate_speech`, `cyberbullying`, `rage_bait`, `harassment`, `violent`,
`self_harm`, `adult`, `nsfw`, `explicit`, `misinformation`

Mapping "negative score > 0.8" to `toxic` is a **threshold heuristic over a
polarity score**, not classification. It conflates sadness, criticism, bad
reviews, and abuse into one axis. A grieving post and an abusive one both read
as "negative"; sarcastic harassment often reads as "positive".

Do not surface heuristic labels to users as though the model detected them —
especially not `self_harm`, where a false negative is a safety failure and a
false positive is an intrusive accusation.

## 2. `image_classifier.tflite` — ImageNet objects, not NSFW

- Size: 18,582,189 bytes.
- Metadata associated file: **`labels_without_background.txt`**, beginning:
  `tench, goldfish, great white shark, tiger shark, hammerhead, electric ray,
  stingray, cock, hen, ostrich, ...`

This is the standard **ImageNet-1000** class list (EfficientNet-Lite / MobileNet
family). It recognises objects: animals, vehicles, household items.

### What this means

There is **no nudity, adult, or explicit class in ImageNet**. This model cannot
perform adult-image detection at any threshold. Wiring it into a Child Safety
Mode would produce a filter that looks functional and blocks nothing — the most
dangerous possible outcome for a child-safety feature.

## 3. Why every result was NEUTRAL

Contributing causes found in `GeminiNanoBridge.kt`:

1. **Broken error logging (fixed).** Both catch blocks used `"\${e.message}"`.
   In Kotlin `\$` escapes the dollar sign, so the literal text `${e.message}` was
   logged instead of the exception. Any model-load failure was therefore silent.
2. **Silent null fallback (now logged).** If `textClassifier == null`, every call
   returned `neutral` immediately — indistinguishable in the UI from a real
   verdict. It now logs at ERROR with the load reason.
3. **Thresholds.** At default sensitivity 0.5 the gate is `negative >= 0.60` for
   `rage-bait` and `>= 0.80` for `toxic`.
4. **UI chrome dilution.** Every accessibility node was classified separately,
   including `Like`, `Share`, `2h`, usernames. Now filtered to snippets of
   >= 25 chars containing a space, capped at 12 per batch (also a large battery
   and latency win — MobileBERT over 100+ nodes per event was the main cost).

Run the app and check logcat:

```bash
adb logcat -s GeminiNanoBridge
```

`selfTest()` runs on service connect and prints the model's real label set plus
scores for three known probe strings. That distinguishes "model never loaded"
from "model loaded but scores are below threshold" in one run.

## 4. Getting to the real taxonomy, offline

The current models cannot support the requested categories. Options that keep
everything on-device:

| Need | Viable on-device approach |
|---|---|
| Multi-category text (toxic, hate, harassment, self-harm) | Multi-label toxicity classifier trained on Jigsaw-style data, exported to TFLite. Gives independent per-category scores, which is what the spec actually describes. |
| Flexible / nuanced categories (rage_bait, misinformation) | MediaPipe **LLM Inference API** (`com.google.mediapipe:tasks-genai`) running Gemma 2B on-device. This matches the original `src/ai/prompt.ts` design — a JSON-returning prompt. Costs ~1.3GB and real latency; use it on the 30s batch, not per event. |
| NSFW / adult imagery | A purpose-trained NSFW image model (e.g. a 5-class drawings/hentai/neutral/porn/sexy MobileNet variant) converted to TFLite. ImageNet cannot substitute. |

Until one of those is in place, Child Safety Mode should **not** be presented to
users as functional adult-content blocking.
