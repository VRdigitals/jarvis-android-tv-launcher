package com.vr.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.IOException;
import java.io.InputStream;

/**
 * Plays the welcome video full screen once, then opens TV Home. Any button skips it.
 *
 * It is usually opened while the TV is turning off, so that when the TV wakes the first
 * thing on screen is this rather than whatever was showing before. Playback only begins
 * once the screen is on and this is in front. While the Bluetooth speaker reconnects
 * (the TV switches Bluetooth off in standby), a pulsing arc reactor is shown instead of black.
 */
public class IntroActivity extends Activity {
    /** True while the intro is up, so the Home redirect brings it back instead of TV Home. */
    static volatile boolean showing;
    static volatile long shownAt;
    /** Longest we hold the intro for the Bluetooth speaker to reconnect after the TV wakes. */
    private static final long SPEAKER_WAIT_MS = 5_000;
    private boolean done;
    private boolean begun;
    private boolean ready;     // speaker connected or wait over
    private boolean started;   // video playing
    private boolean resumed;
    private VideoView video;
    private View waiting;
    private ValueAnimator waitPulse;
    private AudioManager audio;
    private PowerManager power;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AudioDeviceCallback speakerWatch = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            // Let Android finish moving the sound over before the voice starts
            if (speakerConnected()) handler.postDelayed(IntroActivity.this::markReady, 500);
        }
    };
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                begin();
            } else if (started) {
                // Turned off mid-intro: drop this one; a fresh one is prepared for next time
                quit();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showing = true;
        shownAt = SystemClock.uptimeMillis();
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        power = (PowerManager) getSystemService(Context.POWER_SERVICE);
        prefs = getSharedPreferences("intro", MODE_PRIVATE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        video = new VideoView(this);
        root.addView(video, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        waiting = waitingScreen();
        waiting.setVisibility(View.GONE);
        root.addView(waiting, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        video.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.intro));
        video.setOnCompletionListener(mp -> finishIntro());
        video.setOnErrorListener((mp, what, extra) -> {
            finishIntro();
            return true;
        });

        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screen, f);
        if (power.isInteractive()) begin();
    }

    /** Screen is on: wait for the Bluetooth speaker if it's normally used, then play. */
    private void begin() {
        if (begun || done) return;
        begun = true;
        shownAt = SystemClock.uptimeMillis();
        if (prefs.getBoolean("btSpeaker", true) && !speakerConnected()) {
            showWaiting();
            audio.registerAudioDeviceCallback(speakerWatch, handler);
            handler.postDelayed(this::markReady, SPEAKER_WAIT_MS);
        } else {
            markReady();
        }
    }

    private void markReady() {
        if (ready || done) return;
        ready = true;
        handler.removeCallbacksAndMessages(null);
        audio.unregisterAudioDeviceCallback(speakerWatch);
        play();
    }

    /** Starts the video, but only while this is actually in front (never play hidden). */
    private void play() {
        if (!ready || started || done || !resumed) return;
        started = true;
        video.start();
        if (waitPulse != null) waitPulse.cancel();
        waiting.animate().alpha(0f).setDuration(400).withEndAction(() -> waiting.setVisibility(View.GONE)).start();
    }

    private boolean speakerConnected() {
        for (AudioDeviceInfo d : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- waiting screen

    private View waitingScreen() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.BLACK);

        FrameLayout reactor = new FrameLayout(this);
        reactor.setClipChildren(false);
        View glow = new View(this);
        GradientDrawable halo = new GradientDrawable();
        halo.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        halo.setGradientRadius(dp(110));
        halo.setColors(new int[]{0xAA4DA3FF, 0x334DA3FF, 0x004DA3FF});
        glow.setBackground(halo);
        reactor.addView(glow, new FrameLayout.LayoutParams(dp(220), dp(220), Gravity.CENTER));
        ImageView core = new ImageView(this);
        try (InputStream in = getAssets().open("arc_reactor.png")) {
            core.setImageBitmap(BitmapFactory.decodeStream(in));
        } catch (IOException e) {
            core.setImageDrawable(Icons.reactor()); // no assets/arc_reactor.png: drawn one
        }
        reactor.addView(core, new FrameLayout.LayoutParams(dp(120), dp(120), Gravity.CENTER));
        box.addView(reactor, new LinearLayout.LayoutParams(dp(220), dp(220)));

        TextView label = new TextView(this);
        label.setText("Initialising…");
        label.setTextColor(0xCCBFE3FF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        label.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        label.setLetterSpacing(0.3f);
        box.addView(label);

        box.setTag(new View[]{core, glow, label});
        return box;
    }

    private void showWaiting() {
        View[] parts = (View[]) waiting.getTag();
        View core = parts[0], glow = parts[1], label = parts[2];
        waiting.setAlpha(0f);
        waiting.setVisibility(View.VISIBLE);
        waiting.animate().alpha(1f).setDuration(300).start();
        waitPulse = ValueAnimator.ofFloat(0f, 1f);
        waitPulse.setDuration(1600);
        waitPulse.setRepeatCount(ValueAnimator.INFINITE);
        waitPulse.setRepeatMode(ValueAnimator.REVERSE);
        waitPulse.setInterpolator(new AccelerateDecelerateInterpolator());
        waitPulse.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            core.setScaleX(1f + 0.05f * t);
            core.setScaleY(1f + 0.05f * t);
            core.setRotation(core.getRotation() + 0.4f);
            glow.setAlpha(0.45f + 0.55f * t);
            label.setAlpha(0.5f + 0.5f * t);
        });
        waitPulse.start();
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Any button skips; act on key-up so the press doesn't also reach TV Home
        if (event.getAction() == KeyEvent.ACTION_UP) finishIntro();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (power.isInteractive()) {
            begin();
            play();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Covered while the video was playing on a lit screen: don't come back.
        // (While waiting, Google TV's wake-up screen can cover it; the Home redirect
        // brings it back to the front.)
        if (!done && started && power.isInteractive()) quit();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        audio.unregisterAudioDeviceCallback(speakerWatch);
        if (waitPulse != null) waitPulse.cancel();
        unregisterReceiver(screen);
        showing = false;
        super.onDestroy();
    }

    /** Close without opening TV Home. */
    private void quit() {
        done = true;
        showing = false;
        finish();
    }

    private void finishIntro() {
        if (done) return;
        done = true;
        showing = false;
        // Remember for next time whether the speaker is in use (it's reconnected by now if it's on)
        prefs.edit().putBoolean("btSpeaker", speakerConnected()).apply();
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
