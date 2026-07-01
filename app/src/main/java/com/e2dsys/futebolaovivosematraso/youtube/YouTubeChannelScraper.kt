package com.e2dsys.futebolaovivosematraso.youtube

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LiveStreamItem(
    val title: String,
    val url: String,
    val viewCount: Long,
    val isLive: Boolean,
    val isUpcoming: Boolean
)

object YouTubeChannelScraper {

    private const val TAG = "ChannelScraper"
    private const val BASE_URL = "https://www.youtube.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun getLiveStreams(channelHandle: String = "@cazetv"): List<LiveStreamItem> {
        val html = fetchChannelPage(channelHandle) ?: return emptyList()
        val jsonStr = extractInitialData(html) ?: return emptyList()
        return parseStreams(jsonStr)
    }

    private fun fetchChannelPage(handle: String): String? {
        val request = Request.Builder()
            .url("$BASE_URL/$handle/streams")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36")
            .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            response.body?.string()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractInitialData(html: String): String? {
        val newPattern = Regex("""var\s+ytInitialData\s*=\s*'(.*?)';\s*""", RegexOption.DOT_MATCHES_ALL)
        val match = newPattern.find(html)
        if (match != null) {
            return unescapeYtData(match.groupValues[1])
        }

        val legacyPatterns = listOf(
            Regex("""window\[['"]ytInitialData['"]\]\s*=\s*(\{.+?\});""", RegexOption.DOT_MATCHES_ALL),
            Regex("""window\.ytInitialData\s*=\s*(\{.+?\});""", RegexOption.DOT_MATCHES_ALL)
        )
        for (pattern in legacyPatterns) {
            val legacyMatch = pattern.find(html)
            if (legacyMatch != null) return legacyMatch.groupValues[1]
        }
        return null
    }

    private fun unescapeYtData(escaped: String): String {
        val result = StringBuilder(escaped.length)
        var i = 0
        while (i < escaped.length) {
            if (escaped[i] == '\\' && i + 1 < escaped.length) {
                val next = escaped[i + 1]
                when (next) {
                    'x' -> {
                        if (i + 3 < escaped.length) {
                            val hex = escaped.substring(i + 2, i + 4)
                            result.append(hex.toInt(16).toChar())
                            i += 4
                        } else {
                            result.append('\\').append(next)
                            i += 2
                        }
                    }
                    'u' -> {
                        if (i + 5 < escaped.length) {
                            val hex = escaped.substring(i + 2, i + 6)
                            result.append(hex.toInt(16).toChar())
                            i += 6
                        } else {
                            result.append('\\').append(next)
                            i += 2
                        }
                    }
                    'n' -> { result.append('\n'); i += 2 }
                    'r' -> { result.append('\r'); i += 2 }
                    't' -> { result.append('\t'); i += 2 }
                    'b' -> { result.append('\b'); i += 2 }
                    'f' -> { result.append('\u000C'); i += 2 }
                    else -> { result.append(next); i += 2 }
                }
            } else {
                result.append(escaped[i])
                i++
            }
        }
        return result.toString()
    }

    private fun parseStreams(json: String): List<LiveStreamItem> {
        return try {
            val root = JSONObject(json)
            val tabs = root
                .optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?: return emptyList()

            val selectedTab = findSelectedTab(tabs) ?: return emptyList()
            val contents = selectedTab
                .optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("richGridRenderer")
                ?.optJSONArray("contents")
                ?: return emptyList()

            parseItems(contents)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun findSelectedTab(tabs: JSONArray): JSONObject? {
        for (i in 0 until tabs.length()) {
            val tab = tabs.optJSONObject(i)
            val renderer = tab?.optJSONObject("tabRenderer") ?: continue
            if (renderer.optBoolean("selected", false)) return tab
        }
        return tabs.optJSONObject(0)
    }

    private fun parseItems(contents: JSONArray): List<LiveStreamItem> {
        val items = mutableListOf<LiveStreamItem>()

        for (i in 0 until contents.length()) {
            val content = contents.optJSONObject(i) ?: continue
            val richItem = content.optJSONObject("richItemRenderer") ?: continue
            val innerContent = richItem.optJSONObject("content") ?: continue

            val video = innerContent
                .optJSONObject("compactVideoRenderer")
                ?: innerContent.optJSONObject("videoRenderer")
                ?: continue

            val videoId = video.optString("videoId", "") ?: ""
            if (videoId.isEmpty()) continue

            val title = extractTitle(video)
            val isLive = checkIsLive(video)

            Log.d(TAG, "Item[$i] $videoId title=\"${title.take(40)}\" isLive=$isLive")

            if (!isLive) continue

            val viewCount = extractViewCount(video)

            items.add(
                LiveStreamItem(
                    title = title,
                    url = "$BASE_URL/watch?v=$videoId",
                    viewCount = viewCount,
                    isLive = true,
                    isUpcoming = false
                )
            )
        }
        Log.i(TAG, "Total items passing filter: ${items.size}")
        return items
    }

    private fun extractTitle(video: JSONObject): String {
        return video
            .optJSONObject("title")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text", "Sem título")
            ?: "Sem título"
    }

    private fun checkIsLive(video: JSONObject): Boolean {
        val videoId = video.optString("videoId", "?")

        if (video.optBoolean("isLive", false)) {
            Log.d(TAG, "$videoId: isLive=true -> LIVE")
            return true
        }

        if (video.has("upcomingEventData")) {
            Log.d(TAG, "$videoId: has upcomingEventData -> NOT LIVE")
            return false
        }

        val overlays = video.optJSONArray("thumbnailOverlays")
        if (overlays != null) {
            for (i in 0 until overlays.length()) {
                val ts = overlays.optJSONObject(i)
                    ?.optJSONObject("thumbnailOverlayTimeStatusRenderer")
                val style = ts?.optString("style", "")
                if (style == "UPCOMING") {
                    Log.d(TAG, "$videoId: overlay style=UPCOMING -> NOT LIVE")
                    return false
                }
            }
        }

        val accessibilityLabel = video.optJSONObject("accessibility")
            ?.optJSONObject("accessibilityData")
            ?.optString("label", "")
        if (accessibilityLabel != null) {
            val waiting = accessibilityLabel.contains("esperando", ignoreCase = true) ||
                accessibilityLabel.contains("aguardando", ignoreCase = true) ||
                accessibilityLabel.contains("waiting", ignoreCase = true) ||
                accessibilityLabel.contains("scheduled", ignoreCase = true) ||
                accessibilityLabel.contains("estreia", ignoreCase = true) ||
                accessibilityLabel.contains("premiere", ignoreCase = true) ||
                accessibilityLabel.contains("em breve", ignoreCase = true)
            if (waiting) {
                Log.d(TAG, "$videoId: accessibility='${accessibilityLabel.take(60)}' -> NOT LIVE")
                return false
            }
        }

        val viewCountLabel = extractViewCountLabel(video)
        if (viewCountLabel != null) {
            val waiting = viewCountLabel.contains("esperando", ignoreCase = true) ||
                viewCountLabel.contains("aguardando", ignoreCase = true) ||
                viewCountLabel.contains("waiting", ignoreCase = true) ||
                viewCountLabel.contains("scheduled", ignoreCase = true) ||
                viewCountLabel.contains("estreia", ignoreCase = true) ||
                viewCountLabel.contains("premiere", ignoreCase = true)
            if (waiting) {
                Log.d(TAG, "$videoId: viewCount='$viewCountLabel' -> NOT LIVE (waiting)")
                return false
            }
        }

        val badges = video.optJSONArray("badges")
        if (badges != null) {
            for (i in 0 until badges.length()) {
                val label = badges.optJSONObject(i)
                    ?.optJSONObject("metadataBadgeRenderer")
                    ?.optString("label", "")
                if (label?.contains("AO VIVO", ignoreCase = true) == true) {
                    Log.d(TAG, "$videoId: badge 'AO VIVO' -> LIVE")
                    return true
                }
                if (label?.contains("LIVE", ignoreCase = true) == true) {
                    Log.d(TAG, "$videoId: badge 'LIVE' -> LIVE")
                    return true
                }
            }
        }

        if (overlays != null) {
            for (i in 0 until overlays.length()) {
                val ts = overlays.optJSONObject(i)
                    ?.optJSONObject("thumbnailOverlayTimeStatusRenderer")
                val text = ts
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs")
                    ?.optJSONObject(0)
                    ?.optString("text", "")
                if (text?.contains("LIVE", ignoreCase = true) == true) {
                    Log.d(TAG, "$videoId: overlay text 'LIVE' -> LIVE")
                    return true
                }
                if (text?.contains("AO VIVO", ignoreCase = true) == true) {
                    Log.d(TAG, "$videoId: overlay text 'AO VIVO' -> LIVE")
                    return true
                }
            }
        }

        Log.d(TAG, "$videoId: no live indicators -> NOT LIVE")
        return false
    }

    private fun extractViewCountLabel(video: JSONObject): String? {
        val viewCountText = video.optJSONObject("viewCountText")
        val simpleText = viewCountText?.optString("simpleText", null)
        if (simpleText != null) return simpleText

        val runs = viewCountText?.optJSONArray("runs")
        val text = if (runs != null && runs.length() > 0) {
            (0 until runs.length()).mapNotNull {
                runs.optJSONObject(it)?.optString("text", "")
            }.joinToString("")
        } else null
        if (text != null) return text

        val shortViewCountText = video.optJSONObject("shortViewCountText")
        val shortSimple = shortViewCountText?.optString("simpleText", null)
        if (shortSimple != null) return shortSimple

        val shortRuns = shortViewCountText?.optJSONArray("runs")
        if (shortRuns != null && shortRuns.length() > 0) {
            return (0 until shortRuns.length()).mapNotNull {
                shortRuns.optJSONObject(it)?.optString("text", "")
            }.joinToString("")
        }

        return null
    }

    private fun extractViewCount(video: JSONObject): Long {
        fun parseCount(text: String): Long {
            return text.filter { it.isDigit() || it == '.' }
                .let { cleaned ->
                    if (cleaned.contains('.')) {
                        val parts = cleaned.split('.')
                        val base = parts[0].toLongOrNull() ?: return 0
                        if (text.contains("mil", ignoreCase = true)) base * 1000
                        else if (text.contains("mi", ignoreCase = true)) base * 1_000_000
                        else base
                    } else {
                        cleaned.toLongOrNull() ?: 0
                    }
                }
        }

        val viewCountText = video.optJSONObject("viewCountText")
        val simpleText = viewCountText?.optString("simpleText", "")
        if (simpleText != null && simpleText.isNotEmpty()) return parseCount(simpleText)

        val runs = viewCountText?.optJSONArray("runs")
        if (runs != null && runs.length() > 0) {
            return parseCount(runs.optJSONObject(0)?.optString("text", "") ?: "")
        }

        val shortViewCountText = video.optJSONObject("shortViewCountText")
        val shortSimple = shortViewCountText?.optString("simpleText", "")
        if (shortSimple != null && shortSimple.isNotEmpty()) return parseCount(shortSimple)

        val shortRuns = shortViewCountText?.optJSONArray("runs")
        if (shortRuns != null && shortRuns.length() > 0) {
            return parseCount(shortRuns.optJSONObject(0)?.optString("text", "") ?: "")
        }

        return 0
    }
}
