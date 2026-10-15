package com.zapshare.app.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.zapshare.app.R
import com.zapshare.app.data.MediaFile
import com.zapshare.app.data.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Shows the playback notification (previous, play/pause, next) while music is loaded.
 * The player itself lives in [MusicController] so the on-screen controls work even if
 * the notification service is still starting.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var boundPlayer: ExoPlayer? = null
    private var playerListener: Player.Listener? = null

    override fun onCreate() {
        super.onCreate()
        val provider = DefaultMediaNotificationProvider(this)
        provider.setSmallIcon(R.drawable.ic_stat_zap)
        setMediaNotificationProvider(provider)
        val player = MusicController.ensurePlayer(this)
        boundPlayer = player
        session = MediaSession.Builder(this, player)
            .setId("share_music")
            .build()
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_MEDIA_METADATA_CHANGED
                    )
                ) {
                    showMediaNotification(player)
                }
            }
        }
        playerListener = listener
        player.addListener(listener)
        if (player.mediaItemCount > 0) showMediaNotification(player)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREV -> MusicController.previous()
            ACTION_NEXT -> MusicController.next()
            ACTION_TOGGLE -> MusicController.toggle()
        }
        val result = super.onStartCommand(intent, flags, startId)
        boundPlayer?.let { if (it.mediaItemCount > 0) showMediaNotification(it) }
        return result
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        playerListener?.let { boundPlayer?.removeListener(it) }
        playerListener = null
        boundPlayer = null
        session?.release()
        session = null
        super.onDestroy()
    }

    /** Posts the media toolbar immediately. Media3 waits for a later player event, and Android kills the service if startForeground is late. */
    private fun showMediaNotification(player: Player) {
        if (player.mediaItemCount == 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
            else @Suppress("DEPRECATION") stopForeground(true)
            return
        }
        val channelId = "default_channel_id"
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(NotificationChannel(channelId, "Music playback", NotificationManager.IMPORTANCE_LOW))
        }
        val title = player.mediaMetadata.title?.toString().orEmpty().ifBlank { "Share" }
        val artist = player.mediaMetadata.artist?.toString().orEmpty()
        val playing = player.isPlaying
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_zap)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .addAction(action(android.R.drawable.ic_media_previous, "Previous", ACTION_PREV, 1))
            .addAction(action(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (playing) "Pause" else "Play", ACTION_TOGGLE, 2))
            .addAction(action(android.R.drawable.ic_media_next, "Next", ACTION_NEXT, 3))
            .setStyle(
                MediaStyle().setShowActionsInCompactView(0, 1, 2).also { style ->
                    session?.sessionCompatToken?.let { style.setMediaSession(it) }
                }
            )
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun action(icon: Int, label: String, command: String, request: Int): NotificationCompat.Action {
        val pending = PendingIntent.getService(
            this,
            request,
            Intent(this, PlaybackService::class.java).setAction(command),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action(icon, label, pending)
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val ACTION_PREV = "com.zapshare.app.player.PREV"
        private const val ACTION_NEXT = "com.zapshare.app.player.NEXT"
        private const val ACTION_TOGGLE = "com.zapshare.app.player.TOGGLE"
    }
}

