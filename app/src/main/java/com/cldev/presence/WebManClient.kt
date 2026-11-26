
package com.cldev.presence

import android.text.Html
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL

data class WebManResponse(
    val httpCode: Int,
    val rawBody: String,
    val gameTitle: String? = null,
    val titleId: String? = null
)

class WebManClient {

    fun readSystemInfo(host: String, port: Int = 80): WebManResponse {
        val cleanHost = host.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')

        require(cleanHost.isNotBlank()) {
            "Informe o IP do PS3."
        }

        require(!cleanHost.contains("/")) {
            "Informe somente o IP ou nome do host."
        }

        val url = URL("http://$cleanHost:$port/cpursx.ps3")

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 7000
            setRequestProperty("User-Agent", "CLDev-Discord-Presence/0.1")
            setRequestProperty("Accept", "text/html,*/*")
        }

        return try {
            val code = connection.responseCode
            val stream = if (code in 200..399) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val body = stream?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            val parsed = parseGameInfo(body)

            WebManResponse(
                httpCode = code,
                rawBody = body.take(12000),
                gameTitle = parsed.first,
                titleId = parsed.second
            )
        } finally {
            connection.disconnect()
        }
    }


    private fun parseGameInfo(html: String): Pair<String?, String?> {
        val h2: String = Regex(
            """(?is)<h2\b[^>]*>(.*?)</h2>"""
        ).find(html)?.groupValues?.get(1)
            ?: return Pair(null, null)

        val anchors: List<String> = Regex(
            """(?is)<a\b[^>]*>(.*?)</a>"""
        ).findAll(h2).map { match ->
            val text: String = match.groupValues[1]
                .replace(Regex("""<[^>]+>"""), "")
                .let { content ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        Html.fromHtml(
                            content,
                            Html.FROM_HTML_MODE_LEGACY
                        ).toString()
                    } else {
                        @Suppress("DEPRECATION")
                        Html.fromHtml(content).toString()
                    }
                }
                .trim()

            text
        }.filter { value -> value.isNotBlank() }.toList()

        val titleId: String? = anchors.firstOrNull { value ->
            Regex("""^[A-Z]{4}\d{5}$""").matches(value)
        }

        val gameTitle: String? = if (titleId != null) {
            anchors.firstOrNull { value ->
                value != titleId &&
                        !Regex("""^\d+(\.\d+)*$""").matches(value)
            }?.replace(Regex("""\s+\d+(\.\d+)*$"""), "")
                ?.trim()
        } else {
            null
        }

        return Pair(gameTitle, titleId)
    }}
