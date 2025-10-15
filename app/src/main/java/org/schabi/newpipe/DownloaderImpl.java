package org.schabi.newpipe;

import android.content.Context;
import android.webkit.WebSettings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.error.ReCaptchaActivity;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.util.InfoCache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import com.nidoham.opentube.R;

/**
 * DownloaderImpl
 *
 * Notes:
 * - USER_AGENT is a public static field so other classes (e.g., ReCaptchaActivity) can read it.
 * - Call init(context, builder) from Application.onCreate() passing application Context to get a platform-correct UA.
 * - If init is not called with a Context, a conservative fallback UA is used.
 */
public final class DownloaderImpl extends Downloader {
    // Public so other classes can reference it like ReCaptchaActivity does.
    public static volatile String USER_AGENT = "Mozilla/5.0 (Android)";

    public static final String YOUTUBE_RESTRICTED_MODE_COOKIE_KEY = "youtube_restricted_mode_key";
    public static final String YOUTUBE_RESTRICTED_MODE_COOKIE = "PREF=f2=8000000";
    public static final String YOUTUBE_DOMAIN = "youtube.com";

    private static volatile DownloaderImpl instance;

    private final ConcurrentHashMap<String, String> mCookies;
    private final OkHttpClient client;

    /**
     * Initialize the singleton. Call once at application startup and prefer passing application Context.
     *
     * @param context optional application Context; pass getApplicationContext() when available
     * @param builder optional OkHttpClient.Builder; if null, a default builder is used
     * @return initialized singleton instance
     */
    public static DownloaderImpl init(@Nullable final Context context,
                                      @Nullable final OkHttpClient.Builder builder) {
        if (instance == null) {
            synchronized (DownloaderImpl.class) {
                if (instance == null) {
                    instance = new DownloaderImpl(builder != null ? builder : new OkHttpClient.Builder());
                }
            }
        }

        // Initialize USER_AGENT using the provided context if available
        if (context != null) {
            try {
                // Use WebSettings.getDefaultUserAgent for a reliable Android UA string
                final String ua = WebSettings.getDefaultUserAgent(context.getApplicationContext());
                if (ua != null && !ua.isEmpty()) {
                    USER_AGENT = ua;
                }
            } catch (Throwable ignored) {
                // keep existing fallback USER_AGENT
            }
        }

        return instance;
    }

    /**
     * Backward-compatible init(builder)
     */
    public static DownloaderImpl init(@Nullable final OkHttpClient.Builder builder) {
        return init(null, builder);
    }

    /**
     * Return the initialized instance. If not initialized, a default instance will be created.
     *
     * @return singleton instance
     */
    public static DownloaderImpl getInstance() {
        if (instance == null) {
            init(null, new OkHttpClient.Builder());
        }
        return instance;
    }

    /**
     * Private constructor initializes OkHttp client and cookie store.
     *
     * @param builder OkHttpClient.Builder (non-null)
     */
    private DownloaderImpl(final OkHttpClient.Builder builder) {
        // Build a performant OkHttpClient
        this.client = builder
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        this.mCookies = new ConcurrentHashMap<>();
    }

    public String getCookies(final String url) {
        final List<String> cookieParts = new ArrayList<>(2);

        if (url != null && url.contains(YOUTUBE_DOMAIN)) {
            final String yt = getCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY);
            if (yt != null && !yt.isEmpty()) {
                cookieParts.addAll(splitCookieString(yt));
            }
        }

