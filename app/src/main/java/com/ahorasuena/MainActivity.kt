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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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

fun Modifier.fadeBottom(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black,
                0.8f to Color.Black,
                1f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
        )
    }

data class Palette(val base: Color, val blobs: List<Color>)

fun paletteFrom(src: Bitmap): Palette {
    val mid = Bitmap.createScaledBitmap(src, 128, 128, true)
    val small = Bitmap.createScaledBitmap(mid, 16, 16, true)
    val hsv = FloatArray(3)

    fun avg(x0: Int, x1: Int, y0: Int, y1: Int): Int {
        var r = 0
        var g = 0
        var b = 0
        var n = 0
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val c = small.getPixel(x, y)
                r += android.graphics.Color.red(c)
                g += android.graphics.Color.green(c)
                b += android.graphics.Color.blue(c)
                n++
            }
        }
        return android.graphics.Color.rgb(r / n, g / n, b / n)
    }

    fun tune(c: Int, sMul: Float, vMin: Float, vMax: Float): Color {
        android.graphics.Color.colorToHSV(c, hsv)
        hsv[1] = (hsv[1] * sMul).coerceAtMost(1f)
        hsv[2] = hsv[2].coerceIn(vMin, vMax)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    val all = avg(0, 16, 0, 16)
    return Palette(
        base = tune(all, 1.1f, 0.2f, 0.7f),
        blobs = listOf(
            tune(avg(0, 8, 0, 8), 1.4f, 0.4f, 1f),
            tune(avg(8, 16, 0, 8), 1.4f, 0.4f, 1f),
            tune(avg(0, 8, 8, 16), 1.4f, 0.4f, 1f),
            tune(avg(8, 16, 8, 16), 1.4f, 0.4f, 1f),
            tune(all, 1.5f, 0.45f, 1f)
        )
    )
}

@Composable
fun CoverBackground(content: @Composable BoxScope.() -> Unit) {
    val cover = MediaState.hiCover ?: MediaState.cover
    val palette = remember(cover) { cover?.let { paletteFrom(it) } }

    val spec = tween<Color>(1500)
    val base by animateColorAsState(palette?.base ?: Color(0xFF111111), spec, label = "base")
    val c0 by animateColorAsState(palette?.blobs?.get(0) ?: Color(0xFF222222), spec, label = "c0")
    val c1 by animateColorAsState(palette?.blobs?.get(1) ?: Color(0xFF222222), spec, label = "c1")
    val c2 by animateColorAsState(palette?.blobs?.get(2) ?: Color(0xFF222222), spec, label = "c2")
    val c3 by animateColorAsState(palette?.blobs?.get(3) ?: Color(0xFF222222), spec, label = "c3")
    val c4 by animateColorAsState(palette?.blobs?.get(4) ?: Color(0xFF222222), spec, label = "c4")

    val infinite = rememberInfiniteTransition(label = "bgMotion")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(32000, easing = LinearEasing)),
        label = "phase"
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(base)
            val w = size.width
            val h = size.height
            val r = maxOf(w, h) * 0.6f
            val p = phase
            val cols = listOf(c0, c1, c2, c3, c4)
            val centers = listOf(
                Offset(w * (0.2f + 0.12f * sin(p)), h * (0.2f + 0.15f * cos(p * 2f))),
                Offset(w * (0.8f + 0.12f * cos(p)), h * (0.2f + 0.15f * sin(p * 2f))),
                Offset(w * (0.2f + 0.15f * sin(p * 2f + 1f)), h * (0.8f + 0.12f * cos(p))),
                Offset(w * (0.8f + 0.10f * cos(p * 3f + 2f)), h * (0.8f + 0.12f * sin(p))),
                Offset(w * (0.5f + 0.20f * sin(p)), h * (0.5f + 0.20f * cos(p * 3f)))
            )
            for (i in cols.indices) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(cols[i].copy(alpha = 0.95f), cols[i].copy(alpha = 0f)),
                        center = centers[i],
                        radius = r
                    ),
                    radius = r,
                    center = centers[i]
                )
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
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
        Text(fmt(shownPos), color = Color.White.copy(alpha = 0.7f), fontSize = spx(14f))
        Text(fmt(dur), color = Color.White.copy(alpha = 0.7f), fontSize = spx(14f))
    }
}

@Composable
fun PlayerPane(modifier: Modifier) {
    val cover = MediaState.hiCover ?: MediaState.cover
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val colW = px(540f)
        val coverSize = minOf(colW, maxHeight - px(330f)).coerceAtLeast(120.dp)
        val coverShape = RoundedCornerShape(px(16f))
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
                color = Color.White, fontSize = spx(32f), fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                MediaState.artist,
                color = Color.White.copy(alpha = 0.7f), fontSize = spx(20f),
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

    BoxWithConstraints(modifier.fadeBottom()) {
        val topPad = maxHeight * 0.42f - px(40f)
        val bottomPad = maxHeight * 0.6f
        if (lines.isEmpty()) {
            Text(
                MediaState.status,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = spx(28f),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(px(24f))
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = px(40f), end = px(60f), top = topPad, bottom = bottomPad)
            ) {
                itemsIndexed(lines) { i, line ->
                    val synced = MediaState.synced
                    val rel = i - maxOf(current, 0)
                    val targetAlpha = when {
                        !synced -> 0.9f
                        rel < 0 -> if (rel == -1) 0.22f else 0f
                        rel == 0 -> if (current >= 0) 1f else 0.55f
                        else -> (0.55f - 0.12f * (rel - 1)).coerceAtLeast(0.07f)
                    }
                    val targetScale = if (!synced || (rel == 0 && current >= 0)) 1f else 0.92f
                    val blurTarget = if (!synced || (rel == 0 && current >= 0)) 0.dp
                    else minOf(abs(rel) * 1.2f, 4f).dp

                    val a by animateFloatAsState(targetAlpha, animationSpec = tween(450), label = "lineAlpha")
                    val s by animateFloatAsState(targetScale, animationSpec = tween(450), label = "lineScale")
                    val blurRadius by animateDpAsState(blurTarget, label = "lineBlur")

                    Text(
                        text = line.text.ifBlank { "♪" },
                        color = Color.White,
                        fontSize = spx(44f),
                        fontWeight = FontWeight.Bold,
                        lineHeight = spx(52f),
                        modifier = Modifier
                            .padding(vertical = px(14f))
                            .graphicsLayer {
                                alpha = a
                                scaleX = s
                                scaleY = s
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                            .blur(blurRadius)
                    )
                }
            }
        }
    }
}
