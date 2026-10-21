package com.vr.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.animation.LinearInterpolator;
import android.view.View;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A scripted "J.A.R.V.I.S." for TV Home. Not a real AI: it reacts to what you select,
 * comments when you switch inputs, and offers a suggestion when the TV sits idle.
 * Lines and their audio come from assets/voice (made by make_voice.sh from voice/lines.txt).
 */
final class Assistant {
    private static final long SETTLE_MS = 650;          // wait for focus to rest before talking
    private static final long REPEAT_MS = 3 * 60_000;   // don't repeat the same line sooner
    private static final long IDLE_FIRST_MS = 15_000;   // first idle prompt
    private static final long IDLE_AGAIN_MS = 90_000;   // later prompts if still ignored
    static final float IDLE_GLOW = 0.35f;                // reactor glow when not speaking

    private final Context ctx;
    private final TextView bubble, status;
    private final Icons.WaveDrawable wave;
    private final View avatar, glow;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final Map<String, String> text = new HashMap<>();
    private final Map<String, Integer> durationMs = new HashMap<>();
    private final Map<String, Integer> soundId = new HashMap<>();
    private final Map<String, Long> lastSaid = new HashMap<>();
    private final List<String> idleKeys = new ArrayList<>();
    private final SoundPool pool;

    private Runnable pending;
    private ValueAnimator typing, speaking;
    private int streamId;
    private int idleIndex;
    private long idleDelay = IDLE_FIRST_MS;
    private boolean muted;
    private String suggestion;

    private final Runnable idleTick = new Runnable() {
        @Override
        public void run() {
            if (idleKeys.isEmpty()) return;
            String key = idleKeys.get(idleIndex++ % idleKeys.size());
            suggestion = key.substring("idle.".length());
            say(key, true);
            idleDelay = IDLE_AGAIN_MS;
            handler.postDelayed(this, idleDelay);
        }
    };

    Assistant(Context ctx, TextView bubble, TextView status, Icons.WaveDrawable wave, View avatar, View glow) {
        this.ctx = ctx;
        this.bubble = bubble;
        this.status = status;
        this.wave = wave;
        this.avatar = avatar;
        this.glow = glow;
        prefs = ctx.getSharedPreferences("assistant", Context.MODE_PRIVATE);
        muted = prefs.getBoolean("muted", false);
        pool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .build();
        loadLines();
        bubble.setText(greeting());
        showStatus();
    }

    // ---------------------------------------------------------------- events from the screen

    /** A card got focus. Speaks only if focus stays there a moment (no chatter while scrolling). */
    void react(String key) {
        handler.removeCallbacks(pending);
        pending = () -> say(key, false);
        handler.postDelayed(pending, SETTLE_MS);
    }

    /** Speak right away, ignoring the repeat limit (input switches, mute toggles). */
    void sayNow(String key) {
        handler.removeCallbacks(pending);
        say(key, true);
    }

    /** Any remote button: reset the idle timer. */
    void onUserActivity() {
        handler.removeCallbacks(idleTick);
        idleDelay = IDLE_FIRST_MS;
        handler.postDelayed(idleTick, idleDelay);
    }

    void onShown() {
        onUserActivity();
    }

    void onHidden() {
        handler.removeCallbacks(idleTick);
        handler.removeCallbacks(pending);
    }

    /** The app the last idle prompt suggested, or null. */
    String suggestion() {
        return suggestion;
    }

    void toggleMute() {
        muted = !muted;
        prefs.edit().putBoolean("muted", muted).apply();
        if (muted) {
            pool.stop(streamId);
            say("sys.mute", true);
        } else {
            say("sys.unmute", true);
        }
    }

    void release() {
        handler.removeCallbacksAndMessages(null);
        pool.release();
    }

    // ---------------------------------------------------------------- speaking

    private void say(String key, boolean force) {
        String line = text.get(key);
        if (line == null) return;
        long now = SystemClock.uptimeMillis();
        Long last = lastSaid.get(key);
        if (!force && last != null && now - last < REPEAT_MS) return;
        lastSaid.put(key, now);
        if (!key.startsWith("idle.")) suggestion = null;

        // Muted: text only (except the "voice off" line itself, which is shown silently too)
        Integer id = soundId.get(key);
        pool.stop(streamId);
        if (!muted && id != null) streamId = pool.play(id, 1f, 1f, 1, 0, 1f);

        typeOut(line);
        animateSpeaking(durationMs.containsKey(key) ? durationMs.get(key) : line.length() * 60);
    }

    /** Reveals the line a few letters at a time, like it's being typed. */
    private void typeOut(String line) {
        if (typing != null) typing.cancel();
        typing = ValueAnimator.ofInt(0, line.length());
        typing.setDuration(Math.min(1800, line.length() * 32L));
        typing.setInterpolator(new LinearInterpolator());
        typing.addUpdateListener(a -> bubble.setText(line.substring(0, (int) a.getAnimatedValue())));
        typing.start();
    }

    /** Waveform, reactor and its glow come alive for the length of the clip, then settle. */
    private void animateSpeaking(long ms) {
        if (speaking != null) speaking.cancel();
        status.setText(muted ? "Muted" : "Speaking…");
        speaking = ValueAnimator.ofFloat(0f, ms / 2400f);
        speaking.setDuration(ms);
        speaking.setInterpolator(new LinearInterpolator());
        speaking.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            wave.setPhase(t % 1f);
            // Slow turn, a pulse in time with the voice bars, and a brighter glow
            float pulse = (float) Math.abs(Math.sin(t * Math.PI * 4));
            avatar.setRotation(t * 60f);
            avatar.setScaleX(1f + 0.06f * pulse);
            avatar.setScaleY(1f + 0.06f * pulse);
            glow.setAlpha(0.6f + 0.4f * pulse);
        });
        speaking.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                avatar.animate().scaleX(1f).scaleY(1f).setDuration(300).start();
                glow.animate().alpha(IDLE_GLOW).setDuration(500).start();
                showStatus();
            }
        });
        speaking.start();
    }

    private void showStatus() {
        status.setText(muted ? "Muted · hold OK for voice" : "Online");
    }

    private static String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String part = h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
        return part + ", sir. All systems online.";
    }

    // ---------------------------------------------------------------- data

    private void loadLines() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                ctx.getAssets().open("voice/index.txt"), "UTF-8"))) {
            for (String row; (row = r.readLine()) != null; ) {
                String[] p = row.split("\\|", 3);
                if (p.length < 3) continue;
                text.put(p[0], p[2]);
                int ms = Integer.parseInt(p[1]);
                if (ms == 0) continue; // no recording yet: on-screen text only
                durationMs.put(p[0], ms);
                // Only voiced lines are used for idle prompts; a silent prompt would go unnoticed
                if (p[0].startsWith("idle.")) idleKeys.add(p[0]);
                try (AssetFileDescriptor fd = ctx.getAssets().openFd("voice/" + p[0] + ".m4a")) {
                    soundId.put(p[0], pool.load(fd, 1));
                } catch (IOException e) {
                    durationMs.remove(p[0]);
                }
            }
        } catch (IOException | NumberFormatException e) {
            // No voice pack: the assistant stays quiet but the screen still works
        }
    }
}
