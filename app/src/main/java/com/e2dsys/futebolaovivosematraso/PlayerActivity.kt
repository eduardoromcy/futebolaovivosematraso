package com.e2dsys.futebolaovivosematraso

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val MODE_GENTLE = 1
        private const val MODE_BALANCED = 2
        private const val MODE_AGGRESSIVE = 3
        private const val JS_INTERVAL_MS = 250L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var engineRunning = false
    private var currentMode = MODE_AGGRESSIVE
    private var webViewGone = false
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val engineRunnable = object : Runnable {
        override fun run() {
            if (!engineRunning || webViewGone) return
            val wv = findViewById<WebView>(R.id.youtubeWebView) ?: return
            wv.evaluateJavascript("if(window.__zeroDelay) window.__zeroDelay.tick();", null)
            mainHandler.postDelayed(this, JS_INTERVAL_MS)
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

        setupModeButtons()
        setupWebView(webView, progressBar, youtubeUrl)

        val wm = findViewById<ImageView>(R.id.watermark)
        val disp = resources.displayMetrics
        wm.layoutParams = wm.layoutParams.apply {
            width = (disp.widthPixels * 0.22).toInt()
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        wm.visibility = View.VISIBLE
        wm.setOnClickListener { showContactDialog() }
    }

    private fun setupModeButtons() {
        val btns = listOf(
            findViewById<Button>(R.id.modeGentle) to MODE_GENTLE,
            findViewById<Button>(R.id.modeBalanced) to MODE_BALANCED,
            findViewById<Button>(R.id.modeAggressive) to MODE_AGGRESSIVE,
        )
        btns.forEach { (btn, mode) ->
            btn.isSelected = mode == currentMode
            btn.setOnClickListener {
                currentMode = mode
                btns.forEach { (b, m) -> b.isSelected = m == mode }
                switchMode(mode)
            }
        }
        findViewById<Button>(R.id.fullscreenButton).setOnClickListener {
            val wv = findViewById<WebView>(R.id.youtubeWebView) ?: return@setOnClickListener
            wv.evaluateJavascript(
                "(function(){var b=document.querySelector('.ytp-fullscreen-button');if(b)b.click();})()",
                null
            )
        }
    }

    private fun switchMode(mode: Int) {
        val webView = findViewById<WebView>(R.id.youtubeWebView) ?: return
        webView.evaluateJavascript(
            "if(window.__zeroDelay) { window.__zeroDelay.mode = $mode; }",
            null
        )
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
            allowFileAccess = true
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.165 Safari/537.36"
        }

        webView.addJavascriptInterface(JsBridge(), "ZeroDelayBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                val container = findViewById<ViewGroup>(android.R.id.content)
                container.addView(view, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
                findViewById<View>(R.id.topOverlay).visibility = View.GONE
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            }

            override fun onHideCustomView() {
                hideCustomView()
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                if (!webViewGone) injectEngineAndStart(view)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                webViewGone = false
            }
        }

        webView.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun injectEngineAndStart(view: WebView?) {
        val wv = view ?: return
        val js = """
            (function() {
                if (window.__zeroDelayInjected) return;
                window.__zeroDelayInjected = true;

                var ZD = {
                    player: null,
                    caps: null,
                    tickErrors: 0,
                    lastSpeed: 1.0,
                    lastLatency: 0,
                    lastHealth: 0,
                    isAtLiveHead: false,
                    seekableEnd: 0,
                    currentTime: 0,
                    mode: $currentMode,
                    
                    getModeSpeed: function(mode) {
                        if (mode === 1) return 1.1;
                        if (mode === 3) return 1.5;
                        return 1.25;
                    },
                    
                    getModeLabel: function(mode) {
                        if (mode === 1) return "Suave";
                        if (mode === 3) return "Agressivo";
                        return "Equilibrado";
                    },
                    
                    getBufferThreshold: function() {
                        var seg = 5.0;
                        try {
                            var stats = ZD.player.getVideoStats ? ZD.player.getVideoStats() : null;
                            if (stats && stats.segduration) seg = stats.segduration;
                            else {
                                var resp = ZD.player.getPlayerResponse ? ZD.player.getPlayerResponse() : null;
                                if (resp && resp.videoDetails) {
                                    var lc = resp.videoDetails.latencyClass;
                                    if (lc === 'MDE_STREAM_OPTIMIZATIONS_RENDERER_LATENCY_ULTRA_LOW') seg = 1;
                                    else if (lc === 'MDE_STREAM_OPTIMIZATIONS_RENDERER_LATENCY_LOW') seg = 2;
                                }
                            }
                        } catch(e) {}
                        return Math.max(2.0, Math.min(6.0, seg * 2));
                    },
                    
                    findPlayer: function() {
                        try { return document.getElementById('movie_player'); } catch(e) { return null; }
                    },
                    
                    probeCaps: function(p) {
                        if (!p) return null;
                        try {
                            return {
                                stats: typeof p.getStatsForNerds === 'function',
                                progress: typeof p.getProgressState === 'function',
                                setRate: typeof p.setPlaybackRate === 'function',
                                getRate: typeof p.getPlaybackRate === 'function',
                                seekLive: typeof p.seekToLiveHead === 'function',
                                playVideo: typeof p.playVideo === 'function'
                            };
                        } catch(e) { return null; }
                    },
                    
                    setSpeed: function(rate) {
                        try {
                            if (ZD.caps && ZD.caps.setRate && ZD.caps.getRate) {
                                var cur = ZD.player.getPlaybackRate();
                                if (Math.abs(rate - cur) > 0.01) {
                                    ZD.player.setPlaybackRate(rate);
                                }
                            }
                        } catch(e) { /* fail silently */ }
                    },
                    
                    seekToLive: function() {
                        try {
                            if (ZD.caps && ZD.caps.seekLive) {
                                ZD.player.seekToLiveHead();
                                if (ZD.caps.playVideo) ZD.player.playVideo();
                            }
                        } catch(e) { /* fail silently */ }
                    },
                    
                    tick: function() {
                        try {
                            if (!ZD.player) {
                                ZD.player = ZD.findPlayer();
                                if (!ZD.player) return;
                                ZD.caps = ZD.probeCaps(ZD.player);
                                if (!ZD.caps) return;
                            }
                            if (!ZD.caps.stats) return;
                            
                            var stats = ZD.player.getStatsForNerds();
                            if (!stats || stats.live_latency_style !== '') return;
                            
                            ZD.lastLatency = parseFloat(stats.live_latency_secs) || 0;
                            ZD.lastHealth = parseFloat(stats.buffer_health_seconds) || 0;
                            
                            if (ZD.caps.progress) {
                                var ps = ZD.player.getProgressState();
                                if (ps) {
                                    ZD.isAtLiveHead = !!ps.isAtLiveHead;
                                    ZD.seekableEnd = ps.seekableEnd || 0;
                                    ZD.currentTime = ps.current || 0;
                                }
                            }
                            
                            // Buffer-based catch-up (ignores isAtLiveHead, matches extension's smooth mode)
                            var maxSpeed = ZD.getModeSpeed(ZD.mode);
                            var threshold = ZD.getBufferThreshold();
                            var desired = 1.0;
                            
                            if (ZD.lastHealth >= threshold) {
                                desired = maxSpeed;
                            }
                            
                            ZD.setSpeed(desired);
                            ZD.lastSpeed = desired;
                            
                            // Safety net: if delay exceeds 30s, seek to live once
                            if (ZD.seekableEnd > 0 && ZD.currentTime > 0) {
                                var delay = ZD.seekableEnd - ZD.currentTime;
                                if (delay > 30) {
                                    ZD.seekToLive();
                                }
                            }
                            
                            var status = 'synced';
                            var estimatedSecs = 0;
                            if (desired > 1.01) {
                                status = 'catching_up';
                                if (ZD.seekableEnd > 0 && ZD.currentTime > 0) {
                                    var delay = ZD.seekableEnd - ZD.currentTime;
                                    estimatedSecs = Math.round(delay / (desired - 1.0));
                                }
                            } else if (ZD.lastHealth < threshold) {
                                status = 'waiting';
                            }
                            
                            ZeroDelayBridge.onTick(
                                ZD.lastSpeed,
                                ZD.lastLatency,
                                ZD.lastHealth,
                                ZD.isAtLiveHead,
                                ZD.mode,
                                ZD.getModeLabel(ZD.mode),
                                status,
                                estimatedSecs
                            );
                        } catch(e) {
                            ZD.tickErrors++;
                            if (ZD.tickErrors > 50) {
                                ZD.player = null;
                                ZD.tickErrors = 0;
                            }
                        }
                    }
                };
                
                window.__zeroDelay = ZD;

                var detectInterval = setInterval(function() {
                    var p = ZD.findPlayer();
                    if (p) {
                        ZD.player = p;
                        ZD.caps = ZD.probeCaps(p);
                        if (ZD.caps) {
                            clearInterval(detectInterval);
                            ZeroDelayBridge.onReady();
                        }
                    }
                }, 500);
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
    }

    inner class JsBridge {
        @JavascriptInterface
        fun onReady() {
            if (webViewGone) return
            mainHandler.post {
                if (!engineRunning && !webViewGone) {
                    engineRunning = true
                    mainHandler.post(engineRunnable)
                }
            }
        }

        @JavascriptInterface
        fun onTick(speed: Double, latency: Double, health: Double, isAtLiveHead: Boolean, mode: Int, modeLabel: String, status: String, estimatedSecs: Double) {
            if (webViewGone) return
            mainHandler.post {
                val banner = findViewById<TextView>(R.id.speedBanner) ?: return@post
                val actualSpeed = Math.round(speed * 100.0) / 100.0
                val actualLatency = Math.round(latency)
                val actualHealth = Math.round(health * 10.0) / 10.0

                val displaySpeed = if (actualSpeed <= 1.01) "1.0x" else "${actualSpeed}x"

                when (status) {
                    "catching_up" -> {
                        val eta = if (estimatedSecs > 0) " • chegando ~${estimatedSecs}s" else ""
                        banner.text = "⚡ ACELERANDO • ${displaySpeed} • atraso ${actualLatency}s$eta"
                        banner.setBackgroundColor(0xCCFF6600.toInt())
                    }
                    "synced" -> {
                        banner.text = "✅ AO VIVO • $modeLabel • ${displaySpeed} • atraso ${actualLatency}s"
                        banner.setBackgroundColor(0xCC00AA00.toInt())
                    }
                    else -> {
                        banner.text = "⏳ AGUARDANDO • $modeLabel • buffer ${actualHealth}s • atraso ${actualLatency}s"
                        banner.setBackgroundColor(0xCC000000.toInt())
                    }
                }
                banner.visibility = View.VISIBLE
            }
        }

        @JavascriptInterface
        fun setMode(mode: Int) {
            if (webViewGone) return
            switchMode(mode)
        }
    }

    override fun onPause() {
        super.onPause()
        if (customView != null) hideCustomView()
        engineRunning = false
        mainHandler.removeCallbacks(engineRunnable)
        findViewById<WebView>(R.id.youtubeWebView)?.apply {
            evaluateJavascript(
                "if(window.__zeroDelay) { window.__zeroDelay.setSpeed(1.0); }",
                null
            )
            onPause()
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<WebView>(R.id.youtubeWebView)?.onResume()
        if (!webViewGone && !engineRunning) {
            engineRunning = true
            mainHandler.post(engineRunnable)
        }
    }

    override fun onDestroy() {
        webViewGone = true
        engineRunning = false
        mainHandler.removeCallbacksAndMessages(null)
        findViewById<WebView>(R.id.youtubeWebView)?.destroy()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (customView != null) {
            hideCustomView()
            return
        }
        webViewGone = true
        engineRunning = false
        mainHandler.removeCallbacksAndMessages(null)
        super.onBackPressed()
    }

    private fun hideCustomView() {
        customView?.let { findViewById<ViewGroup>(android.R.id.content)?.removeView(it) }
        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
        findViewById<View>(R.id.topOverlay).visibility = View.VISIBLE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun showContactDialog() {
        AlertDialog.Builder(this)
            .setTitle("Contato")
            .setMessage("Como deseja entrar em contato?")
            .setPositiveButton("Instagram") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/eduardoromcy")))
            }
            .setNegativeButton("WhatsApp") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/5585996759645")))
            }
            .setNeutralButton("Fechar", null)
            .show()
    }
}
