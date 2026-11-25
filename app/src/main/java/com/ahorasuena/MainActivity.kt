package com.ahorasuena

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
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
import kotlin.math.abs
import kotlin.math.roundToInt

val Serif = FontFamily.Serif
val LocalK = compositionLocalOf { 1f }

@Composable
fun px(v: Float): Dp = (v * LocalK.current).dp

@Composable
fun spx(v: Float): TextUnit = (v * LocalK.current).sp

class MainActivity : ComponentActivity() {
    private var hasAccess by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                if (hasAccess) {
                    NowPlayingScreen()
                } else {
                    AccessScreen {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        hasAccess = flat.contains(packageName)
    }
}

fun Modifier.fadeVertical(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.14f to Color.Black,
                0.86f to Color.Black,
                1f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
        )
    }

data class BgInfo(val bmp: Bitmap, val lum: Float)

@Composable
fun CoverBackground(content: @Composable BoxScope.() -> Unit) {
    val cover = MediaState.hiCover ?: MediaState.cover
    val info = remember(cover) {
        cover?.let {
            val mid = Bitmap.createScaledBitmap(it, 160, 160, true)
            val small = Bitmap.createScaledBitmap(mid, 24, 24, true)
            val pixels = IntArray(24 * 24)
            small.getPixels(pixels, 0, 24, 0, 0, 24, 24)
            var sum = 0f
            for (c in pixels) {
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                sum += (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            }
            BgInfo(small, sum / pixels.size)
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(targetState = info, animationSpec = tween(900), label = "bg") { bg ->
            if (bg != null) {
                Box(Modifier.fillMaxSize()) {
                    Image(
                        bitmap = bg.bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().blur(40.dp),
                        contentScale = ContentScale.Crop,
                        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.5f) }),
                        filterQuality = FilterQuality.Medium
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Color.Black.copy(alpha = (0.08f + 0.3f * bg.lum).coerceIn(0.08f, 0.4f))
                        )
                    )
                }
            }
        }
        content()
    }
}

@Composable
fun AccessScreen(onOpen: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("Activa el acceso a notificaciones", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(
                "Busca \"Ahora suena\" en la lista y actívalo para poder leer lo que se reproduce.",
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onOpen,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) { Text("Abrir ajustes") }
        }
    }
}

