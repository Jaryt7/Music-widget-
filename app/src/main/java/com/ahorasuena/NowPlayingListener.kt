package com.ahorasuena

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService

class NowPlayingListener : NotificationListenerService() {

    private var manager: MediaSessionManager? = null
    private var current: MediaController? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            readMetadata(current)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            MediaState.isPlaying = state?.state == PlaybackState.STATE_PLAYING
        }
    }

    override fun onListenerConnected() {
        val m = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        manager = m
        val cn = ComponentName(this, NowPlayingListener::class.java)
        m.addOnActiveSessionsChangedListener(sessionsListener, cn)
        pick(m.getActiveSessions(cn))
        MediaState.connected = true
    }

    override fun onListenerDisconnected() {
        manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        current?.unregisterCallback(callback)
        MediaState.connected = false
    }

    private fun pick(list: List<MediaController>?) {
        val best = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        if (best?.sessionToken == current?.sessionToken) return
        current?.unregisterCallback(callback)
        current = best
        MediaState.controller = best
        best?.registerCallback(callback)
        readMetadata(best)
        MediaState.isPlaying = best?.playbackState?.state == PlaybackState.STATE_PLAYING
    }

    private fun readMetadata(c: MediaController?) {
        val md = c?.metadata
        MediaState.title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        MediaState.artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        MediaState.cover = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        MediaState.durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val uris = listOfNotNull(
            md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
            md?.getString(MediaMetadata.METADATA_KEY_ART_URI),
            md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        ).filter { it.isNotBlank() }
        MediaState.loadCover(applicationContext, uris, MediaState.title, MediaState.artist)
        MediaState.loadLyrics()
    }
}
