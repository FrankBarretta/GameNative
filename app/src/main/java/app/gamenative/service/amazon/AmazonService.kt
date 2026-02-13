package app.gamenative.service.amazon

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import app.gamenative.data.AmazonCredentials
import app.gamenative.data.LibraryItem
import app.gamenative.data.GameSource
import app.gamenative.service.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import org.json.JSONArray

/**
 * Amazon Games Service using nile CLI
 */
@AndroidEntryPoint
class AmazonService : Service() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var nileBin: String? = null

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

        fun hasStoredCredentials(context: Context): Boolean {
            return AmazonAuthManager.hasStoredCredentials(context)
        }

        /**
         * Authenticate with Amazon Games using PKCE authorization code.
         * Called from the Settings UI after the WebView captures the code.
         */
        suspend fun authenticateWithCode(context: Context, authCode: String): Result<AmazonCredentials> {
            return AmazonAuthManager.authenticateWithCode(context, authCode)
        }

        fun triggerLibrarySync(context: Context) {
            val svc = instance ?: return
            svc.serviceScope.launch {
                svc.syncLibrary()
            }
        }

        fun getInstance(): AmazonService? = instance

        suspend fun getLibrary(context: Context): List<LibraryItem> {
            return getInstance()?.loadLibrary() ?: emptyList()
        }

        fun isGameInstalled(gameId: String): Boolean {
            val instance = getInstance() ?: return false
            return instance.checkGameInstalled(gameId)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        nileBin = findNileBin()
        Timber.i("[Amazon] Service created, nile: $nileBin")
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

    private fun findNileBin(): String? {
        val paths = listOf(
            File(applicationContext.filesDir, "nile"),
            File("/system/bin/nile"),
            File("/data/local/tmp/nile")
        )
        return paths.firstOrNull { it.exists() && it.canExecute() }?.absolutePath
    }

    private suspend fun syncLibrary() = withContext(Dispatchers.IO) {
        if (nileBin == null) {
            Timber.w("[Amazon] Nile not found, cannot sync")
            return@withContext
        }

        try {
            Timber.i("[Amazon] Syncing library...")
            val process = ProcessBuilder(nileBin, "library", "sync").start()
            process.waitFor()
            Timber.i("[Amazon] Library sync complete")
        } catch (e: Exception) {
            Timber.e(e, "[Amazon] Sync failed")
        }
    }

    internal suspend fun loadLibrary(): List<LibraryItem> = withContext(Dispatchers.IO) {
        if (nileBin == null) return@withContext emptyList()

        val libraryFile = File(applicationContext.getExternalFilesDir(null)?.parent, ".config/nile/library.json")
        if (!libraryFile.exists()) return@withContext emptyList()

        try {
            val json = JSONArray(libraryFile.readText())
            val games = mutableListOf<LibraryItem>()

            for (i in 0 until json.length()) {
                val item = json.getJSONObject(i)
                val product = item.optJSONObject("product") ?: continue
                val gameId = product.optString("id", "")
                val title = product.optString("title", "Unknown")

                if (gameId.isEmpty()) continue

                games.add(
                    LibraryItem(
                        appId = "AMAZON_$gameId",
                        name = title,
                        gameSource = GameSource.AMAZON,
                        iconHash = ""
                    )
                )
            }

            Timber.i("[Amazon] Loaded ${games.size} games")
            return@withContext games
        } catch (e: Exception) {
            Timber.e(e, "[Amazon] Failed to load library")
            return@withContext emptyList()
        }
    }

    internal fun checkGameInstalled(gameId: String): Boolean {
        val installedFile = File(applicationContext.getExternalFilesDir(null)?.parent, ".config/nile/installed.json")
        if (!installedFile.exists()) return false

        return try {
            val json = JSONArray(installedFile.readText())
            (0 until json.length()).any {
                json.getJSONObject(it).optString("id") == gameId
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getInstalledGamePath(gameId: String): String? {
        val installedFile = File(applicationContext.getExternalFilesDir(null)?.parent, ".config/nile/installed.json")
        if (!installedFile.exists()) return null

        return try {
            val json = JSONArray(installedFile.readText())
            for (i in 0 until json.length()) {
                val game = json.getJSONObject(i)
                if (game.optString("id") == gameId) {
                    return game.optString("path")
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
