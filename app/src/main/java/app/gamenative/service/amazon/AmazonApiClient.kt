package app.gamenative.service.amazon

import app.gamenative.data.AmazonGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Low-level HTTP client for the Amazon Gaming Distribution service.
 *
 * Mirrors nile's `Library.request_distribution()` and `Library._get_sync_request_data()`.
 * Endpoint: https://gaming.amazon.com/api/distribution/entitlements
 * Target:   com.amazon.animusdistributionservice.entitlement.AnimusEntitlementsService.GetEntitlements
 */
object AmazonApiClient {

    private const val ENTITLEMENTS_URL =
        "https://gaming.amazon.com/api/distribution/entitlements"

    /**
     * Base URL for the public distribution service — used by GetGameDownload, GetLiveVersionIds, etc.
     * Distinct from ENTITLEMENTS_URL (which is only for GetEntitlements).
     * Confirmed from nile: constants.AMAZON_GAMING_DISTRIBUTION
     */
    private const val DISTRIBUTION_URL =
        "https://gaming.amazon.com/api/distribution/v2/public"

    private const val GET_ENTITLEMENTS_TARGET =
        "com.amazon.animusdistributionservice.entitlement.AnimusEntitlementsService.GetEntitlements"

    private const val GET_GAME_DOWNLOAD_TARGET =
        "com.amazon.animusdistributionservice.external.AnimusDistributionService.GetGameDownload"

    private const val USER_AGENT = "com.amazon.agslauncher.win/3.0.9202.1"
    private const val KEY_ID = "d5dc8b8b-86c8-4fc4-ae93-18c0def5314d"

    /** Result of a GetGameDownload call. */
    data class GameDownloadSpec(
        val downloadUrl: String,
        val versionId: String,
    )

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Fetches all entitlements (owned games) for the authenticated user.
     *
     * @param bearerToken  The `access_token` from [AmazonAuthManager].
     * @param deviceSerial The device serial number (used to compute hardwareHash).
     * @return A list of [AmazonGame] objects parsed from the response.
     */
    suspend fun getEntitlements(
        bearerToken: String,
        deviceSerial: String,
    ): List<AmazonGame> = withContext(Dispatchers.IO) {
        val games = mutableMapOf<String, AmazonGame>() // keyed by product id to deduplicate
        val hardwareHash = sha256Upper(deviceSerial)
        var nextToken: String? = null

        Timber.i("[Amazon] Fetching entitlements (hardwareHash=${hardwareHash.take(8)}…)")

        do {
            val requestBody = buildRequestBody(nextToken, hardwareHash)
            val responseJson = postJson(
                url = ENTITLEMENTS_URL,
                target = GET_ENTITLEMENTS_TARGET,
                bearerToken = bearerToken,
                body = requestBody,
            ) ?: break

            val entitlementsArray = responseJson.optJSONArray("entitlements")
            if (entitlementsArray != null) {
                for (i in 0 until entitlementsArray.length()) {
                    val entitlement = entitlementsArray.getJSONObject(i)
                    val game = parseEntitlement(entitlement) ?: continue
                    // Deduplicate by product id (nile does the same)
                    games[game.id] = game
                }
                Timber.d("[Amazon] Page returned ${entitlementsArray.length()} entitlements, total so far: ${games.size}")
            }

            nextToken = if (responseJson.has("nextToken")) {
                responseJson.getString("nextToken").also {
                    Timber.d("[Amazon] Got nextToken, fetching next page…")
                }
            } else null

        } while (nextToken != null)

        Timber.i("[Amazon] Fetched ${games.size} total entitlements")
        games.values.toList()
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun buildRequestBody(nextToken: String?, hardwareHash: String): JSONObject =
        JSONObject().apply {
            put("Operation", "GetEntitlements")
            put("clientId", "Sonic")
            put("syncPoint", JSONObject.NULL)
            put("nextToken", if (nextToken != null) nextToken else JSONObject.NULL)
            put("maxResults", 50)
            put("productIdFilter", JSONObject.NULL)
            put("keyId", KEY_ID)
            put("hardwareHash", hardwareHash)
        }

    private fun postJson(
        url: String,
        target: String,
        bearerToken: String,
        body: JSONObject,
    ): JSONObject? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("X-Amz-Target", target)
                setRequestProperty("x-amzn-token", bearerToken)
                setRequestProperty("UserAgent", USER_AGENT)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Content-Encoding", "amz-1.0")
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 30_000
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "(no body)"
                Timber.e("[Amazon] HTTP $responseCode from $url (target=${target.substringAfterLast('.')}): $errorBody")
                return null
            }

