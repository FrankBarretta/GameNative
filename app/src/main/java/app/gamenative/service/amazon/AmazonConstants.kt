package app.gamenative.service.amazon

import android.net.Uri

/**
 * Constants for Amazon Games integration.
 *
 * Based on the nile launcher's PKCE OAuth2 flow:
 * https://github.com/imLinguin/nile
 */
object AmazonConstants {

    // ── Device registration identifiers (from nile) ─────────────────────────
    /** Amazon device type for AGS Launcher */
    const val DEVICE_TYPE = "A2UMVHOX7UP4V7"
    /** US marketplace */
    const val MARKETPLACE_ID = "ATVPDKIKX0DER"
    /** Application name presented to Amazon during device registration */
    const val APP_NAME = "AGSLauncher for Windows"
    const val APP_VERSION = "1.0.0"

    // ── Amazon API endpoints ────────────────────────────────────────────────
    const val AMAZON_API_BASE = "https://api.amazon.com"
    const val AUTH_REGISTER_URL = "$AMAZON_API_BASE/auth/register"
    const val AUTH_TOKEN_URL = "$AMAZON_API_BASE/auth/token"
    const val AUTH_DEREGISTER_URL = "$AMAZON_API_BASE/auth/deregister"

    // ── Amazon Gaming API ───────────────────────────────────────────────────
    const val GAMING_API_BASE = "https://gaming.amazon.com/api"
    const val ENTITLEMENTS_URL = "$GAMING_API_BASE/distribution/entitlements"

    // ── OpenID Connect / OAuth parameters ───────────────────────────────────
    const val OPENID_NS = "http://specs.openid.net/auth/2.0"
    const val OPENID_NS_PAPE = "http://specs.openid.net/extensions/pape/1.0"
    const val OPENID_NS_OA2 = "http://www.amazon.com/ap/OA2"
    const val OPENID_ASSOC_HANDLE = "amzn_sonic_games_launcher"
    const val OPENID_CLAIMED_ID = "http://specs.openid.net/auth/2.0/identifier_select"
    const val OPENID_IDENTITY = "http://specs.openid.net/auth/2.0/identifier_select"
    const val OPENID_MODE = "checkid_setup"
    const val OPENID_RETURN_TO = "https://www.amazon.com"

    /**
     * The scopes requested during OAuth.  Amazon Gaming requires these for
     * game library access and CDN downloads.
     */
    const val OA2_SCOPE = "profile payments:shipping_address payments:widget"

    /** Where the redirect lands after user approves login */
    const val OA2_RESPONSE_TYPE = "code"

    // ── User-Agent ──────────────────────────────────────────────────────────
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    /**
     * Builds the full Amazon OAuth login URL for a given PKCE code-challenge
     * and dynamically-generated client_id.
     *
     * @param clientId  Hex-encoded `device_serial#device_type`
     * @param codeChallenge  Base64-URL-encoded SHA-256 of the code verifier (no padding)
     * @return The sign-in URL to load in a WebView
     */
    fun buildAuthUrl(clientId: String, codeChallenge: String): String {
        return Uri.Builder()
            .scheme("https")
            .authority("www.amazon.com")
            .path("/ap/signin")
            .appendQueryParameter("openid.ns", OPENID_NS)
            .appendQueryParameter("openid.ns.pape", OPENID_NS_PAPE)
            .appendQueryParameter("openid.ns.oa2", OPENID_NS_OA2)
            .appendQueryParameter("openid.oa2.response_type", OA2_RESPONSE_TYPE)
            .appendQueryParameter("openid.oa2.scope", OA2_SCOPE)
            .appendQueryParameter("openid.oa2.code_challenge", codeChallenge)
            .appendQueryParameter("openid.oa2.code_challenge_method", "S256")
            .appendQueryParameter("openid.oa2.client_id", clientId)
            .appendQueryParameter("openid.claimed_id", OPENID_CLAIMED_ID)
            .appendQueryParameter("openid.identity", OPENID_IDENTITY)
            .appendQueryParameter("openid.assoc_handle", OPENID_ASSOC_HANDLE)
            .appendQueryParameter("openid.mode", OPENID_MODE)
            .appendQueryParameter("openid.return_to", OPENID_RETURN_TO)
            .build()
            .toString()
    }
}
