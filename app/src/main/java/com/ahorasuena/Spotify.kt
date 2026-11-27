package com.ahorasuena

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors

data class PlaylistInfo(
    val id: String,
    val name: String,
    val uri: String,
    val imageUrl: String?,
    val total: Int,
    val owner: String
)

object Spotify {
    var loggedIn by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var msg by mutableStateOf("")
    val playlists = mutableStateListOf<PlaylistInfo>()
    val covers = mutableStateMapOf<String, Bitmap>()

    private class Resp(val code: Int, val body: String)

    private var sp: SharedPreferences? = null
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(4)
    private val requested = HashSet<String>()
    private var verifier = ""
    private var authState = ""

    @Volatile
    private var server: ServerSocket? = null

    private fun onMain(block: () -> Unit) {
        main.post { block() }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("spotify", Context.MODE_PRIVATE)
        sp = p
        loggedIn = !p.getString("refresh", null).isNullOrEmpty()
    }

    private fun randomString(n: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val rnd = SecureRandom()
        return String(CharArray(n) { chars[rnd.nextInt(chars.length)] })
    }

    private fun http(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): Resp {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 10000
        c.readTimeout = 10000
        for ((k, v) in headers) c.setRequestProperty(k, v)
        if (body != null) {
            c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        return try {
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            Resp(code, stream?.bufferedReader()?.readText() ?: "")
        } finally {
            c.disconnect()
        }
    }

    // ---------- Inicio de sesión (PKCE) ----------

    fun login(ctx: Context) {
        if (SpotifyConfig.CLIENT_ID.startsWith("PEGA")) {
            msg = "Falta pegar tu Client ID en SpotifyConfig.kt"
            return
        }
        verifier = randomString(64)
        authState = randomString(16)
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        startServer()

        val url = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", SpotifyConfig.CLIENT_ID)
            .appendQueryParameter(
                "scope",
                "playlist-read-private playlist-read-collaborative user-read-playback-state user-modify-playback-state"
            )
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("redirect_uri", SpotifyConfig.REDIRECT_URI)
            .appendQueryParameter("state", authState)
            .build()

        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            msg = "Autoriza en el navegador y vuelve a la app"
        } catch (e: Exception) {
            msg = "No se pudo abrir el navegador"
        }
    }

    private fun startServer() {
        try {
            server?.close()
        } catch (e: Exception) {
        }
        val myState = authState
        val myVerifier = verifier

        Thread {
            var opened: ServerSocket? = null
            try {
                val sock = ServerSocket(SpotifyConfig.REDIRECT_PORT, 5, InetAddress.getByName("127.0.0.1"))
                opened = sock
                server = sock
                val deadline = SystemClock.elapsedRealtime() + 180000
                var code: String? = null
                var failed = false

                while (SystemClock.elapsedRealtime() < deadline) {
                    sock.soTimeout = (deadline - SystemClock.elapsedRealtime()).toInt().coerceAtLeast(1)
                    val s = try {
                        sock.accept()
                    } catch (e: SocketTimeoutException) {
                        break
                    }
                    s.soTimeout = 3000
                    val line = try {
                        s.getInputStream().bufferedReader().readLine()
                    } catch (e: Exception) {
                        null
                    }
                    if (line == null || !line.startsWith("GET /callback")) {
                        try {
                            s.close()
                        } catch (e: Exception) {
                        }
                        continue
                    }

                    val path = line.split(" ").getOrNull(1) ?: ""
                    val uri = Uri.parse("http://127.0.0.1$path")
                    val c = uri.getQueryParameter("code")
                    val ok = c != null &&
                        uri.getQueryParameter("state") == myState &&
                        uri.getQueryParameter("error") == null

                    try {
                        val title = if (ok) "Listo" else "No se pudo conectar"
                        val text = if (ok) "Ya puedes volver a la app Ahora suena." else "Vuelve a la app e inténtalo otra vez."
                        val html = "<html><head><meta charset=\"utf-8\">" +
                            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>" +
                            "<body style=\"font-family:sans-serif;background:#111;color:#fff;text-align:center;padding:48px\">" +
                            "<h2>$title</h2><p>$text</p></body></html>"
                        val bytes = html.toByteArray(Charsets.UTF_8)
                        val out = s.getOutputStream()
                        out.write(
                            ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n")
                                .toByteArray(Charsets.UTF_8)
                        )
                        out.write(bytes)
                        out.flush()
                        s.close()
                    } catch (e: Exception) {
                    }

                    if (ok) code = c else failed = true
                    break
                }

                val result = code
                val wasCancelled = failed
                if (result != null) {
                    exchange(result, myVerifier)
                } else {
                    onMain { msg = if (wasCancelled) "Autorización cancelada" else "Se agotó el tiempo, inténtalo otra vez" }
                }
            } catch (e: Exception) {
                onMain { msg = "No se pudo abrir el puerto ${SpotifyConfig.REDIRECT_PORT}. Cierra y abre la app e inténtalo otra vez" }
            } finally {
                try {
                    opened?.close()
                } catch (e: Exception) {
                }
                if (server === opened) server = null
            }
        }.start()
    }

