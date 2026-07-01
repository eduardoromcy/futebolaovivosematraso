package com.e2dsys.futebolaovivosematraso

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PlayerActivity : AppCompatActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val speedCheckRunnable = object : Runnable {
        override fun run() {
            val wv = findViewById<WebView>(R.id.youtubeWebView)
            if (wv != null) {
                injectCatchUpJs(wv)
                mainHandler.postDelayed(this, 4000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val youtubeUrl = intent.getStringExtra("video_url") ?: run {
            Toast.makeText(this, "URL não fornecida", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val webView = findViewById<WebView>(R.id.youtubeWebView)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        setupWebView(webView, progressBar, youtubeUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(webView: WebView, progressBar: ProgressBar, url: String) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            allowContentAccess = true
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.165 Safari/537.36"
        }

        webView.addJavascriptInterface(JsBridge(), "AndroidBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                mainHandler.postDelayed({
                    startCatchUp()
                }, 4000)
            }
        }

        webView.loadUrl(url)
    }

    private fun startCatchUp() {
        mainHandler.post(speedCheckRunnable)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun injectCatchUpJs(webView: WebView) {
        val js = """
            (function() {
                var video = document.querySelector('video');
                if (!video || !video.seekable || video.seekable.length === 0) return;
                
                try {
                    var liveEnd = video.seekable.end(video.seekable.length - 1);
                    var delay = liveEnd - video.currentTime;
                    if (delay < 0) delay = 0;
                    
                    var speed = 1.0;
                    if (delay > 60) speed = 2.0;
                    else if (delay > 30) speed = 1.5;
                    else if (delay > 10) speed = 1.25;
                    
                    if (speed > 1.0) {
                        video.playbackRate = speed;
                        AndroidBridge.onSpeedChange(Number(delay.toFixed(0)), Number(speed));
                    } else {
                        video.playbackRate = 1.0;
                        AndroidBridge.onSpeedChange(Number(delay.toFixed(0)), 1.0);
                    }
                } catch(e) {
                    AndroidBridge.onError('' + e);
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    inner class JsBridge {
        @JavascriptInterface
        fun onSpeedChange(delaySec: Int, speed: Double) {
            mainHandler.post {
                val banner = findViewById<TextView>(R.id.speedBanner)
                if (banner != null) {
                    if (speed > 1.0) {
                        banner.text = "⚡ PEGANDO ATRASO • ${speed}x • atraso ${delaySec}s"
                        banner.visibility = View.VISIBLE
                    } else if (delaySec <= 10) {
                        banner.text = "✅ AO VIVO • atraso ${delaySec}s"
                        banner.visibility = View.VISIBLE
                        mainHandler.postDelayed({ banner.visibility = View.GONE }, 8000)
                    }
                }
            }
        }

        @JavascriptInterface
        fun onError(msg: String) {
            android.util.Log.w("PlayerActivity", "JS error: $msg")
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(speedCheckRunnable)
        super.onDestroy()
    }

    override fun onBackPressed() {
        mainHandler.removeCallbacks(speedCheckRunnable)
        val webView = findViewById<WebView>(R.id.youtubeWebView)
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
