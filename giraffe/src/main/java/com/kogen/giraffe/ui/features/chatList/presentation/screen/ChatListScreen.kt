package com.kogen.giraffe.ui.features.chatList.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kogen.giraffe.BuildConfig
import com.kogen.giraffe.R
import com.kogen.giraffe.ui.common.domain.models.GiraffeChatStatus
import com.kogen.giraffe.ui.common.domain.models.GiraffeLogEntry
import com.kogen.giraffe.ui.common.domain.models.color
import com.kogen.giraffe.ui.common.domain.models.icon
import com.kogen.giraffe.ui.common.domain.models.lastMessagePreview
import com.kogen.giraffe.ui.common.domain.models.title
import com.kogen.giraffe.ui.common.main.BGSecondaryColor
import com.kogen.giraffe.ui.common.main.BackgroundColor
import com.kogen.giraffe.ui.common.main.PrimaryColor
import com.kogen.giraffe.ui.common.main.TextPrimaryColor
import com.kogen.giraffe.ui.common.presentation.NoContentView
import com.kogen.giraffe.ui.features.chatList.presentation.mvi.ChatListAction
import com.kogen.giraffe.ui.features.chatList.presentation.mvi.ChatListState

/** Renders the full chat list with multi-select and bulk-delete, or [NoContentView] when empty. */
@Composable
internal fun ChatListScreen(
    state: ChatListState,
    action: (ChatListAction) -> Unit,
) {
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
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = "\uD83E\uDD92 Giraffe(gRPC logger)",
                            style = TextStyle(
                                fontSize = 24.sp,
                            ),
                            color = TextPrimaryColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "v${BuildConfig.GIRAFFE_VERSION}",
                            style = TextStyle(
                                fontSize = 11.sp,
                            ),
                            color = TextPrimaryColor.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.selectedIds.isNotEmpty()) {
                            Spacer(Modifier.width(16.dp))
                            Icon(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        action(ChatListAction.DeleteChats)
                                    }
                                    .padding(6.dp),
                                painter = painterResource(R.drawable.ic_trash),
                                contentDescription = null,
                                tint = TextPrimaryColor,
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PrimaryColor)
                        .height(1.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .animateContentSize(),
                    horizontalAlignment = Alignment.End,
                ) {
                    AnimatedVisibility(
                        visible = state.selectedIds.isNotEmpty(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Select all",
                                style = TextStyle(
                                    fontSize = 14.sp,
                                ),
                                color = TextPrimaryColor,
                            )
                            Spacer(Modifier.width(8.dp))
                            GiraffeCheckbox(
                                checked = state.selectedIds.size == state.entries.filter {
                                    it.status != GiraffeChatStatus.InProgress
                                }.size,
                                onCheckedChange = {
                                    action(
                                        if (it) ChatListAction.SelectAllChats
                                        else ChatListAction.UnSelectAllChats
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .padding(it)
                .fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            if (state.entries.isNotEmpty()) {
                items(
                    items = state.entries,
                    key = { entry -> entry.id },
                ) { entry ->
                    Box(
                        modifier = Modifier.animateItem()
                    ) {
                        ChatListItem(
                            entry = entry,
                            selectedIds = state.selectedIds,
                            action = action,
                        )
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

/** One row in the unified call list: status icon, title (gRPC URL or REST "METHOD url"), last message preview, and (for completed calls) a selection checkbox. */
@Composable
private fun ChatListItem(
    entry: GiraffeLogEntry,
    selectedIds: Set<String>,
    action: (ChatListAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BackgroundColor)
                .clickable {
                    action(ChatListAction.ShowDetails(entry))
                }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(entry.status.icon()),
                contentDescription = null,
                tint = entry.status.color(),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier
                    .weight(1f),
            ) {
                Text(
                    text = entry.title,
                    style = TextStyle(
                        fontSize = 16.sp,
                    ),
                    color = TextPrimaryColor,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.lastMessagePreview.orEmpty(),
                    style = TextStyle(
                        fontSize = 14.sp,
                    ),
                    color = TextPrimaryColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.status != GiraffeChatStatus.InProgress) {
                Spacer(Modifier.width(8.dp))
                GiraffeCheckbox(
                    checked = selectedIds.contains(entry.id),
                    onCheckedChange = {
                        action(ChatListAction.SelectChat(entry.id, it))
                    },
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BGSecondaryColor)
                .height(1.5.dp),
        )
    }
}

@Composable
private fun GiraffeCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.size(24.dp),
        colors = CheckboxDefaults.colors().copy(
            checkedCheckmarkColor = PrimaryColor,
            uncheckedCheckmarkColor = BackgroundColor,
            checkedBoxColor = BackgroundColor,
            uncheckedBoxColor = BackgroundColor,
            checkedBorderColor = PrimaryColor,
            uncheckedBorderColor = BGSecondaryColor,
        ),
    )
}