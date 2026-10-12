package com.eplik82.parkedvideo

import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer

/**
 * Võtab Android Autolt joonistuspinna ja kuvab sellel [SurfacePresentation]-i
 * virtuaalse ekraani kaudu. Hoiab meeles, mida peab näitama, et pinna või
 * teema vahetusel sisu taastuks.
 */
class SurfaceRenderer(
    private val carContext: CarContext,
    private val onEditableTapped: () -> Unit,
) : SurfaceCallback {

    private sealed interface Content {
        data class Message(val text: String) : Content
        data class Video(val videoId: String) : Content
        data class Web(val url: String) : Content
    }

    private var content: Content = Content.Message("")
    private var dark = true
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: SurfacePresentation? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var visibleArea: Rect? = null

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val surface = surfaceContainer.surface ?: return
        val display = virtualDisplay
        // Sama suurusega pind tagasi (nt klaviatuuriekraanilt): jätka sama sisuga,
        // et veebileht ja aktiivne tekstiväli säiliksid.
        if (display != null && surfaceContainer.width == surfaceWidth && surfaceContainer.height == surfaceHeight) {
            display.surface = surface
            return
        }
        release()
        surfaceWidth = surfaceContainer.width
        surfaceHeight = surfaceContainer.height
        virtualDisplay = carContext.getSystemService(DisplayManager::class.java).createVirtualDisplay(
            "ParkedVideo",
            surfaceWidth,
            surfaceHeight,
            surfaceContainer.dpi,
            surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
        )
        createPresentation()
    }

    /** Pind võib kaduda ajutiselt (teine mall ees) – sisu jääb alles, kuni ekraan suletakse. */
    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        virtualDisplay?.surface = null
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = visibleArea
        presentation?.setVisibleArea(visibleArea)
    }

    override fun onClick(x: Float, y: Float) {
        if (content !is Content.Message) presentation?.dispatchTap(x, y)
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        if (content is Content.Web) presentation?.scrollBy(distanceX, distanceY)
    }

    fun setDarkMode(dark: Boolean) {
        if (this.dark == dark) return
        this.dark = dark
        createPresentation()
    }

    fun showMessage(text: String) {
        if (content == Content.Message(text)) return
        content = Content.Message(text)
        render()
    }

    fun playVideo(videoId: String) {
        content = Content.Video(videoId)
        render()
    }

    fun openWeb(url: String) {
        content = Content.Web(url)
        render()
    }

    fun goBack() = presentation?.goBack()

    fun enterText(text: String) = presentation?.enterText(text)

    fun pause() = presentation?.pause()

    fun resume() = presentation?.resume()

    fun release() {
        presentation?.dismiss()
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
    }

    private fun createPresentation() {
        presentation?.dismiss()
        presentation = null
        val display = virtualDisplay?.display ?: return
        presentation = SurfacePresentation(carContext, display, dark, surfaceWidth, surfaceHeight, onEditableTapped).also {
            it.show()
            visibleArea?.let(it::setVisibleArea)
        }
        render()
    }

    private fun render() {
        val target = presentation ?: return
        when (val c = content) {
            is Content.Message -> target.showMessage(c.text)
            is Content.Video -> target.showVideo(c.videoId)
            is Content.Web -> target.showWeb(c.url)
        }
    }
}
