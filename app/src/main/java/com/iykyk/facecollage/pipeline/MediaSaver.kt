package com.iykyk.facecollage.pipeline

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Saving to the gallery and handing the image to the system share sheet.
 *
 * Two storage paths are unavoidable here: MediaStore with RELATIVE_PATH only works from
 * API 29, and this app supports API 26.
 */
object MediaSaver {

    private const val ALBUM = "Face Collage"
    private const val MIME = "image/jpeg"
    private const val QUALITY = 95

    /**
     * Writes [bitmap] into the device gallery and returns its content Uri.
     *
     * On API 26-28 this needs WRITE_EXTERNAL_STORAGE; callers must hold it before calling.
     */
    fun saveToGallery(context: Context, bitmap: Bitmap, displayName: String = defaultName()): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, bitmap, displayName)
        } else {
            saveViaLegacyPath(context, bitmap, displayName)
        }
    }

    /**
     * A share-ready Uri for the collage, written to app-private cache and exposed through
     * FileProvider. Sharing never requires a storage permission this way, and it works
     * whether or not the user chose to save first.
     */
    fun shareIntent(context: Context, bitmap: Bitmap): Intent {
        val shared = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(shared, "collage.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun saveViaMediaStore(context: Context, bitmap: Bitmap, displayName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, MIME)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create a gallery entry.")

        try {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
                ?: error("Could not open the gallery entry for writing.")
        } catch (e: Throwable) {
            // do not leave a half-written pending row behind
            resolver.delete(uri, null, null)
            throw e
        }

        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveViaLegacyPath(context: Context, bitmap: Bitmap, displayName: String): Uri {
        val album = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM,
        ).apply { mkdirs() }
        val file = File(album, displayName)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }

        // make it visible to the gallery without waiting for a media scan
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, MIME)
            put(MediaStore.Images.Media.DATA, file.absolutePath)
        }
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: Uri.fromFile(file)
    }

    private fun defaultName(): String = "face-collage-${System.currentTimeMillis()}.jpg"
}
