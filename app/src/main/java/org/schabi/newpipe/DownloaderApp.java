package org.schabi.newpipe;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.error.ReCaptchaActivity;
import com.nidoham.opentube.R;

import org.ocpsoft.prettytime.PrettyTime;
import org.schabi.newpipe.util.Localization;

public class DownloaderApp {
    private final Context appContext;

    public DownloaderApp(Context context){
        this.appContext = context.getApplicationContext();

        // Initialize NewPipe with downloader and current localization and content country
        NewPipe.init(getDownloader(),
            Localization.getPreferredLocalization(appContext),
            Localization.getPreferredContentCountry(appContext));

        // Initialize PrettyTime instance for relative time formatting
        Localization.initPrettyTime(Localization.resolvePrettyTime());
    }

    protected Downloader getDownloader() {
        final DownloaderImpl downloader = DownloaderImpl.init(null);
        setCookiesToDownloader(downloader, appContext);
        return downloader;
    }

    protected void setCookiesToDownloader(final DownloaderImpl downloader, Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String key = context.getString(R.string.recaptcha_cookies_key);
        downloader.setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, prefs.getString(key, null));
        downloader.updateYoutubeRestrictedModeCookies(context);
    }
}