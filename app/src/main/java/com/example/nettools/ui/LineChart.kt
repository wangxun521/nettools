package com.example.nettools.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * 轻量折线图：传入一个 Float 列表，自动缩放到画布。
 * @param values   y 数据（末尾 = 最新）。null 表示丢点，会画断点
 * @param lineColor 线条颜色
 * @param minRange y 轴最小跨度（避免数值都很接近时图变平直）
 */
@Composable
fun LineChart(
    values: List<Float?>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillBelow: Boolean = true,
    minRange: Float = 1f,
) {
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            if (values.size < 2) return@Canvas
            val real = values.filterNotNull()
            if (real.isEmpty()) return@Canvas
            val yMin = real.min()
            val yMax = real.max()
            val span = (yMax - yMin).coerceAtLeast(minRange)
            val w = size.width
            val h = size.height
            val n = values.size
            val stepX = if (n > 1) w / (n - 1) else 0f

            fun pt(i: Int, v: Float): Offset {
                val x = i * stepX
                val y = h - (v - yMin) / span * h * 0.9f - h * 0.05f
                return Offset(x, y)
            }

            // 网格 - 4 条横线
            val grid = lineColor.copy(alpha = 0.08f)
            for (i in 0..3) {
                val y = h * i / 3f
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }

            // 路径
            val line = Path()
            val fill = Path()
            var hasMove = false
            values.forEachIndexed { i, v ->
                if (v == null) { hasMove = false; return@forEachIndexed }
                val p = pt(i, v)
                if (!hasMove) {
                    line.moveTo(p.x, p.y)
                    fill.moveTo(p.x, h)
                    fill.lineTo(p.x, p.y)
                    hasMove = true
                } else {
                    line.lineTo(p.x, p.y)
                    fill.lineTo(p.x, p.y)
                }
                if (i == values.lastIndex) fill.lineTo(p.x, h)
            }

            if (fillBelow) {
                drawPath(fill, color = lineColor.copy(alpha = 0.15f))
            }
            drawPath(line, color = lineColor, style = Stroke(width = 3f))

            // 最后一个点画个圆点
            values.lastOrNull()?.let { v ->
                val p = pt(values.lastIndex, v)
                drawCircle(lineColor, radius = 5f, center = p)
            }
        }
    }
}
