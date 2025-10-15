package com.nidoham.opentube.permission;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public class PermissionManager {

    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1001;
    private static final String NOTIFICATION_CHANNEL_ID = "default_channel";
    private static final String NOTIFICATION_CHANNEL_NAME = "Default Notifications";

    private final Context context;
    private PermissionCallback callback;

    public interface PermissionCallback {
        void onPermissionGranted();
        void onPermissionDenied();
    }

    public PermissionManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Default notification channel");

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    public boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    public void requestNotificationPermission(@NonNull Activity activity, @NonNull PermissionCallback callback) {
        this.callback = callback;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission()) {
                ActivityCompat.requestPermissions(
                        activity,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST_CODE
                );
            } else {
                callback.onPermissionGranted();
            }
        } else {
            Intent intent = new Intent()
                    .setAction(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                            Settings.ACTION_APP_NOTIFICATION_SETTINGS :
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull int[] grantResults) {
        if (requestCode != NOTIFICATION_PERMISSION_REQUEST_CODE || callback == null) return;

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            callback.onPermissionGranted();
        } else {
            callback.onPermissionDenied();
        }
    }

    public String getNotificationChannelId() {
        return NOTIFICATION_CHANNEL_ID;
    }
}