package com.vr.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Line icons drawn in code on a 24x24 grid, so the app ships no icon images. */
public final class Icons {
    public enum Type {
        HOME, GRID, LIVE, SETTINGS, WIFI, BLUETOOTH, PICTURE, SOUND, MIC,
        TV, HDMI, AV, USB, AIRPLAY, MIRROR, HEADPHONES, DEVICES,
        SUN, MOON, CLOUD, PARTLY_DAY, PARTLY_NIGHT, RAIN, FOG, STORM, SNOW
    }

    private Icons() {
    }

    public static Drawable of(Type type, int color) {
        return new IconDrawable(type, color);
    }

    /** Drawn arc-reactor, used when there's no assets/arc_reactor.png. */
    public static Drawable reactor() {
        return new ReactorDrawable();
    }

    /** "Voice" bars for the assistant card; animate with {@link WaveDrawable#setPhase}. */
    public static WaveDrawable wave(int color) {
        return new WaveDrawable(color);
    }

    private static final class IconDrawable extends Drawable {
        private final Type type;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

        IconDrawable(Type type, int color) {
            this.type = type;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(1.7f);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setColor(color);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(color);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float size = Math.min(b.width(), b.height());
            c.save();
            c.translate(b.left + (b.width() - size) / 2f, b.top + (b.height() - size) / 2f);
            c.scale(size / 24f, size / 24f);
            switch (type) {
                case HOME: home(c); break;
                case GRID: grid(c); break;
                case LIVE: live(c); break;
                case SETTINGS: settings(c); break;
                case WIFI: wifi(c); break;
                case BLUETOOTH: bluetooth(c); break;
                case PICTURE: picture(c); break;
                case SOUND: sound(c); break;
                case MIC: mic(c); break;
                case TV: tv(c); break;
                case HDMI: hdmi(c); break;
                case AV: av(c); break;
                case USB: usb(c); break;
                case AIRPLAY: airplay(c); break;
                case MIRROR: mirror(c); break;
                case HEADPHONES: headphones(c); break;
                case DEVICES: devices(c); break;
                case SUN: sun(c, 12, 12, 1f); break;
                case MOON: moon(c, 12, 12, 1f); break;
                case CLOUD: cloud(c, 0, 0); break;
                case PARTLY_DAY: sun(c, 8, 8, 0.7f); cloud(c, 1.5f, 2.5f); break;
                case PARTLY_NIGHT: moon(c, 8, 8, 0.7f); cloud(c, 1.5f, 2.5f); break;
                case RAIN: cloud(c, 0, -3); rain(c); break;
                case FOG: fog(c); break;
                case STORM: cloud(c, 0, -3); bolt(c); break;
                case SNOW: cloud(c, 0, -3); snow(c); break;
            }
            c.restore();
        }

        private void home(Canvas c) {
            Path p = new Path();
            p.moveTo(3.5f, 11f); p.lineTo(12f, 3.5f); p.lineTo(20.5f, 11f);
            p.moveTo(5.5f, 9.5f); p.lineTo(5.5f, 20.5f); p.lineTo(18.5f, 20.5f); p.lineTo(18.5f, 9.5f);
            p.moveTo(10f, 20.5f); p.lineTo(10f, 14.5f); p.lineTo(14f, 14.5f); p.lineTo(14f, 20.5f);
            c.drawPath(p, stroke);
        }

        private void grid(Canvas c) {
            for (float x : new float[]{3.5f, 13.5f}) {
                for (float y : new float[]{3.5f, 13.5f}) {
                    c.drawRoundRect(new RectF(x, y, x + 7, y + 7), 1.8f, 1.8f, stroke);
                }
            }
        }

        private void live(Canvas c) {
            c.drawCircle(12, 12, 2f, fill);
            for (float r : new float[]{5.5f, 9.5f}) {
                RectF o = new RectF(12 - r, 12 - r, 12 + r, 12 + r);
                c.drawArc(o, 145, 70, false, stroke);
                c.drawArc(o, -35, 70, false, stroke);
            }
        }