    private fun exchange(code: String, myVerifier: String) {
        try {
            val r = http(
                "POST", "https://accounts.spotify.com/api/token",
                mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                "client_id=${enc(SpotifyConfig.CLIENT_ID)}&grant_type=authorization_code&code=${enc(code)}" +
                    "&redirect_uri=${enc(SpotifyConfig.REDIRECT_URI)}&code_verifier=${enc(myVerifier)}"
            )
            if (r.code == 200) {
                saveTokens(JSONObject(r.body))
                onMain {
                    loggedIn = true
                    msg = ""
                }
                loadPlaylists()
            } else {
                onMain { msg = "Spotify no aceptó el inicio de sesión (${r.code})" }
            }
        } catch (e: Exception) {
            onMain { msg = "No se pudo completar el inicio de sesión" }
        }
    }

    private fun saveTokens(j: JSONObject) {
        val e = sp?.edit() ?: return
        e.putString("access", j.optString("access_token"))
        e.putLong("expires", System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000)
        val refresh = j.optString("refresh_token", "")
        if (refresh.isNotEmpty()) e.putString("refresh", refresh)
        e.apply()
    }

    private fun validToken(): String? {
        val p = sp ?: return null
        val access = p.getString("access", null)
        val exp = p.getLong("expires", 0L)
        if (access != null && System.currentTimeMillis() < exp - 60000) return access
        val refresh = p.getString("refresh", null) ?: return null
        val r = http(
            "POST", "https://accounts.spotify.com/api/token",
            mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            "grant_type=refresh_token&refresh_token=${enc(refresh)}&client_id=${enc(SpotifyConfig.CLIENT_ID)}"
        )
        if (r.code == 200) {
            saveTokens(JSONObject(r.body))
            return p.getString("access", null)
        }
        if (r.body.contains("invalid_grant")) logout()
        return null
    }

    fun logout() {
        sp?.edit()?.clear()?.apply()
        onMain {
            loggedIn = false
            playlists.clear()
            covers.clear()
            synchronized(requested) { requested.clear() }
            msg = ""
        }
    }

    // ---------- Playlists ----------

    private fun parse(o: JSONObject): PlaylistInfo? {
        val id = o.optString("id", "")
        if (id.isEmpty()) return null
        val imgs = o.optJSONArray("images")
        var img: String? = null
        if (imgs != null && imgs.length() > 0) {
            val idx = if (imgs.length() > 1) 1 else 0
            img = imgs.optJSONObject(idx)?.optString("url")
        }
        val total = o.optJSONObject("items")?.optInt("total", -1)
            ?: o.optJSONObject("tracks")?.optInt("total", -1)
            ?: -1
        val owner = o.optJSONObject("owner")?.optString("display_name", "") ?: ""
        return PlaylistInfo(
            id,
            o.optString("name", "Sin nombre"),
            o.optString("uri", "spotify:playlist:$id"),
            img,
            total,
            owner
        )
    }

