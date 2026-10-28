package com.vistamusic.app

import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.net.URL

/**
 * The only owner of ExoPlayer. Survives the UI being backgrounded and publishes
 * a real system media session to Android notifications and the lock screen.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    companion object {
        const val PLAY_QUEUE = "com.vistamusic.app.PLAY_QUEUE"
        const val NEXT = "com.vistamusic.app.NEXT"
        const val PREVIOUS = "com.vistamusic.app.PREVIOUS"
        const val TOGGLE_FAVORITE = "com.vistamusic.app.TOGGLE_FAVORITE"
        const val ACTION_SHADE_LIKE = "com.vistamusic.app.NOTIFICATION_LIKE"
        const val QUEUE_JSON = "queue_json"
        const val QUEUE_INDEX = "queue_index"
        const val SONG_JSON = "vistamusic_song_json"
        const val ACTION_PAUSE_TOGGLE = "com.vistamusic.app.NOTIFICATION_PLAY_PAUSE"
        const val ACTION_SHADE_NEXT = "com.vistamusic.app.NOTIFICATION_NEXT"
        const val ACTION_SHADE_PREVIOUS = "com.vistamusic.app.NOTIFICATION_PREVIOUS"
        private const val SHADE_CHANNEL = "vistamusic_visible_controls"
        private const val SHADE_NOTIFICATION_ID = 1337
    }

    private lateinit var playback: ExoPlayer
    private var session: MediaSession? = null
    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val taste by lazy {
        LocalTaste(getSharedPreferences("vistamusic", MODE_PRIVATE))
    }
    private val catalog = LibraryCatalog()
    private var queue: List<Song> = emptyList()
    private var index = 0
    private var serial = 0
    private var changing = false
    private var currentArtJob: Job? = null
    private lateinit var notificationManager: NotificationManager
    private var currentArtwork: Bitmap? = null
    private lateinit var backwardButton: CommandButton
    private lateinit var forwardButton: CommandButton
    private lateinit var favoritePrefs: SharedPreferences
    private val favoritesChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "favorites" && ::playback.isInitialized) {
                refreshMediaButtons()
                updateShadeNotification()
            }
        }

    private fun activeSong(): Song? {
        val json = playback.currentMediaItem?.mediaMetadata?.extras
            ?.getString(SONG_JSON).orEmpty()
        return runCatching { Song.fromJson(org.json.JSONObject(json)) }
            .getOrNull() ?: queue.getOrNull(index)
    }

    private fun storedFavorites(): List<Song> {
        val raw = favoritePrefs.getString("favorites", "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { obj ->
                runCatching { Song.fromJson(obj) }.getOrNull()
            }
        }
    }

    private fun isLiked(song: Song): Boolean =
        storedFavorites().any { it.id == song.id }

    private fun toggleFavorite() {
        val song = activeSong() ?: return
        val liked = storedFavorites()
        val changed = if (liked.any { it.id == song.id })
            liked.filterNot { it.id == song.id }
        else listOf(song) + liked
        val array = JSONArray()
        changed.forEach { array.put(it.toJson()) }
        favoritePrefs.edit().putString("favorites", array.toString()).apply()
        refreshMediaButtons()
        updateShadeNotification()
    }

    private fun heartButton(): CommandButton {
        val liked = if (::favoritePrefs.isInitialized)
            activeSong()?.let(::isLiked) == true else false
        return CommandButton.Builder(
            if (liked) CommandButton.ICON_HEART_FILLED
            else CommandButton.ICON_HEART_UNFILLED
        )
            .setDisplayName(if (liked) "Remover dos favoritos" else "Curtir música")
            .setSessionCommand(SessionCommand(TOGGLE_FAVORITE, Bundle.EMPTY))
            .build()
    }

    private fun refreshMediaButtons() {
        val currentSession = session ?: return
        if (!::backwardButton.isInitialized || !::forwardButton.isInitialized) return
        val buttons = listOf(backwardButton, forwardButton, heartButton())
        currentSession.setMediaButtonPreferences(buttons)
        currentSession.setCustomLayout(buttons)
    }

    private fun activityIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_CURRENT_PLAYER", true)
        }
        return PendingIntent.getActivity(this, 80, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun actionIntent(action: String, requestId: Int): PendingIntent =
        PendingIntent.getService(this, requestId,
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun updateShadeNotification() {
        if (!::notificationManager.isInitialized || !::playback.isInitialized) return
        val item = playback.currentMediaItem ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return

        val playing = playback.isPlaying || playback.playWhenReady
        val currentTitle = item.mediaMetadata.title?.toString().orEmpty()
        val currentArtist = item.mediaMetadata.artist?.toString().orEmpty()
        val currentSession = session ?: return
        val liked = activeSong()?.let(::isLiked) == true
        val content = NotificationCompat.Builder(this, SHADE_CHANNEL)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(currentSession)
                .setShowActionsInCompactView(0, 1, 2))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentTitle.ifBlank { "VistaMusic" })
            .setContentText(currentArtist.ifBlank { "Reprodução musical" })
            .setColor(Color.rgb(255, 79, 163))
            .setColorized(false)
            .setLargeIcon(currentArtwork)
            .setContentIntent(activityIntent())
            .setAutoCancel(false)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Anterior",
                actionIntent(ACTION_SHADE_PREVIOUS, 91))
            .addAction(if (playing) android.R.drawable.ic_media_pause
                       else android.R.drawable.ic_media_play,
                if (playing) "Pausar" else "Reproduzir",
                actionIntent(ACTION_PAUSE_TOGGLE, 92))
            .addAction(android.R.drawable.ic_media_next, "Próxima",
                actionIntent(ACTION_SHADE_NEXT, 93))
            .addAction(
                if (liked) R.drawable.ic_notification_heart_filled
                else R.drawable.ic_notification_heart,
                if (liked) "Remover favorito" else "Curtir",
                actionIntent(ACTION_SHADE_LIKE, 94)
            )
            .build()
        notificationManager.notify(SHADE_NOTIFICATION_ID, content)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_TOGGLE -> {
                if (playback.isPlaying) playback.pause() else playback.play()
                updateShadeNotification()
            }
            ACTION_SHADE_NEXT -> { playNext(false); updateShadeNotification() }
            ACTION_SHADE_PREVIOUS -> { playPrevious(); updateShadeNotification() }
            ACTION_SHADE_LIKE -> toggleFavorite()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        favoritePrefs = getSharedPreferences("vistamusic", MODE_PRIVATE)
        favoritePrefs.registerOnSharedPreferenceChangeListener(favoritesChangeListener)
        val channel = NotificationChannel(
            "vistamusic_playback", "Reprodução de música",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Controlos de reprodução do VistaMusic"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
        notificationManager.createNotificationChannel(
            NotificationChannel(SHADE_CHANNEL, "Leitor nas notificações",
                NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Mostra a música e botões de reprodução no painel de notificações"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId("vistamusic_playback").build()
        )
        playback = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(), true
            )
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    updateShadeNotification()
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED && !changing) {
                        queue.getOrNull(index)?.let { taste.add(it, "complete") }
                        playNext(fromCompletion = true)
                    }
                }
            })
        }

        val pendingIntent = activityIntent()
        backwardButton = CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setDisplayName("Anterior")
            .setSessionCommand(SessionCommand(PREVIOUS, Bundle.EMPTY))
            .build()
        forwardButton = CommandButton.Builder(CommandButton.ICON_NEXT)
            .setDisplayName("Próxima")
            .setSessionCommand(SessionCommand(NEXT, Bundle.EMPTY))
            .build()

        session = MediaSession.Builder(this, playback)
            .setSessionActivity(pendingIntent)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val commands =
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                            .add(SessionCommand(PLAY_QUEUE, Bundle.EMPTY))
                            .add(SessionCommand(NEXT, Bundle.EMPTY))
                            .add(SessionCommand(PREVIOUS, Bundle.EMPTY))
                            .add(SessionCommand(TOGGLE_FAVORITE, Bundle.EMPTY))
                            .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(mediaSession)
                        .setAvailableSessionCommands(commands)
                        .build()
                }

                override fun onMediaButtonEvent(
                    mediaSession: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent
                ): Boolean {
                    val event = if (android.os.Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    }
                    if (event?.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) {
                        return false
                    }
                    return when (event.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            playNext(false)
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            playPrevious()
                            true
                        }
                        else -> false
                    }
                }

                override fun onCustomCommand(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        PLAY_QUEUE -> {
                            val raw = args.getString(QUEUE_JSON).orEmpty()
                            val incoming = runCatching {
                                val array = JSONArray(raw)
                                (0 until array.length()).mapNotNull { pos ->
                                    array.optJSONObject(pos)?.let { Song.fromJson(it) }
                                }
                            }.getOrDefault(emptyList())
                            if (incoming.isNotEmpty()) {
                                queue = incoming.take(200)
                                index = args.getInt(QUEUE_INDEX, 0).coerceIn(queue.indices)
                                loadCurrent()
                            }
                        }
                        NEXT -> playNext(fromCompletion = false)
                        PREVIOUS -> playPrevious()
                        TOGGLE_FAVORITE -> toggleFavorite()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .setCustomLayout(listOf(backwardButton, forwardButton, heartButton()))
            .setMediaButtonPreferences(listOf(backwardButton, forwardButton, heartButton()))
            .build()
        refreshMediaButtons()
    }

    private fun fallbackArt(song: Song): ByteArray {
        val size = 384
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val tones = arrayOf(
            intArrayOf(0xFF315A56.toInt(), 0xFF172E42.toInt()),
            intArrayOf(0xFF565078.toInt(), 0xFF2E2B42.toInt()),
            intArrayOf(0xFF6B515D.toInt(), 0xFF34253E.toInt())
        )[kotlin.math.abs(song.id.hashCode().toLong() % 3).toInt()]
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(),
                tones[0], tones[1], Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.shader = null
        paint.color = Color.WHITE
        paint.alpha = 180
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 164f
        canvas.drawText("♪", size / 2f, size / 2f + 55f, paint)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 95, output)
        bitmap.recycle()
        return output.toByteArray()
    }

    private fun metadata(song: Song, artwork: ByteArray? = null): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .setTitle(LyricsApi.cleanTitle(song.title, song.artist))
            .setArtist(song.artist)
            .setAlbumArtist(song.artist)
            .setIsPlayable(true)
            .setExtras(Bundle().apply { putString(SONG_JSON, song.toJson().toString()) })
        if (artwork != null) builder.setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        return builder.build()
    }

    private suspend fun artworkBytes(song: Song): ByteArray = withContext(Dispatchers.IO) {
        val url = runCatching { AlbumArtwork.resolve(song) }.getOrDefault("")
        if (url.isBlank() || !url.startsWith("https://")) return@withContext fallbackArt(song)
        runCatching {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 6500
            val data = connection.getInputStream().use { it.readBytes() }
            val original = BitmapFactory.decodeByteArray(data, 0, data.size)
                ?: return@runCatching fallbackArt(song)
            val scaled = Bitmap.createScaledBitmap(original, 440, 440, true)
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)
            if (scaled != original) scaled.recycle()
            original.recycle()
            output.toByteArray()
        }.getOrElse { fallbackArt(song) }
    }

    /**
     * Keep a MediaItem in ExoPlayer's timeline throughout asynchronous source
     * lookup. Previously stop()+clearMediaItems() removed the media session's
     * only item; Android consequently dismissed the SystemUI media card.
     *
     * The placeholder URI is deliberately never prepared or opened.
     */
    private fun loadCurrent() {
        val requested = queue.getOrNull(index) ?: return
        serial += 1
        val ticket = serial
        currentArtJob?.cancel()
        changing = true

        val placeholder = MediaItem.Builder()
            .setMediaId(requested.id)
            .setUri(Uri.parse("https://127.0.0.1/vistamusic-waiting"))
            .setMediaMetadata(metadata(requested, fallbackArt(requested)))
            .build()
        // One atomic timeline change. NEVER clear the playlist first.
        playback.setMediaItem(placeholder)
        refreshMediaButtons()
        currentArtwork = null
        updateShadeNotification()
        // Set playWhenReady immediately so SystemUI knows this media session
        // represents an active user-requested playback during source discovery.
        playback.play()
        changing = false

        playbackScope.launch {
            val song = when {
                requested.audio.startsWith("youtube:") ->
                    runCatching { NativeMusic.musicVersion(requested) }.getOrDefault(requested)
                requested.audio.startsWith("discover:") -> {
                    val tracks = runCatching {
                        NativeMusic.search(requested.audio.removePrefix("discover:"))
                    }.getOrDefault(emptyList())
                    val candidate = tracks.firstOrNull()
                    if (candidate != null) candidate.copy(
                        title = requested.title.ifBlank { candidate.title },
                        artist = requested.artist.ifBlank { candidate.artist }
                    ) else requested
                }
                else -> requested
            }
            if (ticket != serial) return@launch

            val cover = async { artworkBytes(song) }
            val source = runCatching {
                when {
                    song.audio.startsWith("youtube:") ->
                        NativeMusic.resolve(song.audio.removePrefix("youtube:"))
                    song.audio.startsWith("discover:") -> {
                        val matches = NativeMusic.search(song.audio.removePrefix("discover:"))
                        val id = matches.firstOrNull()?.audio?.removePrefix("youtube:")
                            ?: error("Song not found")
                        NativeMusic.resolve(id)
                    }
                    else -> song.audio
                }
            }.getOrNull()

            if (ticket != serial || source.isNullOrBlank()) {
                cover.cancel()
                if (ticket == serial) {
                    // Preserve the track details; don't pretend audio is playing.
                    playback.pause()
                    android.util.Log.w("VistaMusicMedia",
                        "Could not resolve the audio stream for " + requested.title)
                }
                return@launch
            }

            val ready = MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(source)
                .setMediaMetadata(metadata(song, fallbackArt(song)))
                .build()
            // Preserve play/pause state selected in the notification while loading.
            val resume = playback.playWhenReady
            changing = true
            playback.replaceMediaItem(0, ready)
            refreshMediaButtons()
            playback.prepare()
            playback.playWhenReady = resume
            changing = false
            taste.add(song, "play")
            currentArtJob = launch {
                val art = runCatching { cover.await() }.getOrNull()
                if (ticket == serial && art != null) {
                    val picture = BitmapFactory.decodeByteArray(art, 0, art.size)
                    currentArtwork = picture
                    val current = playback.currentMediaItem ?: return@launch
                    playback.replaceMediaItem(
                        0, current.buildUpon()
                            .setMediaMetadata(metadata(song, art)).build()
                    )
                    updateShadeNotification()
                }
            }
        }
    }

    private fun playPrevious() {
        if (playback.currentPosition > 3500L) {
            playback.seekTo(0)
            return
        }
        if (index > 0) {
            index--
            loadCurrent()
        } else {
            playback.seekTo(0)
        }
    }

    private fun playNext(fromCompletion: Boolean) {
        if (!fromCompletion) {
            queue.getOrNull(index)?.let { taste.add(it, "skip") }
        }
        if (index + 1 < queue.size) {
            index++
            loadCurrent()
            return
        }
        val current = queue.getOrNull(index) ?: return
        val prefs = getSharedPreferences("vistamusic", MODE_PRIVATE)
        if (!prefs.getBoolean("auto_dj", true)) return
        playbackScope.launch {
            val suggested = runCatching {
                val related = if (current.audio.startsWith("youtube:"))
                    runCatching {
                        NativeMusic.relatedTo(current.audio.removePrefix("youtube:"))
                    }.getOrDefault(emptyList())
                else emptyList()
                val chart = runCatching { NativeMusic.trending("US") }
                    .getOrDefault(emptyList())
                val discovered = taste.nextSongs(current, queue.map { it.id }.toSet(),
                    emptyList(), chart, catalog)
                (related + discovered).distinctBy { it.id }
                    .filterNot { next -> queue.any { it.id == next.id } }.take(15)
            }.getOrDefault(emptyList())
            if (queue.getOrNull(index)?.id != current.id || suggested.isEmpty()) return@launch
            queue = queue + suggested
            index++
            loadCurrent()
        }
    }

    override fun onUpdateNotification(
        session: MediaSession, startInForegroundRequired: Boolean
    ) {
        super.onUpdateNotification(session, startInForegroundRequired)
        android.util.Log.d("VistaMusicMedia",
            "System media notification updated; foreground=" + startInForegroundRequired +
                " items=" + playback.mediaItemCount +
                " state=" + playback.playbackState)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        serial++
        currentArtJob?.cancel()
        if (::favoritePrefs.isInitialized)
            favoritePrefs.unregisterOnSharedPreferenceChangeListener(favoritesChangeListener)
        if (::notificationManager.isInitialized)
            notificationManager.cancel(SHADE_NOTIFICATION_ID)
        currentArtwork = null
        session?.release()
        playback.release()
        playbackScope.cancel()
        session = null
        super.onDestroy()
    }
}
