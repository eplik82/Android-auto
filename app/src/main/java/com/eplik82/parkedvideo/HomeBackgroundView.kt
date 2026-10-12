package com.eplik82.parkedvideo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import java.io.File
import kotlin.math.min

/**
 * Avaekraani taust Ioniq 5 stiilis: auto külgvaade (nurgeline siluett,
 * Z-joon, „parametric pixel“ tuled) öises/päevases toonis. Hyundai fotosid
 * repos pole (avalik repo); oma pildi saab panna faili
 * `Android/data/com.eplik82.parkedvideo/files/background.jpg` (või .png).
 *
 * Vasak pool jääb teksti jaoks vabaks, auto on paremal.
 */
class HomeBackgroundView(context: Context, private val dark: Boolean) : View(context) {

    private val photo: Bitmap? = loadPhoto(context)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val skyTop = if (dark) 0xFF070C10.toInt() else 0xFFF1F4F6.toInt()
    private val skyBottom = if (dark) 0xFF13232B.toInt() else 0xFFD3DEE3.toInt()
    private val glow = if (dark) 0x5532B6B0 else 0x3332B6B0
    private val pixelColor = if (dark) 0x2232B6B0 else 0x2230707A
    private val floor = if (dark) 0xFF05080A.toInt() else 0xFFBFCBD0.toInt()
    private val bodyLight = if (dark) 0xFF8A949C.toInt() else 0xFF7D8890.toInt()
    private val bodyDark = if (dark) 0xFF2E353B.toInt() else 0xFF3E474E.toInt()
    private val glass = if (dark) 0xFF0B1115.toInt() else 0xFF1C252B.toInt()
    private val tyre = 0xFF0A0C0E.toInt()
    private val rim = if (dark) 0xFFB5BDC3.toInt() else 0xFFA4ADB4.toInt()
    private val lamp = 0xFFF2F7F7.toInt()
    private val tailLamp = 0xFFE5322D.toInt()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val image = photo
        if (image != null) {
            drawPhoto(canvas, image, w, h)
        } else {
            drawScene(canvas, w, h)
        }
        // Teksti alla (vasakule) tumedam/heledam üleminek, et kiri oleks loetav.
        fill.shader = LinearGradient(
            0f, 0f, w * 0.6f, 0f,
            if (dark) 0xCC000000.toInt() else 0xCCF5F5F5.toInt(), Color.TRANSPARENT, Shader.TileMode.CLAMP,
        )
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

    private fun drawScene(c: Canvas, w: Float, h: Float) {
        val horizon = h * 0.80f

        fill.shader = LinearGradient(0f, 0f, 0f, horizon, skyTop, skyBottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, horizon, fill)
        fill.shader = null
        fill.color = floor
        c.drawRect(0f, horizon, w, h, fill)

        // Parametric pixel muster paremas ülanurgas.
        val cell = h / 18f
        fill.color = pixelColor
        var y = cell * 0.6f
        var row = 0
        while (y < horizon * 0.55f) {
            var x = w - cell * 0.6f - cell
            var col = 0
            while (x > w * 0.45f) {
                // Hõreneb vasakule ja alla.
                val density = (x - w * 0.45f) / (w * 0.55f) * (1f - y / (horizon * 0.55f))
                if ((col * 7 + row * 13) % 10 < density * 14) {
                    c.drawRect(x, y, x + cell * 0.72f, y + cell * 0.72f, fill)
                }
                x -= cell
                col++
            }
            y += cell
            row++
        }

        // Auto paremal, rattad horisondil.
        val carW = min(w * 0.56f, (horizon - h * 0.08f) * 2.9f)
        val unit = carW / 100f
        val originX = w - carW - w * 0.04f
        val originY = horizon

        // Helk auto taga.
        fill.shader = RadialGradient(
            originX + carW * 0.55f, originY - unit * 14f, carW * 0.6f,
            glow, Color.TRANSPARENT, Shader.TileMode.CLAMP,
        )
        c.drawRect(originX - carW * 0.2f, 0f, w, h, fill)
        fill.shader = null

        drawCar(c, originX, originY, unit)
    }

