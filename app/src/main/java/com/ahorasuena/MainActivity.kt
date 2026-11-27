package com.ahorasuena

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

val LocalK = compositionLocalOf { 1f }

@Composable
fun px(v: Float): Dp = (v * LocalK.current).dp

@Composable
fun spx(v: Float): TextUnit = (v * LocalK.current).sp

object AppPrefs {
    var theme by mutableStateOf("cover")
    var font by mutableStateOf("sans")
    var lyricSize by mutableStateOf(1)
    var bgSpeed by mutableStateOf(2)
    var lyricBlur by mutableStateOf(true)

    private var sp: SharedPreferences? = null

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("ahora_suena", Context.MODE_PRIVATE)
        sp = p
        theme = p.getString("theme", "cover") ?: "cover"
        font = p.getString("font", "sans") ?: "sans"
        lyricSize = p.getInt("lyricSize", 1)
        bgSpeed = p.getInt("bgSpeed", 2)
        lyricBlur = p.getBoolean("lyricBlur", true)
    }

    fun save() {
        sp?.edit()
            ?.putString("theme", theme)
            ?.putString("font", font)
            ?.putInt("lyricSize", lyricSize)
            ?.putInt("bgSpeed", bgSpeed)
            ?.putBoolean("lyricBlur", lyricBlur)
            ?.apply()
    }
}

data class ThemeDef(val id: String, val label: String, val base: Color?, val blobs: List<Color>?)

val THEMES = listOf(
    ThemeDef("cover", "Portada", null, null),
    ThemeDef(
        "black", "Negro", Color(0xFF000000),
        listOf(Color(0xFF1C1C1C), Color(0xFF0E0E0E), Color(0xFF000000), Color(0xFF262626), Color(0xFF121212))
    ),
    ThemeDef(
        "red", "Rojo", Color(0xFF1A0003),
        listOf(Color(0xFFD50A2B), Color(0xFF7A0010), Color(0xFF000000), Color(0xFFFF4D4D), Color(0xFF4A0008))
    ),
    ThemeDef(
        "blue", "Azul", Color(0xFF020A24),
        listOf(Color(0xFF1E3CFF), Color(0xFF0B1E7A), Color(0xFF000000), Color(0xFF4D8BFF), Color(0xFF081250))
    ),
    ThemeDef(
        "purple", "Morado", Color(0xFF12002B),
        listOf(Color(0xFF8A2BFF), Color(0xFF45107A), Color(0xFF000000), Color(0xFFC77DFF), Color(0xFF2A0A55))
    ),
    ThemeDef(
        "green", "Verde", Color(0xFF011A0C),
        listOf(Color(0xFF1DB954), Color(0xFF0B5D2A), Color(0xFF000000), Color(0xFF5BE38F), Color(0xFF063D1C))
    ),
    ThemeDef(
        "pink", "Rosa", Color(0xFF2A0014),
        listOf(Color(0xFFFF3D8B), Color(0xFF8A1450), Color(0xFF000000), Color(0xFFFF8FBF), Color(0xFF4A0A2B))
    )
)

class MainActivity : ComponentActivity() {
    private var hasAccess by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        AppPrefs.load(applicationContext)
        Spotify.init(applicationContext)
        setConten
