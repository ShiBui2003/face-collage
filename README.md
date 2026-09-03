# Face Collage

An Android app that processes a portrait video entirely on-device, detects every face,
groups faces belonging to the same person, counts how many separate times each person
appears, picks the best representative frame per person, and builds a shareable collage.

No backend. No network calls at runtime. Everything runs on the device.

---

## Build and setup

Requirements:

| | |
|---|---|
| JDK | 17+ (Android Studio's bundled JBR works) |
| Android SDK | platform API 37, build-tools 36 |
| Gradle | 9.7.1 (via the checked-in wrapper) |
| AGP / Kotlin | 9.4.0 / 2.4.10 |
| minSdk / targetSdk | 26 / 36 |

```bash
# 1. Point the build at your SDK (not checked into git)
echo "sdk.dir=/absolute/path/to/Android/Sdk" > local.properties

# 2. Build the debug APK  ->  app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleDebug

# 3. Pure-JVM unit tests (tracking, clustering, scoring logic)
./gradlew testDebugUnitTest

# 4. Ground-truth pipeline test (needs a running emulator or device)
./gradlew connectedDebugAndroidTest
```

---

## Face embedding model

| | |
|---|---|
| **Model** | FaceNet (`facenet.tflite`) |
| **Bundled at** | `app/src/main/assets/facenet.tflite` |
| **Size** | 23,705,216 bytes (~22.6 MB) |
| **SHA-256** | `d7c1f7f130376982c7004920ddc41925ac2e5aecf6522f476c8bbb3669db7013` |
| **Input** | 160 × 160 × 3 RGB, float32 |
| **Embedding dimension** | 128 |
| **Direct source** | [shubham0204/FaceRecognition_With_FaceNet_Android](https://github.com/shubham0204/FaceRecognition_With_FaceNet_Android/blob/master/app/src/main/assets/facenet.tflite) — **Apache-2.0** |
| **Upstream weights** | [nyoki-mtl/keras-facenet](https://github.com/nyoki-mtl/keras-facenet) — **MIT** |
| **Original paper** | [FaceNet: A Unified Embedding for Face Recognition and Clustering](https://arxiv.org/abs/1503.03832) |

The model is loaded with the TensorFlow Lite `Interpreter` behind the `FaceEmbedder`
interface, so the backend can be swapped without touching the pipeline. The input edge and
embedding dimension are read from the model file at runtime rather than hardcoded, and
`FaceEmbedderInstrumentedTest` asserts them against the numbers documented above, so this
table cannot silently drift from the bundled asset.

FaceNet expects per-image standardisation ("prewhitening") rather than a 0..1 scale; the
embedder implements that, since getting it wrong degrades embeddings subtly rather than visibly.

### Why FaceNet and not MobileFaceNet

The original intent was a ~5 MB MobileFaceNet. Every copy of `mobilefacenet.tflite`
findable on GitHub lives in a small hobby repository with no verifiable rights chain for
the binary itself — a permissive *repository* licence does not establish redistribution
rights for a model file of unknown origin dropped into it. The upstream
`sirius-ai/MobileFaceNet_TF` project (Apache-2.0) publishes checkpoints but no TFLite
export and no releases.

FaceNet was chosen because its licence chain is *verifiable end to end*: Apache-2.0
distribution repo, MIT upstream weights, published paper. The cost is APK size
(~22.6 MB vs ~5 MB) and a 160×160 input instead of 112×112. Correct licensing was
judged more important than the size saving.

`android.androidResources.noCompress += "tflite"` keeps the asset uncompressed so it can
be memory-mapped rather than copied to the heap.

---

## Similarity threshold and how it was tuned

**`identityMergeThreshold = 0.50`** (cosine similarity, average linkage).

Tracks are merged into one identity while their average cosine similarity stays at or above
this value. It was chosen by sweeping every candidate value across **all three** clips, not
by picking whatever scored best on the one clip with a known answer. Detection and embedding
run once per clip and the resulting tracks are re-clustered at each threshold, so the sweep
compares like with like (`ThresholdSweepTest`).

| threshold | sample_1 | sample_2 | sample_3 |
|---|---|---|---|
| 0.40 - 0.44 | 5 people `[7,4,4,4,2]` | 5 `[8,4,4,4,1]` | 4 `[7,4,4,4]` |
| **0.48 - 0.52** | **6 `[4,4,4,4,3,2]`** | **6 `[4,4,4,4,4,1]`** | **5 `[4,4,4,4,3]`** |
| 0.56 - 0.64 | 7 `[4,4,4,4,3,1,1]` | 6 `[4,4,4,4,4,1]` | 6 `[4,4,4,3,3,1]` |
| 0.76+ | 9 - 12, fragmenting | 8 - 10 | 8 |

### The trap in tuning on one clip

At 0.40, Sample 1 reports **exactly 5 people**, matching its ground truth. Tuning on that
headline number alone would have selected it. The distribution shows why it is wrong: one
cluster holds 7 appearances, meaning two distinct people have collapsed into a single
identity, and the correct total is reached by coincidence. Samples 2 and 3 confirm the
over-merge independently, at `[8,...]` and `[7,...]`.

0.48 - 0.52 is the only band where all three clips are simultaneously stable: below it they
over-merge, above it Sample 1 fragments into singleton identities. 0.50 sits in the middle
of that band.

### Accuracy achieved

| clip | people found | expected | appearances found | expected |
|---|---|---|---|---|
| sample_1 | 6 | 5 | 21 | 20 |
| sample_2 | 6 | not provided | 21 | not provided |
| sample_3 | 5 | not provided | 19 | not provided |

Sample 1 is over by one person and one appearance. The generalisation trade-off was taken
deliberately: a threshold that made Sample 1 read exactly 5 made Samples 2 and 3 obviously
wrong, so the value that behaves consistently on all three was preferred. Samples 2 and 3
independently resolve to the same "five people, four appearances each" structure as Sample
1's known answer, which is the strongest available evidence the pipeline generalises rather
than fits.

`PipelineGroundTruthTest` asserts this with tolerances of plus or minus one person and one
appearance. Those tolerances record measured accuracy, exist to catch regressions, and are
meant to be tightened. The test also asserts the appearance-count *distribution*, so a run
cannot pass by hitting the totals with broken clustering the way 0.40 does.

## Other tuned constants

| constant | value | basis |
|---|---|---|
| `nmsIouThreshold` | 0.30 | ML Kit emits stacked boxes per face on downscaled frames. Measured overlap is 0.41 - 0.49 between boxes on one face and 0.00 - 0.11 between boxes on different faces, so any value in 0.2 - 0.35 separates them. Without this, counts roughly double. |
| `minVisibleSegmentMs` | 500 ms | The brief counts an appearance as a continuous *visible* segment. Two- and three-frame flashes at scene cuts are not that. `minTrackDetections` is derived from this and the sampling interval rather than set independently. |
| `appearanceCoalesceGapMs` | 250 ms | Must stay strictly below `maxGapFrames x sampleIntervalMs` (375 ms), otherwise clustering re-merges segments the tracker deliberately split. `PipelineConfig` rejects any configuration that violates this, so the two cannot drift apart. |
| `sampleIntervalMs` | 125 ms (8 fps) | Fast enough to catch a ~1.4 s appearance many times over; ~240 frames for a 30 s clip. |
| `minSharpness` / `minFaceHeightFraction` | 15 / 0.07 | Deliberately permissive. Measured detections have sharpness p10 of 552 - 895 and relative height p10 of 0.237, so these gates reject almost nothing on the sample clips; ML Kit already declines to detect faces in badly blurred frames. They remain as guards for clips with genuinely poor material. |