    fun loadPlaylists() {
        onMain {
            loading = true
            msg = ""
        }
        Thread {
            try {
                val token = validToken()
                if (token == null) {
                    onMain {
                        loading = false
                        msg = "Inicia sesión de nuevo"
                    }
                    return@Thread
                }
                val all = mutableListOf<PlaylistInfo>()
                var url: String? = "https://api.spotify.com/v1/me/playlists?limit=50"
                while (url != null && all.size < 300) {
                    val r = http("GET", url, mapOf("Authorization" to "Bearer $token"))
                    if (r.code == 401) {
                        sp?.edit()?.remove("access")?.apply()
                    }
                    if (r.code != 200) {
                        val code = r.code
                        onMain {
                            loading = false
                            msg = "Spotify respondió con el código $code"
                        }
                        return@Thread
                    }
                    val j = JSONObject(r.body)
                    val arr = j.optJSONArray("items")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            val p = parse(o)
                            if (p != null) all.add(p)
                        }
                    }
                    url = if (j.isNull("next")) null else j.optString("next")
                }
                onMain {
                    playlists.clear()
                    playlists.addAll(all)
                    loading = false
                    msg = if (all.isEmpty()) "No se encontraron playlists" else ""
                }
            } catch (e: Exception) {
                onMain {
                    loading = false
                    msg = "No se pudieron cargar las playlists"
                }
            }
        }.start()
    }

    fun ensureCover(p: PlaylistInfo) {
        val u = p.imageUrl ?: return
        synchronized(requested) {
            if (!requested.add(p.id)) return
        }
        pool.execute {
            try {
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                val b = try {
                    BitmapFactory.decodeStream(c.inputStream)
                } finally {
                    c.disconnect()
                }
                if (b != null) onMain { covers[p.id] = b }
            } catch (e: Exception) {
                synchronized(requested) { requested.remove(p.id) }
            }
        }
    }

    // ---------- Reproducir ----------

    fun play(ctx: Context, p: PlaylistInfo) {
        val app = ctx.applicationContext
        onMain { msg = "Conectando con Spotify…" }
        Thread {
            try {
                val token = validToken()
                if (token == null) {
                    onMain { msg = "Inicia sesión de nuevo" }
                    return@Thread
                }
                val h = mapOf("Authorization" to "Bearer $token")
                var code = startPlayback(h, p.uri)
                if (code == 404) {
                    onMain { openInSpotify(app, p.uri) }
                    Thread.sleep(3500)
                    code = startPlayback(h, p.uri)
                }
                val finalCode = code
                onMain {
                    msg = when {
                        finalCode in 200..299 -> "Reproduciendo: ${p.name}"
                        finalCode == 403 -> "Spotify no permitió reproducir (se necesita Premium)"
                        else -> "Abre la playlist en Spotify y toca play"
                    }
                }
            } catch (e: Exception) {
                onMain { msg = "No se pudo conectar con Spotify" }
            }
        }.start()
    }

    private fun startPlayback(h: Map<String, String>, uri: String): Int {
        var deviceQuery = ""
        val d = http("GET", "https://api.spotify.com/v1/me/player/devices", h)
        if (d.code == 200) {
            val arr = JSONObject(d.body).optJSONArray("devices")
            if (arr != null && arr.length() > 0) {
                var pick: JSONObject? = null
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optBoolean("is_active")) {
                        pick = o
                        break
                    }
                    if (pick == null && !o.optBoolean("is_restricted")) pick = o
                }
                val id = pick?.optString("id", "") ?: ""
                if (id.isNotEmpty()) deviceQuery = "?device_id=" + enc(id)
            }
        }
        val r = http(
            "PUT", "https://api.spotify.com/v1/me/player/play$deviceQuery",
            h + mapOf("Content-Type" to "application/json"),
            "{\"context_uri\":\"$uri\"}"
        )
        return r.code
    }

    private fun openInSpotify(ctx: Context, uri: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            msg = "Spotify no está instalado en este dispositivo"
        }
    }
}
