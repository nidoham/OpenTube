package com.nidoham.opentube.agent

import android.content.Context
import android.content.res.Configuration
import android.app.UiModeManager

object AgentUtil {

    const val ANDROID_DEFAULT: String = "Mozilla/5.0 (Linux; Android 10)"

    // Mobile Chrome (generic phone UA, no wv token)
    const val ANDROID_CHROME: String =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro Build/TQ3A.230805.001) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/117.0.0.0 Mobile Safari/537.36"

    // WebView-specific UA (use only when you are actually inside a WebView)
    const val ANDROID_WEBVIEW_CHROME: String =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro Build/TQ3A.230805.001; wv) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/117.0.0.0 Mobile Safari/537.36"

    const val DESKTOP_CHROME: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/117.0.0.0 Safari/537.36"

    const val DESKTOP_FIREFOX: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) " +
        "Gecko/20100101 Firefox/140.0"

    const val IOS_SAFARI: String =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) " +
        "AppleWebKit/605.1.15 (KHTML, like Gecko) " +
        "Version/16.0 Mobile/15E148 Safari/604.1"

    // সরাসরি boolean দেয়ার API (আপনি আগে যা দিয়েছিলেন)
    fun getDefaultUA(isTv: Boolean): String {
        return if (isTv) DESKTOP_CHROME else ANDROID_CHROME
    }

    // Context-ভিত্তিক স্বয়ংক্রিয় শনাক্তকরণ (recommended)
    fun getDefaultUA(context: Context): String {
        // প্রথমে UiModeManager দিয়ে TV চেক করি
        val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTv = uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        return if (isTv) DESKTOP_CHROME else ANDROID_CHROME
    }
}