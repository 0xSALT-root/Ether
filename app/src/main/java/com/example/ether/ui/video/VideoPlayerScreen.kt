package com.example.ether.ui.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoUri: String,
    isMinimized: Boolean,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onExpand: () -> Unit,
    onFullScreenChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val controllerFuture = remember(context) {
        androidx.media3.session.MediaController.Builder(
            context,
            androidx.media3.session.SessionToken(context, android.content.ComponentName(context, VideoPlaybackService::class.java))
        ).buildAsync()
    }
    
    var player by remember { mutableStateOf<Player?>(null) }

    DisposableEffect(controllerFuture, videoUri) {
        controllerFuture.addListener({
            val controller = controllerFuture.get()
            player = controller
            
            val mediaItem = MediaItem.fromUri(Uri.parse(videoUri))
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.playWhenReady = true
        }, androidx.core.content.ContextCompat.getMainExecutor(context))
        
        onDispose {
            androidx.media3.session.MediaController.releaseFuture(controllerFuture)
        }
    }

    var isFullScreen by remember { mutableStateOf(false) }
    
    LaunchedEffect(isFullScreen, isMinimized) {
        if (!isMinimized) {
            onFullScreenChanged(isFullScreen)
        } else {
            onFullScreenChanged(false)
        }
    }

    if (isMinimized) {
        player?.let {
            VideoMinimizedPlayer(
                player = it,
                onClose = onClose,
                onExpand = onExpand
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (player != null) {
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            this.player = player
                            useController = true
                            setBackgroundColor(android.graphics.Color.BLACK)
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            // Top Bar Actions - Use standard padding instead of statusBarsPadding to avoid being pushed down
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }

                IconButton(onClick = onMinimize) {
                    Icon(Icons.Default.OpenInFull, contentDescription = "Minimize", tint = Color.White)
                }
            }

            IconButton(
                onClick = {
                    isFullScreen = !isFullScreen
                },
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomEnd)
            ) {
                Icon(
                    imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = "Toggle Fullscreen",
                    tint = Color.White
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoMinimizedPlayer(
    player: Player,
    onClose: () -> Unit,
    onExpand: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    val playerWidth = with(density) { 200.dp.toPx() }
    val playerHeight = with(density) { 112.dp.toPx() }

    var offsetX by remember { mutableStateOf(screenWidth - playerWidth - 50f) }
    var offsetY by remember { mutableStateOf(screenHeight - playerHeight - 150f) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(200.dp, 112.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .clickable { onExpand() }
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(32.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(bottomStart = 8.dp))
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
