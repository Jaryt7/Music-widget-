package com.ahorasuena

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

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

@Composable
fun AccessScreen(onOpen: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xFF111111)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("Activa el acceso a notificaciones", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text("Busca \"Ahora suena\" en la lista y actívalo para poder leer lo que se reproduce.", color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(24.dp))
            Button(onClick = onOpen) { Text("Abrir ajustes") }
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
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF3A1C2E), Color(0xFF0D0D12))))
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            PlayerPane(position, Modifier.weight(0.42f).fillMaxHeight())
            LyricsPane(position, Modifier.weight(0.58f).fillMaxHeight())
        }
    }
}

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun PlayerPane(position: Long, modifier: Modifier) {
    val cover = MediaState.cover
    val dur = MediaState.durationMs
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.weight(1f, fill = false)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x22FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null) {
                Image(cover.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { MediaState.controller?.transportControls?.skipToPrevious() },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(Icons.Default.SkipPrevious, null, Modifier.size(44.dp), tint = Color.White)
            }
            IconButton(
                onClick = {
                    val tc = MediaState.controller?.transportControls
                    if (MediaState.isPlaying) tc?.pause() else tc?.play()
                },
                modifier = Modifier.size(84.dp)
            ) {
                Icon(
                    if (MediaState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    null, Modifier.size(64.dp), tint = Color.White
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
    val listState = rememberLazyListState()
    val current = if (MediaState.synced) lines.indexOfLast { it.timeMs <= position + 300 } else -1

    LaunchedEffect(current) {
        if (current >= 0) listState.animateScrollToItem(current)
    }

    Box(modifier) {
        if (lines.isEmpty()) {
            Text(
                MediaState.status,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 140.dp, bottom = 300.dp)
            ) {
                itemsIndexed(lines) { i, line ->
                    val active = !MediaState.synced || i == current
                    val color by animateColorAsState(
                        if (active) Color.White else Color.White.copy(alpha = 0.35f),
                        label = "lyric"
                    )
                    Text(
                        text = line.text.ifBlank { "♪" },
                        color = color,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 42.sp,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }
        }
    }
}
