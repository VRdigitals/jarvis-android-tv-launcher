package com.vr.tvhome;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/**
 * The TV switches accessibility off again on some restarts, which would stop the Home
 * button override. On boot, turn our service back on. Needs WRITE_SECURE_SETTINGS,
 * granted once over adb (see install.sh); without it this does nothing.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String SERVICE = "com.vr.tvhome/com.vr.tvhome.HomeOverrideService";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        ContentResolver cr = context.getContentResolver();
        try {
            String current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (current == null || current.isEmpty()) {
                current = SERVICE;
            } else if (!(":" + current + ":").contains(":" + SERVICE + ":")) {
                current = current + ":" + SERVICE;
            }
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, current);
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (SecurityException ignored) {
            // Permission not granted; the override stays off until install.sh is run
        }
    }
}
