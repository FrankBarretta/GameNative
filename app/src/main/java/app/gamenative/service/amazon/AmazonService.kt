package app.gamenative.service.amazon

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import app.gamenative.PluviaApp
import app.gamenative.data.AmazonCredentials
import app.gamenative.data.AmazonGame
import app.gamenative.data.DownloadInfo
import app.gamenative.enums.Marker
import app.gamenative.events.AndroidEvent
import app.gamenative.service.NotificationHelper
import app.gamenative.utils.ContainerUtils
import app.gamenative.utils.MarkerUtils
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * Amazon Games foreground service.
 *
 * Responsibilities:
 *  - Library sync via [AmazonManager] (calls Amazon distribution API directly, no nile binary)
 *  - Game download/install via [AmazonDownloadManager]
 */
@AndroidEntryPoint
class AmazonService : Service() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var amazonManager: AmazonManager

    @Inject
    lateinit var amazonDownloadManager: AmazonDownloadManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Active downloads keyed by Amazon product ID (e.g. "amzn1.adg.product.XXXX")
    private val activeDownloads = ConcurrentHashMap<String, DownloadInfo>()

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

        fun getInstance(): AmazonService? = instance

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
            svc.serviceScope.launch { svc.syncLibrary() }
        }

        // ── Install queries ───────────────────────────────────────────────────

        /**
         * Returns true if the Amazon game with [productId] is marked as installed in the DB.
         */
        fun isGameInstalled(productId: String): Boolean {
            return runBlocking(Dispatchers.IO) {
                instance?.amazonManager?.getGameById(productId)?.isInstalled == true
            }
        }

        /**
         * Returns the [AmazonGame] for the given product ID, or null if not found / service not up.
         */
        fun getAmazonGameOf(productId: String): AmazonGame? {
            return runBlocking(Dispatchers.IO) {
                instance?.amazonManager?.getGameById(productId)
            }
        }

        /**
         * Returns the on-disk install path for [productId], or null if not installed.
         */
        fun getInstallPath(productId: String): String? {
            val game = getAmazonGameOf(productId) ?: return null
            return if (game.isInstalled && game.installPath.isNotEmpty()) game.installPath else null
        }

        /** Deprecated name kept for call-site compatibility — delegates to [getInstallPath]. */
        fun getInstalledGamePath(gameId: String): String? = getInstallPath(gameId)

        // ── Download management ───────────────────────────────────────────────

        /** Returns the active [DownloadInfo] for [productId], or null if not downloading. */
        fun getDownloadInfo(productId: String): DownloadInfo? =
            getInstance()?.activeDownloads?.get(productId)

        /** Returns true if there is at least one active download. */
        fun hasActiveDownload(): Boolean =
            getInstance()?.activeDownloads?.isNotEmpty() == true

        /**
         * Begin downloading [productId] to [installPath].
         *
         * @return A [DownloadInfo] the UI can observe for progress/status updates.
         */
        fun downloadGame(
            context: Context,
            productId: String,
            installPath: String,
        ): Result<DownloadInfo> {
            val instance = getInstance()
                ?: return Result.failure(Exception("Amazon service is not running"))

            // Already downloading?
            instance.activeDownloads[productId]?.let { existing ->
                Timber.tag("Amazon").w("Download already in progress for $productId")
                return Result.success(existing)
            }

            val game = runBlocking(Dispatchers.IO) {
                instance.amazonManager.getGameById(productId)
            } ?: return Result.failure(Exception("Game not found: $productId"))

            val downloadInfo = DownloadInfo(
                jobCount = 1,
                gameId = productId.hashCode(),
                downloadingAppIds = CopyOnWriteArrayList(),
            )
            downloadInfo.setActive(true)
            instance.activeDownloads[productId] = downloadInfo

            PluviaApp.events.emitJava(
                AndroidEvent.DownloadStatusChanged(productId.hashCode(), true)
            )

            val job = instance.serviceScope.launch {
                try {
                    val result = instance.amazonDownloadManager.downloadGame(
                        context = context,
                        game = game,
                        installPath = installPath,
                        downloadInfo = downloadInfo,
                    )

                    if (result.isSuccess) {
                        Timber.tag("Amazon").i("Download succeeded for $productId")
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                context,
                                "Download completed: ${game.title}",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                        PluviaApp.events.emitJava(
                            AndroidEvent.LibraryInstallStatusChanged(productId.hashCode())
                        )
                    } else {
                        val error = result.exceptionOrNull()
                        Timber.tag("Amazon").e(error, "Download failed for $productId")
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                context,
                                "Download failed: ${error?.message ?: "Unknown error"}",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("Amazon").e(e, "Download exception for $productId")
                    downloadInfo.setActive(false)
                } finally {
                    instance.activeDownloads.remove(productId)
                    PluviaApp.events.emitJava(
                        AndroidEvent.DownloadStatusChanged(productId.hashCode(), false)
                    )
                }
            }

            downloadInfo.setDownloadJob(job)
            return Result.success(downloadInfo)
        }

        /**
         * Cancel an in-progress download for [productId].
         * @return true if a download was found and cancelled.
         */
        fun cancelDownload(productId: String): Boolean {
            val instance = getInstance() ?: return false
            val downloadInfo = instance.activeDownloads[productId] ?: run {
                Timber.tag("Amazon").w("No active download for $productId")
                return false
            }
            Timber.tag("Amazon").i("Cancelling download for $productId")
            downloadInfo.cancel()
            instance.activeDownloads.remove(productId)
            return true
        }

        /**
         * Delete installed files for [productId] and mark it as uninstalled in the DB.
         */
        suspend fun deleteGame(context: Context, productId: String): Result<Unit> {
            val instance = getInstance()
                ?: return Result.failure(Exception("Amazon service is not running"))

            return withContext(Dispatchers.IO) {
                try {
                    val game = instance.amazonManager.getGameById(productId)
                        ?: return@withContext Result.failure(Exception("Game not found: $productId"))

                    val path = game.installPath
                    if (path.isNotEmpty() && File(path).exists()) {
                        Timber.tag("Amazon").i("Deleting install dir: $path")
                        File(path).deleteRecursively()
                        MarkerUtils.removeMarker(path, Marker.DOWNLOAD_COMPLETE_MARKER)
                        MarkerUtils.removeMarker(path, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                    }

                    instance.amazonManager.markUninstalled(productId)

                    withContext(Dispatchers.Main) {
                        ContainerUtils.deleteContainer(context, "AMAZON_$productId")
                    }

                    PluviaApp.events.emitJava(
                        AndroidEvent.LibraryInstallStatusChanged(productId.hashCode())
                    )

                    Timber.tag("Amazon").i("Game uninstalled: $productId")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Timber.tag("Amazon").e(e, "Failed to uninstall $productId")
                    Result.failure(e)
                }
            }
        }
    }

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        instance = this
        Timber.i("[Amazon] Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationHelper.createForegroundNotification("Amazon Games Running")
        startForeground(1, notification)

        if (intent?.action == ACTION_SYNC_LIBRARY) {
            serviceScope.launch { syncLibrary() }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        super.onDestroy()
        Timber.i("[Amazon] Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Instance helpers (for callers that hold a direct reference) ───────────

    /** Instance-method accessor for callers using [getInstance]?. */
    fun getInstalledGamePath(gameId: String): String? = getInstallPath(gameId)

    private suspend fun syncLibrary() {
        try {
            amazonManager.refreshLibrary()
        } catch (e: Exception) {
            Timber.e(e, "[Amazon] Library sync failed")
        }
    }
}
