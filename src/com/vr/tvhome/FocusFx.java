package com.vr.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * The "lit blue glass" focus effect: a soft pulsing halo behind the card, a lit edge
 * (light blue on top, deep blue below), a glossy top highlight, a springy zoom and a
 * light sweep across the card. Only transforms and alpha animate, so it stays smooth.
 */
final class FocusFx {
    static final int BLUE = 0xFF2F7BFF;
    static final int BLUE_LIGHT = 0xFFA6DBFF;

    private FocusFx() {
    }

    /**
     * Puts {@code card} in a holder with a glow behind it and wires up focus and click.
     * w/h may be WRAP_CONTENT. The returned holder is what goes into the layout.
     */
    static FrameLayout wrap(View card, int w, int h, float radiusPx, float scale, boolean tint, Runnable onClick) {
        Context c = card.getContext();
        float dp = c.getResources().getDisplayMetrics().density;

        FrameLayout holder = new FrameLayout(c);
        holder.setClipChildren(false);
        holder.setClipToPadding(false);

        // The halo is the holder's background: it is drawn around the card's bounds
        GlowDrawable glow = new GlowDrawable(radiusPx, 16 * dp);
        glow.setAlpha(0);
        holder.setBackground(glow);
        holder.addView(card, new FrameLayout.LayoutParams(w, h));

        // Light sweep, only for cards that clip their content (image cards)
        View sheen = null;
        if (card instanceof FrameLayout && card.getClipToOutline() && w > 0 && h > 0) {
            sheen = new View(c);
            sheen.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0x00FFFFFF, 0x40FFFFFF, 0x00FFFFFF}));
            sheen.setRotation(20f);
            sheen.setAlpha(0f);
            ((FrameLayout) card).addView(sheen, new FrameLayout.LayoutParams(
                    (int) (w * 0.3f), (int) (h * 2f), Gravity.CENTER_VERTICAL));
        }

        TransitionDrawable lit = new TransitionDrawable(new Drawable[]{
                new ColorDrawable(0), new LitDrawable(radiusPx, dp, tint)});
        lit.setCrossFadeEnabled(true);
        card.setForeground(lit);

        attach(holder, card, glow, sheen, lit, scale, w, dp);
        card.setOnClickListener(v -> press(holder, scale, onClick));
        holder.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return holder;
    }

    private static void attach(View holder, View card, GlowDrawable glow, View sheen, TransitionDrawable lit,
                               float scale, int w, float dp) {
        final boolean[] on = {false};
        final ValueAnimator[] fade = {null};
        card.setOnFocusChangeListener((v, has) -> {
            holder.animate().cancel();
            if (fade[0] != null) fade[0].cancel();

            // Raise above neighbours so the zoomed card and its glow aren't covered
            holder.setTranslationZ(has ? 8 * dp : 0);
            holder.animate()
                    .scaleX(has ? scale : 1f).scaleY(has ? scale : 1f)
                    .setInterpolator(has ? new OvershootInterpolator(1.4f) : new DecelerateInterpolator(2f))
                    .setDuration(has ? 320 : 200)
                    .start();

            if (has && !on[0]) lit.startTransition(180);
            if (!has && on[0]) lit.reverseTransition(160);
            on[0] = has;

            if (has) {
                // Fade the halo in, then let it breathe slowly while focused
                fade[0] = ValueAnimator.ofInt(glow.getAlpha(), 255);
                fade[0].setDuration(240);
                fade[0].addUpdateListener(a -> glow.setAlpha((int) a.getAnimatedValue()));
                fade[0].addListener(new AnimatorListenerAdapter() {
                    private boolean cancelled;

                    @Override
                    public void onAnimationCancel(Animator a) {
                        cancelled = true;
                    }

                    @Override
                    public void onAnimationEnd(Animator a) {
                        if (cancelled || !on[0]) return;
                        fade[0] = ValueAnimator.ofInt(255, 140);
                        fade[0].setDuration(1400);
                        fade[0].setRepeatCount(ValueAnimator.INFINITE);
                        fade[0].setRepeatMode(ValueAnimator.REVERSE);
                        fade[0].setInterpolator(new AccelerateDecelerateInterpolator());
                        fade[0].addUpdateListener(b -> glow.setAlpha((int) b.getAnimatedValue()));
                        fade[0].start();
                    }
                });
                fade[0].start();
                if (sheen != null) {
                    sheen.animate().cancel();
                    sheen.setTranslationX(-w * 0.7f);
                    sheen.setAlpha(1f);
                    sheen.animate().translationX(w * 1.1f).setDuration(750).setStartDelay(80)
                            .setInterpolator(new DecelerateInterpolator(1.5f))
                            .withEndAction(() -> sheen.setAlpha(0f)).start();
                }
            } else {
                fade[0] = ValueAnimator.ofInt(glow.getAlpha(), 0);
                fade[0].setDuration(180);
                fade[0].addUpdateListener(a -> glow.setAlpha((int) a.getAnimatedValue()));
                fade[0].start();
            }
        });
    }

    /** Quick press-in, then spring back and run the action. */
    private static void press(View holder, float scale, Runnable onClick) {
        holder.animate().cancel();
        holder.animate().scaleX(scale * 0.94f).scaleY(scale * 0.94f).setDuration(90)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    holder.animate().scaleX(scale).scaleY(scale).setDuration(220)
                            .setInterpolator(new OvershootInterpolator(2f)).start();
                    onClick.run();
                }).start();
    }

    /** Soft blue halo drawn outside the view's bounds as stacked translucent rings. */
    static final class GlowDrawable extends Drawable {
        private static final int RINGS = 12;
        private final float radius, spread;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int alpha = 255;

        GlowDrawable(float radius, float spread) {
            this.radius = radius;
            this.spread = spread;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(spread / RINGS + 1f);
            paint.setColor(BLUE);
        }

        @Override
        public void draw(Canvas canvas) {
            if (alpha == 0) return;
            Rect b = getBounds();
            for (int i = 0; i < RINGS; i++) {
                float t = i / (float) RINGS;
                float out = t * spread;
                float fade = (1f - t) * (1f - t);
                paint.setAlpha((int) (alpha * 0.42f * fade));
                RectF r = new RectF(b.left - out, b.top - out, b.right + out, b.bottom + out);
                canvas.drawRoundRect(r, radius + out, radius + out, paint);
            }
        }

        @Override
        public void setAlpha(int a) {
            alpha = a;
            invalidateSelf();
        }

        @Override
        public int getAlpha() {
            return alpha;
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Lit edge + glossy top highlight (+ optional blue glass tint) drawn over the card. */
    static final class LitDrawable extends Drawable {
        private final float radius, dp;
        private final boolean tint;
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int alpha = 255;

        LitDrawable(float radius, float dp, boolean tint) {
            this.radius = radius;
            this.dp = dp;
            this.tint = tint;
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(2.2f * dp);
        }

        @Override
        protected void onBoundsChange(Rect b) {
            edge.setShader(new LinearGradient(0, b.top, 0, b.bottom, BLUE_LIGHT, BLUE, Shader.TileMode.CLAMP));
            gloss.setShader(new LinearGradient(0, b.top, 0, b.top + b.height() * 0.55f,
                    0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            fill.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                    0x664DA3FF, 0x261E5BFF, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(Canvas canvas) {
            RectF r = new RectF(getBounds());
            edge.setAlpha(alpha);
            gloss.setAlpha(alpha);
            fill.setAlpha(alpha);
            if (tint) canvas.drawRoundRect(r, radius, radius, fill);
            canvas.drawRoundRect(r, radius, radius, gloss);
            float in = edge.getStrokeWidth() / 2f;
            r.inset(in, in);
            canvas.drawRoundRect(r, radius - in, radius - in, edge);
        }

        @Override
        public void setAlpha(int a) {
            alpha = a;
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            edge.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
