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

    private val clearedFile = File(context.noBackupFilesDir, "cleared_up_to")
    private val seenFile = File(context.noBackupFilesDir, "seen_up_to")
    private val partnerReadFile = File(context.noBackupFilesDir, "partner_read_up_to")

    /** Timestamp of the newest doodle the user cleared; anything up to it stays hidden. */
    fun clearedUpTo(): Long = runCatching { clearedFile.readText().toLong() }.getOrDefault(0L)

    fun markCleared(createdAtMillis: Long) {
        clearedFile.writeText(createdAtMillis.toString())
    }

    /** Timestamp of the newest doodle the user actually viewed in the inbox. */
    fun seenUpTo(): Long = runCatching { seenFile.readText().toLong() }.getOrDefault(0L)

    fun markSeen(createdAtMillis: Long) {
        val current = seenUpTo()
        if (createdAtMillis > current) {
            seenFile.writeText(createdAtMillis.toString())
        }
    }

    /** Timestamp of our latest sent doodle that the partner has read. */
    fun partnerReadUpTo(): Long = runCatching { partnerReadFile.readText().toLong() }.getOrDefault(0L)

    fun markReadByPartner(doodleTimestamp: Long) {
        val current = partnerReadUpTo()
        if (doodleTimestamp > current) {
            partnerReadFile.writeText(doodleTimestamp.toString())
        }
    }

    /** True if there is an active doodle newer than both the last seen and last cleared timestamps. */
    fun isUnseen(): Boolean {
        val doodle = read() ?: return false
        return doodle.createdAtMillis > seenUpTo() && doodle.createdAtMillis > clearedUpTo()
    }

    /** Forgets everything, including what was cleared, seen, and read (used when unpairing). */
    fun reset() {
        clear()
        clearedFile.delete()
        seenFile.delete()
        partnerReadFile.delete()
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
