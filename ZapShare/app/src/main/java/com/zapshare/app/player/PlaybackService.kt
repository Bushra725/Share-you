package com.zapshare.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.zapshare.app.data.MediaFile
import com.zapshare.app.data.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Background music playback with lock-screen / notification controls (Media3). */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

/** UI-side handle to the music service. */
object MusicController {
    data class NowPlaying(
        val title: String = "",
        val artist: String = "",
        val artwork: Uri? = null,
        val isPlaying: Boolean = false,
        val position: Long = 0,
        val duration: Long = 0,
        val shuffle: Boolean = false,
        val repeatMode: Int = Player.REPEAT_MODE_OFF,
        val hasMedia: Boolean = false,
        val mediaId: String? = null
    )

    val state = MutableStateFlow(NowPlaying())
    private var controller: MediaController? = null
    private var pending: ((MediaController) -> Unit)? = null
    private var poller: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun connect(ctx: Context) {
        if (controller != null) return
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = publish()
            })
            publish()
            pending?.invoke(c); pending = null
            startPolling()
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun withController(ctx: Context, block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else { pending = block; connect(ctx) }
    }

    fun playList(ctx: Context, songs: List<MediaFile>, start: Int) = withController(ctx) { c ->
        val items = songs.map { s ->
            MediaItem.Builder()
                .setUri(s.uri)
                .setMediaId(s.uri.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.name.substringBeforeLast('.'))
                        .setArtist(s.artist ?: "Unknown artist")
                        .setAlbumTitle(s.album)
                        .setArtworkUri(if (s.albumId > 0) MediaRepository.albumArtUri(s.albumId) else null)
                        .build()
                ).build()
        }
        c.setMediaItems(items, start, 0)
        c.prepare()
        c.play()
    }

    fun toggle() = controller?.let { if (it.isPlaying) it.pause() else it.play() }
    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPreviousMediaItem()
    fun seekTo(ms: Long) = controller?.seekTo(ms)
    fun toggleShuffle() = controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = controller?.let {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    fun stop() = controller?.let { it.stop(); it.clearMediaItems() }

    private fun publish() {
        val c = controller ?: return
        val md = c.mediaMetadata
        state.value = NowPlaying(
            title = md.title?.toString() ?: "",
            artist = md.artist?.toString() ?: "",
            artwork = md.artworkUri,
            isPlaying = c.isPlaying,
            position = c.currentPosition,
            duration = c.duration.takeIf { it > 0 } ?: 0,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            hasMedia = c.mediaItemCount > 0,
            mediaId = c.currentMediaItem?.mediaId
        )
    }

    private fun startPolling() {
        poller?.cancel()
        poller = scope.launch {
            while (isActive) {
                if (controller?.isPlaying == true) publish()
                delay(500)
            }
        }
    }
}
