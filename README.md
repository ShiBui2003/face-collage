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

## Similarity threshold

To be filled in during Phase 4, once the threshold has been tuned against Sample 1 and
sanity-checked against Samples 2 and 3.
