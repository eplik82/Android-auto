package com.eplik82.parkedvideo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import java.io.File

/**
 * Avaekraani taust Ioniq 5 „parametric pixel“ stiilis: ruudud paremal,
 * hõrenevad vasakule, üksikud helendavad nagu tuled. Oma pildi saab panna
 * faili `Android/data/com.eplik82.parkedvideo/files/background.jpg` (või .png);
 * Hyundai fotosid repos pole (avalik repo).
 *
 * Vasak pool jääb teksti jaoks tumedam (päeval heledam).
 */
class HomeBackgroundView(context: Context, private val dark: Boolean) : View(context) {

    private val photo: Bitmap? = loadPhoto(context)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    private val top = if (dark) Color.rgb(7, 12, 16) else Color.rgb(241, 244, 246)
    private val bottom = if (dark) Color.rgb(19, 35, 43) else Color.rgb(211, 222, 227)
    private val pixel = if (dark) Color.rgb(50, 182, 176) else Color.rgb(48, 112, 122)
    private val maxAlpha = if (dark) 70 else 60
    private val accentAlpha = if (dark) 200 else 150
    private val scrim = if (dark) Color.argb(204, 0, 0, 0) else Color.argb(204, 245, 245, 245)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        photo?.let { drawPhoto(canvas, it, w, h) } ?: drawPixels(canvas, w, h)
        // Teksti alla üleminek, et kiri oleks loetav.
        fill.shader = LinearGradient(0f, 0f, w * 0.6f, 0f, scrim, scrim and 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w * 0.6f, h, fill)
        fill.shader = null
    }

    private fun drawPhoto(c: Canvas, image: Bitmap, w: Float, h: Float) {
        // Täidab ala, ülejääk lõigatakse ära (center crop).
        val scale = maxOf(w / image.width, h / image.height)
        val sw = w / scale
        val sh = h / scale
        val left = (image.width - sw) / 2f
        val top = (image.height - sh) / 2f
        val src = Rect(left.toInt(), top.toInt(), (left + sw).toInt(), (top + sh).toInt())
        c.drawBitmap(image, src, RectF(0f, 0f, w, h), fill)
    }

    private fun drawPixels(c: Canvas, w: Float, h: Float) {
        fill.shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null

        val cell = h / 14f
        val size = cell * 0.78f
        val inset = (cell - size) / 2f
        val cols = kotlin.math.ceil(w / cell).toInt()
        val rows = kotlin.math.ceil(h / cell).toInt()
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = w - (col + 1) * cell + inset
                val y = row * cell + inset
                // Tihedus 0 ekraani 35% juures, 1 paremas servas (smoothstep).
                val fx = ((x / w - 0.35f) / 0.65f).coerceAtMost(1f)
                if (fx <= 0f) continue
                val density = fx * fx * (3 - 2 * fx)
                // Püsiv „juhuslikkus“: sama muster iga kord.
                val hash = kotlin.math.abs(((col.toLong() * 73856093L) xor (row.toLong() * 19349663L)) % 1000L) / 1000f
                if (hash >= density * 0.85f) continue
                val alpha = if (hash < 0.06f * density) accentAlpha else (maxAlpha * (0.35f + 0.65f * density)).toInt()
                fill.color = (pixel and 0x00FFFFFF) or (alpha shl 24)
                c.drawRect(x, y, x + size, y + size, fill)
            }
        }
    }

    private companion object {
        fun loadPhoto(context: Context): Bitmap? {
            val dir = context.getExternalFilesDir(null) ?: return null
            val file = listOf("background.jpg", "background.png").map { File(dir, it) }.firstOrNull { it.isFile }
                ?: return null
            return runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
        }
    }
}
