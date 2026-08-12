package com.kogen.giraffe.ui.features.restCallDetails.presentation.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kogen.giraffe.R
import com.kogen.giraffe.ui.common.domain.models.GiraffeHeader
import com.kogen.giraffe.ui.common.domain.models.GiraffeMessage
import com.kogen.giraffe.ui.common.domain.models.GiraffeRestCall
import com.kogen.giraffe.ui.common.domain.models.request
import com.kogen.giraffe.ui.common.domain.models.response
import com.kogen.giraffe.ui.common.domain.models.toClipboardText
import com.kogen.giraffe.ui.common.main.BGSecondaryColor
import com.kogen.giraffe.ui.common.main.BackgroundColor
import com.kogen.giraffe.ui.common.main.PrimaryColor
import com.kogen.giraffe.ui.common.main.TextPrimaryColor
import com.kogen.giraffe.ui.common.presentation.AudioPlaybackState
import com.kogen.giraffe.ui.common.presentation.CollapsibleDetailsDrawer
import com.kogen.giraffe.ui.common.presentation.DetailsDrawerLine
import com.kogen.giraffe.ui.common.presentation.MessageMediaContent
import com.kogen.giraffe.ui.common.presentation.NoContentView
import com.kogen.giraffe.ui.common.presentation.extensions.copyToClipboard
import com.kogen.giraffe.ui.common.presentation.extensions.timestampToDateTime
import com.kogen.giraffe.ui.features.restCallDetails.presentation.mvi.RestCallDetailsAction
import com.kogen.giraffe.ui.features.restCallDetails.presentation.mvi.RestCallDetailsState
import com.kogen.giraffe.ui.features.restCallDetails.presentation.mvi.RestCallTab

/**
 * Chucker-style Request/Response details screen for one REST call: a top bar shared by both tabs,
 * then a per-tab collapsible headers drawer with that side's body rendered below it - the HTTP
 * counterpart to [com.kogen.giraffe.ui.features.chatDetails.presentation.screens.ChatDetailsScreen].
 */
@Composable
internal fun RestCallDetailsScreen(
    state: RestCallDetailsState,
    action: (RestCallDetailsAction) -> Unit,
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
                                    action(RestCallDetailsAction.NavigateBack)
                                }
                                .padding(6.dp),
                            painter = painterResource(R.drawable.ic_arrow_left),
                            contentDescription = null,
                            tint = PrimaryColor,
                        )
                        Text(
                            modifier = Modifier.weight(1f),
                            text = state.call?.url.orEmpty(),
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
                                    state.call?.toClipboardText()
                                        ?.copyToClipboard(context, title = "REST call")
                                }
                                .padding(8.dp),
                            painter = painterResource(R.drawable.ic_copy),
                            contentDescription = "Copy whole call",
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
                RestCallTabRow(
                    selectedTab = state.selectedTab,
                    onSelect = { tab -> action(RestCallDetailsAction.SelectTab(tab)) },
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            when (state.selectedTab) {
                RestCallTab.Request -> {
                    RestCallSideView(
                        call = state.call,
                        message = state.call?.request,
                        isResponse = false,
                        showHeaders = state.showRequestHeaders,
                        onOpenHeaders = { action(RestCallDetailsAction.ShowRequestHeaders) },
                        onCloseHeaders = { action(RestCallDetailsAction.HideRequestHeaders) },
                        audioPlayback = state.audioPlayback,
                        action = action,
                    )
                }

                RestCallTab.Response -> {
                    RestCallSideView(
                        call = state.call,
                        message = state.call?.response,
                        isResponse = true,
                        showHeaders = state.showResponseHeaders,
                        onOpenHeaders = { action(RestCallDetailsAction.ShowResponseHeaders) },
                        onCloseHeaders = { action(RestCallDetailsAction.HideResponseHeaders) },
                        audioPlayback = state.audioPlayback,
                        action = action,
                    )
                }
            }
        }
    }
}

/** The two-tab selector below the top bar, in Giraffe's own style rather than stock Material tabs. */
@Composable
private fun RestCallTabRow(
    selectedTab: RestCallTab,
    onSelect: (RestCallTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        RestCallTabItem(
            label = "Request",
            isSelected = selectedTab == RestCallTab.Request,
            onClick = { onSelect(RestCallTab.Request) },
            modifier = Modifier.weight(1f),
        )
        RestCallTabItem(
            label = "Response",
            isSelected = selectedTab == RestCallTab.Response,
            onClick = { onSelect(RestCallTab.Response) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RestCallTabItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (isSelected) PrimaryColor else TextPrimaryColor.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(2.dp)
                .background(if (isSelected) PrimaryColor else BGSecondaryColor),
        )
    }
}

/** One tab's content: the collapsible headers drawer for that direction, then the body below it. */
@Composable
private fun RestCallSideView(
    call: GiraffeRestCall?,
    message: GiraffeMessage?,
    isResponse: Boolean,
    showHeaders: Boolean,
    onOpenHeaders: () -> Unit,
    onCloseHeaders: () -> Unit,
    audioPlayback: AudioPlaybackState,
    action: (RestCallDetailsAction) -> Unit,
) {
    if (call == null) {
        NoContentView()
        return
    }

    val headers = call.headers.filter { header -> header.isResponse == isResponse }

    CollapsibleDetailsDrawer(
        isVisible = showHeaders,
        onOpen = onOpenHeaders,
        onClose = onCloseHeaders,
    ) {
        if (isResponse) {
            DetailsDrawerLine("Status: ${call.status}${call.httpStatusCode?.let { " ($it)" }.orEmpty()}")
        } else {
            DetailsDrawerLine("Method: ${call.httpMethod}")
            DetailsDrawerLine("Url: ${call.url}")
            DetailsDrawerLine("Start time: ${call.timestamp.timestampToDateTime()}")
        }
        if (headers.isEmpty()) {
            DetailsDrawerLine("Headers: none")
        } else {
            DetailsDrawerLine("Headers:")
            headers.forEach { header: GiraffeHeader ->
                DetailsDrawerLine("${header.key}: ${header.value}")
            }
        }
    }

    RestCallBodyView(
        message = message,
        audioPlayback = audioPlayback,
        action = action,
    )
}

/** The request/response body itself: text plus any extracted media, full-width (there's no left/right speaker distinction here, unlike the gRPC chat transcript). */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun RestCallBodyView(
    message: GiraffeMessage?,
    audioPlayback: AudioPlaybackState,
    action: (RestCallDetailsAction) -> Unit,
) {
    val context = LocalContext.current

    if (message == null) {
        NoContentView()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = BGSecondaryColor,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(12.dp),
        ) {
            Text(
                text = message.textContent.orEmpty(),
                style = TextStyle(
                    fontSize = 14.sp,
                ),
                color = TextPrimaryColor,
            )
            if (message.filePath.isNullOrBlank().not()) {
                MessageMediaContent(
                    message = message,
                    audioPlayback = audioPlayback,
                    onImageClick = { filePath -> action(RestCallDetailsAction.ShowImage(filePath)) },
                    onVideoClick = { filePath -> action(RestCallDetailsAction.ShowVideo(filePath)) },
                    onPdfClick = { filePath -> action(RestCallDetailsAction.ShowPdf(filePath)) },
                    onAudioPlayClick = { filePath -> action(RestCallDetailsAction.PlayAudio(filePath)) },
                    onAudioSeek = { position -> action(RestCallDetailsAction.SeekAudio(position)) },
                )
            }
        }
        if (message.textContent.isNullOrBlank().not()) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.padding(start = 4.dp),
            ) {
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
