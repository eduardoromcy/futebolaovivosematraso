package com.e2dsys.futebolaovivosematraso.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.OkHttpClient

@UnstableApi
class ZeroDelayPlayer(context: Context, playerView: PlayerView) {

    private val player: ExoPlayer
    private val dataSourceFactory: DataSource.Factory

    init {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(500, 3000, 500, 500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val okHttpClient = OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<Cookie>) {}
                override fun loadForRequest(url: okhttp3.HttpUrl): List<Cookie> {
                    if (url.host.contains("youtube.com") || url.host.contains("googlevideo.com")) {
                        val consent = Cookie.Builder()
                            .domain(url.host)
                            .name("CONSENT")
                            .value("YES+cb.20210301-17-p0.en+FX+700")
                            .build()
                        val socs = Cookie.Builder()
                            .domain(url.host)
                            .name("SOCS")
                            .value("CAISEwgDEgk1Nzk3NjE5NTQaAmVuIAEaBgiA1YCwBg")
                            .build()
                        return listOf(consent, socs)
                    }
                    return emptyList()
                }
            })
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                if (!response.isSuccessful) {
                    val fullUrl = request.url.toString()
                    android.util.Log.w("ZeroDelayPlayer", "HTTP ${response.code} for ${fullUrl.take(200)}")
                } else {
                    android.util.Log.d("ZeroDelayPlayer", "HTTP ${response.code} for ${request.url.encodedPath.take(60)}")
                }
                response
            }
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.165 Mobile Safari/537.36")
                    .header("Referer", "https://www.youtube.com")
                    .header("Accept", "*/*")
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .header("Origin", "https://www.youtube.com")
                    .build()
                chain.proceed(request)
            }
            .build()

        dataSourceFactory = YouTubeProxyDataSource.Factory(okHttpClient)

        player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .also { exo ->
                playerView.player = exo
                exo.playWhenReady = true
                exo.repeatMode = Player.REPEAT_MODE_ALL
            }
    }

    fun play(streamUrl: String) {
        android.util.Log.d("ZeroDelayPlayer", "Playing URL: ${streamUrl.take(200)}")
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(500)
                    .setMinOffsetMs(300)
                    .setMaxOffsetMs(2000)
                    .setMaxPlaybackSpeed(1.05f)
                    .setMinPlaybackSpeed(0.95f)
                    .build()
            )
            .build()

        val source = if (streamUrl.contains("m3u8") || streamUrl.contains("hls")) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }

        player.setMediaSource(source)
        player.prepare()
    }

    fun release() {
        player.release()
    }
}
