package com.jimmy.ytcar

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class QueueItem(val id: String, val title: String = "")

data class PlayerState(
    val carConnected: Boolean = false,
    val videoId: String? = null,
    val title: String = "",
    val position: Int = 0,
    val duration: Int = 0,
    val playing: Boolean = false,
    val queue: List<QueueItem> = emptyList(),
    /** 乘客模式：行車中也顯示影片 */
    val passengerMode: Boolean = true,
    /** 車速（km/h），null = 車機未提供 */
    val speedKmh: Float? = null,
) {
    val moving: Boolean get() = (speedKmh ?: 0f) > 7f
    val audioOnly: Boolean get() = moving && !passengerMode
}

/** 車機播放器需要提供給 Controller 的操作 */
interface CarPlayerHandle {
    fun load(id: String, startSec: Int)
    fun setWantPlaying(play: Boolean)
    fun seekBy(sec: Int)
    fun seekTo(sec: Int)
    fun setAudioOnly(audioOnly: Boolean, title: String)
}

/** 手機端（遙控）與車機端（播放）共用的單一狀態來源，全部在主執行緒呼叫。 */
object PlaybackController {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private var player: CarPlayerHandle? = null

    fun attach(p: CarPlayerHandle) {
        player = p
        _state.update { it.copy(carConnected = true) }
        val s = _state.value
        s.videoId?.let { p.load(it, s.position) }
        p.setAudioOnly(s.audioOnly, s.title)
    }

    fun detach(p: CarPlayerHandle) {
        if (player === p) {
            player = null
            _state.update { it.copy(carConnected = false, playing = false) }
        }
    }

    fun playNow(id: String, title: String = "") {
        _state.update { it.copy(videoId = id, title = title, position = 0, duration = 0) }
        player?.load(id, 0)
    }

    fun enqueue(item: QueueItem) {
        _state.update { it.copy(queue = it.queue + item) }
    }

    fun updateQueueTitle(id: String, title: String) {
        _state.update { s -> s.copy(queue = s.queue.map { if (it.id == id && it.title.isEmpty()) it.copy(title = title) else it }) }
    }

    fun removeFromQueue(index: Int) {
        _state.update { s -> s.copy(queue = s.queue.filterIndexed { i, _ -> i != index }) }
    }

    fun clearQueue() = _state.update { it.copy(queue = emptyList()) }

    fun playFromQueue(index: Int) {
        val item = _state.value.queue.getOrNull(index) ?: return
        removeFromQueue(index)
        playNow(item.id, item.title)
    }

    fun next() {
        if (_state.value.queue.isNotEmpty()) playFromQueue(0)
    }

    fun togglePlay() { player?.setWantPlaying(!_state.value.playing) }
    fun setWantPlaying(play: Boolean) { player?.setWantPlaying(play) }
    fun seekBy(sec: Int) { player?.seekBy(sec) }
    fun seekTo(sec: Int) { player?.seekTo(sec) }

    fun setPassengerMode(on: Boolean) {
        _state.update { it.copy(passengerMode = on) }
        applyAudioOnly()
    }

    fun onSpeed(metersPerSecond: Float?) {
        val before = _state.value.audioOnly
        _state.update { it.copy(speedKmh = metersPerSecond?.let { v -> v * 3.6f }) }
        if (before != _state.value.audioOnly) applyAudioOnly()
    }

    /** 車機端每秒回報一次 */
    fun onStatus(position: Int, duration: Int, playing: Boolean, title: String) {
        _state.update {
            it.copy(
                position = position,
                duration = duration,
                playing = playing,
                title = title.ifBlank { it.title },
            )
        }
    }

    fun onEnded() = next()

    private fun applyAudioOnly() {
        val s = _state.value
        player?.setAudioOnly(s.audioOnly, s.title)
    }
}
