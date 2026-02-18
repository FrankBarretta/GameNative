package app.gamenative.service.amazon

import android.content.Context
import app.gamenative.data.AmazonGame
import app.gamenative.db.dao.AmazonGameDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Amazon library manager — the Amazon equivalent of GOGManager / EpicManager.
 *
 * Responsibilities:
 *  1. Load stored credentials via [AmazonAuthManager].
 *  2. Call [AmazonApiClient.getEntitlements] to retrieve the user's library.
 *  3. Upsert results into the [AmazonGameDao] Room table, preserving install status.
 */
@Singleton
class AmazonManager @Inject constructor(
    private val amazonGameDao: AmazonGameDao,
    @ApplicationContext private val context: Context,
) {

    /**
     * Refresh the Amazon library from the API and persist results to the DB.
     * Safe to call from a coroutine running on any dispatcher.
     */
    suspend fun refreshLibrary() = withContext(Dispatchers.IO) {
        Timber.i("[Amazon] Starting library refresh…")

        val credentialsResult = AmazonAuthManager.getStoredCredentials(context)
        if (credentialsResult.isFailure) {
            Timber.w("[Amazon] No stored credentials — ${credentialsResult.exceptionOrNull()?.message}")
            return@withContext
        }
        val credentials = credentialsResult.getOrNull()!!

        val games = AmazonApiClient.getEntitlements(
            bearerToken = credentials.accessToken,
            deviceSerial = credentials.deviceSerial,
        )

        if (games.isEmpty()) {
            Timber.w("[Amazon] No entitlements returned from API")
            return@withContext
        }

        amazonGameDao.upsertPreservingInstallStatus(games)
        Timber.i("[Amazon] Library refresh complete — ${games.size} game(s) in DB")
    }

    /**
     * Look up a single [AmazonGame] by its product ID (e.g. "amzn1.adg.product.XXXX").
     * Returns null if not found.
     */
    suspend fun getGameById(productId: String): AmazonGame? = withContext(Dispatchers.IO) {
        amazonGameDao.getById(productId)
    }
}
