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

    private const val GET_ENTITLEMENTS_TARGET =
        "com.amazon.animusdistributionservice.entitlement.AnimusEntitlementsService.GetEntitlements"

    private const val USER_AGENT = "com.amazon.agslauncher.win/3.0.9202.1"
    private const val KEY_ID = "d5dc8b8b-86c8-4fc4-ae93-18c0def5314d"

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
                Timber.e("[Amazon] GetEntitlements HTTP $responseCode: $errorBody")
                return null
            }

            val responseText = connection.inputStream.bufferedReader().readText()
            JSONObject(responseText)
        } catch (e: Exception) {
            Timber.e(e, "[Amazon] GetEntitlements request failed")
            null
        }
    }

    /**
     * Parse a single entitlement JSON object into an [AmazonGame].
     * Entitlement shape (from nile): { product: { id, title, ... }, ... }
     */
    private fun parseEntitlement(entitlement: JSONObject): AmazonGame? {
        val product = entitlement.optJSONObject("product") ?: return null
        val id = product.optString("id", "").ifEmpty { return null }
        val title = product.optString("title", "")
        val purchasedDate = entitlement.optString("purchasedDate", "")

        // Art URL — Amazon sometimes provides it under different keys; try a few
        val artUrl = product.optString("iconUrl", "")
            .ifEmpty { product.optString("artUrl", "") }
            .ifEmpty { product.optString("imageUrl", "") }

        return AmazonGame(
            id = id,
            title = title,
            artUrl = artUrl,
            purchasedDate = purchasedDate,
            productJson = product.toString(),
        )
    }

    /** SHA-256 of [input], hex-encoded in UPPERCASE — matches nile's hardwareHash. */
    private fun sha256Upper(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }.uppercase()
    }
}
