package com.kogen.giraffe.ui.features.chatDetails.presentation.screens

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import com.kogen.giraffe.R
import com.kogen.giraffe.ui.common.domain.models.GiraffeChat
import com.kogen.giraffe.ui.common.domain.models.GiraffeChatStatus
import com.kogen.giraffe.ui.common.domain.models.GiraffeContentType
import com.kogen.giraffe.ui.common.domain.models.GiraffeMessage
import com.kogen.giraffe.ui.common.domain.models.toClipboardText
import com.kogen.giraffe.ui.common.main.BGSecondaryColor
import com.kogen.giraffe.ui.common.main.BackgroundColor
import com.kogen.giraffe.ui.common.main.PrimaryColor
import com.kogen.giraffe.ui.common.main.TextPrimaryColor
import com.kogen.giraffe.ui.common.presentation.AudioPlaybackState
import com.kogen.giraffe.ui.common.presentation.NoContentView
import com.kogen.giraffe.ui.common.presentation.extensions.copyToClipboard
import com.kogen.giraffe.ui.common.presentation.extensions.decodeImageAspectRatio
import com.kogen.giraffe.ui.common.presentation.extensions.decodeVideoAspectRatio
import com.kogen.giraffe.ui.common.presentation.extensions.msToDurationText
import com.kogen.giraffe.ui.common.presentation.extensions.shareFile
import com.kogen.giraffe.ui.common.presentation.extensions.timestampToDateTime
import com.kogen.giraffe.ui.common.presentation.extensions.timestampToTime
import com.kogen.giraffe.ui.features.chatDetails.presentation.mvi.ChatDetailsAction
import com.kogen.giraffe.ui.features.chatDetails.presentation.mvi.ChatDetailsState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

private const val WAVEFORM_BAR_COUNT = 27

/** Renders one call's request/response history as a chat-style message list, with a collapsible request-metadata header. */
@Composable
internal fun ChatDetailsScreen(
    state: ChatDetailsState,
    action: (ChatDetailsAction) -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        containerColor = BackgroundColor,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    action(ChatDetailsAction.NavigateBack)
                                }
                                .padding(6.dp),
                            painter = painterResource(R.drawable.ic_arrow_left),
                            contentDescription = null,
                            tint = PrimaryColor,
                        )
                        Text(
                            modifier = Modifier.weight(1f),
                            text = state.chat?.url.orEmpty(),
                            style = TextStyle(
                                fontSize = 16.sp,
                            ),
                            color = TextPrimaryColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    state.chat?.toClipboardText()
                                        ?.copyToClipboard(context, title = "Request")
                                }
                                .padding(8.dp),
                            painter = painterResource(R.drawable.ic_copy),
                            contentDescription = "Copy whole request",
                            tint = PrimaryColor,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PrimaryColor)
                            .height(1.dp),
                    )
                }
                RequestDetailsView(
                    chat = state.chat,
                    isVisible = state.showRequestDetails,
                    onOpen = {
                        action(ChatDetailsAction.ShowRequestDetail)
                    },
                    onClose = {
                        action(ChatDetailsAction.HideRequestDetail)
                    }
                )
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.chat?.messages.isNullOrEmpty().not()) {
                items(
                    items = state.chat.messages,
                    key = { message -> message.id }
                ) { message ->
                    if (message.isIncoming) {
                        ServerMessageView(message, state.audioPlayback, action)
                    } else {
                        ClientMessageView(message, state.audioPlayback, action)
                    }
                }
            } else {
                item {
                    NoContentView()
                }
            }
        }
    }
}

