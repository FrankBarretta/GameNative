package app.gamenative.ui.screen.library.appscreen

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.gamenative.data.AmazonGame
import app.gamenative.data.LibraryItem
import app.gamenative.service.amazon.AmazonConstants
import app.gamenative.service.amazon.AmazonService
import app.gamenative.ui.data.AppMenuOption
import app.gamenative.ui.data.GameDisplayInfo
import app.gamenative.ui.enums.AppOptionMenuType
import app.gamenative.utils.ContainerUtils
import com.winlator.container.ContainerData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Amazon-specific implementation of [BaseAppScreen].
 *
 * Amazon games are currently library-only (no download/install through GameNative).
 * This screen displays game metadata read from the Room database ([AmazonGame]).
 */
class AmazonAppScreen : BaseAppScreen() {

    companion object {
        private const val TAG = "AmazonAppScreen"

        /** Extract the raw Amazon product ID from a library item's appId. */
        fun productIdOf(libraryItem: LibraryItem): String =
            libraryItem.appId.removePrefix("AMAZON_")

        /** Format a byte count as a user-friendly size string. */
        private fun formatBytes(bytes: Long): String {
            val kb = 1024.0
            val mb = kb * 1024
            val gb = mb * 1024
            return when {
                bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes / gb)
                bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
                bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
                else -> "$bytes B"
            }
        }

        /**
         * Parse an Amazon release-date string into a Unix timestamp in seconds.
         *
         * Amazon may return dates in several formats:
         *  - Full ISO-8601:  "2022-08-18T17:50:00Z"
         *  - Short ISO-8601 date: "2022-08-18"
         *  - Year only:      "2022"
         */
        fun parseReleaseDate(dateStr: String): Long {
            if (dateStr.isBlank()) return 0L
            return try {
                when {
                    // Full ISO-8601 with timezone (e.g. "2022-08-18T17:50:00Z" or +offset)
                    dateStr.contains('T') -> {
                        ZonedDateTime.parse(dateStr).toInstant().epochSecond
                    }
                    // yyyy-MM-dd
                    dateStr.length == 10 && dateStr[4] == '-' -> {
                        LocalDate.parse(dateStr).atStartOfDay().toInstant(
                            java.time.ZoneOffset.UTC
                        ).epochSecond
                    }
                    // Four-digit year only
                    dateStr.length == 4 -> {
                        LocalDate.of(dateStr.toInt(), 1, 1).atStartOfDay().toInstant(
                            java.time.ZoneOffset.UTC
                        ).epochSecond
                    }
                    else -> 0L
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to parse Amazon release date: $dateStr")
                0L
            }
        }
    }

    // ── BaseAppScreen contract ─────────────────────────────────────────────

    @Composable
    override fun getGameDisplayInfo(
        context: Context,
        libraryItem: LibraryItem,
    ): GameDisplayInfo {
        val productId = productIdOf(libraryItem)
        Timber.tag(TAG).d(
            "getGameDisplayInfo: productId=$productId name=${libraryItem.name} " +
                "gameId=${productId.hashCode()} libraryItem.gameId=${libraryItem.gameId}"
        )

        var game by remember(productId) { mutableStateOf<AmazonGame?>(null) }

        LaunchedEffect(productId) {
            game = AmazonService.getAmazonGameOf(productId)
            Timber.tag(TAG).d(
                "Loaded game: title=${game?.title}, developer=${game?.developer}, " +
                    "releaseDate=${game?.releaseDate}, artUrl=${game?.artUrl?.take(60)}, " +
                    "heroUrl=${game?.heroUrl?.take(60)}, downloadSize=${game?.downloadSize}"
            )
        }

        val g = game

        // Artwork — use heroUrl for the backdrop, artUrl/iconHash for the icon
        val heroImageUrl = g?.heroUrl?.takeIf { it.isNotEmpty() }
            ?: g?.artUrl?.takeIf { it.isNotEmpty() }   // fall back to art if no hero
            ?: libraryItem.iconHash.takeIf { it.isNotEmpty() }

        val iconUrl = g?.artUrl?.takeIf { it.isNotEmpty() }
            ?: libraryItem.iconHash.takeIf { it.isNotEmpty() }

        // Metadata
        val developer = g?.developer?.takeIf { it.isNotEmpty() }
            ?: g?.publisher?.takeIf { it.isNotEmpty() }
            ?: ""

        val releaseDateTs = g?.releaseDate?.let { parseReleaseDate(it) } ?: 0L

        val sizeFromStore = if ((g?.downloadSize ?: 0L) > 0L) {
            formatBytes(g!!.downloadSize)
        } else {
            null
        }

        return GameDisplayInfo(
            name = g?.title ?: libraryItem.name,
            iconUrl = iconUrl,
            heroImageUrl = heroImageUrl,
            gameId = productId.hashCode(), // Stable Int from Amazon UUID — matches AmazonService event IDs
            appId = libraryItem.appId,
            releaseDate = releaseDateTs,
            developer = developer,
            installLocation = if (g?.isInstalled == true && g.installPath.isNotEmpty()) {
                g.installPath
            } else {
                null
            },
            sizeOnDisk = if ((g?.installSize ?: 0L) > 0L) formatBytes(g!!.installSize) else null,
            sizeFromStore = sizeFromStore,
        )
    }

override fun isInstalled(context: Context, libraryItem: LibraryItem): Boolean =
        AmazonService.isGameInstalled(productIdOf(libraryItem))

