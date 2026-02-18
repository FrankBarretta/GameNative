package app.gamenative.service.amazon

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import app.gamenative.data.AmazonCredentials
import app.gamenative.data.AmazonGame
import app.gamenative.service.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject

/**
 * Amazon Games foreground service.
 *
 * On start it triggers a library sync via [AmazonManager], which calls the
 * Amazon Gaming Distribution API directly (no nile binary required).
 */
@AndroidEntryPoint
class AmazonService : Service() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var amazonManager: AmazonManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val ACTION_SYNC_LIBRARY = "app.gamenative.AMAZON_SYNC_LIBRARY"
        private var instance: AmazonService? = null

        val isRunning: Boolean
            get() = instance != null

        fun start(context: Context) {
            if (isRunning) {
                Timber.d("[Amazon] Service already running")
                return
            }
            val intent = Intent(context, AmazonService::class.java)
            intent.action = ACTION_SYNC_LIBRARY
            context.startForegroundService(intent)
        }

        fun stop() {
            instance?.stopSelf()
        }

        fun hasStoredCredentials(context: Context): Boolean =
            AmazonAuthManager.hasStoredCredentials(context)

        /**
         * Authenticate with Amazon Games using PKCE authorization code.
         * Called from the Settings UI after the WebView captures the code.
         */
        suspend fun authenticateWithCode(
            context: Context,
            authCode: String,
        ): Result<AmazonCredentials> = AmazonAuthManager.authenticateWithCode(context, authCode)

        fun triggerLibrarySync(context: Context) {
            val svc = instance ?: return
            svc.serviceScope.launch {
                svc.syncLibrary()
            }
        }

        fun getInstance(): AmazonService? = instance

        /**
         * Returns true if the Amazon game with [productId] is marked as installed in the DB.
         * Delegates to the running service's AmazonManager; returns false if the service is not up.
         */
        fun isGameInstalled(productId: String): Boolean {
            return runBlocking(Dispatchers.IO) {
                instance?.amazonManager?.getGameById(productId)?.isInstalled == true
            }
        }

        /**
         * Returns the [AmazonGame] for the given product ID, or null if not found / service not running.
         */
        fun getAmazonGameOf(productId: String): AmazonGame? {
            return runBlocking(Dispatchers.IO) {
                instance?.amazonManager?.getGameById(productId)
            }
        }

        /**
         * Returns the on-disk install path for [gameId], or null if not installed.
         * Real install tracking is a future feature — always returns null for now.
         */
        fun getInstalledGamePath(gameId: String): String? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Timber.i("[Amazon] Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationHelper.createForegroundNotification("Amazon Games Running")
        startForeground(1, notification)

        if (intent?.action == ACTION_SYNC_LIBRARY) {
            serviceScope.launch {
                syncLibrary()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Instance-method accessor so callers using [getInstance]?. can call this directly. */
    fun getInstalledGamePath(gameId: String): String? = null

    private suspend fun syncLibrary() {
        try {
            amazonManager.refreshLibrary()
        } catch (e: Exception) {
            Timber.e(e, "[Amazon] Library sync failed")
        }
    }
}