        private void settings(Canvas c) {
            c.drawCircle(12, 12, 3f, stroke);
            c.drawCircle(12, 12, 6.8f, stroke);
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians(i * 45);
                c.drawLine(12 + (float) Math.cos(a) * 7.2f, 12 + (float) Math.sin(a) * 7.2f,
                        12 + (float) Math.cos(a) * 9.6f, 12 + (float) Math.sin(a) * 9.6f, stroke);
            }
        }

        private void wifi(Canvas c) {
            for (float r : new float[]{4.5f, 9f, 13.5f}) {
                c.drawArc(new RectF(12 - r, 19.5f - r, 12 + r, 19.5f + r), -135, 90, false, stroke);
            }
            c.drawCircle(12, 19.5f, 1.4f, fill);
        }

        private void bluetooth(Canvas c) {
            Path p = new Path();
            p.moveTo(7f, 7.5f); p.lineTo(16.5f, 16.5f); p.lineTo(12f, 21f); p.lineTo(12f, 3f);
            p.lineTo(16.5f, 7.5f); p.lineTo(7f, 16.5f);
            c.drawPath(p, stroke);
        }

        private void picture(Canvas c) {
            c.drawRoundRect(new RectF(3, 5, 21, 19), 2.2f, 2.2f, stroke);
            c.drawCircle(8.5f, 9.8f, 1.6f, stroke);
            Path p = new Path();
            p.moveTo(3.5f, 17f); p.lineTo(9f, 12.5f); p.lineTo(13f, 15.5f); p.lineTo(16f, 13f); p.lineTo(20.5f, 16.5f);
            c.drawPath(p, stroke);
        }

        private void sound(Canvas c) {
            Path p = new Path();
            p.moveTo(4f, 9.5f); p.lineTo(7.5f, 9.5f); p.lineTo(12f, 5.5f); p.lineTo(12f, 18.5f);
            p.lineTo(7.5f, 14.5f); p.lineTo(4f, 14.5f); p.close();
            c.drawPath(p, stroke);
            c.drawArc(new RectF(10.5f, 8.5f, 17.5f, 15.5f), -50, 100, false, stroke);
            c.drawArc(new RectF(8.5f, 5f, 21f, 19f), -50, 100, false, stroke);
        }

        private void mic(Canvas c) {
            c.drawRoundRect(new RectF(9, 3, 15, 14), 3, 3, stroke);
            c.drawArc(new RectF(5.5f, 5f, 18.5f, 17.5f), 0, 180, false, stroke);
            c.drawLine(12, 17.5f, 12, 21, stroke);
            c.drawLine(9, 21, 15, 21, stroke);
        }

        private void tv(Canvas c) {
            c.drawRoundRect(new RectF(3, 4.5f, 21, 16.5f), 1.8f, 1.8f, stroke);
            c.drawLine(12, 16.5f, 12, 20, stroke);
            c.drawLine(8, 20, 16, 20, stroke);
        }

        private void hdmi(Canvas c) {
            Path p = new Path();
            p.moveTo(3.5f, 8.5f); p.lineTo(20.5f, 8.5f); p.lineTo(20.5f, 12.5f); p.lineTo(17.5f, 15.5f);
            p.lineTo(6.5f, 15.5f); p.lineTo(3.5f, 12.5f); p.close();
            c.drawPath(p, stroke);
            c.drawLine(7.5f, 11.5f, 16.5f, 11.5f, stroke);
        }

        private void av(Canvas c) {
            for (float x : new float[]{5.5f, 12f, 18.5f}) {
                c.drawCircle(x, 12, 2.6f, stroke);
                c.drawCircle(x, 12, 0.7f, fill);
            }
        }

        private void usb(Canvas c) {
            c.drawRoundRect(new RectF(8.5f, 3, 15.5f, 9.5f), 1, 1, stroke);
            c.drawRoundRect(new RectF(6.5f, 9.5f, 17.5f, 21), 2, 2, stroke);
            c.drawRect(10.3f, 5.3f, 11.3f, 6.8f, fill);
            c.drawRect(12.7f, 5.3f, 13.7f, 6.8f, fill);
        }

