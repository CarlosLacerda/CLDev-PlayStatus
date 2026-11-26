
package com.cldev.presence

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GameMetadata(
    val id: Int,
    val title: String,
    val releaseDate: String?,
    val coverUrl: String?
)

class TheGamesDbClient {

    companion object {
        private const val BASE_URL = "https://api.thegamesdb.net"
        private const val PLATFORM_PS3 = "12"
        private const val IMAGE_BASE_URL =
            "https://cdn.thegamesdb.net/images/medium/"
    }

    fun searchGame(gameName: String): GameMetadata? {
        val apiKey = BuildConfig.THEGAMESDB_API_KEY

        if (apiKey.isBlank()) {
            throw IllegalStateException(
                "Chave da TheGamesDB não configurada."
            )
        }

        val encodedName = URLEncoder.encode(gameName, "UTF-8")
        val encodedKey = URLEncoder.encode(apiKey, "UTF-8")

        val requestUrl = URL(
            "$BASE_URL/v1.1/Games/ByGameName" +
                    "?apikey=$encodedKey" +
                    "&name=$encodedName" +
                    "&filter%5Bplatform%5D=$PLATFORM_PS3" +
                    "&include=boxart,platform"
        )

        val connection = requestUrl.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val responseCode = connection.responseCode

            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use {
                    it.readText()
                }
            } else {
                connection.errorStream?.bufferedReader()?.use {
                    it.readText()
                }.orEmpty()
            }

            if (responseCode !in 200..299) {
                throw IllegalStateException(
                    "TheGamesDB respondeu HTTP $responseCode."
                )
            }

            return parseResponse(responseBody, gameName)

        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(
        responseBody: String,
        searchedName: String
    ): GameMetadata? {

        val root = JSONObject(responseBody)


        val data = root.optJSONObject("data")
            ?: return null

        val games = data.opt("games")
            ?: return null

        val candidates = mutableListOf<JSONObject>()

        when (games) {
            is JSONArray -> {
                for (i in 0 until games.length()) {
                    games.optJSONObject(i)?.let {
                        candidates.add(it)
                    }
                }
            }

            is JSONObject -> {
                val keys = games.keys()

                while (keys.hasNext()) {
                    val key = keys.next()
                    games.optJSONObject(key)?.let {
                        candidates.add(it)
                    }
                }
            }
        }

        val normalizedSearch = normalize(searchedName)

        val selectedGame = candidates.firstOrNull { game ->
            normalize(game.optString("game_title")) ==
                    normalizedSearch
        } ?: return null

        val gameId = selectedGame.optInt("id", 0)

        if (gameId == 0) return null

        val coverUrl = findCoverUrl(
            root = root,
            gameId = gameId
        )

        return GameMetadata(
            id = gameId,
            title = selectedGame.optString("game_title"),
            releaseDate = selectedGame.optString("release_date")
                .takeIf { it.isNotBlank() },
            coverUrl = coverUrl
        )
    }




    private fun findCoverUrl(
        root: JSONObject,
        gameId: Int
    ): String? {

        val include = root.optJSONObject("include")
            ?: return null

        val boxart = include.optJSONObject("boxart")
            ?: return null

        val baseUrls = boxart.optJSONObject("base_url")
            ?: return null

        val baseUrl = baseUrls.optString("medium")
            .takeIf { it.isNotBlank() }
            ?: return null

        val images = boxart.optJSONObject("data")
            ?: return null

        val gameImages = images.optJSONArray(gameId.toString())
            ?: return null

        for (i in 0 until gameImages.length()) {

            val image = gameImages.optJSONObject(i) ?: continue

            val isFront = image.optString("side") == "front"
            val isBoxart = image.optString("type") == "boxart"

            val filename = image.optString("filename")

            if (isFront && isBoxart && filename.isNotBlank()) {
                return baseUrl + filename
            }
        }

        return null
    }




    private fun normalize(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
    }
}
