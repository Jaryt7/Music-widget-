package com.ahorasuena

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LyricLine(val timeMs: Long, val text: String)

object MediaState {
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var cover by mutableStateOf<Bitmap?>(null)
    var hiCover by mutableStateOf<Bitmap?>(null)
    var isPlaying by mutableStateOf(false)
    var durationMs by mutableStateOf(0L)
    var lyrics by mutableStateOf<List<LyricLine>>(emptyList())
    var synced by mutableStateOf(false)
    var status by mutableStateOf("Reproduce una canción para ver la letra")
    var connected by mutableStateOf(false)
    var controller: MediaController? = null

    private var lastKey = ""
    private var coverKey = ""
    private val main = Handler(Looper.getMainLooper())

    fun currentPosition(): Long {
        val s = controller?.playbackState ?: return 0L
        return if (s.state == PlaybackState.STATE_PLAYING) {
            s.position + ((SystemClock.elapsedRealtime() - s.lastPositionUpdateTime) * s.playbackSpeed).toLong()
        } else s.position
    }

    fun loadCover(ctx: Context, uris: List<String>, t: String, a: String) {
        if (t.isBlank()) return
        val key = "$t|$a"
        if (key == coverKey) return
        coverKey = key
        hiCover = null
        Thread {
            var best: Bitmap? = null
            for (u in uris) {
                val b = tryLoadUri(ctx, u)
                if (b != null) {
                    best = b
                    if (b.width >= 600) break
                }
            }
            if (best == null || best.width < 600) {
                val hi = fetchItunes(t, a)
                if (hi != null) best = hi
            }
            val result = best
            main.post { if (key == coverKey) hiCover = result }
        }.start()
    }

    private fun tryLoadUri(ctx: Context, u: String): Bitmap? = try {
        val uri = Uri.parse(u)
        when (uri.scheme) {
            "http", "https" -> fetchBitmap(u)
            "content", "file", "android.resource" ->
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    private fun fetchItunes(t: String, a: String): Bitmap? = try {
        val body = fetch("https://itunes.apple.com/search?media=music&entity=song&limit=5&term=${enc("$t $a")}")
        if (body == null) {
            null
        } else {
            val results = JSONObject(body).getJSONArray("results")
            val aKey = a.lowercase().split(",", "&", " ").firstOrNull { it.length > 2 } ?: ""
            var art: String? = null
            for (i in 0 until results.length()) {
                val r = results.getJSONObject(i)
                val ra = r.optString("artistName").lowercase()
                if (aKey.isEmpty() || ra.contains(aKey)) {
                    art = r.optString("artworkUrl100")
                    break
                }
            }
            if (art.isNullOrBlank()) null else fetchBitmap(art.replace("100x100bb", "1000x1000bb"))
        }
    } catch (e: Exception) {
        null
    }

    private fun fetchBitmap(u: String): Bitmap? {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        return try {
            if (c.responseCode == 200) BitmapFactory.decodeStream(c.inputStream) else null
        } finally {
            c.disconnect()
        }
    }

    fun loadLyrics() {
        if (title.isBlank()) return
        val key = "$title|$artist"
        if (key == lastKey) return
        lastKey = key
        lyrics = emptyList()
        synced = false
        status = "Buscando letra..."
        val t = title
        val a = artist
        val d = durationMs / 1000
        Thread {
            var newLyrics: List<LyricLine> = emptyList()
            var newSynced = false
            var newStatus = "No se encontró la letra"
            try {
                var url = "https://lrclib.net/api/get?track_name=${enc(t)}&artist_name=${enc(a)}"
                if (d > 0) url += "&duration=$d"
                var obj: JSONObject? = fetch(url)?.let { JSONObject(it) }
                if (obj == null) {
                    val arr = fetch("https://lrclib.net/api/search?q=${enc("$t $a")}")?.let { JSONArray(it) }
                    if (arr != null && arr.length() > 0) obj = arr.getJSONObject(0)
                }
                if (obj != null) {
                    val syncedText = obj.str("syncedLyrics")
                    val plainText = obj.str("plainLyrics")
                    if (syncedText.isNotBlank()) {
                        newLyrics = parseLrc(syncedText)
                        newSynced = true
                        newStatus = ""
                    } else if (plainText.isNotBlank()) {
                        newLyrics = plainText.lines().map { LyricLine(-1, it) }
                        newStatus = ""
                    }
                }
            } catch (e: Exception) {
                newStatus = "No se pudo cargar la letra"
            }
            main.post {
                if (key == lastKey) {
                    lyrics = newLyrics
                    synced = newSynced
                    status = newStatus
                    if (newStatus == "No se pudo cargar la letra") lastKey = ""
                }
            }
        }.start()
    }

    private fun parseLrc(lrc: String): List<LyricLine> {
        val re = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")
        val out = mutableListOf<LyricLine>()
        for (line in lrc.lines()) {
            val matches = re.findAll(line).toList()
            if (matches.isEmpty()) continue
            val text = line.substring(matches.last().range.last + 1).trim()
            for (m in matches) {
                val seconds = m.groupValues[1].toLong() * 60 + m.groupValues[2].toDouble()
                out.add(LyricLine((seconds * 1000).toLong(), text))
            }
        }
        return out.sortedBy { it.timeMs }
    }

    private fun fetch(u: String): String? {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "AhoraSuena/1.0")
        return try {
            if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null
        } finally {
            c.disconnect()
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k, "")
}
