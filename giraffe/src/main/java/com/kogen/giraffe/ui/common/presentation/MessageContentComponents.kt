package com.kogen.giraffe.ui.common.presentation

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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import com.kogen.giraffe.ui.common.domain.models.GiraffeContentType
import com.kogen.giraffe.ui.common.domain.models.GiraffeMessage
import com.kogen.giraffe.ui.common.main.BGSecondaryColor
import com.kogen.giraffe.ui.common.main.BackgroundColor
import com.kogen.giraffe.ui.common.main.PrimaryColor
import com.kogen.giraffe.ui.common.main.TextPrimaryColor
import com.kogen.giraffe.ui.common.presentation.extensions.decodeImageAspectRatio
import com.kogen.giraffe.ui.common.presentation.extensions.decodeVideoAspectRatio
import com.kogen.giraffe.ui.common.presentation.extensions.msToDurationText
import com.kogen.giraffe.ui.common.presentation.extensions.shareFile
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

private const val WAVEFORM_BAR_COUNT = 27

/**
 * The Giraffe "drawer" pattern shared by every details screen: a chevron below the top bar that
 * toggles a rounded panel sliding out underneath it. [content] renders whatever metadata/headers
 * that screen wants shown - gRPC's [com.kogen.giraffe.ui.features.chatDetails.presentation.screens.ChatDetailsScreen]
 * and REST's call details screen both plug their own fields into the same shell.
 */
@Composable
internal fun CollapsibleDetailsDrawer(
    isVisible: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
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
                    content = content,
                )
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

/** A single "label: value" line in the style [CollapsibleDetailsDrawer] content uses throughout. */
@Composable
internal fun DetailsDrawerLine(text: String) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = 14.sp,
            color = TextPrimaryColor,
        ),
    )
}

/**
 * Renders [message]'s extracted media below its text, dispatching on [GiraffeMessage.contentType].
 * Shared by gRPC's server/client bubbles and REST's request/response bodies - identical either way,
 * since a body's content type carries no gRPC/REST-specific meaning.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
internal fun MessageMediaContent(
    message: GiraffeMessage,
    audioPlayback: AudioPlaybackState,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onPdfClick: (String) -> Unit,
    onAudioPlayClick: (String) -> Unit,
    onAudioSeek: (Int) -> Unit,
) {
    val context = LocalContext.current
    val filePath = message.filePath ?: return

    when (message.contentType) {
        GiraffeContentType.Image -> {
            Spacer(Modifier.height(8.dp))

            val aspectRatio = remember(filePath) {
                decodeImageAspectRatio(filePath)
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val targetHeight = (maxWidth / (aspectRatio ?: 0f)).coerceIn(120.dp, 260.dp)

                AsyncImage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(targetHeight)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onImageClick(filePath) },
                    model = File(filePath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
            }
        }

        GiraffeContentType.Audio -> {
            Spacer(Modifier.height(4.dp))
            VoiceMessageView(
                filePath = filePath,
                playback = audioPlayback,
                onPlayClick = onAudioPlayClick,
                onSeek = onAudioSeek,
            )
        }

        GiraffeContentType.Video -> {
            Spacer(Modifier.height(8.dp))
            VideoThumbnailView(
                filePath = filePath,
                context = context,
                onClick = { onVideoClick(filePath) },
            )
        }

        GiraffeContentType.Pdf -> {
            Spacer(Modifier.height(4.dp))
            FileChipView(
                label = "PDF document",
                trailingIcon = R.drawable.ic_chevron_down,
                trailingIconRotation = -90f,
                onClick = { onPdfClick(filePath) },
            )
        }

        GiraffeContentType.Unknown -> {
            Spacer(Modifier.height(4.dp))
            FileChipView(
                label = "Unknown file",
                trailingIcon = R.drawable.ic_share,
                onClick = { File(filePath).shareFile(context) },
            )
        }

        else -> {}
    }
}

/**
 * A voice-message bubble: play/pause button, a scrubbable waveform, and elapsed/total time.
 * Bar heights are derived deterministically from [filePath]'s hash (there's no real waveform data
 * available) so a given message's waveform stays visually stable across recompositions.
 */
@Composable
internal fun VoiceMessageView(
    filePath: String,
    playback: AudioPlaybackState,
    onPlayClick: (String) -> Unit,
    onSeek: (Int) -> Unit,
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
                .clickable { onPlayClick(filePath) },
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
                                        onSeek((fraction * durationMs).toInt())
                                    }
                                }
                                launch {
                                    detectDragGestures(
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val fraction =
                                                (change.position.x / size.width).coerceIn(0f, 1f)
                                            onSeek((fraction * durationMs).toInt())
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

/**
 * Generic file chip: an icon, a one-line label, and a trailing action icon. Used for both a
 * message whose media type couldn't be identified (tap shares the raw file directly - there's
 * nothing to preview) and a PDF (tap opens the page viewer instead).
 */
@Composable
internal fun FileChipView(
    label: String,
    trailingIcon: Int,
    trailingIconRotation: Float = 0f,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .widthIn(min = 160.dp, max = 220.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(BackgroundColor)
            .clickable(onClick = onClick)
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
            text = label,
            style = TextStyle(fontSize = 13.sp),
            color = TextPrimaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            modifier = Modifier
                .size(16.dp)
                .rotate(trailingIconRotation),
            painter = painterResource(trailingIcon),
            contentDescription = null,
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
internal fun VideoThumbnailView(
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
