package com.example.formulamaster.domain.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * 笔画 → Bitmap 渲染器（A1 / A2 / 未来 L2 共用）
 *
 * 关键设计：
 * - 直接在目标分辨率上绘制（避免 createScaledBitmap 后插值模糊导致 OCR 准确率下降）
 * - 最小宽度 [TARGET_MIN_WIDTH_PX]，宽高比保持原样
 * - 笔画粗细随缩放因子放大，避免在大画布上变成头发丝
 * - ROUND cap/join 提高线段连贯性
 *
 * 输入：每笔为有序坐标点 `(x, y)` 列表（屏幕坐标系）
 */
object StrokeBitmapRenderer {

    /** OCR 推荐的最小图像宽度（px），低于此值会上采样 */
    private const val TARGET_MIN_WIDTH_PX = 800f

    /** 笔画 bitmap 的内边距（原坐标系，缩放前） */
    private const val PADDING_PX = 20f

    /** 基础笔画粗细（原坐标系，缩放前） */
    private const val STROKE_WIDTH_PX = 3f

    fun render(strokes: List<List<Pair<Float, Float>>>): Bitmap {
        if (strokes.isEmpty()) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        val allPoints = strokes.flatten()
        if (allPoints.isEmpty()) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        val minX = allPoints.minOf { it.first }
        val maxX = allPoints.maxOf { it.first }
        val minY = allPoints.minOf { it.second }
        val maxY = allPoints.maxOf { it.second }

        val rawWidth = (maxX - minX + 2 * PADDING_PX).coerceAtLeast(100f)
        val rawHeight = (maxY - minY + 2 * PADDING_PX).coerceAtLeast(100f)

        // 缩放因子：保证目标宽度至少 TARGET_MIN_WIDTH_PX；不下采样
        val scale = (TARGET_MIN_WIDTH_PX / rawWidth).coerceAtLeast(1f)

        val width = (rawWidth * scale).toInt()
        val height = (rawHeight * scale).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            strokeWidth = STROKE_WIDTH_PX * scale
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        for (stroke in strokes) {
            for (i in 0 until stroke.size - 1) {
                val (x0, y0) = stroke[i]
                val (x1, y1) = stroke[i + 1]
                canvas.drawLine(
                    (x0 - minX + PADDING_PX) * scale,
                    (y0 - minY + PADDING_PX) * scale,
                    (x1 - minX + PADDING_PX) * scale,
                    (y1 - minY + PADDING_PX) * scale,
                    paint
                )
            }
        }

        return bitmap
    }
}
