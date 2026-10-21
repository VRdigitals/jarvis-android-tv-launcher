package com.vr.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Google TV won't let another app replace its home screen (disabling it makes Google's
 * setup app loop). Instead we leave it enabled and jump to TV Home whenever it appears.
 * The service is limited to the Google TV home package in res/xml/home_override.xml.
 *
 * It also plays the welcome intro when the TV starts: after a full boot, and every time
 * the screen comes on (this TV sleeps instead of shutting down when you press power). The
 * intro is opened as the screen turns off, so it is already on screen when the TV wakes.
 */
public class HomeOverrideService extends AccessibilityService {
    private static final String GOOGLE_HOME = "com.google.android.apps.tv.launcherx";
    private static final long BOOT_WINDOW_MS = 5 * 60_000;
    private static final long INTRO_GAP_MS = 15_000;
    private long lastRedirect;
    private long lastIntro = -INTRO_GAP_MS;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                // Get the intro ready now; it starts playing when the screen comes back on.
                // (Short delay so an intro being closed by this same screen-off is gone first.)
                handler.postDelayed(HomeOverrideService.this::prepareIntro, 300);
            } else {
                // Fallback if no intro was prepared: open one once the screen has woken
                handler.postDelayed(() -> {
                    if (!IntroActivity.showing) openIntro();
                }, 500);
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screen, f);
        // At boot the Google TV home is already up before this service connects, so its
        // window event is missed. Within a few minutes of boot, start with the intro.
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) openIntro();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        unregisterReceiver(screen);
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        CharSequence cls = event.getClassName();
        if (pkg == null || !GOOGLE_HOME.contentEquals(pkg)) return;
        // On wake Google TV puts up its own screen on top; if the intro is up, bring it back
        if (IntroActivity.showing && SystemClock.uptimeMillis() - IntroActivity.shownAt < 50_000) {
            startActivity(new Intent(this, IntroActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        if (cls == null || !cls.toString().endsWith("HomeActivity")) return;
        openHome();
    }

    @Override
    public void onInterrupt() {
    }

    private void openHome() {
        long now = SystemClock.uptimeMillis();
        // The intro opens TV Home itself when it ends (time limit: never block Home if it got stuck)
        if (IntroActivity.showing && now - IntroActivity.shownAt < 50_000) return;
        if (now - lastRedirect < 800) return;
        lastRedirect = now;
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    private void prepareIntro() {
        if (IntroActivity.showing) return;
        lastIntro = SystemClock.elapsedRealtime();
        lastRedirect = SystemClock.uptimeMillis();
        startActivity(new Intent(this, IntroActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void openIntro() {
        // elapsedRealtime keeps counting while the TV sleeps, so a quick off/on still counts
        long now = SystemClock.elapsedRealtime();
        if (now - lastIntro < INTRO_GAP_MS) return;
        lastIntro = now;
        lastRedirect = SystemClock.uptimeMillis(); // the intro hands over to TV Home itself
        startActivity(new Intent(this, IntroActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
