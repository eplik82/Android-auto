package com.eplik82.parkedvideo

import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer

/**
 * Võtab Android Autolt joonistuspinna ja kuvab sellel [VideoPresentation]-i
 * virtuaalse ekraani kaudu. Hoiab meeles, mida peab näitama, et pinna
 * taasloomisel (nt ekraani suuruse muutus) sisu taastuks.
 */
class VideoSurfaceRenderer(private val carContext: CarContext) : SurfaceCallback {

    private sealed interface Content {
        data class Message(val text: String) : Content
        data class Video(val videoId: String) : Content
    }

    private var content: Content = Content.Message("")
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: VideoPresentation? = null
    private var visibleArea: Rect? = null

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val surface = surfaceContainer.surface ?: return
        release()
        val displayManager = carContext.getSystemService(DisplayManager::class.java)
        val display = displayManager.createVirtualDisplay(
            "ParkedVideo",
            surfaceContainer.width,
            surfaceContainer.height,
            surfaceContainer.dpi,
            surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
        )
        virtualDisplay = display
        presentation = VideoPresentation(
            carContext,
            display.display,
            surfaceContainer.width,
            surfaceContainer.height,
        ).also {
            it.show()
            visibleArea?.let(it::setVisibleArea)
        }
        render()
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) = release()

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = visibleArea
        presentation?.setVisibleArea(visibleArea)
    }

    override fun onClick(x: Float, y: Float) {
        if (content is Content.Video) presentation?.dispatchTap(x, y)
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

    fun pause() = presentation?.pause()

    fun resume() = presentation?.resume()

    fun release() {
        presentation?.dismiss()
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
    }

    private fun render() {
        val target = presentation ?: return
        when (val c = content) {
            is Content.Message -> target.showMessage(c.text)
            is Content.Video -> target.showVideo(c.videoId)
        }
    }
}
