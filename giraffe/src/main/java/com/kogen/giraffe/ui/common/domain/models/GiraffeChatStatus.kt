package com.kogen.giraffe.ui.common.domain.models

import androidx.compose.ui.graphics.Color
import com.kogen.giraffe.ui.common.main.BGSecondaryColor
import com.kogen.giraffe.ui.common.main.BGTertiaryColor
import com.kogen.giraffe.ui.common.main.ErrorColor
import com.kogen.giraffe.ui.common.main.PrimaryColor
import com.kogen.giraffe.ui.common.main.SuccessColor

/**
 * Lifecycle state of a logged gRPC call. [InProgress] until the call closes, then [Ok] or
 * [Error] depending on the closing [io.grpc.Status]; [Interrupted] is only ever set retroactively,
 * for calls that were still [InProgress] when the process died (see
 * [com.kogen.giraffe.db.dao.GiraffeLogDao.sanitizeStuckChats]).
 */
internal enum class GiraffeChatStatus {
    InProgress,
    Ok,
    Error,
    Interrupted,
}

/** Status indicator color shown in the chat list/details UI. */
internal fun GiraffeChatStatus.color(): Color {
    return when (this) {
        GiraffeChatStatus.InProgress -> BGTertiaryColor
        GiraffeChatStatus.Ok -> SuccessColor
        GiraffeChatStatus.Error -> ErrorColor
        GiraffeChatStatus.Interrupted -> PrimaryColor
    }
}
