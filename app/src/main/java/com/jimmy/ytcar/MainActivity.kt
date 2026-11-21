package com.jimmy.ytcar

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * 手機端 = 遙控器。上半部是已登入的 YouTube，點影片時會攔截下來送到車機播放；
 * 下半部是遙控列。
 */
class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var status: TextView
    private lateinit var title: TextView
    private lateinit var seek: SeekBar
    private lateinit var time: TextView
    private lateinit var swPassenger: SwitchCompat
    private val main = Handler(Looper.getMainLooper())
    private var dragging = false
    private var lastHandled: Pair<String, Long>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        web = findViewById(R.id.web)
        status = findViewById(R.id.status)
        title = findViewById(R.id.title)
        seek = findViewById(R.id.seek)
        time = findViewById(R.id.time)
        swPassenger = findViewById(R.id.swPassenger)

        WebSetup.apply(web)
        web.webViewClient = object : WebViewClient() {
            // m.youtube.com 是 SPA，點影片時走 history.pushState，從這裡攔得到
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                onPhoneUrl(url)
            }
        }
        if (savedInstanceState == null) web.loadUrl(HOME) else web.restoreState(savedInstanceState)

        findViewById<Button>(R.id.btnHome).setOnClickListener { web.loadUrl(HOME) }
        findViewById<Button>(R.id.btnBack).setOnClickListener { PlaybackController.seekBy(-10) }
        findViewById<Button>(R.id.btnFwd).setOnClickListener { PlaybackController.seekBy(10) }
        findViewById<Button>(R.id.btnPlay).setOnClickListener { PlaybackController.togglePlay() }
        findViewById<Button>(R.id.btnNext).setOnClickListener {
            if (PlaybackController.state.value.queue.isEmpty()) toast("佇列是空的") else PlaybackController.next()
        }
        findViewById<Button>(R.id.btnQueue).setOnClickListener { showQueue() }
        swPassenger.setOnCheckedChangeListener { _, on -> PlaybackController.setPassengerMode(on) }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) time.text = "${fmt(p)} / ${fmt(sb.max)}"
            }
            override fun onStartTrackingTouch(sb: SeekBar) { dragging = true }
            override fun onStopTrackingTouch(sb: SeekBar) {
                dragging = false
                PlaybackController.seekTo(sb.progress)
            }
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PlaybackController.state.collect(::render)
            }
        }

        ActivityCompat.requestPermissions(this, arrayOf(CAR_SPEED), 1)
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val id = YouTubeUrls.videoIdFromSharedText(intent.getStringExtra(Intent.EXTRA_TEXT))
        if (id == null) { toast("分享內容中找不到 YouTube 影片"); return }
        sendToCar(id)
    }

    private fun onPhoneUrl(url: String) {
        val id = YouTubeUrls.videoId(url) ?: return
        val now = System.currentTimeMillis()
        lastHandled?.let { (lastId, at) -> if (lastId == id && now - at < 2000) return }
        lastHandled = id to now
        if (!PlaybackController.state.value.carConnected) return // 車機沒連線 → 就在手機上正常播放

        pausePhoneVideo()
        if (web.canGoBack()) web.goBack()
        sendToCar(id)
    }

    private fun sendToCar(id: String) {
        val s = PlaybackController.state.value
        if (!s.carConnected) {
            toast("車機未連線：請先在 Android Auto 中開啟 YtCar")
            return
        }
        if (s.videoId == null) {
            PlaybackController.playNow(id)
            toast("▶ 已在車機播放")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("送到車機")
            .setItems(arrayOf("立即播放", "加入佇列")) { _, which ->
                if (which == 0) {
                    PlaybackController.playNow(id)
                } else {
                    PlaybackController.enqueue(QueueItem(id))
                    fetchTitle(id)
                    toast("已加入佇列")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun pausePhoneVideo() {
        val js = "document.querySelectorAll('video').forEach(function(v){v.muted=true;v.pause();})"
        listOf(0L, 400L, 1200L, 2500L).forEach { d -> main.postDelayed({ web.evaluateJavascript(js, null) }, d) }
    }

    private fun fetchTitle(id: String) = thread {
        runCatching {
            val c = URL("https://www.youtube.com/oembed?format=json&url=https://www.youtube.com/watch?v=$id")
                .openConnection() as HttpURLConnection
            c.connectTimeout = 5000
            c.readTimeout = 5000
            val t = JSONObject(c.inputStream.bufferedReader().readText()).optString("title")
            if (t.isNotBlank()) main.post { PlaybackController.updateQueueTitle(id, t) }
        }
    }

    private fun showQueue() {
        val q = PlaybackController.state.value.queue
        if (q.isEmpty()) { toast("佇列是空的"); return }
        val labels = q.mapIndexed { i, it -> "${i + 1}. ${it.title.ifBlank { it.id }}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("播放佇列")
            .setItems(labels) { _, i ->
                AlertDialog.Builder(this)
                    .setTitle(labels[i])
                    .setItems(arrayOf("立即播放", "移除")) { _, w ->
                        if (w == 0) PlaybackController.playFromQueue(i) else PlaybackController.removeFromQueue(i)
                    }
                    .show()
            }
            .setNeutralButton("清空") { _, _ -> PlaybackController.clearQueue() }
            .setNegativeButton("關閉", null)
            .show()
    }

    private fun render(s: PlayerState) {
        val speed = s.speedKmh?.let { "%.0f km/h".format(it) } ?: "車速無法取得"
        status.text = when {
            !s.carConnected -> "🚗 車機未連線（在 Android Auto 開啟 YtCar 後，點影片就會送到車機）"
            s.audioOnly -> "🚗 已連線 · $speed · 行駛中僅聲音"
            else -> "🚗 已連線 · $speed" + if (s.queue.isNotEmpty()) " · 佇列 ${s.queue.size}" else ""
        }
        title.text = if (s.videoId == null) "尚未播放" else (if (s.playing) "▶ " else "⏸ ") + s.title
        seek.max = maxOf(s.duration, 1)
        if (!dragging) {
            seek.progress = s.position
            time.text = "${fmt(s.position)} / ${fmt(s.duration)}"
        }
        if (swPassenger.isChecked != s.passengerMode) swPassenger.isChecked = s.passengerMode
    }

    private fun fmt(sec: Int): String {
        val h = sec / 3600
        val m = sec % 3600 / 60
        val ss = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, ss) else "%d:%02d".format(m, ss)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val HOME = "https://m.youtube.com/"
        private const val CAR_SPEED = "com.google.android.gms.permission.CAR_SPEED"
    }
}
