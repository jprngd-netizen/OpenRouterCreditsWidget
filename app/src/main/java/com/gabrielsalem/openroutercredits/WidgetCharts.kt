package com.gabrielsalem.openroutercredits

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Renderiza gráficos em Bitmap para empurrar via setImageViewBitmap no RemoteViews.
 * - sparkline: linha do uso derivado localmente (resolução por poll de 15min)
 * - bars: barras dos últimos 7 dias (granularidade que /activity realmente entrega)
 */
object WidgetCharts {

    fun sparkline(points: List<Pair<Long, Double>>, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (points.size < 2) return bmp
        val values = points.map { it.second }
        val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1e-9)
        val padTop = h * 0.12f
        val padBottom = h * 0.12f
        val usableH = h - padTop - padBottom
        val stepX = w.toFloat() / (points.size - 1)

        val path = android.graphics.Path()
        val pts = points.mapIndexed { i, p ->
            val x = i * stepX
            val y = (h - padBottom) - (p.second / max * usableH).toFloat()
            x to y
        }
        pts.forEachIndexed { i, (x, y) ->
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fill = android.graphics.Path(path)
        fill.lineTo(w.toFloat(), h.toFloat())
        fill.lineTo(0f, h.toFloat())
        fill.close()

        val paintFill = Paint().apply {
            style = Paint.Style.FILL
            shader = android.graphics.LinearGradient(
                0f, padTop, 0f, h.toFloat(),
                Color.parseColor("#334CC2FF"),
                Color.parseColor("#004CC2FF"),
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(fill, paintFill)

        val paintLine = Paint().apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#4CC2FF")
            strokeWidth = (h * 0.06f).coerceAtLeast(2f)
            isAntiAlias = true
        }
        canvas.drawPath(path, paintLine)

        val last = pts.last()
        val paintDot = Paint().apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#FFFFFF")
            isAntiAlias = true
        }
        canvas.drawCircle(last.first, last.second, (h * 0.08f).coerceAtLeast(2f), paintDot)
        return bmp
    }

    fun bars(days: List<Pair<String, Double>>, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (days.isEmpty()) return bmp
        val max = (days.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1e-9)
        val n = days.size
        val gap = w * 0.04f
        val barW = (w - gap * (n + 1)) / n
        val padBottom = h * 0.18f
        val usableH = h - padBottom
        val paint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = Color.parseColor("#999999")
            textSize = (h * 0.16f).coerceAtLeast(8f)
            textAlign = Paint.Align.CENTER
        }
        days.forEachIndexed { i, (label, v) ->
            val x = gap + i * (barW + gap)
            val bh = (v / max * usableH).toFloat().coerceAtLeast(2f)
            val y = h - padBottom - bh
            paint.color = if (i == n - 1) Color.parseColor("#4CC2FF") else Color.parseColor("#2E7DA8")
            canvas.drawRoundRect(RectF(x, y, x + barW, h - padBottom), 4f, 4f, paint)
            // rótulo: 2 últimos dígitos do dia
            val day = label.takeLast(2)
            canvas.drawText(day, x + barW / 2, h - 2f, textPaint)
        }
        return bmp
    }
}