@Composable
fun NowPlayingScreen() {
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            position = MediaState.currentPosition()
            delay(250)
        }
    }
    CoverBackground {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val k = maxWidth.value / 1920f
            CompositionLocalProvider(LocalK provides k) {
                Row(
                    Modifier.fillMaxSize().padding(vertical = px(24f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerPane(Modifier.weight(1f).fillMaxHeight())
                    Box(
                        Modifier.width(1.dp).fillMaxHeight(0.8f)
                            .background(Color.White.copy(alpha = 0.2f))
                    )
                    LyricsPane(position, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun ThinSlider(
    fraction: Float,
    trackHeight: Dp,
    thumbSize: Dp,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinish)
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .height(maxOf(thumbSize, px(28f)))
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    change((o.x / widthPx).coerceIn(0f, 1f))
                    finish()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { finish() },
                    onDragCancel = { finish() }
                ) { ch, _ ->
                    ch.consume()
                    change((ch.position.x / widthPx).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier.fillMaxWidth().height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.28f))
        )
        Box(
            Modifier.fillMaxWidth(f).height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
        )
        if (thumbSize > 0.dp) {
            Box(
                Modifier
                    .offset { IntOffset((widthPx * f - thumbSize.toPx() / 2f).toInt(), 0) }
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
fun ProgressSection() {
    val dur = MediaState.durationMs
    var pos by remember { mutableLongStateOf(0L) }
    var dragFrac by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            if (MediaState.isPlaying) withFrameNanos { } else delay(250)
            if (dragFrac == null) pos = MediaState.currentPosition()
        }
    }

    val d = dragFrac
    val frac = d ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val shownPos = if (d != null) (d * dur).toLong() else pos

    ThinSlider(
        fraction = frac,
        trackHeight = px(4f),
        thumbSize = 0.dp,
        modifier = Modifier.fillMaxWidth(),
        onChange = { dragFrac = it },
        onFinish = {
            val target = dragFrac
            if (target != null && dur > 0) {
                MediaState.controller?.transportControls?.seekTo((target * dur).toLong())
                pos = (target * dur).toLong()
            }
            dragFrac = null
        }
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(fmt(shownPos), color = Color.White.copy(alpha = 0.7f), fontSize = spx(14f), fontFamily = Serif)
        Text(fmt(dur), color = Color.White.copy(alpha = 0.7f), fontSize = spx(14f), fontFamily = Serif)
    }
}

@Composable
fun PlayerPane(modifier: Modifier) {
    val cover = MediaState.hiCover ?: MediaState.cover
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val colW = px(466f)
        val coverSize = minOf(colW, maxHeight - px(330f)).coerceAtLeast(120.dp)
        val coverShape = RoundedCornerShape(px(22f))
        Column(Modifier.width(colW), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(coverSize)
                    .clip(coverShape)
                    .background(Color(0x22FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                if (cover != null) {
                    Image(
                        cover.asImageBitmap(), null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High
                    )
                } else {
                    Icon(Icons.Default.MusicNote, null, Modifier.size(px(80f)), tint = Color.White.copy(alpha = 0.5f))
                }
            }
            Spacer(Modifier.height(px(22f)))
            Text(
                MediaState.title.ifBlank { "Nada en reproducción" },
                color = Color.White, fontSize = spx(34f), fontWeight = FontWeight.SemiBold,
                fontFamily = Serif,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                MediaState.artist,
                color = Color.White.copy(alpha = 0.65f), fontSize = spx(20f),
                fontFamily = Serif,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(px(14f)))
            ProgressSection()
            Spacer(Modifier.height(px(20f)))
            Row(
                horizontalArrangement = Arrangement.spacedBy(px(29f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { MediaState.controller?.transportControls?.skipToPrevious() },
                    modifier = Modifier.size(px(48f))
                ) {
                    Icon(Icons.Default.SkipPrevious, null, Modifier.size(px(30f)), tint = Color.White)
                }
                Box(
                    Modifier
                        .size(px(70f))
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable {
                            val tc = MediaState.controller?.transportControls
                            if (MediaState.isPlaying) tc?.pause() else tc?.play()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (MediaState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null, Modifier.size(px(36f)), tint = Color.Black
                    )
                }
                IconButton(
                    onClick = { MediaState.controller?.transportControls?.skipToNext() },
                    modifier = Modifier.size(px(48f))
                ) {
                    Icon(Icons.Default.SkipNext, null, Modifier.size(px(30f)), tint = Color.White)
                }
            }
            Spacer(Modifier.height(px(16f)))
            VolumeBar()
        }
    }
}

@Composable
fun VolumeBar() {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            if (!dragging) vol = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            delay(1000)
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.VolumeDown, null, Modifier.size(px(22f)), tint = Color.White.copy(alpha = 0.7f))
        ThinSlider(
            fraction = vol / max,
            trackHeight = px(3f),
            thumbSize = px(16f),
            modifier = Modifier.weight(1f).padding(start = px(14f)),
            onChange = {
                dragging = true
                val v = (it * max).roundToInt()
                vol = v.toFloat()
                am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
            },
            onFinish = { dragging = false }
        )
    }
}

@Composable
fun LyricsPane(position: Long, modifier: Modifier) {
    val lines = MediaState.lyrics
    val listState = remember(lines) { LazyListState() }
    var first by remember(lines) { mutableStateOf(true) }
    val current = if (MediaState.synced) lines.indexOfLast { it.timeMs <= position + 300 } else -1

    LaunchedEffect(current, lines) {
        if (lines.isEmpty()) return@LaunchedEffect
        val target = current.coerceAtLeast(0)
        if (first) {
            listState.scrollToItem(target)
            first = false
        } else {
            listState.animateScrollToItem(target)
        }
    }

    BoxWithConstraints(modifier.fadeVertical()) {
        val pad = maxHeight / 2 - px(34f)
        if (lines.isEmpty()) {
            Text(
                MediaState.status,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = spx(26f),
                fontFamily = Serif,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(px(24f))
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = px(83f), end = px(60f), top = pad, bottom = pad)
            ) {
                itemsIndexed(lines) { i, line ->
                    val synced = MediaState.synced
                    val dist = if (!synced || current < 0) 0 else abs(i - current)
                    val active = !synced || i == current
                    val color by animateColorAsState(
                        if (active) Color.White else Color.White.copy(alpha = 0.45f),
                        label = "lyricColor"
                    )
                    val blurRadius by animateDpAsState(
                        if (dist == 0) 0.dp else minOf(dist * 1.5f, 5f).dp,
                        label = "lyricBlur"
                    )
                    Text(
                        text = line.text.ifBlank { "♪" },
                        color = color,
                        fontSize = spx(32f),
                        fontWeight = FontWeight.Medium,
                        fontFamily = Serif,
                        lineHeight = spx(40f),
                        modifier = Modifier.padding(vertical = px(14f)).blur(blurRadius)
                    )
                }
            }
        }
    }
}