        final String recaptcha = getCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY);
        if (recaptcha != null && !recaptcha.isEmpty()) {
            cookieParts.addAll(splitCookieString(recaptcha));
        }

        // Deduplicate while preserving order
        final List<String> unique = new ArrayList<>();
        for (String c : cookieParts) {
            if (!unique.contains(c)) {
                unique.add(c);
            }
        }

        if (unique.isEmpty()) {
            return "";
        }

        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < unique.size(); i++) {
            if (i > 0) sb.append("; ");
            sb.append(unique.get(i));
        }
        return sb.toString();
    }

    private List<String> splitCookieString(final String cookieHeader) {
        if (cookieHeader == null) return Collections.emptyList();
        final String[] parts = cookieHeader.split("; *");
        final List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) {
            final String trimmed = p.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    public String getCookie(final String key) {
        return mCookies.get(key);
    }

    public void setCookie(final String key, final String cookie) {
        if (key == null) return;
        if (cookie == null) {
            mCookies.remove(key);
        } else {
            mCookies.put(key, cookie);
        }
    }

    public void removeCookie(final String key) {
        if (key != null) mCookies.remove(key);
    }

    public void updateYoutubeRestrictedModeCookies(final Context context) {
        if (context == null) return;
        final String restrictedModeEnabledKey = context.getString(R.string.youtube_restricted_mode_enabled);
        final boolean restrictedModeEnabled = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(restrictedModeEnabledKey, false);
        updateYoutubeRestrictedModeCookies(restrictedModeEnabled);
    }

    public void updateYoutubeRestrictedModeCookies(final boolean youtubeRestrictedModeEnabled) {
        if (youtubeRestrictedModeEnabled) {
            setCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY, YOUTUBE_RESTRICTED_MODE_COOKIE);
        } else {
            removeCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY);
        }
        InfoCache.getInstance().clearCache();
    }

    /**
     * Get the size of the content that the url is pointing by firing a HEAD request.
     *
     * @param url an url pointing to the content
     * @return the size of the content, in bytes
     */
    public long getContentLength(final String url) throws IOException {
        try {
            final Response r = head(url);
            final String len = r.getHeader("Content-Length");
            if (len == null || len.isEmpty()) {
                throw new IOException("Content-Length header missing");
            }
            return Long.parseLong(len);
        } catch (final NumberFormatException e) {
            throw new IOException("Invalid content length", e);
        } catch (final ReCaptchaException e) {
            throw new IOException(e);
        }
    }

    @Override
    public Response execute(@NonNull final Request request) throws IOException, ReCaptchaException {
        final String httpMethod = request.httpMethod();
        final String url = request.url();
        final Map<String, List<String>> headers = request.headers();
        final byte[] dataToSend = request.dataToSend();

        RequestBody requestBody = null;
        if (dataToSend != null) {
            // Use octet-stream as a conservative default media type
            requestBody = RequestBody.create(MediaType.parse("application/octet-stream"), dataToSend);
        } else if ("POST".equalsIgnoreCase(httpMethod) || "PUT".equalsIgnoreCase(httpMethod)) {
            // OkHttp requires non-null body for some methods depending on version; use empty body if necessary
            requestBody = RequestBody.create(null, new byte[0]);
        }

        final okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder()
                .method(httpMethod, requestBody)
                .url(url)
                .header("User-Agent", USER_AGENT);

        final String cookies = getCookies(url);
        if (!cookies.isEmpty()) {
            requestBuilder.header("Cookie", cookies);
        }

        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                final String headerName = e.getKey();
                final List<String> valueList = e.getValue();
                if (headerName == null || valueList == null) continue;
                // removeHeader then add each value to mimic original behavior
                requestBuilder.removeHeader(headerName);
                for (String v : valueList) {
                    if (v != null) requestBuilder.addHeader(headerName, v);
                }
            }
        }

        try (okhttp3.Response okhttpResponse = client.newCall(requestBuilder.build()).execute()) {
            if (okhttpResponse.code() == 429) {
                throw new ReCaptchaException("reCaptcha Challenge requested", url);
            }

            String responseBodyToReturn = null;
            try (ResponseBody body = okhttpResponse.body()) {
                if (body != null) {
                    responseBodyToReturn = body.string();
                }
            }

            final String latestUrl = okhttpResponse.request().url().toString();
            return new Response(
                    okhttpResponse.code(),
                    okhttpResponse.message(),
                    okhttpResponse.headers().toMultimap(),
                    responseBodyToReturn,
                    latestUrl);
        }
    }
}