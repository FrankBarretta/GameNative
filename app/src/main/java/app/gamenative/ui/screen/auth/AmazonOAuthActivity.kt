package app.gamenative.ui.screen.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.gamenative.service.amazon.AmazonAuthManager
import app.gamenative.ui.component.dialog.AuthWebViewDialog
import app.gamenative.ui.theme.PluviaTheme
import timber.log.Timber

/**
 * Amazon OAuth Activity using WebView with aggressive URL interception.
 *
 * Amazon's device auth redirects to https://www.amazon.com?openid.oa2.authorization_code=...
 * but WebView must intercept this URL BEFORE the page loads to capture the code.
 * 
 * We override shouldOverrideUrlLoading to intercept ALL navigation attempts,
 * similar to unifideck's CDP monitoring of Steam's CEF browser.
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

        val authUrl = if (savedInstanceState != null) {
            savedInstanceState.getString(SAVED_AUTH_URL) ?: AmazonAuthManager.startAuthFlow()
        } else {
            AmazonAuthManager.startAuthFlow()
        }
        initialAuthUrl = authUrl

        setContent {
            PluviaTheme {
                AmazonAuthWebView(
                    authUrl = authUrl,
                    onCodeCaptured = { code ->
                        Timber.i("[AmazonOAuth] ✓ Captured authorization code")
                        finishWithCode(code)
                    },
                    onDismiss = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }

    private fun finishWithCode(code: String) {
        val resultIntent = Intent().apply { putExtra(EXTRA_AUTH_CODE, code) }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}

/**
 * Custom WebView composable that aggressively intercepts Amazon OAuth redirects.
 * Intercepts navigation in shouldOverrideUrlLoading before page loads.
 */
@Composable
private fun AmazonAuthWebView(
    authUrl: String,
    onCodeCaptured: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val webViewClient = remember {
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                Timber.d("[AmazonOAuth] Intercepting navigation: ${url.take(100)}...")
                
                // Check if this is the redirect with auth code
                if (url.startsWith("https://www.amazon.com/") || url.startsWith("https://amazon.com/")) {
                    val code = extractAuthCode(url)
                    if (code != null) {
                        Timber.i("[AmazonOAuth] ✓ Code captured in shouldOverrideUrlLoading")
                        onCodeCaptured(code)
                        return true // Prevent WebView from loading the page
                    }
                }
                
                // Allow navigation to continue
                return false
            }
            
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                Timber.d("[AmazonOAuth] Intercepting navigation (legacy): ${url.take(100)}...")
                
                if (url.startsWith("https://www.amazon.com/") || url.startsWith("https://amazon.com/")) {
                    val code = extractAuthCode(url)
                    if (code != null) {
                        Timber.i("[AmazonOAuth] ✓ Code captured in shouldOverrideUrlLoading (legacy)")
                        onCodeCaptured(code)
                        return true
                    }
                }
                
                return false
            }
        }
    }
    
    AuthWebViewDialog(
        isVisible = true,
        url = authUrl,
        onDismissRequest = onDismiss,
        customWebViewClient = webViewClient
    )
}

private fun extractAuthCode(url: String): String? {
    return try {
        val uri = Uri.parse(url)
        uri.getQueryParameter("openid.oa2.authorization_code")
    } catch (e: Exception) {
        Timber.w(e, "[AmazonOAuth] Failed to parse URL")
        null
    }
}
