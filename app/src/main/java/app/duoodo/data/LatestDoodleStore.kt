package app.duoodo.data

import android.content.Context
import android.util.AtomicFile
import app.duoodo.model.Doodle
import app.duoodo.model.DoodleCodec
import java.io.File
import java.io.IOException

/**
 * Holds exactly one doodle: the latest one received. Each new doodle overwrites the previous one,
 * so there is no history. The file lives in app-private, no-backup storage that no other app
 * can read.
 */
class LatestDoodleStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "latest.doodle"))

    fun read(): Doodle? = try {
        DoodleCodec.decode(file.readFully())
    } catch (e: IOException) {
        null
    }

    fun clear() {
        file.delete()
    }

    fun overwrite(doodle: Doodle) {
        val bytes = DoodleCodec.encode(doodle)
        val out = file.startWrite()
        try {
            out.write(bytes)
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            throw e
        }
    }
}
