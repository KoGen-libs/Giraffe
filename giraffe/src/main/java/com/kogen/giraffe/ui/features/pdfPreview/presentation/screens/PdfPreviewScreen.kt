package com.kogen.giraffe.ui.features.pdfPreview.presentation.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kogen.giraffe.R
import com.kogen.giraffe.ui.common.main.TextPrimaryColor
import com.kogen.giraffe.ui.common.presentation.PreviewIconButton
import com.kogen.giraffe.ui.common.presentation.extensions.shareFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import java.io.File

/**
 * Full-screen PDF viewer: one page per screen, swiped through via [HorizontalPager], each page
 * independently pinch-zoomable. Pages are rasterized on demand through the platform's
 * [PdfRenderer] - this is a viewer, not a reader, so there's no text layer or search, only what
 * amounts to a stack of page images. That's enough to tell what document came through the wire,
 * without pulling in a full PDF-rendering dependency for a debug tool.
 */
@Composable
internal fun PdfPreviewScreen(
    filePath: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val file = remember(filePath) { File(filePath) }

    val renderer = remember(filePath) {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(descriptor)
    }
    // PdfRenderer isn't thread-safe, and HorizontalPager can keep more than one page composed
    // around a swipe - every render call funnels through this lock instead of racing on the one
    // renderer instance.
    val renderLock = remember { Mutex() }

    DisposableEffect(renderer) {
        onDispose { renderer.close() }
    }

    val pagerState = rememberPagerState(pageCount = { renderer.pageCount })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            PdfPageView(renderer = renderer, renderLock = renderLock, pageIndex = pageIndex)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PreviewIconButton(
                icon = R.drawable.ic_arrow_left,
                contentDescription = "Back",
                onClick = onBack,
            )
            PreviewIconButton(
                icon = R.drawable.ic_share,
                contentDescription = "Share",
                onClick = { file.shareFile(context, mimeType = "application/pdf") },
            )
        }

        if (renderer.pageCount > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "${pagerState.currentPage + 1}/${renderer.pageCount}",
                    style = TextStyle(fontSize = 13.sp),
                    color = TextPrimaryColor,
                )
            }
        }
    }
}

/** Rasterizes and displays a single PDF page, at a resolution matching the available width. */
@Composable
private fun PdfPageView(
    renderer: PdfRenderer,
    renderLock: Mutex,
    pageIndex: Int,
) {
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val widthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val zoomState = rememberZoomState()

        val bitmap by produceState<Bitmap?>(initialValue = null, key1 = pageIndex, key2 = widthPx) {
            value = withContext(Dispatchers.IO) {
                renderLock.withLock {
                    renderer.openPage(pageIndex).use { page ->
                        val heightPx =
                            (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).apply {
                            // PdfRenderer only paints actual page content - without this, areas
                            // the page doesn't draw over are transparent instead of page-white.
                            Canvas(this).drawColor(android.graphics.Color.WHITE)
                            page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }

        bitmap?.let { pageBitmap ->
            LaunchedEffect(pageBitmap) {
                zoomState.setContentSize(Size(pageBitmap.width.toFloat(), pageBitmap.height.toFloat()))
            }
            Image(
                bitmap = pageBitmap.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier
                    .fillMaxSize()
                    .zoomable(zoomState),
            )
        }
    }
}
