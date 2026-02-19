package app.gamenative.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.gamenative.data.AmazonGame
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Amazon games in the Room database.
 */
@Dao
interface AmazonGameDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(game: AmazonGame)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(games: List<AmazonGame>)

    @Update
    suspend fun update(game: AmazonGame)

    @Delete
    suspend fun delete(game: AmazonGame)

    @Query("DELETE FROM amazon_games WHERE id = :gameId")
    suspend fun deleteById(gameId: String)

    @Query("SELECT * FROM amazon_games WHERE id = :gameId")
    suspend fun getById(gameId: String): AmazonGame?

    @Query("SELECT * FROM amazon_games ORDER BY title ASC")
    fun getAll(): Flow<List<AmazonGame>>

    @Query("SELECT * FROM amazon_games ORDER BY title ASC")
    suspend fun getAllAsList(): List<AmazonGame>

    @Query("SELECT * FROM amazon_games WHERE is_installed = :isInstalled ORDER BY title ASC")
    fun getByInstallStatus(isInstalled: Boolean): Flow<List<AmazonGame>>

    @Query("SELECT id FROM amazon_games")
    suspend fun getAllIds(): List<String>

    @Query("SELECT COUNT(*) FROM amazon_games")
    fun getCount(): Flow<Int>

    @Query("DELETE FROM amazon_games")
    suspend fun deleteAll()

    @Query(
        "UPDATE amazon_games SET is_installed = 1, install_path = :path, install_size = :size, version_id = :versionId WHERE id = :id",
    )
    suspend fun markAsInstalled(id: String, path: String, size: Long, versionId: String)

    @Query("UPDATE amazon_games SET is_installed = 0, install_path = '', install_size = 0, version_id = '' WHERE id = :id")
    suspend fun markAsUninstalled(id: String)

    @Query("UPDATE amazon_games SET download_size = :size WHERE id = :id")
    suspend fun updateDownloadSize(id: String, size: Long)

    @Query("UPDATE amazon_games SET last_played = :lastPlayed, play_time_minutes = :playTimeMinutes WHERE id = :id")
    suspend fun updatePlaytime(id: String, lastPlayed: Long, playTimeMinutes: Long)

    // Only delete non-installed games from DB - Need to preserve any currently installed games.
    @Query("DELETE FROM amazon_games WHERE is_installed = false")
    suspend fun deleteAllNonInstalledGames()

    @Query("SELECT * FROM amazon_games WHERE id IN (:ids)")
    suspend fun getGamesByIds(ids: List<String>): List<AmazonGame>

    /**
     * Upsert Amazon games while preserving install status and install path.
     * Used when refreshing the library from the Amazon API — we don't want to
     * wipe locally-tracked installation info just because the API response
     * doesn't include it.
     *
     * Optimized to avoid N+1 queries by fetching all existing games in one batch.
     */
    @Transaction
    suspend fun upsertPreservingInstallStatus(games: List<AmazonGame>) {
        // Batch fetch all existing games in one query (avoids N+1)
        val gameIds = games.map { it.id }
        val existingGames = getGamesByIds(gameIds)
        val existingMap = existingGames.associateBy { it.id }

        val toInsert = games.map { newGame ->
            val existing = existingMap[newGame.id]
            if (existing != null) {
                // Preserve install-related fields and playtime from DB
                newGame.copy(
                    isInstalled = existing.isInstalled,
                    installPath = existing.installPath,
                    installSize = existing.installSize,
                    versionId = existing.versionId,
                    productSku = if (newGame.productSku.isNotEmpty()) newGame.productSku else existing.productSku,
                    lastPlayed = existing.lastPlayed,
                    playTimeMinutes = existing.playTimeMinutes,
                )
            } else {
                newGame
            }
        }

        // InsertAll with REPLACE strategy handles both insert and update
        insertAll(toInsert)
    }
}
