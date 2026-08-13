package com.kogen.giraffeapp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kogen.giraffeapp.di.koGenViewModel

@Composable
fun RestScreen(viewModel: RestViewModel = koGenViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Text("REST Test Client", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            RestConnectionSection(
                serverHost = state.serverHost,
                serverPort = state.serverPort,
                useKtor = state.useKtor,
                onServerHostChange = viewModel::setServerHost,
                onServerPortChange = viewModel::setServerPort,
                onStackChange = viewModel::setUseKtor,
            )
            Spacer(Modifier.height(12.dp))

            Text("Сервер отдаёт →", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RestButton("Text", viewModel::getText)
                RestButton("JSON obj", viewModel::getJsonObject)
                RestButton("JSON arr", viewModel::getJsonArray)
                RestButton("Image jpg", viewModel::getImageJpg)
                RestButton("Image png", viewModel::getImagePng)
                RestButton("Image webp", viewModel::getImageWebp)
                RestButton("Image1 jpg", viewModel::getImage1Jpg)
                RestButton("Image1 png", viewModel::getImage1Png)
                RestButton("Video mp4", viewModel::getVideoMp4)
                RestButton("Audio mp3", viewModel::getAudioMp3)
                RestButton("PDF", viewModel::getPdf)
                RestButton("Unknown", viewModel::getUnknown)
                RestButton("Random", viewModel::getRandom)
            }

            Spacer(Modifier.height(12.dp))
            Text("← Клиент отправляет", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RestButton("Text (echo)", viewModel::sendText)
                RestButton("JSON (echo)", viewModel::sendJson)
                RestButton("Unknown (echo)", viewModel::sendUnknown)
                RestButton("Image (round-trip)", viewModel::roundTripImage)
                RestButton("Upload (ack)", viewModel::sendUploadAck)
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Журнал", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                OutlinedButton(onClick = viewModel::clearLog) { Text("Очистить") }
            }
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))

            RestLogList(log = state.log, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RestButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun RestConnectionSection(
    serverHost: String,
    serverPort: String,
    useKtor: Boolean,
    onServerHostChange: (String) -> Unit,
    onServerPortChange: (String) -> Unit,
    onStackChange: (Boolean) -> Unit,
) {
    Card(elevation = CardDefaults.cardElevation(2.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = serverHost,
                    onValueChange = onServerHostChange,
                    label = { Text("IP сервера") },
                    placeholder = { Text("192.168.x.x", color = MaterialTheme.colorScheme.outline) },
                    singleLine = true,
                    modifier = Modifier.weight(3f)
                )
                OutlinedTextField(
                    value = serverPort,
                    onValueChange = onServerPortChange,
                    label = { Text("Порт") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Стек:", fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = !useKtor, onClick = { onStackChange(false) }, label = { Text("Retrofit") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = useKtor, onClick = { onStackChange(true) }, label = { Text("Ktor") })
            }
        }
    }
}

@Composable
private fun RestLogList(log: List<RestLogEntry>, modifier: Modifier) {
    val listState = rememberLazyListState()

    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.animateScrollToItem(log.size - 1)
    }

    if (log.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Пока пусто", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(log) { entry -> RestLogBubble(entry) }
        }
    }
}

@Composable
private fun RestLogBubble(entry: RestLogEntry) {
    val isError = entry.error != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.direction, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("[${entry.stack}]", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(2.dp))
            if (isError) {
                Text("Ошибка: ${entry.error}", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            } else {
                Text(
                    "${entry.httpStatus ?: "?"} · ${entry.contentType ?: "?"} · ${entry.sizeBytes} байт",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(2.dp))
                Text(entry.preview, fontSize = 13.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
