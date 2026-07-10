package com.example.facemocap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var points: List<Pair<Float, Float>> = emptyList()
    private var imageWidth = 1
    private var imageHeight = 1
    private var mirror = true

    private val dotPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
        alpha = 180
    }

    fun setResults(
        normalizedPoints: List<Pair<Float, Float>>,
        srcImageWidth: Int,
        srcImageHeight: Int,
        mirrorHorizontally: Boolean
    ) {
        points = normalizedPoints
        imageWidth = srcImageWidth
        imageHeight = srcImageHeight
        mirror = mirrorHorizontally
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return

        val rotatedImageWidth = imageHeight
        val rotatedImageHeight = imageWidth

        val viewAspect = width.toFloat() / height.toFloat()
        val imageAspect = rotatedImageWidth.toFloat() / rotatedImageHeight.toFloat()

        val scale: Float
        var dx = 0f
        var dy = 0f

        if (viewAspect > imageAspect) {
            scale = width.toFloat() / rotatedImageWidth
            dy = (height - rotatedImageHeight * scale) / 2f
        } else {
            scale = height.toFloat() / rotatedImageHeight
            dx = (width - rotatedImageWidth * scale) / 2f
        }

        fun project(nx: Float, ny: Float): Pair<Float, Float> {
            val rotatedNx = ny
            val rotatedNy = 1f - nx
            var px = rotatedNx * rotatedImageWidth * scale + dx
            val py = rotatedNy * rotatedImageHeight * scale + dy
            if (mirror) px = width - px
            return px to py
        }

        // Линии - только если точек хватает на полный набор индексов (468),
        // иначе просто пропускаем (например, если модель отдаёт другое число точек).
        if (points.size >= 468) {
            for ((a, b) in FACE_CONNECTIONS) {
                val (ax, ay) = project(points[a].first, points[a].second)
                val (bx, by) = project(points[b].first, points[b].second)
                canvas.drawLine(ax, ay, bx, by, linePaint)
            }
        }

        for ((nx, ny) in points) {
            val (px, py) = project(nx, ny)
            canvas.drawCircle(px, py, 5f, dotPaint)
        }
    }

    companion object {
        // Стандартные контуры MediaPipe Face Mesh: овал лица, брови, глаза, губы.
        // Не полная тесселяция (~470+ рёбер по всей поверхности), а только контуры -
        // выглядит как читаемый каркас лица, а не сплошная сетка треугольников.
        private val FACE_OVAL = listOf(
            10 to 338, 338 to 297, 297 to 332, 332 to 284, 284 to 251, 251 to 389,
            389 to 356, 356 to 454, 454 to 323, 323 to 361, 361 to 288, 288 to 397,
            397 to 365, 365 to 379, 379 to 378, 378 to 400, 400 to 377, 377 to 152,
            152 to 148, 148 to 176, 176 to 149, 149 to 150, 150 to 136, 136 to 172,
            172 to 58, 58 to 132, 132 to 93, 93 to 234, 234 to 127, 127 to 162,
            162 to 21, 21 to 54, 54 to 103, 103 to 67, 67 to 109, 109 to 10
        )

        private val LEFT_EYE = listOf(
            263 to 249, 249 to 390, 390 to 373, 373 to 374, 374 to 380, 380 to 381,
            381 to 382, 382 to 362, 263 to 466, 466 to 388, 388 to 387, 387 to 386,
            386 to 385, 385 to 384, 384 to 398, 398 to 362
        )

        private val RIGHT_EYE = listOf(
            33 to 7, 7 to 163, 163 to 144, 144 to 145, 145 to 153, 153 to 154,
            154 to 155, 155 to 133, 33 to 246, 246 to 161, 161 to 160, 160 to 159,
            159 to 158, 158 to 157, 157 to 173, 173 to 133
        )

        private val LEFT_EYEBROW = listOf(
            276 to 283, 283 to 282, 282 to 295, 295 to 285,
            300 to 293, 293 to 334, 334 to 296, 296 to 336
        )

        private val RIGHT_EYEBROW = listOf(
            46 to 53, 53 to 52, 52 to 65, 65 to 55,
            70 to 63, 63 to 105, 105 to 66, 66 to 107
        )

        private val LIPS = listOf(
            61 to 146, 146 to 91, 91 to 181, 181 to 84, 84 to 17, 17 to 314,
            314 to 405, 405 to 321, 321 to 375, 375 to 291, 61 to 185, 185 to 40,
            40 to 39, 39 to 37, 37 to 0, 0 to 267, 267 to 269, 269 to 270,
            270 to 409, 409 to 291, 78 to 95, 95 to 88, 88 to 178, 178 to 87,
            87 to 14, 14 to 317, 317 to 402, 402 to 318, 318 to 324, 324 to 308,
            78 to 191, 191 to 80, 80 to 81, 81 to 82, 82 to 13, 13 to 312,
            312 to 311, 311 to 310, 310 to 415, 415 to 308
        )

        val FACE_CONNECTIONS: List<Pair<Int, Int>> =
            FACE_OVAL + LEFT_EYE + RIGHT_EYE + LEFT_EYEBROW + RIGHT_EYEBROW + LIPS
    }
}