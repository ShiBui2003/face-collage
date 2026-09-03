package com.iykyk.facecollage

import android.content.Context
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import java.io.File

/**
 * Discovers the test clips under androidTest/assets/samples.
 *
 * The directory is enumerated, never listed by name, so dropping in a sample_4.mp4 needs no
 * code change. Ground truth is data, not code: a clip with a sibling <name>.expected.json is
 * asserted strictly, one without is only sanity-checked. This is the single place in the
 * project where Sample 1's known answer is allowed to exist.
 */
object SampleVideos {

    data class Expected(val identities: Int, val appearancesPerIdentity: List<Int>)

    data class Sample(val name: String, val uri: Uri, val expected: Expected?)

    private const val DIR = "samples"

    fun all(): List<Sample> {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val entries = assets.list(DIR).orEmpty().toList()

        return entries.filter { it.endsWith(".mp4") }.sorted().map { fileName ->
            val stem = fileName.removeSuffix(".mp4")
            Sample(
                name = stem,
                uri = Uri.fromFile(copyToCache(target, "$DIR/$fileName", fileName)),
                expected = readExpected(entries, stem),
            )
        }
    }

    private fun readExpected(entries: List<String>, stem: String): Expected? {
        val expectedName = "$stem.expected.json"
        if (expectedName !in entries) return null
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val json = JSONObject(assets.open("$DIR/$expectedName").bufferedReader().use { it.readText() })
        val counts = json.getJSONArray("appearancesPerIdentity")
        return Expected(
            identities = json.getInt("identities"),
            appearancesPerIdentity = (0 until counts.length()).map { counts.getInt(it) },
        )
    }

    /** MediaMetadataRetriever needs a real file, so stage the asset in the app cache once. */
    private fun copyToCache(context: Context, assetPath: String, fileName: String): File {
        val out = File(context.cacheDir, fileName)
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val expectedSize = assets.openFd(assetPath).use { it.length }
        if (out.exists() && out.length() == expectedSize) return out
        assets.open(assetPath).use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }
}
