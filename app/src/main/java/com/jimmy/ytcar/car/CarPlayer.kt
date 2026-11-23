package com.jimmy.ytcar.car

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.jimmy.ytcar.CarPlayerHandle
import com.jimmy.ytcar.PlaybackController
import com.jimmy.ytcar.WebSetup
import org.json.JSONObject

/** 車機端播放器：已登入的 m.youtube.com 觀看頁面，用 CSS 把影片撐滿全螢幕，用 JS 控制。 */
@SuppressLint("SetJavaScriptEnabled")
class CarPlayer(context: Context) : CarPlayerHandle {
    val root = FrameLayout(context)
    private val web = WebView(context)
    private val handler = Handler(Looper.getMainLooper())

    private var wantPlaying = true
    private var endedReported = false
    private var currentId: String? = null
    private var destroyed = false
    private val media = MediaBridge(context)

    private val tick = object : Runnable {
        override fun run() {
            if (destroyed) return
            if (currentId != null) web.evaluateJavascript(tickJs(wantPlaying), ::onTick)
            handler.postDelayed(this, 1000)
        }
    }

    init {
        root.setBackgroundColor(Color.BLACK)
        web.setBackgroundColor(Color.BLACK)
        WebSetup.apply(web)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (currentId != null) view.evaluateJavascript(tickJs(wantPlaying), null)
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            // 去掉 WebView 預設的灰色播放鍵縮圖
            override fun getDefaultVideoPoster(): Bitmap =
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        root.addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        showIdle()
        handler.post(tick)
    }

    private fun showIdle() {
        val html = """
            <html><body style="background:#000;color:#aaa;font-family:sans-serif;display:flex;
            align-items:center;justify-content:center;height:100vh;margin:0;font-size:5vh">
            <div style="text-align:center"><div style="font-size:12vh;color:#f03">&#9654;</div>
            YtCar 已連線<br><span style="font-size:3.5vh">請在手機上選擇影片</span></div></body></html>
        """.trimIndent()
        web.loadDataWithBaseURL("https://m.youtube.com/", html, "text/html", "utf-8", null)
    }

    override fun load(id: String, startSec: Int) {
        currentId = id
        wantPlaying = true
        endedReported = false
        val t = if (startSec > 3) "&t=${startSec}s" else ""
        web.loadUrl("https://m.youtube.com/watch?v=$id$t")
    }

    override fun setWantPlaying(play: Boolean) {
        wantPlaying = play
        web.evaluateJavascript(tickJs(play), ::onTick)
    }

    override fun seekBy(sec: Int) {
        web.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(v)v.currentTime=Math.max(0,v.currentTime+($sec));})()",
            null,
        )
    }

    override fun seekTo(sec: Int) {
        web.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(v)v.currentTime=$sec;})()",
            null,
        )
    }

    private fun onTick(result: String?) {
        if (destroyed || result == null || result == "null") return
        val o = runCatching { JSONObject(result) }.getOrNull() ?: return
        val title = o.optString("title").removeSuffix(" - YouTube").trim()
        PlaybackController.onStatus(
            o.optDouble("t", 0.0).toInt(),
            o.optDouble("d", 0.0).toInt(),
            o.optBoolean("p"),
            title,
        )
        media.update(PlaybackController.state.value)
        if (o.optBoolean("e") && !endedReported) {
            endedReported = true
            PlaybackController.onEnded()
        }
    }

    /** 車機觸控點擊 → 轉給 WebView，讓 YouTube 播放器自己的控制介面（畫質、字幕、進度）可以操作 */
    fun tap(x: Float, y: Float) {
        handler.post {
            if (destroyed) return@post
            val t = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
            web.dispatchTouchEvent(down)
            down.recycle()
            handler.postDelayed({
                if (destroyed) return@postDelayed
                val t2 = SystemClock.uptimeMillis()
                val up = MotionEvent.obtain(t2, t2, MotionEvent.ACTION_UP, x, y, 0)
                web.dispatchTouchEvent(up)
                up.recycle()
            }, 60)
        }
    }

    /** 離開車機畫面前先暫停影片並載入空白頁，確保聲音一定停掉 */
    fun stopPlayback() {
        wantPlaying = false
        runCatching {
            web.evaluateJavascript("document.querySelectorAll('video').forEach(function(v){v.pause();})", null)
            web.onPause()
            web.loadUrl("about:blank")
        }
    }

    fun destroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        media.release()
        runCatching {
            web.stopLoading()
            (web.parent as? android.view.ViewGroup)?.removeView(web)
            web.removeAllViews()
            web.destroy()
        }
    }

    companion object {
        // 隱藏 YouTube 手機版的頂列與其他內容，只留下全螢幕播放器
        private const val CSS = """
html,body{background:#000!important;overflow:hidden!important}
ytm-mobile-topbar-renderer,header,#header-bar,.mobile-topbar-header,ytm-pivot-bar-renderer,
ytm-single-column-watch-next-results-renderer,ytm-item-section-renderer,.watch-below-the-player,
ytm-engagement-panel,bottom-sheet-container,.ytp-unmute,.ytp-unmute-inner,.ytp-unmute-animated,.ytp-unmute-text,ytm-consent-bump-v2-renderer{display:none!important}
#player-container-id,#player-container,.player-container,#player,#movie_player,.html5-video-player{
position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;
max-height:none!important;margin:0!important;padding:0!important;z-index:2147483646!important;background:#000!important}
.html5-video-container{width:100%!important;height:100%!important}
video{position:absolute!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;object-fit:contain!important}
"""
        private val CSS_JS: String = JSONObject.quote(CSS)

        fun tickJs(want: Boolean): String = """
(function(want){
  if(!document.getElementById('ytcar-css')){var s=document.createElement('style');s.id='ytcar-css';
    s.textContent=$CSS_JS;(document.head||document.documentElement).appendChild(s);}
  var v=document.querySelector('video'); if(!v) return null;
  var mp=document.getElementById('movie_player');
  if(want && mp){ try{ if(mp.isMuted&&mp.isMuted()) mp.unMute(); if(mp.getVolume&&mp.getVolume()<100) mp.setVolume(100); }catch(e){} }
  if(want && v.muted){ v.muted=false; }
  if(want && v.paused && !v.ended){ v.muted=false; var p=v.play(); if(p&&p.catch)p.catch(function(){}); }
  if(!want && !v.paused) v.pause();
  return {t:v.currentTime||0,d:v.duration||0,p:!v.paused,e:v.ended,title:document.title||''};
})($want)
"""
    }
}
