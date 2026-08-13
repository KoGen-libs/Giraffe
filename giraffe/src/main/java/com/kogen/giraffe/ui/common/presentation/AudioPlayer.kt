package com.kogen.giraffe.ui.common.presentation

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kz.evko.kogen_di.annotations.KoGenComponent
import kotlin.time.Duration.Companion.milliseconds

private val TAG = AudioPlayer::class.java.simpleName

/** Observable playback state for whichever voice message is currently loaded, if any. */
internal data class AudioPlaybackState(
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
internal class AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    // SupervisorJob so a failure in one operation (or one tick of the progress loop) can't poison
    // the scope for every later one. CoroutineExceptionHandler is the last-resort net - this is a
    // passive debug feature, so nothing it does should ever be able to crash the host app, no
    // matter what state the underlying MediaPlayer ends up in.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, throwable ->
            Log.w(TAG, "Unexpected error in Giraffe's audio player - ignoring it", throwable)
        }
    )

    private val _state = MutableStateFlow(AudioPlaybackState())
    val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

    /** Starts playback of [filePath], or resumes it if it's already the loaded (paused) track; otherwise releases the current player first. */
    fun play(filePath: String) {
        if (_state.value.filePath == filePath && mediaPlayer != null) {
            resume()
            return
        }

        release()

        val player = try {
            MediaPlayer().apply {
                setDataSource(filePath)
                setOnCompletionListener {
                    stopProgressLoop()
                    _state.value = _state.value.copy(isPlaying = false, currentPositionMs = 0)
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            // setDataSource/prepare throw for a file that's missing, corrupt, or an unsupported
            // format - not something worth crashing the host app over.
            Log.w(TAG, "Failed to start playback for $filePath", e)
            return
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
        safely("pause") {
            mediaPlayer?.let {
                if (it.isPlaying) it.pause()
            }
        }
        stopProgressLoop()
        _state.value = _state.value.copy(isPlaying = false)
    }

    /** Resumes a paused player at its current position. */
    fun resume() {
        safely("resume") {
            mediaPlayer?.let {
                it.start()
                _state.value = _state.value.copy(isPlaying = true)
                startProgressLoop()
            }
        }
    }

    /** Jumps to [positionMs] in the current track, e.g. from dragging the waveform. */
    fun seekTo(positionMs: Int) {
        safely("seekTo") {
            mediaPlayer?.seekTo(positionMs)
        }
        _state.value = _state.value.copy(currentPositionMs = positionMs)
    }

    /**
     * Releases the underlying [MediaPlayer] and resets to an empty [state]. [AudioPlayer] is a
     * single shared instance used by more than one details screen (gRPC and REST both play voice
     * messages through it) - a consuming screen's own teardown should call [pause] instead, so
     * leaving one screen doesn't tear down playback state a *different* currently-visible screen
     * might still own. [play] already calls this itself before loading a new track, so nothing
     * outside [AudioPlayer] normally needs to call it directly.
     */
    fun release() {
        stopProgressLoop()
        safely("release") {
            mediaPlayer?.release()
        }
        mediaPlayer = null
        _state.value = AudioPlaybackState()
    }

    /** Polls [MediaPlayer.getCurrentPosition] on a timer to drive the waveform's progress indicator, since MediaPlayer has no position-changed callback. */
    private fun startProgressLoop() {
        stopProgressLoop()
        progressJob = scope.launch {
            while (true) {
                val pos = try {
                    mediaPlayer?.currentPosition ?: break
                } catch (e: Exception) {
                    // getCurrentPosition() throws IllegalStateException if the player ends up in
                    // an unexpected state between ticks - stop polling rather than spin on it.
                    Log.w(TAG, "Failed to read playback position - stopping progress updates", e)
                    break
                }
                _state.value = _state.value.copy(currentPositionMs = pos)
                delay(100.milliseconds)
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
    }

    /** Runs a [MediaPlayer] call that can throw `IllegalStateException` depending on the player's current state, logging and ignoring any failure instead of crashing the host app over it. */
    private inline fun safely(what: String, action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            Log.w(TAG, "MediaPlayer operation failed: $what - ignoring it", e)
        }
    }
}
