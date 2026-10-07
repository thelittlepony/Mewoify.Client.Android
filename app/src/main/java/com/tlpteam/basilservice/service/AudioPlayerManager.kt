package com.tlpteam.basilservice.service

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AudioPlayerManager {
    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLooping = MutableStateFlow(false)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    fun updateState(
        url: String?,
        playing: Boolean,
        looping: Boolean,
        position: Int,
        dur: Int
    ) {
        _currentUrl.value = url
        _isPlaying.value = playing
        _isLooping.value = looping
        _currentPosition.value = position
        _duration.value = dur
    }

    private fun withController(context: Context, action: (MediaController) -> Unit) {
        val sessionToken = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                action(controller)
                controller.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun play(context: Context, url: String) {
        withController(context) { controller ->
            val mediaItem = MediaItem.fromUri(url)
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
    }

    fun pause(context: Context) {
        withController(context) { controller ->
            controller.pause()
        }
    }

    fun resume(context: Context) {
        withController(context) { controller ->
            controller.play()
        }
    }

    fun seekTo(context: Context, position: Int) {
        withController(context) { controller ->
            controller.seekTo(position.toLong())
        }
    }

    fun rewind(context: Context) {
        withController(context) { controller ->
            val newPos = (controller.currentPosition - 10000).coerceAtLeast(0)
            controller.seekTo(newPos)
        }
    }

    fun fastForward(context: Context) {
        withController(context) { controller ->
            val newPos = (controller.currentPosition + 10000).coerceAtMost(controller.duration.coerceAtLeast(0))
            controller.seekTo(newPos)
        }
    }

    fun toggleLoop(context: Context) {
        withController(context) { controller ->
            val newMode = if (controller.repeatMode == Player.REPEAT_MODE_ONE) {
                Player.REPEAT_MODE_OFF
            } else {
                Player.REPEAT_MODE_ONE
            }
            controller.repeatMode = newMode
            _isLooping.value = (newMode == Player.REPEAT_MODE_ONE)
        }
    }

    fun stop(context: Context) {
        withController(context) { controller ->
            controller.stop()
            controller.clearMediaItems()
        }
    }
}
