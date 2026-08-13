package com.kogen.giraffe.ui.common.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A plain colored dot standing in for a call's status - a status "lamp" instead of a distinct
 * pictogram per state. Shared by the call list row and the REST call details top bar.
 */
@Composable
internal fun StatusLamp(color: Color) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/** Small colored pill for a REST call's HTTP status code, à la Postman/Chucker - [tint] is the same color the status lamp next to it already uses. */
@Composable
internal fun StatusCodeBadge(code: Int, tint: Color) {
    Text(
        text = code.toString(),
        style = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        ),
        color = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
