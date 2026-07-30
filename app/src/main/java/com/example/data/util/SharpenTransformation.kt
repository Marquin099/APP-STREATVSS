package com.example.ui.util

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation

/**
 * Filtro de nitidez (Sharpen Filter) ~28% leve e otimizado para evitar estouro de memória (OOM).
 */
class SharpenTransformation(
    private val intensity: Float = 0.28f
) : Transformation {

    override val cacheKey: String = "SharpenTransformation_${(intensity * 100).toInt()}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (intensity <= 0f) return input

        try {
            val width = input.width
            val height = input.height
            if (width <= 2 || height <= 2) return input

            // Se a imagem for gigante (ex: > 1200px), limita para não estourar a memória durante o scroll
            if (width * height > 1_500_000) {
                return input
            }

            val config = input.config ?: Bitmap.Config.ARGB_8888
            val output = Bitmap.createBitmap(width, height, config)

            val pixels = IntArray(width * height)
            input.getPixels(pixels, 0, width, 0, 0, width, height)
            val resultPixels = IntArray(width * height)

            val factor = intensity.coerceIn(0.05f, 0.4f)
            val centerWeight = 1f + 4f * factor
            val neighborWeight = -factor

            for (y in 1 until height - 1) {
                val yPrev = (y - 1) * width
                val yCurr = y * width
                val yNext = (y + 1) * width

                for (x in 1 until width - 1) {
                    val idx = yCurr + x
                    val c = pixels[idx]
                    val top = pixels[yPrev + x]
                    val bottom = pixels[yNext + x]
                    val left = pixels[yCurr + x - 1]
                    val right = pixels[yCurr + x + 1]

                    val a = (c ushr 24) and 0xFF
                    val rC = (c ushr 16) and 0xFF
                    val gC = (c ushr 8) and 0xFF
                    val bC = c and 0xFF

                    val rT = (top ushr 16) and 0xFF
                    val gT = (top ushr 8) and 0xFF
                    val bT = top and 0xFF

                    val rB = (bottom ushr 16) and 0xFF
                    val gB = (bottom ushr 8) and 0xFF
                    val bB = bottom and 0xFF

                    val rL = (left ushr 16) and 0xFF
                    val gL = (left ushr 8) and 0xFF
                    val bL = left and 0xFF

                    val rR = (right ushr 16) and 0xFF
                    val gR = (right ushr 8) and 0xFF
                    val bR = right and 0xFF

                    var r = (rC * centerWeight + (rT + rB + rL + rR) * neighborWeight).toInt()
                    var g = (gC * centerWeight + (gT + gB + gL + gR) * neighborWeight).toInt()
                    var b = (bC * centerWeight + (bT + bB + bL + bR) * neighborWeight).toInt()

                    if (r < 0) r = 0 else if (r > 255) r = 255
                    if (g < 0) g = 0 else if (g > 255) g = 255
                    if (b < 0) b = 0 else if (b > 255) b = 255

                    resultPixels[idx] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            output.setPixels(resultPixels, 0, width, 0, 0, width, height)
            return output
        } catch (t: Throwable) {
            // Em caso de falta de memória (OutOfMemoryError) ou erro de processamento, retorna a imagem original intacta
            return input
        }
    }
}

