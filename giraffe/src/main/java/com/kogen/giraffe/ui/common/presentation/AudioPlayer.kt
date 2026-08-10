package com.kogen.giraffe.ui.common.presentation

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kz.evko.kogen_di.annotations.KoGenComponent
import kotlin.time.Duration.Companion.milliseconds

/** Observable playback state for whichever voice message is currently loaded, if any. */
data class AudioPlaybackState(
    val filePath: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
)

/**
 * Single shared [MediaPlayer] wrapper for voice-message playback across the chat details screen -
 * shared (rather than one per message bubble) since only one voice message can play at a time.
 */
@KoGenComponent(true)
class AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(AudioPlaybackState())
    val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

    /** Starts playback of [filePath], or resumes it if it's already the loaded (paused) track; otherwise releases the current player first. */
    fun play(filePath: String) {
        if (_state.value.filePath == filePath && mediaPlayer != null) {
            resume()
            return
        }

        release()

        val player = MediaPlayer().apply {
            setDataSource(filePath)
            setOnCompletionListener {
                stopProgressLoop()
                _state.value = _state.value.copy(isPlaying = false, currentPositionMs = 0)
            }
            prepare()
            start()
        }
        mediaPlayer = player

        _state.value = AudioPlaybackState(
            filePath = filePath,
            isPlaying = true,
            currentPositionMs = 0,
            durationMs = player.duration,
        )
        startProgressLoop()
    }

    /** Pauses playback without releasing the player, so [play] can resume from the same position. */
    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) it.pause()
        }
        stopProgressLoop()
        _state.value = _state.value.copy(isPlaying = false)
    }

    /** Resumes a paused player at its current position. */
    fun resume() {
        mediaPlayer?.let {
            it.start()
            _state.value = _state.value.copy(isPlaying = true)
            startProgressLoop()
        }
    }

    /** Jumps to [positionMs] in the current track, e.g. from dragging the waveform. */
    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
        _state.value = _state.value.copy(currentPositionMs = positionMs)
    }

    /** Releases the underlying [MediaPlayer] and resets to an empty [state] - must be called when the owning screen is torn down. */
    fun release() {
        stopProgressLoop()
        mediaPlayer?.release()
        mediaPlayer = null
        _state.value = AudioPlaybackState()
    }

    /** Polls [MediaPlayer.getCurrentPosition] on a timer to drive the waveform's progress indicator, since MediaPlayer has no position-changed callback. */
    private fun startProgressLoop() {
        stopProgressLoop()
        progressJob = scope.launch {
            while (true) {
                val pos = mediaPlayer?.currentPosition ?: break
                _state.value = _state.value.copy(currentPositionMs = pos)
                delay(100.milliseconds)
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
    }
}