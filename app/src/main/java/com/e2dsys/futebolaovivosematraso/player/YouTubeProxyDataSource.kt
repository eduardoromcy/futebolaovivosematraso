package com.e2dsys.futebolaovivosematraso.player

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream

class YouTubeProxyDataSource(private val client: OkHttpClient) : DataSource {

    private var currentResponse: okhttp3.Response? = null
    private var currentStream: InputStream? = null
    private var fileDataSource: FileDataSource? = null

    override fun open(dataSpec: DataSpec): Long {
        val scheme = dataSpec.uri.scheme ?: ""
        if (scheme == "file") {
            val fd = FileDataSource()
            fileDataSource = fd
            return fd.open(dataSpec)
        }

        if (scheme != "http" && scheme != "https") {
            throw IOException("Unsupported URI scheme: $scheme")
        }

        val url = dataSpec.uri.toString()
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.165 Mobile Safari/537.36")
            .header("Referer", "https://www.youtube.com")
            .header("Accept", "*/*")
            .header("Accept-Language", "pt-BR,pt;q=0.9")
            .header("Origin", "https://www.youtube.com")

        if (dataSpec.position != 0L || dataSpec.length != -1L) {
            val rangeEnd = if (dataSpec.length != -1L) dataSpec.position + dataSpec.length - 1 else ""
            builder.header("Range", "bytes=${dataSpec.position}-$rangeEnd")
        }

        val response = client.newCall(builder.build()).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code} for ${url.take(100)}")
        }

        currentResponse = response
        currentStream = response.body?.byteStream()
        return response.body?.contentLength() ?: -1L
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        fileDataSource?.let { return it.read(buffer, offset, length) }
        return currentStream?.read(buffer, offset, length) ?: -1
    }

    override fun getUri(): Uri? {
        fileDataSource?.let { return it.uri }
        return currentResponse?.request?.url?.let { Uri.parse(it.toString()) }
    }

    override fun close() {
        fileDataSource?.close()
        fileDataSource = null
        currentStream?.close()
        currentResponse?.close()
        currentStream = null
        currentResponse = null
    }

    override fun addTransferListener(transferListener: TransferListener) {}

    class Factory(private val client: OkHttpClient) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return YouTubeProxyDataSource(client)
        }
    }
}
