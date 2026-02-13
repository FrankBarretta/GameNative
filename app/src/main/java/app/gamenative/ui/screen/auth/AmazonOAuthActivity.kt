package app.gamenative.ui.screen.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.gamenative.service.amazon.AmazonAuthManager
import app.gamenative.ui.component.dialog.AuthWebViewDialog
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.utils.redactUrlForLogging
import timber.log.Timber

/**
 * Amazon OAuth Activity that hosts AuthWebViewDialog and automatically captures
 * the PKCE authorization code when Amazon redirects back.
 *
 * Amazon's OpenID flow redirects to `https://www.amazon.com?…` with the auth
 * code in the query parameter `openid.oa2.authorization_code`.
 *
 * Unlike Epic/GOG, Amazon uses PKCE (no fixed client credentials).  The PKCE
 * state (code_verifier, device_serial) is prepared by [AmazonAuthManager.startAuthFlow]
 * and the resulting sign-in URL is passed to the WebView here.
 */
class AmazonOAuthActivity : ComponentActivity() {

    companion object {
        const val EXTRA_AUTH_CODE = "auth_code"
        const val EXTRA_ERROR = "error"
        private const val SAVED_AUTH_URL = "auth_url"
    }

    private var initialAuthUrl: String? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        initialAuthUrl?.let { outState.putString(SAVED_AUTH_URL, it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore or generate auth URL.
        // Note: On config change (rotation) we restore the URL so the WebView
        // keeps the same session.  The PKCE state lives in AmazonAuthManager.
        val authUrl = if (savedInstanceState != null) {
            savedInstanceState.getString(SAVED_AUTH_URL) ?: AmazonAuthManager.startAuthFlow()
        } else {
            AmazonAuthManager.startAuthFlow()
        }
        initialAuthUrl = authUrl

        setContent {
            PluviaTheme {
                AuthWebViewDialog(
                    isVisible = true,
                    url = authUrl,
                    onDismissRequest = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                    onUrlChange = { currentUrl: String ->
                        // Amazon redirects to https://www.amazon.com?openid.oa2.authorization_code=...
                        val code = extractAuthCode(currentUrl)
                        if (code != null) {
                            Timber.d("[AmazonOAuth] Captured authorization code from redirect")
                            finishWithCode(code)
                        }
                    },
                )
            }
        }
    }

    private fun finishWithCode(code: String) {
        val resultIntent = Intent().apply { putExtra(EXTRA_AUTH_CODE, code) }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    /**
     * Extracts the `openid.oa2.authorization_code` query parameter from the
     * redirect URL, if present.
     */
    private fun extractAuthCode(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            uri.getQueryParameter("openid.oa2.authorization_code")
        } catch (e: Exception) {
            Timber.w(e, "[AmazonOAuth] Failed to parse URL: %s", redactUrlForLogging(url))
            null
        }
    }
}
