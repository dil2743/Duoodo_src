package app.duoodo.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.duoodo.R
import app.duoodo.data.PairingRepository
import app.duoodo.data.Settings
import app.duoodo.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps live relay subscriptions open so a partner's doodle reaches the widget within seconds,
 * even when the app is closed. Android only allows long-lived connections from a foreground
 * service, which must show a notification; it's posted on a silent, minimal-importance channel.
 */
class LiveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var receiver: LiveReceiver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(this), type)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pairing = PairingRepository(this).pairing
        if (pairing == null || !Settings(this).instantDelivery) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (receiver == null) {
            receiver = LiveReceiver(this, pairing).also { it.start(scope) }
            watchNetwork()
            // Catch anything sent while we weren't connected.
            scope.launch { runCatching { Sync.check(this@LiveService) } }
        }
        return START_STICKY
    }

    /** On a new network, sockets on the old one are dead; reconnect at once rather than waiting for pings. */
    private fun watchNetwork() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            private var first = true
            override fun onAvailable(network: Network) {
                if (first) { first = false; return }
                receiver?.reconnect(scope)
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        networkCallback = callback
    }

    /** Android 15 caps dataSync services at ~6 h/day; bow out and leave it to the periodic check. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        networkCallback?.let { runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        receiver?.stop()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "live"
        private const val NOTIFICATION_ID = 1

        /** Starts or stops the service to match the current pairing and setting. */
        fun sync(context: Context) {
            val app = context.applicationContext
            val wanted = PairingRepository(app).pairing != null && Settings(app).instantDelivery
            val intent = Intent(app, LiveService::class.java)
            if (wanted) {
                // Starting from the background can be refused on newer Android; the periodic check covers that.
                runCatching { ContextCompat.startForegroundService(app, intent) }
            } else {
                app.stopService(intent)
            }
        }

        private fun ensureChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Instant delivery", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps Duoodo connected so doodles arrive instantly. You can hide this notification."
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun notification(context: Context): Notification {
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Waiting for doodles")
                .setContentText("Connected so your partner's doodles arrive instantly")
                .setContentIntent(open)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
        }
    }
}

/** Restarts live delivery after a reboot or an app update. */
class LiveStarter : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                LiveService.sync(context)
                CheckWorker.schedule(context)
            }
        }
    }
}
