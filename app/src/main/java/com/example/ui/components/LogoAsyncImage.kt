package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest

// Cache for scanned logo filter results to eliminate CPU recalculation overhead
private val logoFilterCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

@Composable
fun LogoAsyncImage(
    logoUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
    onState: ((AsyncImagePainter.State) -> Unit)? = null
) {
    if (logoUrl.isNullOrEmpty()) return

    val context = LocalContext.current
    var applyWhiteFilter by remember(logoUrl) {
        mutableStateOf(logoFilterCache[logoUrl] ?: false)
    }

    val logoRequest = remember(logoUrl) {
        ImageRequest.Builder(context)
            .data(logoUrl)
            .crossfade(true)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .listener(
                onSuccess = { _, result ->
                    if (logoFilterCache.containsKey(logoUrl)) {
                        applyWhiteFilter = logoFilterCache[logoUrl]!!
                        return@listener
                    }
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                        try {
                            val drawable = result.drawable
                            val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: run {
                                val bmp = Bitmap.createBitmap(
                                    if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 400,
                                    if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 200,
                                    Bitmap.Config.ARGB_8888
                                )
                                val canvas = android.graphics.Canvas(bmp)
                                drawable.setBounds(0, 0, canvas.width, canvas.height)
                                drawable.draw(canvas)
                                bmp
                            }

                            var totalVisible = 0
                            var darkMonochromeCount = 0

                            val stepX = (bitmap.width / 20).coerceIn(1, 10)
                            val stepY = (bitmap.height / 20).coerceIn(1, 10)

                            for (x in 0 until bitmap.width step stepX) {
                                for (y in 0 until bitmap.height step stepY) {
                                    val pixel = bitmap.getPixel(x, y)
                                    val alpha = android.graphics.Color.alpha(pixel)
                                    if (alpha > 40) {
                                        val r = android.graphics.Color.red(pixel)
                                        val g = android.graphics.Color.green(pixel)
                                        val b = android.graphics.Color.blue(pixel)

                                        val maxChannel = maxOf(r, g, b)
                                        val minChannel = minOf(r, g, b)
                                        val colorDiff = maxChannel - minChannel

                                        totalVisible++

                                        if (maxChannel < 85 && colorDiff < 35) {
                                            darkMonochromeCount++
                                        }
                                    }
                                }
                            }

                            val isDark = if (totalVisible >= 10) {
                                val darkRatio = darkMonochromeCount.toFloat() / totalVisible
                                darkRatio >= 0.85f
                            } else false

                            logoFilterCache[logoUrl] = isDark
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                applyWhiteFilter = isDark
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("LogoAsyncImage", "Error inspecting logo bitmap", e)
                        }
                    }
                }
            )
            .build()
    }

    AsyncImage(
        model = logoRequest,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
        colorFilter = if (applyWhiteFilter && contentDescription?.contains("Mestres do Universo", ignoreCase = true) != true && contentDescription?.contains("Masters of the Universe", ignoreCase = true) != true) ColorFilter.tint(Color.White) else null,
        onState = onState
    )
}
