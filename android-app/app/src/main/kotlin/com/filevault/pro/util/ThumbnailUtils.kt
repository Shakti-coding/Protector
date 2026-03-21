package com.filevault.pro.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Size
import androidx.compose.runtime.*
import com.filevault.pro.domain.model.FileEntry
import com.filevault.pro.domain.model.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun rememberFileThumbnail(file: FileEntry, context: Context): Bitmap? {
    var bitmap by remember(file.path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file.path) {
        bitmap = loadThumbnail(context, file)
    }
    return bitmap
}

suspend fun loadThumbnail(context: Context, file: FileEntry): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        when (file.fileType) {
            FileType.PHOTO -> loadPhotoThumbnail(file.path)
            FileType.VIDEO -> loadVideoThumbnail(context, file.path)
            FileType.AUDIO -> loadAudioArt(file.path)
            FileType.DOCUMENT -> if (file.mimeType == "application/pdf") loadPdfFirstPage(file.path) else null
            FileType.APK -> loadApkIcon(context, file.path)
            else -> null
        }
    }.getOrNull()
}

private fun loadPhotoThumbnail(path: String): Bitmap? {
    val file = File(path)
    if (!file.exists()) return null
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, opts)
    if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
    opts.inSampleSize = calculateInSampleSize(opts, 128, 128)
    opts.inJustDecodeBounds = false
    opts.inPreferredConfig = Bitmap.Config.RGB_565
    return BitmapFactory.decodeFile(path, opts)
}

private fun calculateInSampleSize(opts: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
    val h = opts.outHeight
    val w = opts.outWidth
    var inSampleSize = 1
    if (h > reqH || w > reqW) {
        val halfH = h / 2
        val halfW = w / 2
        while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

private fun loadVideoThumbnail(context: Context, path: String): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching {
            android.media.ThumbnailUtils.createVideoThumbnail(File(path), Size(128, 128), null)
        }.getOrNull()?.let { return it }
    }
    val retriever = MediaMetadataRetriever()
    return runCatching {
        retriever.setDataSource(path)
        retriever.frameAtTime
    }.getOrNull().also { retriever.release() }
}

private fun loadAudioArt(path: String): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return runCatching {
        retriever.setDataSource(path)
        val bytes = retriever.embeddedPicture ?: return@runCatching null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull().also { retriever.release() }
}

private fun loadPdfFirstPage(path: String): Bitmap? {
    val file = File(path)
    if (!file.exists()) return null
    val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    return runCatching {
        val renderer = PdfRenderer(fd)
        val page = renderer.openPage(0)
        val bmp = Bitmap.createBitmap(128, (128f * page.height / page.width).toInt(), Bitmap.Config.ARGB_8888)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        renderer.close()
        bmp
    }.getOrNull().also { fd.close() }
}

private fun loadApkIcon(context: Context, path: String): Bitmap? {
    val pm = context.packageManager
    val info = pm.getPackageArchiveInfo(path, 0) ?: return null
    info.applicationInfo?.sourceDir = path
    info.applicationInfo?.publicSourceDir = path
    val drawable = info.applicationInfo?.loadIcon(pm) ?: return null
    val bmp = Bitmap.createBitmap(
        drawable.intrinsicWidth.coerceAtLeast(1),
        drawable.intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bmp)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bmp
}