    override fun isValidToDownload(context: Context, libraryItem: LibraryItem): Boolean =
        !isInstalled(context, libraryItem) &&
            AmazonService.getDownloadInfo(productIdOf(libraryItem)) == null

    override fun isDownloading(context: Context, libraryItem: LibraryItem): Boolean =
        AmazonService.getDownloadInfo(productIdOf(libraryItem)) != null

    override fun getDownloadProgress(context: Context, libraryItem: LibraryItem): Float =
        AmazonService.getDownloadInfo(productIdOf(libraryItem))?.getProgress() ?: 0f

    override fun hasPartialDownload(context: Context, libraryItem: LibraryItem): Boolean = false

    override fun onDownloadInstallClick(
        context: Context,
        libraryItem: LibraryItem,
        onClickPlay: (Boolean) -> Unit,
    ) {
        val productId = productIdOf(libraryItem)
        val game = AmazonService.getAmazonGameOf(productId) ?: run {
            Toast.makeText(context, "Game not found — try syncing library", Toast.LENGTH_SHORT).show()
            Timber.tag(TAG).w("onDownloadInstallClick: game not found for $productId")
            return
        }
        val installPath = AmazonConstants.getGameInstallPath(context, game.title)
        Timber.tag(TAG).i("Downloading '${game.title}' → $installPath")

        val result = AmazonService.downloadGame(context, productId, installPath)
        if (result.isFailure) {
            val msg = result.exceptionOrNull()?.message ?: "Unknown error"
            Toast.makeText(context, "Failed to start download: $msg", Toast.LENGTH_LONG).show()
            Timber.tag(TAG).e("downloadGame failed: $msg")
        }
    }

    override fun onPauseResumeClick(context: Context, libraryItem: LibraryItem) {
        val productId = productIdOf(libraryItem)
        if (AmazonService.getDownloadInfo(productId) != null) {
            Timber.tag(TAG).i("Cancelling download for $productId")
            AmazonService.cancelDownload(productId)
        } else {
            // Resume — re-start the download
            onDownloadInstallClick(context, libraryItem) {}
        }
    }

    override fun onDeleteDownloadClick(context: Context, libraryItem: LibraryItem) {
        val productId = productIdOf(libraryItem)
        Timber.tag(TAG).i("Deleting game $productId")
        CoroutineScope(Dispatchers.IO).launch {
            AmazonService.deleteGame(context, productId)
        }
    }

    override fun onUpdateClick(context: Context, libraryItem: LibraryItem) {
        // Not applicable yet
    }

    override fun getInstallPath(context: Context, libraryItem: LibraryItem): String? {
        return AmazonService.getInstallPath(productIdOf(libraryItem))
    }

    override fun getExportFileExtension(): String = ".amazon"

    @Composable
    override fun getResetContainerOption(
        context: Context,
        libraryItem: LibraryItem,
    ): AppMenuOption? {
        var showDialog by remember { mutableStateOf(false) }

        if (showDialog) {
            ResetConfirmDialog(
                onConfirm = {
                    showDialog = false
                    resetContainerToDefaults(context, libraryItem)
                },
                onDismiss = { showDialog = false },
            )
        }

        return AppMenuOption(
            optionType = AppOptionMenuType.ResetToDefaults,
            onClick = { showDialog = true },
        )
    }

    override fun loadContainerData(context: Context, libraryItem: LibraryItem): ContainerData {
        val container = ContainerUtils.getOrCreateContainer(context, libraryItem.appId)
        return ContainerUtils.toContainerData(container)
    }

    override fun saveContainerConfig(
        context: Context,
        libraryItem: LibraryItem,
        config: ContainerData,
    ) {
        ContainerUtils.applyToContainer(context, libraryItem.appId, config)
    }

    override fun supportsContainerConfig(): Boolean = true
}
