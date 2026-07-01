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
            "if(window.__zeroDelay) { window.__zeroDelay.mode = $mode; window.__zeroDelay.phase = 'init'; window.__zeroDelay.phaseTick = 0; }",
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
                    tickErrors: 0,
                    lastSpeed: 1.0,
                    lastLatency: 0,
                    lastHealth: 0,
                    isAtLiveHead: false,
                    seekableEnd: 0,
                    currentTime: 0,
                    mode: $currentMode,
                    
                    // Burst cycle: accel then rest, repeat
                    phase: 'init',   // 'init', 'accel', 'rest'
                    phaseTick: 0,
                    
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
                    
                    getSegDuration: function() {
                        try {
                            var stats = ZD.player.getVideoStats ? ZD.player.getVideoStats() : null;
                            if (stats && stats.segduration) return stats.segduration;
                            var resp = ZD.player.getPlayerResponse ? ZD.player.getPlayerResponse() : null;
                            if (resp && resp.videoDetails) {
                                var lc = resp.videoDetails.latencyClass;
                                if (lc === 'MDE_STREAM_OPTIMIZATIONS_RENDERER_LATENCY_ULTRA_LOW') return 1;
                                if (lc === 'MDE_STREAM_OPTIMIZATIONS_RENDERER_LATENCY_LOW') return 2;
                            }
                            return 5;
                        } catch(e) { return 5; }
                    },
                    
                    getDurationScale: function() {
                        var seg = ZD.getSegDuration();
                        return Math.max(0.5, Math.min(1.0, 0.3 + 0.7 * seg / 5.0));
                    },
                    
                    getAccelTicks: function(mode) {
                        var base = 20;
                        if (mode === 1) base = 30;
                        if (mode === 3) base = 12;
                        return Math.round(base * ZD.getDurationScale());
                    },
                    
                    getRestTicks: function(mode) {
                        return Math.round(10 * ZD.getDurationScale());
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
                            
                            // --- Burst cycle state machine ---
                            var maxSpeed = ZD.getModeSpeed(ZD.mode);
                            
                            if (ZD.phase === 'init') {
                                // First tick: go to live and start resting
                                ZD.seekToLive();
                                ZD.setSpeed(1.0);
                                ZD.lastSpeed = 1.0;
                                ZD.phase = 'rest';
                                ZD.phaseTick = 0;
                                
                            } else if (ZD.phase === 'accel') {
                                ZD.setSpeed(maxSpeed);
                                ZD.lastSpeed = maxSpeed;
                                ZD.phaseTick++;
                                // Safety net: if delay exceeds 30s, seek to live immediately
                                if (ZD.seekableEnd > 0 && ZD.currentTime > 0) {
                                    var delay = ZD.seekableEnd - ZD.currentTime;
                                    if (delay > 30) {
                                        ZD.seekToLive();
                                        ZD.phase = 'rest';
                                        ZD.phaseTick = 0;
                                        ZD.setSpeed(1.0);
                                        ZD.lastSpeed = 1.0;
                                    }
                                }
                                if (ZD.phase === 'accel' && ZD.phaseTick >= ZD.getAccelTicks(ZD.mode)) {
                                    ZD.phase = 'rest';
                                    ZD.phaseTick = 0;
                                    ZD.seekToLive();
                                    ZD.setSpeed(1.0);
                                    ZD.lastSpeed = 1.0;
                                }
                                
                            } else if (ZD.phase === 'rest') {
                                ZD.lastSpeed = 1.0;
                                ZD.phaseTick++;
                                // Re-seek to live every 3 ticks during rest to keep badge active
                                if (ZD.phaseTick % 6 === 0) {
                                    ZD.seekToLive();
                                }
                                if (ZD.phaseTick >= ZD.getRestTicks(ZD.mode)) {
                                    ZD.phase = 'accel';
                                    ZD.phaseTick = 0;
                                }
                            }
                            
                            ZeroDelayBridge.onTick(
                                ZD.lastSpeed,
                                ZD.lastLatency,
                                ZD.lastHealth,
                                ZD.isAtLiveHead,
                                ZD.mode,
                                ZD.getModeLabel(ZD.mode),
                                ZD.phase
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
        fun onTick(speed: Double, latency: Double, health: Double, isAtLiveHead: Boolean, mode: Int, modeLabel: String, phase: String) {
            if (webViewGone) return
            mainHandler.post {
                val banner = findViewById<TextView>(R.id.speedBanner) ?: return@post
                val actualSpeed = Math.round(speed * 100.0) / 100.0
                val actualLatency = Math.round(latency)
                val actualHealth = Math.round(health * 10.0) / 10.0

                val displaySpeed = if (actualSpeed <= 1.01) "1.0x" else "${actualSpeed}x"
                val phaseIcon = if (phase == "accel") "⚡" else if (phase == "rest") "⏳" else "▶️"
                val modeInfo = "$phaseIcon $modeLabel • $displaySpeed"

                if (phase == "accel") {
                    banner.text = "⚡ ACELERANDO • ${displaySpeed} • atraso ${actualLatency}s • buffer ${actualHealth}s"
                    banner.setBackgroundColor(0xCCFF6600.toInt())
                } else if (phase == "rest") {
                    val liveStatus = if (isAtLiveHead) "✅ AO VIVO" else "⏱️ atraso ${actualLatency}s"
                    banner.text = "⏳ REPOUSO • $liveStatus • buffer ${actualHealth}s"
                    banner.setBackgroundColor(0xCC0088CC.toInt())
                } else {
                    banner.text = "▶️ INICIANDO • $modeLabel"
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
        webViewGone = true
        engineRunning = false
        mainHandler.removeCallbacksAndMessages(null)
        super.onBackPressed()
    }
}