        private void airplay(Canvas c) {
            Path p = new Path();
            p.moveTo(6.5f, 17f); p.lineTo(4f, 17f); p.lineTo(4f, 4.5f); p.lineTo(20f, 4.5f);
            p.lineTo(20f, 17f); p.lineTo(17.5f, 17f);
            c.drawPath(p, stroke);
            Path t = new Path();
            t.moveTo(12f, 13.5f); t.lineTo(17f, 20.5f); t.lineTo(7f, 20.5f); t.close();
            c.drawPath(t, fill);
        }

        private void mirror(Canvas c) {
            c.drawRoundRect(new RectF(2.5f, 8, 8.5f, 20), 1.4f, 1.4f, stroke);
            c.drawRoundRect(new RectF(11, 4, 21.5f, 12.5f), 1.4f, 1.4f, stroke);
            Path p = new Path();
            p.moveTo(10.5f, 16.5f); p.lineTo(15.5f, 16.5f); p.lineTo(15.5f, 14.5f);
            c.drawPath(p, stroke);
            c.drawLine(13.8f, 14.8f, 15.5f, 14.5f, stroke);
        }

        private void headphones(Canvas c) {
            c.drawArc(new RectF(4, 4, 20, 20), 180, 180, false, stroke);
            c.drawRoundRect(new RectF(3.5f, 12.5f, 7.5f, 20), 1.5f, 1.5f, stroke);
            c.drawRoundRect(new RectF(16.5f, 12.5f, 20.5f, 20), 1.5f, 1.5f, stroke);
        }

        private void devices(Canvas c) {
            c.drawRoundRect(new RectF(2.5f, 5.5f, 15.5f, 15.5f), 1.4f, 1.4f, stroke);
            c.drawLine(6, 19, 12, 19, stroke);
            c.drawRoundRect(new RectF(17, 9, 21.5f, 19.5f), 1.2f, 1.2f, stroke);
        }

        // ------------------------------------------------------------ weather (filled)

