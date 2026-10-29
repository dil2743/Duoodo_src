package app.duoodo.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Checks for a new doodle every [INTERVAL_MINUTES]. WorkManager's periodic work has a 15-minute
 * floor, so this is a one-time job that re-enqueues itself to achieve a shorter interval.
 */
class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            transport.fetchLatest()?.let { Inbox.deliver(applicationContext, it) }
        } catch (e: Exception) {
            // A failed check (offline, relay down) just waits for the next one.
        }
        // Queued behind this run, so the chain continues once it finishes.
        schedule(applicationContext, delayMinutes = INTERVAL_MINUTES, chain = true)
        return Result.success()
    }

    companion object {
        const val INTERVAL_MINUTES = 10L
        private const val WORK_NAME = "duoodo-check"
        var transport: DoodleTransport = LoopbackTransport

        /**
         * Ensures the check loop is running. Without [chain] an already-scheduled check is left
         * alone; with it the new check is queued after the current one.
         */
        fun schedule(context: Context, delayMinutes: Long = 0, chain: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<CheckWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                if (chain) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
