package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import com.nidoham.opentube.R;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.util.concurrent.TimeUnit;

public final class ServiceHelper {
    private static final StreamingService DEFAULT_FALLBACK_SERVICE = ServiceList.YouTube;
    private static final int YOUTUBE_SERVICE_ID = 0;

    private ServiceHelper() { }

    @DrawableRes
    public static int getIcon(final int serviceId) {
        // Only YouTube supported
        return R.mipmap.ic_launcher;
    }

    public static String getTranslatedFilterString(final String filter, final Context c) {
        switch (filter) {
            case "all":
                return c.getString(R.string.all);
            case "videos":
                return c.getString(R.string.videos_string);
            case "channels":
                return c.getString(R.string.channels);
            case "playlists":
                return c.getString(R.string.playlists);
            default:
                return filter;
        }
    }

    /**
     * Get a resource string with instructions for importing subscriptions for YouTube.
     *
     * @param serviceId service to get the instructions for (only YouTube supported)
     * @return the string resource containing the instructions or -1 if not supported
     */
    @StringRes
    public static int getImportInstructions(final int serviceId) {
        if (serviceId == YOUTUBE_SERVICE_ID) {
            return R.string.import_youtube_instructions;
        }
        return -1;
    }

    /**
     * YouTube doesn't support importing from channel URL, so this always returns -1.
     *
     * @param serviceId service to get the hint for
     * @return -1 (not supported)
     */
    @StringRes
    public static int getImportInstructionsHint(final int serviceId) {
        return -1;
    }

    public static int getSelectedServiceId(final Context context) {
        // Always return YouTube service ID
        return YOUTUBE_SERVICE_ID;
    }

    @Nullable
    public static StreamingService getSelectedService(final Context context) {
        // Always return YouTube service
        try {
            return NewPipe.getService(YOUTUBE_SERVICE_ID);
        } catch (final ExtractionException e) {
            return DEFAULT_FALLBACK_SERVICE;
        }
    }

    @NonNull
    public static String getNameOfServiceById(final int serviceId) {
        // Only YouTube supported
        return ServiceList.YouTube.getServiceInfo().getName();
    }

    /**
     * @param serviceId the id of the service (only YouTube supported)
     * @return YouTube service
     * @throws java.util.NoSuchElementException if serviceId is not YouTube
     */
    @NonNull
    public static StreamingService getServiceById(final int serviceId) {
        if (serviceId == YOUTUBE_SERVICE_ID) {
            return ServiceList.YouTube;
        }
        throw new java.util.NoSuchElementException("Only YouTube service is supported");
    }

    public static void setSelectedServiceId(final Context context, final int serviceId) {
        // Force YouTube service regardless of input
        setSelectedServicePreferences(context, ServiceList.YouTube.getServiceInfo().getName());
    }

    private static void setSelectedServicePreferences(final Context context,
                                                      final String serviceName) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(context.getString(R.string.current_service_key), serviceName)
                .apply();
    }

    public static long getCacheExpirationMillis(final int serviceId) {
        // Standard 1 hour cache for YouTube
        return TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
    }

    public static void initService(final Context context, final int serviceId) {
        // No special initialization needed for YouTube
    }

    public static void initServices(final Context context) {
        // No special initialization needed for YouTube
    }
}