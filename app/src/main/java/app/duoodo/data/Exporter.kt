package app.duoodo.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.duoodo.model.Doodle
import app.duoodo.render.DoodleRenderer
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves a doodle as a PNG in its own "Pictures/Duoodo" folder. Uses MediaStore, which lets an app
 * add its own files without any storage permission and without being able to read anyone else's.
 */
object Exporter {
    const val FOLDER = "Duoodo"
    private const val SIZE_PX = 1080

    /** Requires Android 10+ (scoped storage); returns false on older versions. */
    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** @return the display name of the saved file. @throws IOException if saving failed. */
    fun save(context: Context, doodle: Doodle): String {
        check(isSupported)
        val name = "duoodo_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(doodle.createdAtMillis)) + ".png"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("couldn't create file")
        try {
            val bitmap = DoodleRenderer.toBitmap(doodle, SIZE_PX)
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                ?: throw IOException("couldn't write file")
            bitmap.recycle()
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e as? IOException ?: IOException(e)
        }
        return name
    }
}
