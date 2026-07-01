package com.e2dsys.futebolaovivosematraso.youtube

import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response as NewPipeResponse
import java.util.concurrent.TimeUnit

class NewPipeDownloader : Downloader() {

    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: NewPipeRequest): NewPipeResponse {
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val body = dataToSend?.toRequestBody()

        val okRequest = OkHttpRequest.Builder()
            .url(url)
            .apply {
                headers?.forEach { (key, values) ->
                    values.forEach { addHeader(key, it) }
                }
            }
            .apply {
                when (request.httpMethod().uppercase()) {
                    "POST" -> post(body!!)
                    "HEAD" -> head()
                    else -> get()
                }
            }
            .build()

        val response = client.newCall(okRequest).execute()

        val responseHeaders = mutableMapOf<String, MutableList<String>>()
        for (i in 0 until response.headers.size) {
            val name = response.headers.name(i)
            val value = response.headers.value(i)
            responseHeaders.getOrPut(name) { mutableListOf() }.add(value)
        }

        return NewPipeResponse(
            response.code,
            response.message,
            responseHeaders,
            response.body?.string() ?: "",
            url
        )
    }
}
