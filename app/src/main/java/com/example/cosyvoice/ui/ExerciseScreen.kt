package com.example.cosyvoice.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.example.cosyvoice.util.ThumbnailUtils
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ExerciseScreen(navController: NavHostController, person: String) {
    val context = LocalContext.current
    val assetManager = context.assets
    val exerciseFiles = assetManager.list("exercise/$person")?.filter { it.endsWith(".mp4") } ?: emptyList()
    var selectedVideo by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val scope = rememberCoroutineScope()

    val screenHeight = with(LocalDensity.current) { context.resources.displayMetrics.heightPixels.toDp() }
    val screenWidth = with(LocalDensity.current) { context.resources.displayMetrics.widthPixels.toDp() }
    val thumbnailHeight = (screenHeight / 3) - 16.dp
    val thumbnailWidth = (screenWidth - 48.dp) / 3

    val thumbnails = remember { mutableStateMapOf<String, Bitmap?>() }

    DisposableEffect(Unit) {
        player = ExoPlayer.Builder(context)
            .build()
            .apply {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build()
                setAudioAttributes(audioAttributes, true)
                volume = 1.0f
            }
        onDispose {
            player?.release()
        }
    }

    LaunchedEffect(exerciseFiles) {
        exerciseFiles.forEach { video ->
            if (thumbnails[video] == null) {
                scope.launch(Dispatchers.IO) {
                    val thumbnail = ThumbnailUtils.getThumbnailFromAsset(context, "exercise/$person/$video")
                    thumbnails[video] = thumbnail
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (selectedVideo != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        val uri = Uri.parse("asset:///exercise/$person/$selectedVideo")
                        val mediaItem = MediaItem.fromUri(uri)
                        player?.setMediaItem(mediaItem)
                        player?.prepare()
                        player?.play()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
            )
            Button(
                onClick = { selectedVideo = null },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("재생 중지")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(exerciseFiles) { video ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { selectedVideo = video }
                    ) {
                        val thumbnail = thumbnails[video]
                        if (thumbnail != null) {
                            Image(
                                painter = rememberAsyncImagePainter(thumbnail),
                                contentDescription = "Thumbnail for $video",
                                modifier = Modifier
                                    .width(thumbnailWidth)
                                    .height(thumbnailHeight)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .width(thumbnailWidth)
                                    .height(thumbnailHeight)
                                    .background(Color.Gray)
                            ) {
                                Text(
                                    "로딩 중...",
                                    modifier = Modifier.align(Alignment.Center),
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = video,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("뒤로가기")
            }
        }
    }
}