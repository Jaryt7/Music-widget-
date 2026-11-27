package com.ahorasuena

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService

object SessionReader {
    private var current: MediaController? = null
    private var lastRebind = 0L

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            val c = current ?: return
            val ctx = appCtx ?: return
            readInto(ctx, c, true)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            MediaState.isPlaying = state?.state == PlaybackState.STATE_PLAYING
        }

        override fun onSessionDestroyed() {
            current = null
        }
    }

    private var appCtx: Context? = null

    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        appCtx = app
        try {
            val m = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val cn = ComponentName(app, NowPlayingListener::class.java)
            pick(app, m.getActiveSessions(cn))
        } catch (e: SecurityException) {
            // todavía sin acceso a notificaciones
        } catch (e: Exception) {
            // se reintenta en el siguiente ciclo
        }

        if (!MediaState.connected) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastRebind > 20000) {
                lastRebind = now
                try {
                    NotificationListenerService.requestRebind(ComponentName(app, NowPlayingListener::class.java))
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun safeUnregister(c: MediaController?) {
        try {
            c?.unregisterCallback(callback)
        } catch (e: Exception) {
        }
    }

    private fun pick(ctx: Context, list: List<MediaController>?) {
        val best = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()

        if (best == null) {
            safeUnregister(current)
            current = null
            MediaState.controller = null
            MediaState.isPlaying = false
            return
        }

        val changed = best.sessionToken != current?.sessionToken
        if (changed) {
            safeUnregister(current)
            current = best
            MediaState.controller = best
            best.registerCallback(callback)
        }
        MediaState.isPlaying = best.playbackState?.state == PlaybackState.STATE_PLAYING
        readInto(ctx, best, changed)
    }

    private fun readInto(ctx: Context, c: MediaController, force: Boolean) {
        val md = c.metadata
        val t = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        if (t.isBlank()) return
        val a = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val d = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

        val changed = force || t != MediaState.title || a != MediaState.artist || d != MediaState.durationMs
        if (changed) {
            MediaState.title = t
            MediaState.artist = a
            MediaState.durationMs = d
            MediaState.cover = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            val uris = listOfNotNull(
                md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
                md?.getString(MediaMetadata.METADATA_KEY_ART_URI),
                md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            ).filter { it.isNotBlank() }
            MediaState.loadCover(ctx, uris, t, a)
            MediaState.loadLyrics()
        } else if (MediaState.cover == null) {
            MediaState.cover = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        }
    }
}

class NowPlayingListener : NotificationListenerService() {

    private var manager: MediaSessionManager? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener {
        SessionReader.refresh(applicationContext)
    }

    override fun onListenerConnected() {
        val m = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        manager = m
        val cn = ComponentName(this, NowPlayingListener::class.java)
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, cn)
        } catch (e: SecurityException) {
        }
        MediaState.connected = true
        SessionReader.refresh(applicationContext)
    }

    override fun onListenerDisconnected() {
        try {
            manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
        }
        MediaState.connected = false
        try {
            NotificationListenerService.requestRebind(ComponentName(this, NowPlayingListener::class.java))
        } catch (e: Exception) {
        }
    }
}
