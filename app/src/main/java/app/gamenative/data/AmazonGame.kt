package app.gamenative.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Amazon Game entity for Room database.
 * Represents an entitlement returned by the Amazon Gaming Distribution service.
 */
@Entity(tableName = "amazon_games")
data class AmazonGame(
    /** Amazon product ID (e.g. "amzn1.adg.product.XXXX") */
    @PrimaryKey
    @ColumnInfo("id")
    val id: String,

    @ColumnInfo("title")
    val title: String = "",

    // Installation info
    @ColumnInfo("is_installed")
    val isInstalled: Boolean = false,

    @ColumnInfo("install_path")
    val installPath: String = "",

    // Art – full HTTPS URL to the product image (may be empty if not provided by API)
    @ColumnInfo("art_url")
    val artUrl: String = "",

    /** ISO-8601 date string from the entitlement, e.g. "2024-01-15T00:00:00.000Z" */
    @ColumnInfo("purchased_date")
    val purchasedDate: String = "",

    /** Raw product JSON kept for future use (install, manifest lookup, etc.) */
    @ColumnInfo("product_json")
    val productJson: String = "",
)

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
