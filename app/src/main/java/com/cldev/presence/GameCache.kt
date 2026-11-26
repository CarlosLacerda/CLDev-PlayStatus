
package com.cldev.presence

import android.content.Context
import org.json.JSONObject

data class CachedGame(
    val titleId: String,
    val metadata: GameMetadata
)

class GameCache(context: Context) {

    private val preferences = context.getSharedPreferences(
        "cldev_game_cache",
        Context.MODE_PRIVATE
    )

    fun save(titleId: String, game: GameMetadata) {
        val json = JSONObject().apply {
            put("id", game.id)
            put("title", game.title)
            put("releaseDate", game.releaseDate ?: JSONObject.NULL)
            put("coverUrl", game.coverUrl ?: JSONObject.NULL)
        }

        preferences.edit()
            .putString(titleId, json.toString())
            .apply()
    }

    fun get(titleId: String): GameMetadata? {
        val savedData = preferences.getString(titleId, null)
            ?: return null

        return parseGame(savedData)
    }

    fun getAll(): List<CachedGame> {
        return preferences.all.mapNotNull { entry ->

            val titleId = entry.key
            val savedData = entry.value as? String ?: return@mapNotNull null

            val metadata = parseGame(savedData)
                ?: return@mapNotNull null

            CachedGame(
                titleId = titleId,
                metadata = metadata
            )
        }.sortedBy { it.metadata.title.lowercase() }
    }

    fun contains(titleId: String): Boolean {
        return preferences.contains(titleId)
    }

    private fun parseGame(savedData: String): GameMetadata? {
        return try {
            val json = JSONObject(savedData)

            GameMetadata(
                id = json.getInt("id"),
                title = json.getString("title"),
                releaseDate = json.optString("releaseDate")
                    .takeUnless { it == "null" || it.isBlank() },
                coverUrl = json.optString("coverUrl")
                    .takeUnless { it == "null" || it.isBlank() }
            )
        } catch (e: Exception) {
            null
        }
    }
}
