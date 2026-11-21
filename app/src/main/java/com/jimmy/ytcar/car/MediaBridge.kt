package com.jimmy.ytcar.car

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.jimmy.ytcar.PlaybackController
import com.jimmy.ytcar.PlayerState

/**
 * 讓系統把 YtCar 當成「正在播放的媒體」：
 * - MediaSession：Android Auto 下方的媒體控制、方向盤按鍵都會控制 YtCar
 * - 音訊焦點：其他媒體 App 不會搶走聲音
 */
class MediaBridge(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val session = MediaSession(app, "YtCar")
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private var pausedByFocusLoss = false
    private var lastTitle = ""
    private var lastDuration = -1

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                pausedByFocusLoss = false
                PlaybackController.setWantPlaying(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (PlaybackController.state.value.playing) {
                    pausedByFocusLoss = true
                    PlaybackController.setWantPlaying(false)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                if (pausedByFocusLoss) {
                    pausedByFocusLoss = false
                    PlaybackController.setWantPlaying(true)
                }
            }
        }
    }

    init {
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { requestFocus(); PlaybackController.setWantPlaying(true) }
            override fun onPause() { PlaybackController.setWantPlaying(false) }
            override fun onSkipToNext() { PlaybackController.next() }
            override fun onSkipToPrevious() { PlaybackController.seekBy(-10) }
            override fun onFastForward() { PlaybackController.seekBy(10) }
            override fun onRewind() { PlaybackController.seekBy(-10) }
            override fun onSeekTo(pos: Long) { PlaybackController.seekTo((pos / 1000).toInt()) }
        }, Handler(Looper.getMainLooper()))
        session.isActive = true
        publish(PlaybackController.state.value)
    }

    /** 每秒由 CarPlayer 呼叫 */
    fun update(s: PlayerState) {
        if (s.playing) requestFocus()
        publish(s)
    }

    private fun publish(s: PlayerState) {
        if (s.title != lastTitle || s.duration != lastDuration) {
            lastTitle = s.title
            lastDuration = s.duration
            session.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, s.title.ifBlank { "YtCar" })
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "YouTube")
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, s.duration * 1000L)
                    .build()
            )
        }
        val state = when {
            s.videoId == null -> PlaybackState.STATE_NONE
            s.playing -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_FAST_FORWARD or
                        PlaybackState.ACTION_REWIND or PlaybackState.ACTION_SEEK_TO
                )
                .setState(state, s.position * 1000L, if (s.playing) 1f else 0f)
                .build()
        )
    }

    private fun requestFocus() {
        if (hasFocus) return
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
            .also { focusRequest = it }
        hasFocus = audio.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun release() {
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        session.isActive = false
        session.release()
    }
}
