package com.e2dsys.futebolaovivosematraso

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val MODE_GENTLE = 1
        private const val MODE_BALANCED = 2
        private const val MODE_AGGRESSIVE = 3
        private const val JS_INTERVAL_MS = 500L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var engineRunning = false
    private var currentMode = MODE_BALANCED
    private var webViewGone = false

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
    }

    private fun switchMode(mode: Int) {
        val webView = findViewById<WebView>(R.id.youtubeWebView) ?: return
        webView.evaluateJavascript(
            "if(window.__zeroDelay) { window.__zeroDelay.mode = $mode; window.__zeroDelay.bufferEma = null; }",
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
                    appliedRate: 1.0,
                    yieldedToUser: false,
                    bufferEma: null,
                    catchingUp: false,
                    tickErrors: 0,
                    lastSpeed: 1.0,
                    lastLatency: 0,
                    lastHealth: 0,
                    seekableEnd: 0,
                    currentTime: 0,
                    isAtLiveHead: false,
                    mode: $currentMode,
                    
                    WARN_BUFFER: 2.5,
                    BUFFER_FLOOR: 1.5,
                    BUFFER_BACKOFF: 2.5,
                    BUFFER_RESUME: 4.0,
                    CATCH_UP_BAND: 1.5,
                    MIN_LATENCY: 2.0,
                    
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
                    
                    getSkipThreshold: function(mode) {
                        if (mode === 1) return 60;
                        if (mode === 3) return 15;
                        return 30;
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
                                playVideo: typeof p.playVideo === 'function',
                                stateObject: typeof p.getPlayerStateObject === 'function'
                            };
                        } catch(e) { return null; }
                    },
                    
                    applyPlaybackRate: function(desired) {
                        try {
                            if (!ZD.player || !ZD.caps || !ZD.caps.setRate || !ZD.caps.getRate) return;
                            var cur = ZD.player.getPlaybackRate();
                            if (Math.abs(cur - ZD.appliedRate) > 0.01) {
                                if (Math.abs(cur - 1.0) < 0.01) {
                                    ZD.appliedRate = 1.0;
                                    ZD.yieldedToUser = false;
                                } else {
                                    ZD.yieldedToUser = true;
                                    ZD.appliedRate = cur;
                                }
                            }
                            if (ZD.yieldedToUser) return;
                            if (Math.abs(desired - ZD.appliedRate) > 0.01) {
                                ZD.player.setPlaybackRate(desired);
                                ZD.appliedRate = desired;
                            }
                        } catch(e) { /* fail silently */ }
                    },
                    
                    skipIfOverThreshold: function(latency) {
                        try {
                            if (!ZD.caps || !ZD.caps.seekLive || !ZD.caps.stateObject) return;
                            var threshold = ZD.getSkipThreshold(ZD.mode);
                            if (ZD.player && latency >= threshold) {
                                var state = ZD.player.getPlayerStateObject();
                                if (state && state.isPlaying) {
                                    ZD.player.seekToLiveHead();
                                    if (ZD.caps.playVideo) ZD.player.playVideo();
                                    ZD.bufferEma = null;
                                    ZD.catchingUp = false;
                                }
                            }
                        } catch(e) { /* fail silently */ }
                    },
                    
                    calcPlaybackRate: function(speed, latency, health) {
                        if (!isFinite(health) || !isFinite(latency)) return 1.0;
                        ZD.bufferEma = ZD.bufferEma === null ? health : ZD.bufferEma * 0.9 + health * 0.1;
                        if (latency < ZD.MIN_LATENCY) return 1.0;
                        var target = 6.0;
                        if (ZD.bufferEma > target + ZD.CATCH_UP_BAND) ZD.catchingUp = true;
                        else if (ZD.bufferEma <= target) ZD.catchingUp = false;
                        if (!ZD.catchingUp) return 1.0;
                        if (health < ZD.BUFFER_FLOOR) return 1.0;
                        return speed;
                    },
                    
                    liveHoldTick: 0,
                    
                    forceSeekToLive: function() {
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
                            
                            if (ZD.caps.setRate && ZD.caps.getRate) {
                                var speed = ZD.getModeSpeed(ZD.mode);
                                var desired = ZD.calcPlaybackRate(speed, ZD.lastLatency, ZD.lastHealth);
                                ZD.applyPlaybackRate(desired);
                                ZD.lastSpeed = desired;
                            }
                            
                            ZD.skipIfOverThreshold(ZD.lastLatency);
                            
                            // Re-seek to live every ~5s (10 ticks) to keep the AO VIVO badge active
                            ZD.liveHoldTick++;
                            if (ZD.liveHoldTick >= 10) {
                                ZD.liveHoldTick = 0;
                                if (!ZD.isAtLiveHead && ZD.lastLatency > 5) {
                                    ZD.forceSeekToLive();
                                }
                            }
                            
                            ZeroDelayBridge.onTick(
                                ZD.lastSpeed,
                                ZD.lastLatency,
                                ZD.lastHealth,
                                ZD.isAtLiveHead,
                                ZD.mode,
                                ZD.getModeLabel(ZD.mode)
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
        fun onTick(speed: Double, latency: Double, health: Double, isAtLiveHead: Boolean, mode: Int, modeLabel: String) {
            if (webViewGone) return
            mainHandler.post {
                val banner = findViewById<TextView>(R.id.speedBanner) ?: return@post
                val actualSpeed = Math.round(speed * 100.0) / 100.0
                val actualLatency = Math.round(latency)
                val actualHealth = Math.round(health * 10.0) / 10.0

                val displaySpeed = if (actualSpeed <= 1.01) "1.0x" else "${actualSpeed}x"
                val modeInfo = "$modeLabel • $displaySpeed"

                if (actualSpeed > 1.01) {
                    banner.text = "⚡ $modeInfo • atraso ${actualLatency}s • buffer ${actualHealth}s"
                    banner.setBackgroundColor(0xCCFF6600.toInt())
                } else if (isAtLiveHead && actualLatency <= 10) {
                    banner.text = "✅ AO VIVO • $modeInfo • atraso ${actualLatency}s"
                    banner.setBackgroundColor(0xCC00AA00.toInt())
                } else {
                    banner.text = "⏱️ $modeInfo • atraso ${actualLatency}s • buffer ${actualHealth}s"
                    banner.setBackgroundColor(0xCC000000.toInt())
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
        engineRunning = false
        mainHandler.removeCallbacks(engineRunnable)
        findViewById<WebView>(R.id.youtubeWebView)?.apply {
            evaluateJavascript(
                "if(window.__zeroDelay) { window.__zeroDelay.appliedRate = 1.0; window.__zeroDelay.player && window.__zeroDelay.player.setPlaybackRate(1.0); }",
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
        webViewGone = true
        engineRunning = false
        mainHandler.removeCallbacksAndMessages(null)
        super.onBackPressed()
    }
}
