package com.jimmy.ytcar.car

import android.content.Context
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
 */
class MediaBridge(context: Context) {
    private val app = context.applicationContext
    private val session = MediaSession(app, "YtCar")
    private var lastTitle = ""
    private var lastDuration = -1

    init {
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { PlaybackController.setWantPlaying(true) }
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

    fun release() {
        session.isActive = false
        session.release()
    }
}
