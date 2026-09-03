# Face Collage

An Android app that processes a portrait video entirely on-device, detects every face,
groups faces belonging to the same person, counts how many separate times each person
appears, picks the best representative frame per person, and builds a shareable collage.

No backend. No network calls at runtime. Everything runs on the device.

![Demo](demo.gif)

47.5s end-to-end flow on all three sample clips ([full-quality video](screen_recording.mp4)).

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

Step 4 runs every instrumented class, including the one-shot diagnostics above and a
threshold sweep, on all three sample clips: roughly 25-30 minutes on an emulator. To run
only the graded accuracy check:

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.iykyk.facecollage.PipelineGroundTruthTest
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

The sweep above was measured with `nmsIouThreshold` at 0.30, before residual duplicate
suppression was tightened to 0.22. The threshold choice it produced was re-verified against
all three clips afterwards; the final numbers are below.

### Accuracy achieved

| clip | people found | expected | appearances found | expected | distribution |
|---|---|---|---|---|---|
| sample_1 | **5** | 5 | 19 | 20 | `[4,4,4,4,3]` |
| sample_2 | **5** | not provided | 20 | not provided | `[4,4,4,4,4]` |
| sample_3 | **5** | not provided | 19 | not provided | `[4,4,4,4,3]` |

Sample 1 matches the published person count exactly and is short by one appearance, for the
reason recorded under Known defects. Samples 2 and 3 have no published answer, yet both
resolve independently to the same "five people, four appearances each" structure, and
Sample 2 lands on exactly 20 appearances. Three clips converging on the same shape, when
only one of them could be checked, is the strongest available evidence the pipeline
generalises rather than fits the one clip with a known answer.

For contrast, before any of this work the same clips reported 11, 14 and 13 people with 40,
47 and 43 appearances.

`PipelineGroundTruthTest` asserts the person count exactly and allows one appearance of
slack. Those tolerances record measured accuracy and exist to catch regressions. The test
also asserts the appearance-count *distribution*, so a run cannot pass by hitting the totals
with broken clustering the way a 0.40 merge threshold does.

**These figures are emulator-measured (Pixel 6 Pro AVD, API 34).** Run on a physical device
(tested: a real Android phone via `adb install`), Sample 1 gave **18** appearances instead of
19 — same 5 people, but one identity had 2 recorded segments instead of 3, rather than the
emulator's single merged-segment shortfall. Real hardware's video decoder samples frames at
the same nominal timestamps slightly differently than the emulator's software decoder, which
can shift which frames pass the visibility gate. Both runs are within 1-2 of the published 20
and preserve the correct person count; treat the exact appearance figures above as one
measured environment, not a hardware-independent constant.

### Duplicate suppression, measured twice

The first measurement, taken on the busiest frames, showed boxes on one face overlapping by
**0.41 - 0.49** against **0.00 - 0.11** for boxes on different faces. A threshold of 0.30 sat
comfortably in that gap and removed the obvious duplicates.

A second, subtler population survived it. Some duplicate boxes overlap by only **0.245 - 0.290**,
which is below 0.30, so they persisted and formed a *parallel track on a person already being
tracked*. Those two tracks then coexist in time, and clustering bars temporally overlapping
tracks from merging, so the duplicate became a permanent extra identity that no similarity
threshold could remove. Diagnosis: the pair sat at cosine similarity 0.597, well above the
0.50 merge threshold, and was refused solely by the temporal-exclusion rule.

Overlap between concurrent tracks, split by whether they look like the same face:

| clip | same face (residual duplicates) | different faces (real pairs) |
|---|---|---|
| sample_1 | n=9, 0.245 - 0.290 | n=20, 0.000 - 0.193 |
| sample_2 | n=4, 0.254 - 0.271 | n=22, 0.000 - 0.198 |
| sample_3 | none | n=17, 0.000 - 0.082 |

