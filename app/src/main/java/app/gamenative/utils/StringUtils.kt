package app.gamenative.utils

import android.text.Html
import app.gamenative.Constants
import java.util.Locale

/**
 * Extension functions relating to [String] as the receiver type.
 */

fun String.getAvatarURL(): String =
    this.ifEmpty { null }
        ?.takeIf { str -> str.isNotEmpty() && !str.all { it == '0' } }
        ?.let { "${Constants.Persona.AVATAR_BASE_URL}${it.substring(0, 2)}/${it}_full.jpg" }
        ?: Constants.Persona.MISSING_AVATAR_URL

fun String.fromHtml(): String = Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString()

// This doesn't belong here, but i'm tired.
fun Long.getProfileUrl(): String = "${Constants.Persona.PROFILE_URL}$this/"

/**
 * Formats a byte count into a human-readable string with appropriate units.
 * Uses Locale.ROOT to ensure consistent formatting across different locales.
 */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.ROOT, "%.2f GB", gb)
}