/** Collapsible panel showing a call's URL, timing, status, and headers, toggled by the chevron below the top bar. */
@Composable
private fun RequestDetailsView(
    chat: GiraffeChat?,
    isVisible: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    chat?.let { chat ->
        if (isVisible) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                AnimatedVisibility(
                    visible = true,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = BGSecondaryColor,
                                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = TextPrimaryColor,
                        )
                        Text(
                            text = "Url: ${chat.url}",
                            style = textStyle,
                        )
                        Text(
                            text = "Start time: ${chat.timestamp.timestampToDateTime()}",
                            style = textStyle,
                        )
                        if (chat.status != GiraffeChatStatus.InProgress) {
                            Text(
                                text = "End time: ${chat.messages.lastOrNull()?.timestamp?.timestampToDateTime()}",
                                style = textStyle
                            )
                        }
                        Text(
                            text = "Status: ${chat.status}",
                            style = textStyle,
                        )
                        if (chat.headers.isNotEmpty()) {
                            Text(
                                text = "Headers:",
                                style = textStyle,
                            )
                            chat.headers.forEach { header ->
                                Text(
                                    text = "${header.key}: ${header.value}",
                                    style = textStyle,
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                modifier = Modifier
                    .rotate(
                        animateFloatAsState(if (isVisible) 180f else 0f).value
                    )
                    .clip(CircleShape)
                    .clickable {
                        if (isVisible) onClose() else onOpen()
                    },
                painter = painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
                tint = PrimaryColor,
            )
        }
    }
}

/** Left-aligned bubble for an incoming (server) message: text plus any extracted media rendered per [message]'s content type. */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun ServerMessageView(
    message: GiraffeMessage,
    audioPlayback: AudioPlaybackState,
    action: (ChatDetailsAction) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 40.dp,
            ),
    ) {
        Column(
            modifier = Modifier
                .background(
                    color = BGSecondaryColor,
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp,
                    )
                )
                .padding(8.dp),
        ) {
            Text(
                text = message.textContent.orEmpty(),
                style = TextStyle(
                    fontSize = 14.sp,
                ),
                color = TextPrimaryColor,
            )
            if (message.filePath.isNullOrBlank().not()) {
                when (message.contentType) {
                    GiraffeContentType.Image -> {
                        Spacer(Modifier.height(8.dp))

                        val aspectRatio = remember(message.filePath) {
                            decodeImageAspectRatio(message.filePath)
                        }
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val targetHeight =
                                (maxWidth / (aspectRatio ?: 0f)).coerceIn(120.dp, 260.dp)

                            AsyncImage(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(targetHeight)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        action(ChatDetailsAction.ShowImage(message.filePath))
                                    },
                                model = File(message.filePath),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }

                    GiraffeContentType.Audio -> {
                        Spacer(Modifier.height(4.dp))
                        VoiceMessageView(
                            filePath = message.filePath,
                            playback = audioPlayback,
                            action = action,
                        )
                    }

                    GiraffeContentType.Video -> {
                        Spacer(Modifier.height(8.dp))
                        VideoThumbnailView(
                            filePath = message.filePath,
                            context = context,
                            onClick = {
                                action(ChatDetailsAction.ShowVideo(message.filePath))
                            },
                        )
                    }

                    GiraffeContentType.Unknown -> {
                        Spacer(Modifier.height(4.dp))
                        UnknownFileView(
                            filePath = message.filePath,
                            context = context,
                        )
                    }

                    else -> {}
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.padding(start = 4.dp),
        ) {
            if (message.textContent.isNullOrBlank().not()) {
                Icon(
                    modifier = Modifier
                        .size(14.dp)
                        .clickable {
                            message.textContent.copyToClipboard(context)
                        },
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = null,
                    tint = TextPrimaryColor,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = message.timestamp.timestampToTime(),
                style = TextStyle(fontSize = 12.sp),
                color = PrimaryColor
            )
        }

    }
}

/** Right-aligned mirror of [ServerMessageView] for an outgoing (client) message. */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun ClientMessageView(
    message: GiraffeMessage,
    audioPlayback: AudioPlaybackState,
    action: (ChatDetailsAction) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 40.dp,
                end = 16.dp,
            ),
        horizontalAlignment = Alignment.End,
    ) {
        Column(
            modifier = Modifier
                .background(
                    color = BGSecondaryColor,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 4.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp,
                    )
                )
                .padding(8.dp),
        ) {
            Text(
                text = message.textContent.orEmpty(),
                style = TextStyle(
                    fontSize = 14.sp,
                ),
                color = TextPrimaryColor,
            )
            if (message.filePath.isNullOrBlank().not()) {
                when (message.contentType) {
                    GiraffeContentType.Image -> {
                        Spacer(Modifier.height(8.dp))

                        val aspectRatio = remember(message.filePath) {
                            decodeImageAspectRatio(message.filePath)
                        }
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val targetHeight =
                                (maxWidth / (aspectRatio ?: 0f)).coerceIn(120.dp, 260.dp)

                            AsyncImage(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(targetHeight)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        action(ChatDetailsAction.ShowImage(message.filePath))
                                    },
                                model = File(message.filePath),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }

                    GiraffeContentType.Audio -> {
                        Spacer(Modifier.height(4.dp))
                        VoiceMessageView(
                            filePath = message.filePath,
                            playback = audioPlayback,
                            action = action,
                        )
                    }

                    GiraffeContentType.Video -> {
                        Spacer(Modifier.height(8.dp))
                        VideoThumbnailView(
                            filePath = message.filePath,
                            context = context,
                            onClick = {
                                action(ChatDetailsAction.ShowVideo(message.filePath))
                            },
                        )
                    }

                    GiraffeContentType.Unknown -> {
                        Spacer(Modifier.height(4.dp))
                        UnknownFileView(
                            filePath = message.filePath,
                            context = context,
                        )
                    }

                    else -> {}
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.padding(end = 4.dp),
        ) {
            Text(
                text = message.timestamp.timestampToTime(),
                style = TextStyle(fontSize = 12.sp),
                color = PrimaryColor
            )
            if (message.textContent.isNullOrBlank().not()) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    modifier = Modifier
                        .size(14.dp)
                        .clickable {
                            message.textContent.copyToClipboard(context)
                        },
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = null,
                    tint = TextPrimaryColor,
                )
            }
        }
    }
}

