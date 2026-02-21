package app.gamenative.service.amazon

import org.json.JSONObject

/** Typed models for Amazon Gaming Distribution API responses. */

data class EntitlementsResponse(
    val entitlements: List<EntitlementData>,
    val nextToken: String?,
) {
    companion object {
        fun fromJson(json: JSONObject): EntitlementsResponse {
            val entitlements = mutableListOf<EntitlementData>()
            val entitlementsArray = json.optJSONArray("entitlements")
            if (entitlementsArray != null) {
                for (i in 0 until entitlementsArray.length()) {
                    val entitlement = entitlementsArray.getJSONObject(i)
                    EntitlementData.fromJson(entitlement)?.let { entitlements.add(it) }
                }
            }
            val nextToken = if (json.has("nextToken") && !json.isNull("nextToken")) {
                json.getString("nextToken")
            } else null
            return EntitlementsResponse(entitlements, nextToken)
        }
    }
}

data class EntitlementData(
    val id: String,              // Top-level entitlement UUID (needed for GetGameDownload)
    val productId: String,       // Product ID (game identifier)
    val title: String,
    val purchasedDate: String,
    val developer: String,
    val publisher: String,
    val releaseDate: String,
    val downloadSize: Long,
    val artUrl: String,
    val heroUrl: String,
    val productSku: String,
    val productJson: String,
) {
    companion object {
        fun fromJson(entitlement: JSONObject): EntitlementData? {
            val product = entitlement.optJSONObject("product") ?: return null
            val productId = product.optString("id", "").ifEmpty { return null }
            val title = product.optString("title", "")
            val purchasedDate = entitlement.optString("purchasedDate", "")
            val entitlementId = entitlement.optString("id", "").ifEmpty { return null }

            val productDetail = product.optJSONObject("productDetail")
            val details = productDetail?.optJSONObject("details")

            val developer = details?.optString("developer", "") ?: ""
            val publisher = details?.optString("publisher", "") ?: ""
            val releaseDate = details?.optString("releaseDate", "") ?: ""
            val downloadSize = details?.optLong("fileSize", 0L) ?: 0L

            val artUrl = resolveArtUrl(productDetail, details)
            val heroUrl = resolveHeroUrl(details)
            val productSku = product.optString("sku", "")

            return EntitlementData(
                id = entitlementId,
                productId = productId,
                title = title,
                purchasedDate = purchasedDate,
                developer = developer,
                publisher = publisher,
                releaseDate = releaseDate,
                downloadSize = downloadSize,
                artUrl = artUrl,
                heroUrl = heroUrl,
                productSku = productSku,
                productJson = product.toString(),
            )
        }

        private fun resolveArtUrl(productDetail: JSONObject?, details: JSONObject?): String {
            val iconUrl = productDetail?.optString("iconUrl", "") ?: ""
            if (iconUrl.isNotEmpty()) return iconUrl
            val logoUrl = details?.optString("logoUrl", "") ?: ""
            if (logoUrl.isNotEmpty()) return logoUrl
            return ""
        }

        private fun resolveHeroUrl(details: JSONObject?): String {
            val bg1 = details?.optString("backgroundUrl1", "") ?: ""
            if (bg1.isNotEmpty()) return bg1
            val bg2 = details?.optString("backgroundUrl2", "") ?: ""
            if (bg2.isNotEmpty()) return bg2
            return ""
        }
    }
}

data class GameDownloadResponse(
    val downloadUrl: String,
    val versionId: String,
) {
    companion object {
        fun fromJson(json: JSONObject): GameDownloadResponse? {
            val downloadUrl = json.optString("downloadUrl", "").ifEmpty { return null }
            val versionId = json.optString("versionId", "")
            return GameDownloadResponse(downloadUrl, versionId)
        }
    }
}

data class LiveVersionIdsResponse(
    val versionMap: Map<String, String>,
) {
    companion object {
        fun fromJson(json: JSONObject): LiveVersionIdsResponse? {
            val versions = json.optJSONObject("adgProductIdToVersionIdMap") ?: return null
            val result = mutableMapOf<String, String>()
            for (key in versions.keys()) {
                result[key] = versions.optString(key, "")
            }
            return LiveVersionIdsResponse(result)
        }
    }
}
