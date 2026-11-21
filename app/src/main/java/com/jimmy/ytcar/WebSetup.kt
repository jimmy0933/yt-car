package com.jimmy.ytcar

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object WebSetup {
    /**
     * Google 會擋「內嵌 WebView」登入，所以把 User-Agent 中的 "; wv" 和 "Version/x.x" 拿掉，
     * 讓它看起來像一般的手機版 Chrome。手機端和車機端使用同一個 UA 和同一組 Cookie。
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun apply(web: WebView) {
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.userAgentString = WebSettings.getDefaultUserAgent(web.context)
            .replace("; wv", "")
            .replace(Regex("Version/\\d+(\\.\\d+)* "), "")
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
    }
}
