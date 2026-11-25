package com.ahorasuena

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

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

fun Modifier.glass(shape: Shape = RoundedCornerShape(32.dp)): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f))
        )
    )
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.5f),
                Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = 0.3f)
            )
        ),
        shape
    )

@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .drawBehind {
                val c1 = Offset(size.width * 0.12f, size.height * 0.08f)
                val c2 = Offset(size.width * 0.9f, size.height * 0.95f)
                val r = size.width * 0.45f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                        center = c1, radius = r
                    ),
                    radius = r, center = c1
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                        center = c2, radius = r
                    ),
                    radius = r, center = c2
                )
            },
        content = content
    )
}

@Composable
fun AccessScreen(onOpen: () -> Unit) {
    GlassBackground {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center).glass().padding(40.dp)
        ) {
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
    GlassBackground {
        Row(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            PlayerPane(position, Modifier.weight(0.42f).fillMaxHeight().glass().padding(28.dp))
            LyricsPane(position, Modifier.weight(0.58f).fillMaxHeight().glass())
        }
    }
}

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun PlayerPane(position: Long, modifier: Modifier) {
    val cover = MediaState.hiCover ?: MediaState.cover
    val dur = MediaState.durationMs
    val coverShape = RoundedCornerShape(24.dp)
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .weight(1f, fill = false)
                .aspectRatio(1f)
                .clip(coverShape)
                .background(Color(0x22FFFFFF))
                .border(1.dp, Color.White.copy(alpha = 0.3f), coverShape),
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
                Icon(Icons.Default.MusicNote, null, Modifier.size(72.dp), tint = Color.White.copy(alpha = 0.5f))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            MediaState.title.ifBlank { "Nada en reproducción" },
            color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            MediaState.artist,
            color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(
            progress = { if (dur > 0) (position.toFloat() / dur).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.2f)
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(position), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Text(fmt(dur), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { MediaState.controller?.transportControls?.skipToPrevious() },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(Icons.Default.SkipPrevious, null, Modifier.size(44.dp), tint = Color.White)
            }
            Box(
                Modifier
                    .size(76.dp)
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
                    null, Modifier.size(46.dp), tint = Color.Black
                )
            }
            IconButton(
                onClick = { MediaState.controller?.transportControls?.skipToNext() },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(Icons.Default.SkipNext, null, Modifier.size(44.dp), tint = Color.White)
            }
        }
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

    BoxWithConstraints(modifier) {
        val pad = maxHeight / 2 - 40.dp
        if (lines.isEmpty()) {
            Text(
                MediaState.status,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 36.dp, end = 36.dp, top = pad, bottom = pad)
            ) {
                itemsIndexed(lines) { i, line ->
                    val synced = MediaState.synced
                    val dist = if (!synced || current < 0) 0 else abs(i - current)
                    val active = !synced || i == current
                    val color by animateColorAsState(
                        if (active) Color.White else Color.White.copy(alpha = 0.35f),
                        label = "lyricColor"
                    )
                    val blurRadius by animateDpAsState(
                        if (dist == 0) 0.dp else minOf(dist * 1.5f, 5f).dp,
                        label = "lyricBlur"
                    )
                    Text(
                        text = line.text.ifBlank { "♪" },
                        color = color,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 42.sp,
                        modifier = Modifier.padding(vertical = 12.dp).blur(blurRadius)
                    )
                }
            }
        }
    }
}