    /** Ioniq 5 külgvaade; x 0 = tagumine kaitseraud, 100 = eesmine; y 0 = maapind, üles positiivne. */
    private fun drawCar(c: Canvas, ox: Float, oy: Float, u: Float) {
        fun px(x: Float) = ox + x * u
        fun py(y: Float) = oy - y * u

        // Vari.
        fill.color = 0x66000000
        c.drawOval(RectF(px(-2f), py(1.2f), px(102f), py(-1.6f)), fill)

        val rearWheel = 16.5f
        val frontWheel = 81f
        val wheelR = 7.4f
        val archR = 8.6f

        val body = Path().apply {
            moveTo(px(1.5f), py(8.5f))
            lineTo(px(0.2f), py(12f))
            lineTo(px(0.6f), py(24f))
            lineTo(px(2.4f), py(30.6f))
            lineTo(px(8f), py(31.6f))
            lineTo(px(62f), py(31.8f))
            lineTo(px(78.5f), py(23.4f))
            lineTo(px(97f), py(20.4f))
            lineTo(px(99.8f), py(16.5f))
            lineTo(px(99.6f), py(11f))
            lineTo(px(97.5f), py(8.2f))
            lineTo(px(frontWheel + archR + 0.4f), py(8.2f))
            arcTo(RectF(px(frontWheel - archR), py(7.4f + archR), px(frontWheel + archR), py(7.4f - archR)), 0f, -180f)
            lineTo(px(rearWheel + archR), py(8f))
            arcTo(RectF(px(rearWheel - archR), py(7.4f + archR), px(rearWheel + archR), py(7.4f - archR)), 0f, -180f)
            close()
        }
        fill.shader = LinearGradient(0f, py(31.8f), 0f, py(8f), bodyLight, bodyDark, Shader.TileMode.CLAMP)
        c.drawPath(body, fill)
        fill.shader = null

        // Klaasid (must „greenhouse“).
        val glassPath = Path().apply {
            moveTo(px(3.2f), py(25.5f))
            lineTo(px(4.2f), py(29.2f))
            lineTo(px(9f), py(30.2f))
            lineTo(px(61f), py(30.4f))
            lineTo(px(75.5f), py(23.6f))
            lineTo(px(5f), py(23.2f))
            close()
        }
        fill.color = glass
        c.drawPath(glassPath, fill)
        // B- ja C-sammas kerevärvi.
        fill.color = bodyDark
        c.drawPath(Path().apply {
            moveTo(px(45f), py(30.4f)); lineTo(px(47f), py(30.4f)); lineTo(px(47.6f), py(23.4f)); lineTo(px(45.4f), py(23.4f)); close()
        }, fill)
        c.drawPath(Path().apply {
            moveTo(px(20f), py(30.3f)); lineTo(px(26f), py(30.3f)); lineTo(px(28.5f), py(23.3f)); lineTo(px(21f), py(23.3f)); close()
        }, fill)

        // Õlajoon ja Z-kujuline diagonaal uksel.
        stroke.color = 0x55FFFFFF
        stroke.strokeWidth = u * 0.28f
        c.drawLine(px(1f), py(22.2f), px(78f), py(22.4f), stroke)
        stroke.color = 0x88000000.toInt()
        stroke.strokeWidth = u * 0.32f
        c.drawLine(px(27f), py(10.5f), px(70f), py(19.6f), stroke)
        stroke.color = 0x33FFFFFF
        c.drawLine(px(27.2f), py(10.9f), px(70.2f), py(20f), stroke)
        // Uksejooned ja ukselingid (peidetud käepidemed).
        stroke.color = 0x66000000
        stroke.strokeWidth = u * 0.18f
        c.drawLine(px(46.2f), py(23f), px(46.6f), py(9f), stroke)
        c.drawLine(px(72f), py(23.2f), px(71.2f), py(10f), stroke)
        fill.color = 0x44000000
        c.drawRoundRect(RectF(px(36f), py(20.6f), px(41f), py(19.9f)), u * 0.3f, u * 0.3f, fill)
        c.drawRoundRect(RectF(px(59f), py(20.8f), px(64f), py(20.1f)), u * 0.3f, u * 0.3f, fill)

        // Parametric pixel tuled: taga punane ruudustik, ees valge riba.
        pixelLamp(c, px(0.5f), py(27.2f), u, rows = 4, cols = 2, color = tailLamp)
        pixelLamp(c, px(96.4f), py(20f), u, rows = 1, cols = 4, color = lamp)
        // Esitule helk.
        fill.shader = RadialGradient(px(100f), py(19.4f), u * 7f, 0x66FFFFFF, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(px(100f), py(19.4f), u * 7f, fill)
        fill.shader = null

        wheel(c, px(rearWheel), py(wheelR), wheelR * u)
        wheel(c, px(frontWheel), py(wheelR), wheelR * u)
    }

    private fun pixelLamp(c: Canvas, left: Float, top: Float, u: Float, rows: Int, cols: Int, color: Int) {
        val size = u * 0.7f
        val gap = u * 0.25f
        fill.color = color
        for (r in 0 until rows) {
            for (col in 0 until cols) {
                val x = left + col * (size + gap)
                val y = top + r * (size + gap)
                c.drawRect(x, y, x + size, y + size, fill)
            }
        }
    }

    /** Aero-velg „pikslitega“ nagu Ioniq 5 20" veljel. */
    private fun wheel(c: Canvas, cx: Float, cy: Float, r: Float) {
        fill.color = tyre
        c.drawCircle(cx, cy, r, fill)
        val rimR = r * 0.72f
        fill.shader = RadialGradient(cx - rimR * 0.3f, cy - rimR * 0.3f, rimR * 1.3f, rim, bodyDark, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, rimR, fill)
        fill.shader = null
        // Pikslimuster velje sees.
        fill.color = tyre
        val cell = rimR / 4.2f
        var y = cy - rimR
        while (y < cy + rimR) {
            var x = cx - rimR
            while (x < cx + rimR) {
                val dx = x + cell / 2 - cx
                val dy = y + cell / 2 - cy
                val d = kotlin.math.sqrt(dx * dx + dy * dy)
                if (d < rimR * 0.85f && d > rimR * 0.3f && ((x - cx) / cell).toInt() % 2 == ((y - cy) / cell).toInt() % 2) {
                    c.drawRect(x + cell * 0.18f, y + cell * 0.18f, x + cell * 0.82f, y + cell * 0.82f, fill)
                }
                x += cell
            }
            y += cell
        }
        fill.color = bodyDark
        c.drawCircle(cx, cy, rimR * 0.18f, fill)
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
