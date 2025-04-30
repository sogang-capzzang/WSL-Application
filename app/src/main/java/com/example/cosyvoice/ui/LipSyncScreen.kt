package com.example.cosyvoice.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.example.cosyvoice.model.Video
import com.example.cosyvoice.ui.components.AnimatedContent
import com.example.cosyvoice.ui.components.CategoryButton
import com.example.cosyvoice.ui.components.VideoListItem
import com.example.cosyvoice.ui.theme.AnimationConstants
import com.example.cosyvoice.util.ThumbnailUtils
import com.example.cosyvoice.viewmodel.VideoViewModel
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LipSyncScreen(navController: NavHostController, person: String) {
    val context = LocalContext.current
    val viewModel = remember { VideoViewModel() }
    var selectedVideo by remember { mutableStateOf<String?>(null) }
    var isFullScreen by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val thumbnails = remember { mutableStateMapOf<String, Bitmap?>() }

    LaunchedEffect(person) {
        viewModel.loadVideos(context, person)
    }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            viewModel.videos.forEach { video ->
                if (thumbnails[video.path] == null) {
                    val thumbnail = ThumbnailUtils.getThumbnailFromAsset(context, video.path)
                    withContext(Dispatchers.Main) {
                        thumbnails[video.path] = thumbnail
                    }
                }
            }
        }
    }

    // ExoPlayer 초기화
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

    Scaffold(
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                    title = { Text("립싱크 영상") },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (selectedVideo != null) {
                                selectedVideo = null
                                isFullScreen = false
                            } else {
                                navController.popBackStack()
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isFullScreen) 0.dp else 16.dp)
            ) {
                if (!isFullScreen && selectedVideo == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CategoryButton(
                            text = "식사",
                            icon = Icons.Outlined.Restaurant,
                            isSelected = viewModel.selectedCategory.value == "meal",
                            onClick = { viewModel.selectedCategory.value = "meal" }
                        )
                        CategoryButton(
                            text = "복약",
                            icon = Icons.Outlined.Medication,
                            isSelected = viewModel.selectedCategory.value == "medication",
                            onClick = { viewModel.selectedCategory.value = "medication" }
                        )
                        CategoryButton(
                            text = "체조",
                            icon = Icons.Outlined.FitnessCenter,
                            isSelected = viewModel.selectedCategory.value == "exercise",
                            onClick = { viewModel.selectedCategory.value = "exercise" }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (selectedVideo != null) {
                    AnimatedContent(visible = true) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .let {
                                        if (isFullScreen) it.fillMaxHeight() else it.height(400.dp)
                                    },
                                shape = if (isFullScreen) MaterialTheme.shapes.small else MaterialTheme.shapes.medium,
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isFullScreen) 0.dp else 4.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AndroidView(
                                        factory = { ctx ->
                                            PlayerView(ctx).apply {
                                                this.player = player
                                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                val uri = Uri.parse("asset:///$selectedVideo")
                                                val mediaItem = MediaItem.fromUri(uri)
                                                player?.setMediaItem(mediaItem)
                                                player?.prepare()
                                                player?.play()
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            IconButton(
                                onClick = { isFullScreen = !isFullScreen },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        shape = MaterialTheme.shapes.small
                                    )
                            ) {
                                Icon(
                                    if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = if (isFullScreen) "일반 화면" else "전체 화면",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                } else {
                    val filteredVideos = viewModel.getFilteredVideos()
                    if (filteredVideos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.VideocamOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "이 카테고리에 영상이 없습니다",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            itemsIndexed(filteredVideos) { index, video ->
                                var visible by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    delay(index * AnimationConstants.STAGGERED_ANIMATION_DELAY.toLong())
                                    visible = true
                                }
                                AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn(tween(300)) + expandVertically(tween(300))
                                ) {
                                    VideoListItem(
                                        video = video,
                                        thumbnailUrl = thumbnails[video.path],
                                        onClick = { selectedVideo = video.path }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (viewModel.videos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}