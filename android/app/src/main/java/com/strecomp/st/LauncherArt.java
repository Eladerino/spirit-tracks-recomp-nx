package com.strecomp.st;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The start screen's picture, drawn in code so it fits any screen in either orientation: a bright morning sky with
 * clouds, green hills with a stone tower in the distance, a tree at the edge, and a little steam engine coming down a
 * winding track towards you, its stack puffing. (Our own drawing; nothing from the game.)
 */
final class LauncherArt extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    LauncherArt() {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void draw(Canvas c) {
        final Rect b = getBounds();
        final float w = b.width(), h = b.height();
        if (w <= 0 || h <= 0) return;
        final boolean landscape = w > h;
        final float u = Math.min(w, h) / 100f;     // one unit: 1% of the short side
        final float horizon = h * (landscape ? 0.60f : 0.58f);
        c.save();
        c.translate(b.left, b.top);

        // Sky, and the sun's glow at the top right.
        fill.setShader(new LinearGradient(0, 0, 0, horizon, new int[] {0xFF2E8FD8, 0xFF7CC8F2, 0xFFD9F2FF},
                new float[] {0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, horizon + 1, fill);
        fill.setShader(new RadialGradient(w * 0.80f, h * 0.10f, 34 * u, 0xB0FFFFF0, 0x00FFFFF0, Shader.TileMode.CLAMP));
        c.drawCircle(w * 0.80f, h * 0.10f, 34 * u, fill);
        fill.setShader(null);

        // Clouds.
        cloud(c, w * 0.18f, h * 0.12f, 7 * u, 0xF0FFFFFF);
        cloud(c, w * 0.55f, h * 0.20f, 5 * u, 0xE0FFFFFF);
        cloud(c, w * 0.88f, h * 0.30f, 4 * u, 0xD0FFFFFF);
        if (landscape) cloud(c, w * 0.38f, h * 0.07f, 4 * u, 0xD8FFFFFF);

        // Far hills, the tower on them, near hills, the meadow (on the far hills' green, so no gap shows at the edges).
        fill.setColor(0xFF9CD08C);
        c.drawRect(0, horizon, w, h, fill);
        hills(c, w, horizon, 9 * u, 3, 0xFF9CD08C, 0.3f);
        tower(c, w * (landscape ? 0.70f : 0.74f), horizon - 5 * u, u * (landscape ? 1.0f : 1.15f));
        hills(c, w, horizon + 3 * u, 6 * u, 2, 0xFF7DC46C, 1.7f);
        fill.setShader(new LinearGradient(0, horizon, 0, h, new int[] {0xFF74BF62, 0xFF4F9C45, 0xFF2F6E33},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, horizon + 4 * u, w, h, fill);
        fill.setShader(null);

        // The track: from the bottom left, curving away to the horizon.
        final PointF p0 = new PointF(w * (landscape ? 0.02f : 0.30f), h * 1.06f);
        final PointF p1 = new PointF(w * (landscape ? 0.30f : 0.80f), h * (landscape ? 0.78f : 0.80f));
        final PointF p2 = new PointF(w * (landscape ? 0.42f : 0.52f), horizon + 4 * u);
        track(c, p0, p1, p2, 15 * u, 1.0f * u);

        // A tree framing the right edge.
        tree(c, w, h, u, landscape);

        // The engine, on the track near us.
        final float t = 0.30f;
        final PointF at = bezier(p0, p1, p2, t);
        final float gauge = lerp(15 * u, 1.0f * u, t);
        engine(c, at.x, at.y - gauge * 0.15f, gauge * 0.13f);

        // A little darker at the bottom: the buttons stand out.
        fill.setShader(new LinearGradient(0, h * 0.7f, 0, h, 0x00000000, 0x55000000, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.7f, w, h, fill);
        fill.setShader(null);
        c.restore();
    }

    private void cloud(Canvas c, float x, float y, float r, int color) {
        fill.setColor(color);
        c.drawCircle(x - 1.3f * r, y + 0.2f * r, 0.75f * r, fill);
        c.drawCircle(x - 0.4f * r, y - 0.35f * r, 1.0f * r, fill);
        c.drawCircle(x + 0.6f * r, y - 0.15f * r, 0.85f * r, fill);
        c.drawCircle(x + 1.4f * r, y + 0.25f * r, 0.6f * r, fill);
        c.drawRoundRect(new RectF(x - 2.0f * r, y, x + 2.0f * r, y + 0.85f * r), 0.42f * r, 0.42f * r, fill);
    }

    private void hills(Canvas c, float w, float y, float height, int bumps, int color, float phase) {
        path.reset();
        path.moveTo(0, y + height);
        final float step = w / bumps;
        for (int i = 0; i < bumps; i++) {
            final float x0 = i * step;
            final float lift = height * (0.7f + 0.3f * (float) Math.sin(phase + i * 2.1f));
            path.quadTo(x0 + step * 0.5f, y - lift, x0 + step, y + height * 0.2f);
        }
        path.lineTo(w, y + 6 * height);
        path.lineTo(0, y + 6 * height);
        path.close();
        fill.setColor(color);
        c.drawPath(path, fill);
    }

    // A slender stone tower with a round crown and a pointed roof (seen far away).
    private void tower(Canvas c, float x, float base, float u) {
        final float top = base - 30 * u;
        path.reset();
        path.moveTo(x - 2.6f * u, base);
        path.lineTo(x - 1.7f * u, top);
        path.lineTo(x + 1.7f * u, top);
        path.lineTo(x + 2.6f * u, base);
        path.close();
        fill.setColor(0xFFCDBB97);
        c.drawPath(path, fill);
        fill.setColor(0xFFB09D78);  // the shaded side
        path.reset();
        path.moveTo(x + 0.4f * u, base);
        path.lineTo(x + 0.3f * u, top);
        path.lineTo(x + 1.7f * u, top);
        path.lineTo(x + 2.6f * u, base);
        path.close();
        c.drawPath(path, fill);
        line.setColor(0xFF9A8763);
        line.setStrokeWidth(0.45f * u);
        for (int i = 1; i <= 3; i++) {
            final float y = base - i * 7.5f * u;
            final float half = lerp(2.6f, 1.7f, i / 4f) * u;
            c.drawLine(x - half, y, x + half, y, line);
        }
        // The crown and roof.
        fill.setColor(0xFFD8C7A2);
        c.drawRoundRect(new RectF(x - 3.0f * u, top - 3.6f * u, x + 3.0f * u, top + 0.6f * u), 1.2f * u, 1.2f * u, fill);
        fill.setColor(0xFF4F6E9E);
        path.reset();
        path.moveTo(x - 3.4f * u, top - 3.4f * u);
        path.lineTo(x, top - 9.5f * u);
        path.lineTo(x + 3.4f * u, top - 3.4f * u);
        path.close();
        c.drawPath(path, fill);
        fill.setColor(0xFF3A3F55);
        for (int i = -1; i <= 1; i++) c.drawRect(x + i * 1.6f * u - 0.4f * u, top - 2.6f * u, x + i * 1.6f * u + 0.4f * u, top - 0.6f * u, fill);
    }

    private void track(Canvas c, PointF p0, PointF p1, PointF p2, float g0, float g1) {
        final int n = 48;
        final PointF[] left = new PointF[n + 1], right = new PointF[n + 1];
        for (int i = 0; i <= n; i++) {
            final float t = i / (float) n;
            final PointF p = bezier(p0, p1, p2, t);
            final PointF d = tangent(p0, p1, p2, t);
            final float g = lerp(g0, g1, t) * 0.5f;
            left[i] = new PointF(p.x - d.y * g, p.y + d.x * g);
            right[i] = new PointF(p.x + d.y * g, p.y - d.x * g);
        }
        // The gravel bed.
        path.reset();
        for (int i = 0; i <= n; i++) {
            final PointF a = widen(left[i], right[i], 1.35f, true);
            if (i == 0) path.moveTo(a.x, a.y); else path.lineTo(a.x, a.y);
        }
        for (int i = n; i >= 0; i--) {
            final PointF a = widen(left[i], right[i], 1.35f, false);
            path.lineTo(a.x, a.y);
        }
        path.close();
        fill.setColor(0xFFC2AE86);
        c.drawPath(path, fill);
        // Sleepers, closer together into the distance.
        line.setColor(0xFF7E5332);
        for (float t = 0.02f; t < 1f; t += 0.035f * (1.15f - t * 0.75f)) {
            final int i = Math.min(n, Math.round(t * n));
            final PointF a = widen(left[i], right[i], 1.2f, true), z = widen(left[i], right[i], 1.2f, false);
            line.setStrokeWidth(Math.max(1f, lerp(g0, g1, t) * 0.11f));
            c.drawLine(a.x, a.y, z.x, z.y, line);
        }
        // The rails.
        line.setColor(0xFF545A63);
        for (int i = 0; i < n; i++) {
            line.setStrokeWidth(Math.max(1f, lerp(g0, g1, i / (float) n) * 0.07f));
            c.drawLine(left[i].x, left[i].y, left[i + 1].x, left[i + 1].y, line);
            c.drawLine(right[i].x, right[i].y, right[i + 1].x, right[i + 1].y, line);
        }
    }

    private void tree(Canvas c, float w, float h, float u, boolean landscape) {
        final float x = w - (landscape ? 6 : 3) * u;
        fill.setColor(0xFF6B4A2E);
        path.reset();
        path.moveTo(x - 4 * u, h);
        path.cubicTo(x - 2 * u, h * 0.7f, x - 3 * u, h * 0.45f, x - 1 * u, h * 0.28f);
        path.lineTo(x + 3 * u, h * 0.28f);
        path.cubicTo(x + 2 * u, h * 0.5f, x + 4 * u, h * 0.75f, x + 6 * u, h);
        path.close();
        c.drawPath(path, fill);
        final float cy = h * 0.2f;
        final int[] greens = {0xFF2E6F31, 0xFF3C8A3C, 0xFF4FA048};
        final float[][] blobs = {{-14, 4, 13}, {-4, -6, 15}, {8, 2, 14}, {-20, -10, 9}, {2, 12, 10}, {-10, 14, 9}};
        for (int k = 0; k < greens.length; k++) {
            fill.setColor(greens[k]);
            for (float[] bl : blobs) {
                final float s = 1f - k * 0.18f;
                c.drawCircle(x + bl[0] * u - k * 1.5f * u, cy + bl[1] * u - k * 2f * u, bl[2] * u * s, fill);
            }
        }
    }

    // A toy-like steam engine seen from the front, `s` pixels a unit (about 10 units wide).
    private void engine(Canvas c, float x, float y, float s) {
        c.save();
        c.translate(x, y);
        c.scale(s, s);
        // Steam from the stack, drifting up and to the right.
        final float[][] puffs = {{0.6f, -10.5f, 1.5f, 235}, {2.2f, -12.8f, 2.1f, 215}, {4.8f, -14.6f, 2.7f, 190},
                                 {8.2f, -15.8f, 3.2f, 160}, {12.4f, -16.4f, 3.6f, 125}, {17f, -16.6f, 3.9f, 90}};
        for (float[] p : puffs) {
            fill.setColor(((int) p[3] << 24) | 0xFFFFFF);
            c.drawCircle(p[0], p[1], p[2], fill);
            c.drawCircle(p[0] + p[2] * 0.7f, p[1] + p[2] * 0.3f, p[2] * 0.7f, fill);
        }
        // The cab behind, wider than the boiler, with its windows and roof.
        fill.setColor(0xFF1F4D3A);
        c.drawRoundRect(new RectF(-5.4f, -6.2f, 5.4f, 2.0f), 0.8f, 0.8f, fill);
        fill.setColor(0xFF7A3B2A);
        c.drawRoundRect(new RectF(-6.0f, -7.2f, 6.0f, -5.8f), 0.7f, 0.7f, fill);
        fill.setColor(0xFFBFE6F5);
        c.drawRect(-5.0f, -5.0f, -3.9f, -3.0f, fill);
        c.drawRect(3.9f, -5.0f, 5.0f, -3.0f, fill);
        // The smokestack.
        path.reset();
        path.moveTo(-1.1f, -4.4f);
        path.lineTo(-1.8f, -8.4f);
        path.lineTo(1.8f, -8.4f);
        path.lineTo(1.1f, -4.4f);
        path.close();
        fill.setColor(0xFF2A2C33);
        c.drawPath(path, fill);
        fill.setColor(0xFFD2A94E);
        c.drawRoundRect(new RectF(-2.1f, -9.0f, 2.1f, -8.1f), 0.3f, 0.3f, fill);
        // The wheels.
        for (int side = -1; side <= 1; side += 2) {
            fill.setColor(0xFF2D2524);
            c.drawCircle(side * 3.8f, 3.4f, 1.7f, fill);
            line.setColor(0xFFD2A94E);
            line.setStrokeWidth(0.35f);
            c.drawCircle(side * 3.8f, 3.4f, 1.3f, line);
        }
        // The buffer beam and the cowcatcher.
        fill.setColor(0xFFB03C29);
        c.drawRoundRect(new RectF(-4.9f, 1.6f, 4.9f, 2.9f), 0.3f, 0.3f, fill);
        path.reset();
        path.moveTo(-3.4f, 2.9f);
        path.lineTo(3.4f, 2.9f);
        path.lineTo(2.1f, 5.3f);
        path.lineTo(-2.1f, 5.3f);
        path.close();
        fill.setColor(0xFFC8503A);
        c.drawPath(path, fill);
        line.setColor(0xFF8C2F20);
        line.setStrokeWidth(0.3f);
        for (int i = -2; i <= 2; i++) c.drawLine(i * 1.3f, 3.0f, i * 0.8f, 5.2f, line);
        // The boiler's round front, a brass rim with rivets, the headlamp in the middle.
        fill.setColor(0xFF2F7553);
        c.drawCircle(0, -1.2f, 4.3f, fill);
        line.setColor(0xFFD2A94E);
        line.setStrokeWidth(0.6f);
        c.drawCircle(0, -1.2f, 4.3f, line);
        fill.setColor(0xFF3E8F66);
        c.drawCircle(0, -1.2f, 3.0f, fill);
        fill.setColor(0xFFD2A94E);
        for (int i = 0; i < 10; i++) {
            final double a = i * Math.PI / 5;
            c.drawCircle((float) Math.cos(a) * 3.65f, -1.2f + (float) Math.sin(a) * 3.65f, 0.22f, fill);
        }
        fill.setShader(new RadialGradient(0, -1.2f, 3.2f, 0xC0FFF6C8, 0x00FFF6C8, Shader.TileMode.CLAMP));
        c.drawCircle(0, -1.2f, 3.2f, fill);
        fill.setShader(null);
        fill.setColor(0xFFFFF3B8);
        c.drawCircle(0, -1.2f, 1.25f, fill);
        line.setStrokeWidth(0.35f);
        c.drawCircle(0, -1.2f, 1.25f, line);
        c.restore();
    }

    private static PointF widen(PointF l, PointF r, float k, boolean leftSide) {
        final float mx = (l.x + r.x) / 2, my = (l.y + r.y) / 2;
        final PointF p = leftSide ? l : r;
        return new PointF(mx + (p.x - mx) * k, my + (p.y - my) * k);
    }

    private static PointF bezier(PointF a, PointF b, PointF c, float t) {
        final float m = 1 - t;
        return new PointF(m * m * a.x + 2 * m * t * b.x + t * t * c.x, m * m * a.y + 2 * m * t * b.y + t * t * c.y);
    }

    private static PointF tangent(PointF a, PointF b, PointF c, float t) {
        final float dx = 2 * (1 - t) * (b.x - a.x) + 2 * t * (c.x - b.x);
        final float dy = 2 * (1 - t) * (b.y - a.y) + 2 * t * (c.y - b.y);
        final float len = (float) Math.max(1e-3, Math.hypot(dx, dy));
        return new PointF(dx / len, dy / len);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter cf) {}
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