            val responseText = connection.inputStream.bufferedReader().readText()
            JSONObject(responseText)
        } catch (e: Exception) {
            Timber.e(e, "[Amazon] POST to $url failed")
            null
        }
    }

    /**
     * Parse a single entitlement JSON object into an [AmazonGame].
     * Entitlement shape (from nile): { product: { id, title, productDetail: { details: {...} }, ... }, ... }
     */
    private fun parseEntitlement(entitlement: JSONObject): AmazonGame? {
        val product = entitlement.optJSONObject("product") ?: return null
        val id = product.optString("id", "").ifEmpty { return null }
        val title = product.optString("title", "")
        val purchasedDate = entitlement.optString("purchasedDate", "")

        // Top-level entitlement UUID  — needed for GetGameDownload, NOT the product ID
        val entitlementId = entitlement.optString("id", "")

        // productDetail sits between product and details:
        // product -> productDetail -> details
        //                         -> iconUrl  (box art lives here, NOT inside details)
        val productDetail = product.optJSONObject("productDetail")
        val details = productDetail?.optJSONObject("details")

        val developer = details?.optString("developer", "") ?: ""
        val publisher = details?.optString("publisher", "") ?: ""
        val releaseDate = details?.optString("releaseDate", "") ?: ""
        val downloadSize = details?.optLong("fileSize", 0L) ?: 0L

        val artUrl = resolveArtUrl(productDetail, details)
        val heroUrl = resolveHeroUrl(details)

        return AmazonGame(
            id = id,
            entitlementId = entitlementId,
            title = title,
            artUrl = artUrl,
            heroUrl = heroUrl,
            purchasedDate = purchasedDate,
            developer = developer,
            publisher = publisher,
            releaseDate = releaseDate,
            downloadSize = downloadSize,
            productJson = product.toString(),
        )
    }

    /**
     * Resolve the primary (icon/box) artwork URL.
     *
     * Live API structure (confirmed from device logs):
     *   product -> productDetail -> iconUrl          (box art)
     *   product -> productDetail -> details -> logoUrl  (transparent logo PNG, fallback)
     */
    private fun resolveArtUrl(productDetail: JSONObject?, details: JSONObject?): String {
        // Primary: iconUrl lives directly on productDetail, NOT inside details
        val iconUrl = productDetail?.optString("iconUrl", "") ?: ""
        if (iconUrl.isNotEmpty()) return iconUrl

        // Fallback: transparent logo PNG inside details
        val logoUrl = details?.optString("logoUrl", "") ?: ""
        if (logoUrl.isNotEmpty()) return logoUrl

        return ""
    }

    /**
     * Resolve the hero/background artwork URL for the detail screen.
     *
     * Live API structure (confirmed from device logs):
     *   product -> productDetail -> details -> backgroundUrl1  (primary background)
     *   product -> productDetail -> details -> backgroundUrl2  (secondary background)
     */
    private fun resolveHeroUrl(details: JSONObject?): String {
        val bg1 = details?.optString("backgroundUrl1", "") ?: ""
        if (bg1.isNotEmpty()) return bg1

        val bg2 = details?.optString("backgroundUrl2", "") ?: ""
        if (bg2.isNotEmpty()) return bg2

        return ""
    }

    /** SHA-256 of [input], hex-encoded in UPPERCASE — matches nile's hardwareHash. */
    private fun sha256Upper(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }.uppercase()
    }

    // ── Download API ─────────────────────────────────────────────────────────────────────────────

    /**
     * Fetches the download manifest spec for a game.
     *
     * Mirrors nile's `Library.get_game_manifest(id)` where `id` is the top-level
     * entitlement UUID (NOT the product ID).
     *
     * @param entitlementId  The UUID from [AmazonGame.entitlementId].
     * @param bearerToken    The `access_token` from [AmazonAuthManager].
     * @return [GameDownloadSpec] on success, null on failure.
     */
    suspend fun fetchGameDownload(
        entitlementId: String,
        bearerToken: String,
    ): GameDownloadSpec? = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("entitlementId", entitlementId)
            put("Operation", "GetGameDownload")
        }

        Timber.tag("Amazon").d("fetchGameDownload: entitlementId=$entitlementId, token=${bearerToken.take(20)}…")

        val response = postJson(
            url = DISTRIBUTION_URL,
            target = GET_GAME_DOWNLOAD_TARGET,
            bearerToken = bearerToken,
            body = body,
        ) ?: return@withContext null

        val downloadUrl = response.optString("downloadUrl", "").ifEmpty {
            Timber.e("[Amazon] GetGameDownload: missing downloadUrl in response: ${response.toString().take(500)}")
            return@withContext null
        }
        val versionId = response.optString("versionId", "")
        Timber.i("[Amazon] GetGameDownload: versionId=$versionId url=$downloadUrl")
        GameDownloadSpec(downloadUrl = downloadUrl, versionId = versionId)
    }
}