Different faces never exceed **0.198**; residual duplicates never fall below **0.245**. The gap
is narrower than the first one (0.047 wide against 0.30), so the value is a tighter call, but it
is clean across 59 different-face pairs on three clips. `nmsIouThreshold` is set to **0.22**, the
middle of that gap. Note the asymmetry of the risk: under-suppression adds a spurious identity,
whereas over-suppression deletes a real person, so the value is kept above the different-face
maximum rather than centred on convenience. `FaceSuppressionTest` pins both bands.

This change is not specific to the clip with a known answer: Sample 2 carries the same duplicate
population and Sample 3 carries none at all, so the fix cannot affect it.

## Known defects

**Sample 1: one tile shows two people.** One person in Sample 1 never appears alone, so their
best available frame is a two-shot. The representative-frame scorer already penalises shared
frames heavily (`sharedFramePenalty`), which fixed the other affected tile, but this frame is
recorded as holding a *single* face: ML Kit did not detect the neighbour in that particular
frame, even though a human sees them. Because nothing in the pipeline knows the second person
is there, no face-count-based rule can act on it. Reducing the crop for such frames was tried
and made no difference for exactly this reason, so it was removed rather than shipped as an
inert branch. Samples 2 and 3 show five cleanly separated people each.


**Sample 1 undercounts by one appearance.** One person's segments at 10.13-11.38s and
11.75-13.00s are reported as a single 2.87s appearance. The gap between them is 370ms, just
inside the tracker's own 375ms tolerance (`maxGapFrames` x `sampleIntervalMs`), so the tracker
bridges a scene cut and never splits the track in the first place. This is not the clustering
bug that was fixed in Phase 4; coalescing is already stricter than track-breaking.

It is left unfixed deliberately. The only lever is `maxGapFrames`, and lowering it purely to
correct this one segment on the one clip with a published answer is the kind of change that
fits a sample rather than improves the pipeline. It is recorded here instead.

## Diagnostics behind the numbers above

These are one-shot measurement tools, not pass/fail tests, kept because their output is
quoted directly in this README rather than summarised from memory:

| file | produced |
|---|---|
| `FrameDumpTest` | the first duplicate-box IoU measurement (0.41-0.49 vs 0.00-0.11) and the annotated split-screen image that ruled out background faces |
| `DetectionDiagnosticsTest` | the sharpness/relative-height percentiles behind `minSharpness` and `minFaceHeightFraction` |
| `ClusterGapDiagnosticTest` | the 0.597 cosine similarity showing the Sample 1 residual is a temporal-exclusion case, not a threshold or embedding problem |
| `ResidualDuplicateIouTest` | the second IoU measurement (0.245-0.290 vs 0.000-0.198) behind `nmsIouThreshold = 0.22` |
| `ThresholdSweepTest` | the `identityMergeThreshold` sweep table |

## Other tuned constants

| constant | value | basis |
|---|---|---|
| `nmsIouThreshold` | 0.22 | ML Kit emits stacked boxes per face on downscaled frames; without suppression the counts roughly double. Measured in two passes, see below. |
| `minVisibleSegmentMs` | 500 ms | The brief counts an appearance as a continuous *visible* segment. Two- and three-frame flashes at scene cuts are not that. `minTrackDetections` is derived from this and the sampling interval rather than set independently. |
| `appearanceCoalesceGapMs` | 250 ms | Must stay strictly below `maxGapFrames x sampleIntervalMs` (375 ms), otherwise clustering re-merges segments the tracker deliberately split. `PipelineConfig` rejects any configuration that violates this, so the two cannot drift apart. |
| `sampleIntervalMs` | 125 ms (8 fps) | Fast enough to catch a ~1.4 s appearance many times over; ~240 frames for a 30 s clip. |
| `minSharpness` / `minFaceHeightFraction` | 15 / 0.07 | Deliberately permissive. Measured detections have sharpness p10 of 552 - 895 and relative height p10 of 0.237, so these gates reject almost nothing on the sample clips; ML Kit already declines to detect faces in badly blurred frames. They remain as guards for clips with genuinely poor material. |
