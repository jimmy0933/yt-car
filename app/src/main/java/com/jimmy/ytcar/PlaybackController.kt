package com.jimmy.ytcar

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class QueueItem(val id: String, val title: String = "")

data class PlayerState(
    /** 車機連線中（Android Auto 的 YtCar Screen 還在，不論是否在前景） */
    val carConnected: Boolean = false,
    /** YtCar 的車機畫面目前在前景、播放器已建立 */
    val surfaceReady: Boolean = false,
    val videoId: String? = null,
    val title: String = "",
    val position: Int = 0,
    val duration: Int = 0,
    val playing: Boolean = false,
    val queue: List<QueueItem> = emptyList(),
) {
}

/** 車機播放器需要提供給 Controller 的操作 */
interface CarPlayerHandle {
    fun load(id: String, startSec: Int)
    fun setWantPlaying(play: Boolean)
    fun seekBy(sec: Int)
    fun seekTo(sec: Int)
}

/** 手機端（遙控）與車機端（播放）共用的單一狀態來源，全部在主執行緒呼叫。 */
object PlaybackController {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private var player: CarPlayerHandle? = null

    fun attach(p: CarPlayerHandle) {
        player = p
        _state.update { it.copy(carConnected = true, surfaceReady = true) }
        val s = _state.value
        s.videoId?.let { p.load(it, s.position) }
    }

    fun detach(p: CarPlayerHandle) {
        if (player === p) {
            player = null
            _state.update { it.copy(surfaceReady = false, playing = false) }
        }
    }

    fun setCarSession(active: Boolean) {
        _state.update { it.copy(carConnected = active, surfaceReady = if (active) it.surfaceReady else false) }
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
}
