package com.eplik82.parkedvideo

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/** Auto ekraani pinnale joonistatav sisu: teade, YouTube'i mängija või veebileht. */
class SurfacePresentation(
    context: Context,
    display: Display,
    private val dark: Boolean,
    private val surfaceWidth: Int,
    private val surfaceHeight: Int,
    /** Kutsutakse, kui puudutus viis fookuse veebilehe tekstiväljale. */
    private val onEditableTapped: () -> Unit,
) : Presentation(
    context,
    display,
    if (dark) android.R.style.Theme_Material_NoActionBar_Fullscreen
    else android.R.style.Theme_Material_Light_NoActionBar_Fullscreen,
) {
    private val backgroundColor = if (dark) Color.BLACK else Color.rgb(0xF5, 0xF5, 0xF5)
    private val textColor = if (dark) Color.WHITE else Color.rgb(0x20, 0x21, 0x24)

    private lateinit var root: FrameLayout
    private lateinit var message: TextView
    private var webView: WebView? = null

    /** YouTube'i jms täisekraanivaade (WebChromeClient.onShowCustomView). */
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Viimase auto ekraani puudutuse aeg; leht saab klaviatuuri avada ainult vahetult pärast seda. */
    private var lastTapAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setBackgroundDrawable(ColorDrawable(backgroundColor))
        root = FrameLayout(context).apply { setBackgroundColor(backgroundColor) }
        message = TextView(context).apply {
            setTextColor(textColor)
            textSize = 26f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        root.addView(message, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)
    }

    /** Jätab vaba ruumi Android Auto nuppudele ja olekuribale (v.a video, mis täidab kogu ekraani). */
    fun setVisibleArea(area: Rect) {
        visibleArea = area
        applyPadding()
    }

    private var visibleArea: Rect? = null
    private var fullBleed = false

    private fun applyPadding() {
        val area = visibleArea
        if (fullBleed || area == null) {
            root.setPadding(0, 0, 0, 0)
            return
        }
        root.setPadding(
            area.left,
            area.top,
            (surfaceWidth - area.right).coerceAtLeast(0),
            (surfaceHeight - area.bottom).coerceAtLeast(0),
        )
    }

    private fun setFullBleed(value: Boolean) {
        fullBleed = value
        applyPadding()
    }

    fun showMessage(text: String) {
        setFullBleed(false)
        destroyWebView()
        message.text = text
        message.visibility = View.VISIBLE
    }

    fun showVideo(videoId: String) {
        val origin = "https://${context.packageName}"
        // Video kogu ekraanile; Android Auto nupud hõljuvad peal ja peituvad ise.
        setFullBleed(true)
        newWebView().loadDataWithBaseURL(origin, playerHtml(videoId, origin), "text/html", "utf-8", null)
    }

    fun showWeb(url: String) {
        setFullBleed(false)
        newWebView().loadUrl(url)
    }

    fun goBack() {
        if (fullscreenView != null) {
            exitFullscreen()
            return
        }
        webView?.takeIf { it.canGoBack() }?.goBack()
    }

    fun scrollBy(distanceX: Float, distanceY: Float) {
        webView?.scrollBy(distanceX.toInt(), distanceY.toInt())
    }

    fun pause() = command("pauseVideo")

    fun resume() = command("playVideo")

    /** Edastab auto puuteekraani puudutuse veebivaatele (lingid, YouTube'i nupud). */
    fun dispatchTap(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            root.dispatchTouchEvent(event)
            event.recycle()
        }
        // Kui puudutus viib fookuse tekstiväljale (ka veidi hiljem, nt Google'i
        // otsingukast), on vaja auto klaviatuuri.
        lastTapAt = SystemClock.uptimeMillis()
        webView?.evaluateJavascript(WATCH_FOCUS_JS, null)
    }

    /** Lehe JavaScripti teade: puudutus viis fookuse tekstiväljale. */
    private inner class InputBridge {
        @JavascriptInterface
        fun onEditableFocused() {
            handler.post {
                if (SystemClock.uptimeMillis() - lastTapAt <= FOCUS_WATCH_MILLIS) {
                    lastTapAt = 0L
                    onEditableTapped()
                }
            }
        }
    }

    /**
     * Kirjutab teksti aktiivsele tekstiväljale ja saadab vormi (nt otsingu).
     * Kui ükski väli pole aktiivne, avab teksti aadressina või Google'i otsinguna.
     */
    fun enterText(text: String) {
        val view = webView ?: return
        view.evaluateJavascript("($INSERT_TEXT_JS)(${JSONObject.quote(text)})") { inserted ->
            if (inserted != "true" && webView === view) view.loadUrl(SettingsInput.addressOrSearch(text))
        }
    }

    override fun onStop() {
        handler.removeCallbacksAndMessages(null)
        destroyWebView()
        super.onStop()
    }

    private fun command(func: String) {
        webView?.evaluateJavascript("cmd('$func')", null)
    }

    /** Iga uus sisu saab puhta veebivaate, et eelmise sisu ajalugu ja heli ei jääks alles. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(): WebView {
        destroyWebView()
        message.visibility = View.GONE
        val view = WebView(context)
        view.setBackgroundColor(backgroundColor)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.mediaPlaybackRequiresUserGesture = false
        applyDarkMode(view)
        view.addJavascriptInterface(InputBridge(), BRIDGE)
        view.webViewClient = object : WebViewClient() {
            // Ainult veebilehed: intent:// jms ei tohi telefonis teisi rakendusi avada.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.scheme !in setOf("http", "https")
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                fullscreenView = view
                fullscreenCallback = callback
                root.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }

            override fun onHideCustomView() = exitFullscreen()
        }
        root.addView(view, 0, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        webView = view
        return view
    }

    private fun applyDarkMode(view: WebView) {
        // Android 13+: tume teema lubab lehtedel prefers-color-scheme: dark ja vajadusel automaatse tumenduse.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(view.settings, dark)
        }
        // Vanemad WebView versioonid.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            @Suppress("DEPRECATION")
            WebSettingsCompat.setForceDark(
                view.settings,
                if (dark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF,
            )
        }
    }

    private fun exitFullscreen() {
        val view = fullscreenView ?: return
        fullscreenView = null
        root.removeView(view)
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
    }

    private fun destroyWebView() {
        exitFullscreen()
        val view = webView ?: return
        webView = null
        view.loadUrl("about:blank")
        root.removeView(view)
        view.destroy()
    }

    private companion object {
        const val BRIDGE = "ParkedVideoInput"
        const val FOCUS_WATCH_MILLIS = 1_500L

        /** Kontrollib kohe ja veel 1,5 s jooksul, kas fookus on tekstiväljal. */
        val WATCH_FOCUS_JS = """(function() {
            function editable(e) {
                if (!e) return false;
                if (e.isContentEditable || e.tagName == 'TEXTAREA') return true;
                return e.tagName == 'INPUT' &&
                    /^(text|search|email|url|tel|number|password|)$/i.test(e.getAttribute('type') || '');
            }
            function report() { $BRIDGE.onEditableFocused(); }
            if (window.__pvFocus) document.removeEventListener('focusin', window.__pvFocus, true);
            window.__pvFocus = function(ev) {
                if (editable(ev.target)) {
                    document.removeEventListener('focusin', window.__pvFocus, true);
                    report();
                }
            };
            document.addEventListener('focusin', window.__pvFocus, true);
            setTimeout(function() { document.removeEventListener('focusin', window.__pvFocus, true); }, $FOCUS_WATCH_MILLIS);
        })()"""

        /** Seab välja väärtuse nii, et ka Reacti jms raamistike lehed selle märkaksid, ja saadab vormi. */
        const val INSERT_TEXT_JS = """function(t) {
            var e = document.activeElement;
            if (!e || !(e.isContentEditable || e.tagName == 'INPUT' || e.tagName == 'TEXTAREA')) return false;
            if (e.isContentEditable) {
                document.execCommand('insertText', false, t);
            } else {
                var setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e), 'value').set;
                setter.call(e, t);
            }
            e.dispatchEvent(new Event('input', {bubbles: true}));
            e.dispatchEvent(new Event('change', {bubbles: true}));
            if (e.tagName == 'TEXTAREA' || e.isContentEditable) return true;
            if (e.form) {
                if (e.form.requestSubmit) e.form.requestSubmit(); else e.form.submit();
            } else {
                ['keydown', 'keypress', 'keyup'].forEach(function(type) {
                    e.dispatchEvent(new KeyboardEvent(type, {key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true}));
                });
            }
            return true;
        }"""
    }

    private fun playerHtml(videoId: String, origin: String) = """
        <!DOCTYPE html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>html,body{margin:0;height:100%;background:#000}iframe{border:0;width:100%;height:100%}</style>
        </head><body>
        <iframe id="p"
          src="https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&enablejsapi=1&origin=$origin"
          allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen
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