        private void sun(Canvas c, float cx, float cy, float s) {
            Paint p = new Paint(fill);
            p.setColor(0xFFFFC94D);
            Paint rays = new Paint(stroke);
            rays.setColor(0xFFFFC94D);
            c.drawCircle(cx, cy, 4.2f * s, p);
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians(i * 45);
                c.drawLine(cx + (float) Math.cos(a) * 6.5f * s, cy + (float) Math.sin(a) * 6.5f * s,
                        cx + (float) Math.cos(a) * 9f * s, cy + (float) Math.sin(a) * 9f * s, rays);
            }
        }

        private void moon(Canvas c, float cx, float cy, float s) {
            Path outer = new Path();
            outer.addCircle(cx, cy, 8f * s, Path.Direction.CW);
            Path bite = new Path();
            bite.addCircle(cx + 4.5f * s, cy - 3.5f * s, 7f * s, Path.Direction.CW);
            outer.op(bite, Path.Op.DIFFERENCE);
            Paint p = new Paint(fill);
            p.setColor(0xFFDDE6F5);
            c.drawPath(outer, p);
        }

        private void cloud(Canvas c, float dx, float dy) {
            Path p = new Path();
            p.addCircle(8.5f + dx, 14.5f + dy, 4.2f, Path.Direction.CW);
            Path a = new Path();
            a.addCircle(13.5f + dx, 11.5f + dy, 5.5f, Path.Direction.CW);
            Path b = new Path();
            b.addCircle(17.5f + dx, 15f + dy, 3.7f, Path.Direction.CW);
            Path base = new Path();
            base.addRoundRect(new RectF(4.3f + dx, 13f + dy, 21.2f + dx, 18.7f + dy), 2.8f, 2.8f, Path.Direction.CW);
            p.op(a, Path.Op.UNION);
            p.op(b, Path.Op.UNION);
            p.op(base, Path.Op.UNION);
            Paint f = new Paint(fill);
            f.setColor(0xFFE6ECF5);
            c.drawPath(p, f);
        }

        private void rain(Canvas c) {
            Paint p = new Paint(stroke);
            p.setColor(0xFF7FB8FF);
            for (float x : new float[]{8f, 12.5f, 17f}) c.drawLine(x, 18.5f, x - 1.5f, 22f, p);
        }

        private void snow(Canvas c) {
            Paint p = new Paint(fill);
            p.setColor(0xFFFFFFFF);
            for (float x : new float[]{8f, 12.5f, 17f}) c.drawCircle(x, 20.5f, 1.1f, p);
        }

        private void fog(Canvas c) {
            Paint p = new Paint(stroke);
            p.setColor(0xFFDDE3EC);
            p.setStrokeWidth(2f);
            float[][] lines = {{4, 8, 18}, {6, 12, 20}, {3, 16, 17}, {7, 20, 19}};
            for (float[] l : lines) c.drawLine(l[0], l[1], l[2], l[1], p);
        }

        private void bolt(Canvas c) {
            Path p = new Path();
            p.moveTo(13f, 15f); p.lineTo(9.5f, 20f); p.lineTo(12f, 20f); p.lineTo(10.5f, 23.5f);
            p.lineTo(15f, 18f); p.lineTo(12.5f, 18f); p.close();
            Paint f = new Paint(fill);
            f.setColor(0xFFFFD34D);
            c.drawPath(p, f);
        }

        @Override
        public void setAlpha(int alpha) {
            stroke.setAlpha(alpha);
            fill.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            stroke.setColorFilter(cf);
            fill.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    public static final class WaveDrawable extends Drawable {
        private static final float[] HEIGHTS = {.15f, .3f, .2f, .5f, .35f, .8f, .55f, 1f, .7f, .9f, .45f, .65f,
                .3f, .5f, .2f, .35f, .15f, .25f, .1f};
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float phase;

        WaveDrawable(int color) {
            paint.setColor(color);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float step = b.width() / (float) (HEIGHTS.length + 1);
            paint.setStrokeWidth(step * 0.45f);
            float mid = b.exactCenterY();
            for (int i = 0; i < HEIGHTS.length; i++) {
                float x = b.left + step * (i + 1);
                // Each bar bobs on its own offset so the row ripples like a voice meter
                double s = Math.sin(2 * Math.PI * (phase * 2 + i * 0.37));
                float level = HEIGHTS[i] * (0.55f + 0.45f * (float) Math.abs(s));
                float h = Math.max(paint.getStrokeWidth(), level * b.height()) / 2f;
                c.drawLine(x, mid - h, x, mid + h, paint);
            }
        }

        /** 0..1, wraps; call every frame from an animator. */
        public void setPhase(float p) {
            phase = p;
            invalidateSelf();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
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

    private static final class ReactorDrawable extends Drawable {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int alpha = 255;

        ReactorDrawable() {
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float cx = b.exactCenterX(), cy = b.exactCenterY();
            float r = Math.min(b.width(), b.height()) / 2f;
            core.setShader(new android.graphics.RadialGradient(cx, cy, r * 0.62f,
                    new int[]{0xFFFFFFFF, 0xFF9FD8FF, 0x552F7BFF, 0x00000000},
                    new float[]{0f, 0.25f, 0.7f, 1f}, android.graphics.Shader.TileMode.CLAMP));
            core.setAlpha(alpha);
            c.drawCircle(cx, cy, r * 0.62f, core);
            ring.setColor(0xFF4DA3FF);
            ring.setAlpha(alpha / 2);
            ring.setStrokeWidth(r * 0.05f);
            c.drawCircle(cx, cy, r * 0.92f, ring);
            ring.setAlpha(alpha);
            ring.setStrokeWidth(r * 0.1f);
            RectF outer = new RectF(cx - r * 0.8f, cy - r * 0.8f, cx + r * 0.8f, cy + r * 0.8f);
            for (int i = 0; i < 8; i++) c.drawArc(outer, i * 45 + 6, 33, false, ring);
            ring.setStrokeWidth(r * 0.06f);
            ring.setColor(0xFFFFB347);
            RectF inner = new RectF(cx - r * 0.5f, cy - r * 0.5f, cx + r * 0.5f, cy + r * 0.5f);
            for (int i = 0; i < 3; i++) c.drawArc(inner, i * 120, 70, false, ring);
        }

        @Override
        public void setAlpha(int a) {
            alpha = a;
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            ring.setColorFilter(cf);
            core.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
