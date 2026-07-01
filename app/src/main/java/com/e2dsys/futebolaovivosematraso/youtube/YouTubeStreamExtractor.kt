package com.e2dsys.futebolaovivosematraso.youtube

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object YouTubeStreamExtractor {

    private const val TAG = "StreamExtractor"
    private const val INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val INNERTUBE_URL = "https://www.youtube.com/youtubei/v1/player?key=$INNERTUBE_API_KEY"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun extractStreamUrl(videoUrl: String, cacheDir: java.io.File? = null): String? {
        val videoId = extractVideoId(videoUrl) ?: run {
            Log.e(TAG, "Failed to extract videoId from: $videoUrl")
            return null
        }
        return fetchStreamUrl(videoId, cacheDir)
    }

    private fun extractVideoId(url: String): String? {
        val watchMatch = Regex("""[?&]v=([^&]+)""").find(url)
        if (watchMatch != null) return watchMatch.groupValues[1]

        val shortMatch = Regex("""youtu\.be/([^?&]+)""").find(url)
        if (shortMatch != null) return shortMatch.groupValues[1]

        return null
    }

    private val consentCookie = "CONSENT=YES+cb.20210301-17-p0.en+FX+700; SOCS=CAISEwgDEgk1Nzk3NjE5NTQaAmVuIAEaBgiA1YCwBg"

    private fun fetchStreamUrl(videoId: String, cacheDir: java.io.File? = null): String? {
        val fromPage = tryExtractFromWatchPage(videoId, cacheDir)
        if (fromPage != null) return fromPage
        return tryInnerTube(videoId)
    }

    private fun tryExtractFromWatchPage(videoId: String, cacheDir: java.io.File? = null): String? {
        return try {
            val url = "https://www.youtube.com/watch?v=$videoId&hl=pt&gl=BR"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.165 Mobile Safari/537.36")
                .header("Accept-Language", "pt-BR,pt;q=0.9")
                .header("Cookie", consentCookie)
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return null

            val match = Regex("""ytInitialPlayerResponse\s*=\s*(\{.+?\})\s*;</script>""",
                RegexOption.DOT_MATCHES_ALL).find(html)
            if (match != null) {
                val json = JSONObject(match.groupValues[1])
                val sd = json.optJSONObject("streamingData")
                if (sd != null) {
                    val streamUrl = extractStreamFromJson(sd, videoId, cacheDir)
                    if (streamUrl != null) {
                        return streamUrl
                    }
                } else {
                    val ps = json.optJSONObject("playabilityStatus")
                    Log.d(TAG, "Watch page: status=${ps?.optString("status")} reason=${ps?.optString("reason")}")
                }
            }

            null
        } catch (e: Exception) {
            Log.w(TAG, "Watch page extraction failed: ${e.message}")
            null
        }
    }

    private fun testUrlWithOkHttp(variantUrl: String) {
        try {
            val testClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()

            fun reqBuilder(url: String): Request.Builder {
                val b = Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .header("Referer", "https://www.youtube.com")
                    .header("Cookie", consentCookie)
                return b
            }

            val getReq = reqBuilder(variantUrl).build()
            testClient.newCall(getReq).execute().use { r ->
                if (r.code != 200) return@use
                val body = r.body?.string() ?: return@use
                Log.i(TAG, "Variant manifest (${body.length} chars)")

                val subUrl = body.lines().firstOrNull { line ->
                    line.startsWith("http") && !line.startsWith("#")
                } ?: return@use

                val subReq = reqBuilder(subUrl).build()
                testClient.newCall(subReq).execute().use { sr ->
                    if (sr.code != 200) return@use
                    val subBody = sr.body?.string() ?: return@use
                    Log.i(TAG, "Sub-playlist (${subBody.length} chars):\n${subBody.take(800)}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "OkHttp test failed: ${e.message}")
        }
    }

    private fun extractStreamFromJson(sd: JSONObject, videoId: String, cacheDir: java.io.File? = null): String? {
        // Try HLS manifest (most reliable for live streams)
        val hls = sd.optString("hlsManifestUrl", "").takeIf { it.isNotEmpty() }
        if (hls != null && cacheDir != null) {
            Log.d(TAG, "HLS manifest found, downloading sub-playlist locally")
            val localPlaylist = downloadAndFixHlsPlaylist(hls, cacheDir)
            if (localPlaylist != null) return localPlaylist
        }

        // Fall back to direct progressive URL from formats
        val formats = sd.optJSONArray("formats")
        if (formats != null) {
            for (i in 0 until formats.length()) {
                val fmt = formats.optJSONObject(i) ?: continue
                val url = fmt.optString("url", "").takeIf { it.isNotEmpty() }
                if (url != null) {
                    Log.d(TAG, "Direct format: itag=${fmt.optInt("itag")}")
                    return url
                }
            }
        }

        val adaptive = sd.optJSONArray("adaptiveFormats")
        if (adaptive != null) {
            for (i in 0 until adaptive.length()) {
                val fmt = adaptive.optJSONObject(i) ?: continue
                val url = fmt.optString("url", "").takeIf { it.isNotEmpty() }
                if (url != null) {
                    Log.d(TAG, "Direct adaptive format: itag=${fmt.optInt("itag")}")
                    return url
                }
            }
        }

        return if (hls != null) {
            Log.w(TAG, "Falling back to raw HLS URL (no cache dir)")
            hls
        } else null
    }

    private fun downloadAndFixHlsPlaylist(hlsUrl: String, cacheDir: java.io.File): String? {
        return try {
            val req = Request.Builder()
                .url(hlsUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .header("Referer", "https://www.youtube.com")
                .header("Cookie", consentCookie)
                .build()

            val resp = client.newCall(req).execute()
            val variantManifest = resp.body?.string() ?: return null

            val subUrl = variantManifest.lines().firstOrNull { line ->
                line.startsWith("http") && !line.startsWith("#")
            } ?: return null

            val subReq = Request.Builder()
                .url(subUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .header("Referer", "https://www.youtube.com")
                .header("Cookie", consentCookie)
                .build()

            val subResp = client.newCall(subReq).execute()
            val subContent = subResp.body?.string() ?: return null

            val playlistFile = java.io.File(cacheDir, "live_${System.currentTimeMillis()}.m3u8")
            playlistFile.writeText(subContent)
            Log.i(TAG, "Local playlist: ${playlistFile.name} (${subContent.lines().count { it.startsWith("http") }} seg URLs)")

            playlistFile.toURI().toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download HLS playlist: ${e.message}")
            null
        }
    }

    private fun tryInnerTube(videoId: String): String? {
        val clients = listOf(
            mapOf(
                "clientName" to "WEB",
                "clientVersion" to "2.20260629.05.00",
                "hl" to "pt",
                "gl" to "BR"
            ),
            mapOf(
                "clientName" to "ANDROID",
                "clientVersion" to "19.09.37",
                "androidSdkVersion" to 30,
                "hl" to "pt",
                "gl" to "BR"
            ),
            mapOf(
                "clientName" to "TVHTML5",
                "clientVersion" to "7.20240101",
                "hl" to "pt",
                "gl" to "BR"
            ),
            mapOf(
                "clientName" to "MWEB",
                "clientVersion" to "2.20260629",
                "hl" to "pt",
                "gl" to "BR"
            )
        )

        for (clientConfig in clients) {
            try {
                val clientObj = JSONObject()
                for ((k, v) in clientConfig) {
                    when (v) {
                        is String -> clientObj.put(k, v)
                        is Int -> clientObj.put(k, v)
                    }
                }

                val bodyJson = JSONObject().apply {
                    put("videoId", videoId)
                    put("context", JSONObject().apply {
                        put("client", clientObj)
                    })
                    put("racyCheckOk", true)
                    put("contentCheckOk", true)
                    put("playbackContext", JSONObject().apply {
                        put("contentPlaybackContext", JSONObject().apply {
                            put("html5Preference", "HTML5_PREF_WANTS")
                        })
                    })
                }

                val request = Request.Builder()
                    .url(INNERTUBE_URL)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.165 Mobile Safari/537.36")
                    .header("Content-Type", "application/json")
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .header("Cookie", consentCookie)
                    .header("Origin", "https://www.youtube.com")
                    .header("Referer", "https://www.youtube.com/watch?v=$videoId")
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    Log.w(TAG, "${clientConfig["clientName"]} returned ${response.code}: ${body.take(200)}")
                    continue
                }

                val json = JSONObject(body)
                val ps = json.optJSONObject("playabilityStatus")
                val status = ps?.optString("status", "") ?: ""
                val reason = ps?.optString("reason", "") ?: ""
                Log.d(TAG, "${clientConfig["clientName"]} status=$status reason=$reason")

                if (status != "OK" && status != "LIVE_STREAM_OFFLINE") {
                    Log.w(TAG, "${clientConfig["clientName"]} skipped: $status")
                    continue
                }

                if (status == "LIVE_STREAM_OFFLINE") {
                    Log.d(TAG, "${clientConfig["clientName"]} live stream offline (not started yet)")
                    continue
                }

                val url = extractStreamFromResponse(json)
                if (url != null) {
                    Log.i(TAG, "Stream URL found via ${clientConfig["clientName"]}: ${url.take(80)}...")
                    return url
                }

                Log.w(TAG, "${clientConfig["clientName"]} no stream URL in response")
            } catch (e: Exception) {
                Log.e(TAG, "${clientConfig["clientName"]} error: ${e.message}")
            }
        }
        return null
    }

    private fun extractStreamFromResponse(json: JSONObject): String? {
        val streamingData = json.optJSONObject("streamingData") ?: return null

        streamingData.optString("hlsManifestUrl", "").takeIf { it.isNotEmpty() }?.let { return it }

        val formats = streamingData.optJSONArray("formats")
        val url = findFirstUrl(formats)
        if (url != null) return url

        val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
        return findFirstUrl(adaptiveFormats)
    }

    private fun findFirstUrl(formats: JSONArray?): String? {
        if (formats == null) return null
        for (i in 0 until formats.length()) {
            val fmt = formats.optJSONObject(i) ?: continue

            val url = fmt.optString("url", "").takeIf { it.isNotEmpty() }
            if (url != null) {
                Log.d(TAG, "found direct url (itag=${fmt.optInt("itag")}, mime=${fmt.optString("mimeType", "").take(30)})")
                return url
            }

            val cipher = fmt.optString("cipher", "").takeIf { it.isNotEmpty() }
            if (cipher != null) {
                Log.w(TAG, "found cipher URL (no signature decryption available, skipping)")
                return null
            }

            val sigCipher = fmt.optString("signatureCipher", "").takeIf { it.isNotEmpty() }
            if (sigCipher != null) {
                Log.w(TAG, "found signatureCipher URL (no signature decryption available, skipping)")
                return null
            }
        }
        return null
    }

    private fun decodeCipher(cipher: String): String? {
        val params = cipher.split("&").mapNotNull { part ->
            val eq = part.indexOf("=")
            if (eq < 0) null else part.substring(0, eq) to part.substring(eq + 1)
        }.toMap()

        val url = params["url"] ?: return null
        val s = params["s"]
        val sp = params.getOrDefault("sp", "signature")

        return if (s != null) "$url&$sp=$s" else url
    }
}
