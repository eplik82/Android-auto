package com.eplik82.parkedvideo

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView

/** Auto ekraani pinnale joonistatav sisu: kas teade või YouTube'i mängija. */
class VideoPresentation(
    context: Context,
    display: Display,
    private val surfaceWidth: Int,
    private val surfaceHeight: Int,
) : Presentation(context, display) {

    private lateinit var root: FrameLayout
    private lateinit var message: TextView
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        root = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        message = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 26f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        root.addView(message, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)
    }

    /** Jätab vaba ruumi Android Auto nuppudele ja olekuribale. */
    fun setVisibleArea(area: Rect) {
        root.setPadding(
            area.left,
            area.top,
            (surfaceWidth - area.right).coerceAtLeast(0),
            (surfaceHeight - area.bottom).coerceAtLeast(0),
        )
    }

    fun showMessage(text: String) {
        destroyWebView()
        message.text = text
        message.visibility = View.VISIBLE
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun showVideo(videoId: String) {
        message.visibility = View.GONE
        val view = webView ?: WebView(context).also { view ->
            view.setBackgroundColor(Color.BLACK)
            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = true
            view.settings.mediaPlaybackRequiresUserGesture = false
            view.webViewClient = WebViewClient()
            view.webChromeClient = WebChromeClient()
            root.addView(view, 0, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            webView = view
        }
        val origin = "https://${context.packageName}"
        view.loadDataWithBaseURL(origin, playerHtml(videoId, origin), "text/html", "utf-8", null)
    }

    fun pause() = command("pauseVideo")

    fun resume() = command("playVideo")

    /** Edastab auto puuteekraani puudutuse mängijale (nt YouTube'i nupud). */
    fun dispatchTap(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            root.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    override fun onStop() {
        destroyWebView()
        super.onStop()
    }

    private fun command(func: String) {
        webView?.evaluateJavascript("cmd('$func')", null)
    }

    private fun destroyWebView() {
        val view = webView ?: return
        webView = null
        view.loadUrl("about:blank")
        root.removeView(view)
        view.destroy()
    }

    private fun playerHtml(videoId: String, origin: String) = """
        <!DOCTYPE html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>html,body{margin:0;height:100%;background:#000}iframe{border:0;width:100%;height:100%}</style>
        </head><body>
        <iframe id="p"
          src="https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&enablejsapi=1&origin=$origin"
          allow="autoplay; encrypted-media; picture-in-picture"
          referrerpolicy="strict-origin-when-cross-origin"></iframe>
        <script>
          function cmd(f) {
            document.getElementById('p').contentWindow.postMessage(
              JSON.stringify({event: 'command', func: f, args: []}), '*');
          }
        </script>
        </body></html>
    """.trimIndent()
}
