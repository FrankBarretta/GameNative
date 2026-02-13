package app.gamenative.data

/**
 * Amazon Games credentials for OAuth authentication (PKCE-based).
 *
 * Unlike Epic/GOG, Amazon uses a dynamic client_id and device_serial
 * that must be persisted alongside the tokens for refresh & deregister.
 */
data class AmazonCredentials(
    val accessToken: String,
    val refreshToken: String,
    val deviceSerial: String,
    val clientId: String,
    val expiresAt: Long = 0,
)
