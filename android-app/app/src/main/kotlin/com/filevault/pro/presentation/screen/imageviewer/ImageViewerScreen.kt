package com.filevault.pro.presentation.screen.imageviewer

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.filevault.pro.util.FileUtils
import com.filevault.pro.util.MediaQueue
import java.io.File
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(
    path: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val decodedPath = remember(path) {
        try { URLDecoder.decode(path, "UTF-8") } catch (_: Exception) { path }
    }

    val allPaths = remember { MediaQueue.filePaths.ifEmpty { listOf(decodedPath) } }
    val startIndex = remember { allPaths.indexOf(decodedPath).coerceAtLeast(0) }

    val pagerState = rememberPagerState(initialPage = startIndex) { allPaths.size }
    var showControls by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true
        ) { pageIndex ->
            val pagePath = allPaths.getOrNull(pageIndex) ?: decodedPath
            val pageFile = remember(pagePath) { File(pagePath) }
            ZoomableImage(
                file = pageFile,
                onTap = { showControls = !showControls }
            )
        }

        val currentFile = remember(pagerState.currentPage) {
            File(allPaths.getOrNull(pagerState.currentPage) ?: decodedPath)
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            currentFile.name,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (allPaths.size > 1) {
                            Text(
                                "${pagerState.currentPage + 1} / ${allPaths.size}",
                                color = Color.White.copy(0.6f),
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(onClick = {
                        val fileUri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", currentFile
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Image"))
                    }) {
                        Icon(Icons.Default.Share, "Share", tint = Color.White)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showControls && allPaths.size > 1,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)
        ) {
            val scope = rememberCoroutineScope()
            IconButton(
                onClick = {
                    if (pagerState.currentPage > 0) {
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }
                },
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(Color.Black.copy(0.45f))
            ) {
                Icon(Icons.Default.ChevronLeft, "Previous", tint = Color.White)
            }
        }

        AnimatedVisibility(
            visible = showControls && allPaths.size > 1,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
        ) {
            IconButton(
                onClick = {
                    if (pagerState.currentPage < allPaths.size - 1) {
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(Color.Black.copy(0.45f))
            ) {
                Icon(Icons.Default.ChevronRight, "Next", tint = Color.White)
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BottomControls(
                file = currentFile,
                onOpenExternal = {
                    val fileUri = FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", currentFile
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(fileUri, "image/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(intent) }
                }
            )
        }
    }
}

@Composable
private fun ZoomableImage(
    file: File,
    onTap: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotation by remember { mutableFloatStateOf(0f) }

    val minScale = 1f
    val maxScale = 5f

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = file,
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                    rotationZ = rotation
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                        scale = newScale
                        if (scale > 1f) {
                            offset = Offset(offset.x + pan.x, offset.y + pan.y)
                        } else {
                            offset = Offset.Zero
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                }
        )

        if (scale > 1f) {
            var showResetButton by remember { mutableStateOf(true) }
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 8.dp)) {
                SmallFloatingActionButton(
                    onClick = { scale = 1f; offset = Offset.Zero; rotation = 0f },
                    containerColor = Color.Black.copy(0.55f),
                    contentColor = Color.White,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.FitScreen, "Reset zoom", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun BottomControls(
    file: File,
    onOpenExternal: () -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .navigationBarsPadding()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(icon = Icons.Default.RotateRight, label = "Rotate") {}
            ControlButton(icon = Icons.Default.Info, label = "Info") { showInfo = !showInfo }
            ControlButton(icon = Icons.Default.OpenInNew, label = "Open") { onOpenExternal() }
        }
    }

    if (showInfo) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
                .pointerInput(Unit) { detectTapGestures { showInfo = false } },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("File Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    InfoRow("Name", file.name)
                    InfoRow("Size", FileUtils.formatSize(file.length()))
                    InfoRow("Path", file.parent ?: "")
                    InfoRow("Modified",
                        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                            .format(Date(file.lastModified())))
                    InfoRow("Extension", file.extension.uppercase().ifEmpty { "Unknown" })
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showInfo = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