/**
 * A voice-message bubble: play/pause button, a scrubbable waveform, and elapsed/total time.
 * Bar heights are derived deterministically from [filePath]'s hash (there's no real waveform data
 * available) so a given message's waveform stays visually stable across recompositions.
 */
@Composable
private fun VoiceMessageView(
    filePath: String,
    playback: AudioPlaybackState,
    action: (ChatDetailsAction) -> Unit,
) {
    val isActive = playback.filePath == filePath
    val isPlaying = isActive && playback.isPlaying
    val durationMs = if (isActive) playback.durationMs else 0
    val positionMs = if (isActive) playback.currentPositionMs else 0
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val barHeights = remember(filePath) {
        val random = Random(filePath.hashCode())
        List(WAVEFORM_BAR_COUNT) { random.nextFloat().coerceIn(0.25f, 1f) }
    }

    Row(
        modifier = Modifier.widthIn(min = 180.dp, max = 220.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(PrimaryColor)
                .clickable { action(ChatDetailsAction.PlayAudio(filePath)) },
            contentAlignment = Alignment.Center,
        ) {
            if (isPlaying) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .size(width = 3.dp, height = 12.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(BackgroundColor)
                        )
                    }
                }
            } else {
                Canvas(modifier = Modifier.size(width = 12.dp, height = 14.dp)) {
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height / 2f)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(path, color = BackgroundColor)
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Column {
            Row(
                modifier = Modifier
                    .height(24.dp)
                    .widthIn(min = 100.dp, max = 160.dp)
                    .pointerInput(isActive, durationMs) {
                        if (isActive && durationMs > 0) {
                            coroutineScope {
                                launch {
                                    detectTapGestures { offset ->
                                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                        action(ChatDetailsAction.SeekAudio((fraction * durationMs).toInt()))
                                    }
                                }
                                launch {
                                    detectDragGestures(
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val fraction =
                                                (change.position.x / size.width).coerceIn(0f, 1f)
                                            action(ChatDetailsAction.SeekAudio((fraction * durationMs).toInt()))
                                        }
                                    )
                                }
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                barHeights.forEachIndexed { index, heightFraction ->
                    val isPlayed = index.toFloat() / barHeights.size < progress
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(heightFraction)
                            .clip(RoundedCornerShape(1.dp))
                            .background(
                                if (isPlayed) PrimaryColor else TextPrimaryColor.copy(alpha = 0.3f)
                            )
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (isActive && durationMs > 0) {
                    "${positionMs.msToDurationText()} / ${durationMs.msToDurationText()}"
                } else {
                    "Voice message"
                },
                style = TextStyle(fontSize = 11.sp),
                color = TextPrimaryColor.copy(alpha = 0.6f),
            )
        }
    }
}

/** Bubble for a message whose media type couldn't be identified: a generic file chip that shares the raw file when tapped. */
@Composable
private fun UnknownFileView(
    filePath: String,
    context: Context,
) {
    val file = remember(filePath) { File(filePath) }

    Row(
        modifier = Modifier
            .widthIn(min = 160.dp, max = 220.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(BackgroundColor)
            .clickable { file.shareFile(context) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            painter = painterResource(R.drawable.ic_file),
            contentDescription = null,
            tint = PrimaryColor,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = "Unknown file",
            style = TextStyle(fontSize = 13.sp),
            color = TextPrimaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            modifier = Modifier.size(16.dp),
            painter = painterResource(R.drawable.ic_share),
            contentDescription = "Share",
            tint = PrimaryColor,
        )
    }
}

/**
 * Thumbnail bubble for a video message: a decoded first frame with a play badge over it, tapping
 * anywhere opens the full-screen, auto-playing [com.kogen.giraffe.ui.features.videoPreview.presentation.screens.VideoPreviewScreen]
 * rather than trying to play video inline the way voice messages do.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun VideoThumbnailView(
    filePath: String,
    context: Context,
    onClick: () -> Unit,
) {
    val aspectRatio = remember(filePath) { decodeVideoAspectRatio(filePath) }
    // Coil doesn't decode video frames out of the box; this loader is scoped to this bubble
    // (rather than the whole screen) to keep the change local - LazyColumn only keeps a handful
    // of these composed at once, so the duplication is bounded.
    val videoImageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val targetHeight = (maxWidth / (aspectRatio ?: 0f)).coerceIn(120.dp, 260.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(targetHeight)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
        ) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = File(filePath),
                imageLoader = videoImageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.size(22.dp),
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
    }
}