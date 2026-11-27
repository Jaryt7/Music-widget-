package com.ahorasuena

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun PillButton(label: String, filled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.14f))
            .clickable { onClick() }
            .padding(horizontal = px(26f), vertical = px(14f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (filled) Color.Black else Color.White,
            fontSize = spx(22f),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun PlaylistRow(p: PlaylistInfo, onClick: () -> Unit) {
    LaunchedEffect(p.id) { Spotify.ensureCover(p) }
    val cover = Spotify.covers[p.id]
    val sub = buildString {
        if (p.total >= 0) append("${p.total} canciones")
        if (p.owner.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append(p.owner)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(px(14f)))
            .clickable { onClick() }
            .padding(px(10f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(px(84f))
                .clip(RoundedCornerShape(px(10f)))
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null) {
                Image(cover.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.MusicNote, null, Modifier.size(px(36f)), tint = Color.White.copy(alpha = 0.5f))
            }
        }
        Spacer(Modifier.width(px(18f)))
        Column(Modifier.weight(1f)) {
            Text(
                p.name,
                color = Color.White,
                fontSize = spx(24f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                sub,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = spx(18f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PlaylistsPanel(modifier: Modifier, onClose: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Spotify.init(ctx) }
    LaunchedEffect(Spotify.loggedIn) {
        if (Spotify.loggedIn && Spotify.playlists.isEmpty()) Spotify.loadPlaylists()
    }

    Column(
        modifier
            .fillMaxHeight()
            .width(px(680f))
            .background(Color(0xF2101010))
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(px(36f))
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Playlists",
                color = Color.White,
                fontSize = spx(34f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
        }

        if (Spotify.msg.isNotEmpty()) {
            Spacer(Modifier.height(px(10f)))
            Text(Spotify.msg, color = Color.White.copy(alpha = 0.75f), fontSize = spx(18f))
        }
        Spacer(Modifier.height(px(20f)))

        if (!Spotify.loggedIn) {
            Text(
                "Conecta tu cuenta de Spotify para ver aquí tus playlists.",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = spx(22f)
            )
            Spacer(Modifier.height(px(24f)))
            PillButton("Conectar con Spotify") { Spotify.login(ctx) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(px(12f))) {
                PillButton("Actualizar", filled = false) { Spotify.loadPlaylists() }
                PillButton("Cerrar sesión", filled = false) { Spotify.logout() }
            }
            Spacer(Modifier.height(px(16f)))
            if (Spotify.loading && Spotify.playlists.isEmpty()) {
                Text("Cargando…", color = Color.White.copy(alpha = 0.7f), fontSize = spx(20f))
            }
            LazyColumn(Modifier.weight(1f)) {
                items(Spotify.playlists, key = { it.id }) { p ->
                    PlaylistRow(p) { Spotify.play(ctx, p) }
                }
            }
        }
    }
}
