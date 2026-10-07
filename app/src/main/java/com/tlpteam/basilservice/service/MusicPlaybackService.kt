package com.tlpteam.basilservice.service

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.tlpteam.basilservice.util.NotificationHelper
import kotlinx.coroutines.*

class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)

        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    val currentUrl = currentMediaItem?.localConfiguration?.uri?.toString()
                    val pos = currentPosition.toInt()
                    val dur = duration.coerceAtLeast(0).toInt()
                    AudioPlayerManager.updateState(currentUrl, isPlaying, repeatMode == Player.REPEAT_MODE_ONE, pos, dur)
                    if (isPlaying) {
                        startPositionUpdates()
                    } else {
                        stopPositionUpdates()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val currentUrl = currentMediaItem?.localConfiguration?.uri?.toString()
                    val pos = currentPosition.toInt()
                    val dur = duration.coerceAtLeast(0).toInt()
                    AudioPlayerManager.updateState(currentUrl, player.isPlaying, repeatMode == Player.REPEAT_MODE_ONE, pos, dur)
                }
            })
        }

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = serviceScope.launch {
            while (true) {
                if (player.isPlaying) {
                    val currentUrl = player.currentMediaItem?.localConfiguration?.uri?.toString()
                    AudioPlayerManager.updateState(currentUrl, true, player.repeatMode == Player.REPEAT_MODE_ONE, player.currentPosition.toInt(), player.duration.coerceAtLeast(0).toInt())
                }
                delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    override fun onDestroy() {
        stopPositionUpdates()
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        AudioPlayerManager.updateState(null, false, false, 0, 0)
        super.onDestroy()
    }
}