/** UI-side handle for music. Commands go straight to the player. */
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
        val mediaId: String? = null,
        val error: String? = null
    )

    val state = MutableStateFlow(NowPlaying())
    private var player: ExoPlayer? = null
    private var appContext: Context? = null
    private var poller: Job? = null
    private var lastError: String? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    fun connect(ctx: Context) {
        appContext = ctx.applicationContext
    }

    @OptIn(UnstableApi::class)
    fun ensurePlayer(ctx: Context): ExoPlayer {
        player?.let { return it }
        val app = ctx.applicationContext
        appContext = app
        val created = ExoPlayer.Builder(app, DefaultRenderersFactory(app).setEnableDecoderFallback(true))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        created.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = publish()
            override fun onPlayerError(error: PlaybackException) {
                lastError = "Can't play this audio"
                publish()
            }
        })
        player = created
        startPolling()
        return created
    }

    fun playList(ctx: Context, songs: List<MediaFile>, start: Int) {
        if (songs.isEmpty()) return
        val p = ensurePlayer(ctx)
        val index = start.coerceIn(0, songs.lastIndex)
        val items = songs.map { s ->
            val uri = playableUri(ctx, s)
            val item = MediaItem.Builder()
                .setUri(uri)
                .setMediaId(s.uri.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.name.substringBeforeLast('.'))
                        .setArtist(s.artist ?: "Unknown artist")
                        .setAlbumTitle(s.album)
                        .setArtworkUri(if (s.albumId > 0) MediaRepository.albumArtUri(s.albumId) else null)
                        .build()
                )
            audioMime(s.name)?.let { item.setMimeType(it) }
            item.build()
        }
        lastError = null
        p.setMediaItems(items, index, 0L)
        p.prepare()
        p.play()
        showNotification(ctx)
        publish()
    }

    fun toggle() {
        val p = player ?: return
        if (p.playerError != null || p.playbackState == Player.STATE_IDLE) {
            lastError = null
            p.prepare()
            p.play()
            appContext?.let { showNotification(it) }
        } else if (p.isPlaying) p.pause() else {
            p.play()
            appContext?.let { showNotification(it) }
        }
        publish()
    }

    fun next() {
        val p = player ?: return
        val stalled = p.playerError != null || p.playbackState == Player.STATE_IDLE
        when {
            p.hasNextMediaItem() -> p.seekToNextMediaItem()
            p.mediaItemCount > 1 -> p.seekTo(0, 0L)
            p.mediaItemCount == 1 -> p.seekTo(0)
        }
        if (stalled) {
            lastError = null
            p.prepare()
        }
        p.play()
        publish()
    }

    fun previous() {
        val p = player ?: return
        val stalled = p.playerError != null || p.playbackState == Player.STATE_IDLE
        when {
            p.currentPosition > 3_000 && p.playerError == null -> p.seekTo(0)
            p.hasPreviousMediaItem() -> p.seekToPreviousMediaItem()
            p.mediaItemCount > 0 -> p.seekTo(0, 0L)
        }
        if (stalled) {
            lastError = null
            p.prepare()
        }
        p.play()
        publish()
    }

    fun seekTo(ms: Long) {
        player?.seekTo(ms)
        publish()
    }

    fun toggleShuffle() {
        player?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
        publish()
    }

    fun cycleRepeat() {
        val p = player ?: return
        p.repeatMode = when (p.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        publish()
    }

    fun stop() {
        player?.let { it.pause(); it.clearMediaItems() }
        publish()
    }

    private fun playableUri(ctx: Context, file: MediaFile): Uri {
        if (file.uri.scheme != "file") return file.uri
        val path = file.path ?: file.uri.path ?: return file.uri
        return runCatching {
            ctx.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                "_data=?",
                arrayOf(path),
                null
            )?.use { c ->
                if (!c.moveToFirst()) null
                else ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
            }
        }.getOrNull() ?: file.uri
    }

    private fun audioMime(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "wav" -> "audio/wav"
        "ogg", "opus" -> "audio/ogg"
        "flac" -> "audio/flac"
        "amr" -> "audio/3gpp"
        else -> null
    }

    private fun showNotification(ctx: Context) {
        val app = ctx.applicationContext
        runCatching {
            ContextCompat.startForegroundService(app, Intent(app, PlaybackService::class.java))
        }
    }

    private fun publish() {
        val c = player ?: return
        if (c.isPlaying || c.playbackState == Player.STATE_READY || c.playbackState == Player.STATE_BUFFERING) {
            if (c.playerError == null) lastError = null
        }
        val md = c.mediaMetadata
        state.value = NowPlaying(
            title = md.title?.toString() ?: "",
            artist = md.artist?.toString() ?: "",
            artwork = md.artworkUri,
            isPlaying = c.isPlaying,
            position = c.currentPosition.coerceAtLeast(0),
            duration = c.duration.takeIf { it > 0 } ?: 0,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            hasMedia = c.mediaItemCount > 0,
            mediaId = c.currentMediaItem?.mediaId,
            error = lastError
        )
    }

    private fun startPolling() {
        poller?.cancel()
        poller = scope.launch {
            while (isActive) {
                if (player?.isPlaying == true) publish()
                delay(500)
            }
        }
    }
}
