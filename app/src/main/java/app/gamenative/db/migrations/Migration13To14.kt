package app.gamenative.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migration from version 13 to 14.
 *
 * Schema changes for amazon_games table:
 * - Rename column `id` → `product_id`
 * - Add new auto-generated `app_id` INTEGER PRIMARY KEY
 *
 * This migration:
 * 1. Creates a new table with the updated schema
 * 2. Copies existing data (existing games get auto-generated appIds)
 * 3. Drops the old table
 * 4. Renames the new table
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create new table with updated schema
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS amazon_games_new (
                app_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                product_id TEXT NOT NULL,
                entitlement_id TEXT NOT NULL DEFAULT '',
                title TEXT NOT NULL DEFAULT '',
                is_installed INTEGER NOT NULL DEFAULT 0,
                install_path TEXT NOT NULL DEFAULT '',
                art_url TEXT NOT NULL DEFAULT '',
                hero_url TEXT NOT NULL DEFAULT '',
                purchased_date TEXT NOT NULL DEFAULT '',
                developer TEXT NOT NULL DEFAULT '',
                publisher TEXT NOT NULL DEFAULT '',
                release_date TEXT NOT NULL DEFAULT '',
                download_size INTEGER NOT NULL DEFAULT 0,
                install_size INTEGER NOT NULL DEFAULT 0,
                version_id TEXT NOT NULL DEFAULT '',
                product_sku TEXT NOT NULL DEFAULT '',
                last_played INTEGER NOT NULL DEFAULT 0,
                play_time_minutes INTEGER NOT NULL DEFAULT 0,
                product_json TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )

        // Copy existing data (app_id will be auto-generated)
        db.execSQL(
            """
            INSERT INTO amazon_games_new (
                product_id, entitlement_id, title, is_installed, install_path,
                art_url, hero_url, purchased_date, developer, publisher,
                release_date, download_size, install_size, version_id,
                product_sku, last_played, play_time_minutes, product_json
            )
            SELECT
                id, entitlement_id, title, is_installed, install_path,
                art_url, hero_url, purchased_date, developer, publisher,
                release_date, download_size, install_size, version_id,
                product_sku, last_played, play_time_minutes, product_json
            FROM amazon_games
            """.trimIndent()
        )

        // Drop old table
        db.execSQL("DROP TABLE amazon_games")

        // Rename new table to original name
        db.execSQL("ALTER TABLE amazon_games_new RENAME TO amazon_games")

        // Create index on product_id for efficient lookups
        db.execSQL("CREATE INDEX IF NOT EXISTS index_amazon_games_product_id ON amazon_games (product_id)")
    }
}
