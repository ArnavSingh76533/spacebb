package com.space.browser;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/**
 * Ultra-futuristic celestial construct: quantum planetary core, layered energy rings,
 * orbital telemetry telemetry arcs, holographic grid lines, and glowing constellation nodes.
 * Vector-only, zero network payloads or background loops.
 */
final class OrbitView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private Shader halo, planetHalo, planetShader, ringShader;

    OrbitView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float r = Math.min(w, h) * .23f, x = w * .52f, y = h * .49f;
        if (r <= 0) return;
        // Deep space quantum aura with iridescent cyan-purple glow
        halo = new RadialGradient(x, y, r * 2.8f,
            new int[]{0x663B2288, 0x3312386E, 0x1500F0FF, 0x000A0E1A},
            new float[]{0f, 0.45f, 0.75f, 1f}, Shader.TileMode.CLAMP);
        // Secondary concentrated planet atmosphere
        planetHalo = new RadialGradient(x, y, r * 1.45f,
            new int[]{0x9950E3C2, 0x55A259FF, 0x001B0E33},
            null, Shader.TileMode.CLAMP);
        // Hyper-futuristic metallic/cyber crystal sphere gradient
        planetShader = new LinearGradient(x - r, y - r, x + r, y + r,
            new int[]{0xFFFFFFFF, 0xFF9AE5FF, 0xFF7952F5, 0xFF1D1248},
            new float[]{0f, 0.25f, 0.68f, 1f}, Shader.TileMode.CLAMP);
        // Bioluminescent particle ring
        ringShader = new SweepGradient(x, y,
            new int[]{0xFF00F5D4, 0xFF7B2CBF, 0xFFFF007F, 0xFF00F5D4},
            null);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float r = Math.min(w, h) * .23f, x = w * .52f, y = h * .49f;
        Paint p = paint;

        // 1. Deep Space Atmospheric Nebula / Quantum Halo
        p.setStyle(Paint.Style.FILL);
        p.setAlpha(255);
        if (halo != null) {
            p.setShader(halo);
            c.drawCircle(x, y, r * 2.7f, p);
        }
        if (planetHalo != null) {
            p.setShader(planetHalo);
            c.drawCircle(x, y, r * 1.4f, p);
            p.setShader(null);
        }

        // 2. Futuristic Cybernetic Coordinate Grid & Targeting Reticle
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f);
        p.setColor(0x2863E6BE);
        // Outer telemetry radar ring with dashes
        p.setPathEffect(new DashPathEffect(new float[]{8, 14}, 0));
        c.drawCircle(x, y, r * 2.05f, p);
        p.setColor(0x1F8050E0);
        p.setPathEffect(new DashPathEffect(new float[]{4, 8}, 0));
        c.drawCircle(x, y, r * 1.6f, p);
        p.setPathEffect(null);

        // Crosshair ticks on outer ring
        p.setColor(0x4463E6BE);
        p.setStrokeWidth(1.5f);
        c.drawLine(x - r * 2.2f, y, x - r * 1.9f, y, p);
        c.drawLine(x + r * 1.9f, y, x + r * 2.2f, y, p);
        c.drawLine(x, y - r * 2.2f, x, y - r * 1.9f, p);
        c.drawLine(x, y + r * 1.9f, x, y + r * 2.2f, p);

        // 3. Elliptical Astrodynamic Orbital Bands (Angle -25 deg)
        c.save();
        c.rotate(-25, x, y);

        // Outer telemetry ellipse (upper half & full background track)
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f);
        p.setColor(0x356A5B9C);
        c.drawOval(x - r * 2.2f, y - r * .92f, x + r * 2.2f, y + r * .92f, p);

        // Main orbital rail with precision gradient stroke
        p.setStrokeWidth(1.6f);
        p.setColor(0x7500F5D4);
        c.drawOval(x - r * 1.8f, y - r * .75f, x + r * 1.8f, y + r * .75f, p);

        // Inner secondary resonance orbit
        p.setStrokeWidth(1.0f);
        p.setColor(0x40B388FF);
        p.setPathEffect(new DashPathEffect(new float[]{6, 6}, 0));
        c.drawOval(x - r * 1.4f, y - r * .58f, x + r * 1.4f, y + r * .58f, p);
        p.setPathEffect(null);
        c.restore();

        // 4. Futuristic Celestial Core (Planet Sphere)
        p.setStyle(Paint.Style.FILL);
        p.setAlpha(255);
        if (planetShader != null) {
            p.setShader(planetShader);
            c.drawCircle(x, y, r, p);
            p.setShader(null);
        }

        // Planetary specular limb lighting (curved cybernetic rim glow)
        c.save();
        path.reset();
        path.addCircle(x, y, r, Path.Direction.CW);
        c.clipPath(path);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.22f);
        p.setColor(0x40FFFFFF);
        c.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.95f, p);

        // Planetary cybernetic meridian lines
        p.setStrokeWidth(1.0f);
        p.setColor(0x2800F5D4);
        c.drawOval(x - r * 0.45f, y - r, x + r * 0.45f, y + r, p);
        c.drawOval(x - r * 0.85f, y - r, x + r * 0.85f, y + r, p);
        c.drawLine(x - r, y, x + r, y, p);
        c.restore();

        // 5. Forefront Glowing Equatorial Ring Arc (Over the planet)
        c.save();
        c.rotate(-25, x, y);
        p.setStyle(Paint.Style.STROKE);
        // Outer glowing halo of the foreground ring
        p.setStrokeWidth(r * .16f);
        p.setColor(0x447A5CFF);
        c.drawArc(x - r * 1.85f, y - r * .52f, x + r * 1.85f, y + r * .52f, 0, 180, false, p);

        // Bright neon primary foreground ring
        p.setStrokeWidth(r * .095f);
        p.setColor(0xFF00F5D4);
        c.drawArc(x - r * 1.8f, y - r * .50f, x + r * 1.8f, y + r * .50f, 0, 180, false, p);

        // Core white-hot laser crest line along the ring
        p.setStrokeWidth(1.8f);
        p.setColor(0xFFFFFFFF);
        c.drawArc(x - r * 1.8f, y - r * .50f, x + r * 1.8f, y + r * .50f, 10, 160, false, p);
        c.restore();

        // 6. Constellation Satellites, Quantum Nodes & Laser Crosses
        p.setStyle(Paint.Style.FILL);
        // Node 1: Top right beacon
        p.setColor(0xFF00F5D4);
        c.drawCircle(x + r * 1.5f, y - r * 1.25f, 3.5f, p);
        p.setColor(0x5500F5D4);
        c.drawCircle(x + r * 1.5f, y - r * 1.25f, 7f, p);

        // Node 2: Left mid beacon
        p.setColor(0xFFB594FF);
        c.drawCircle(x - r * 1.65f, y - r * .6f, 2.5f, p);
        p.setColor(0x44B594FF);
        c.drawCircle(x - r * 1.65f, y - r * .6f, 6f, p);

        // Node 3: Bottom left beacon
        p.setColor(0xFF70D6FF);
        c.drawCircle(x - r * 1.1f, y + r * 1.4f, 2.5f, p);

        // Node 4: Distant neon pulsar
        p.setColor(0xFFFF70A6);
        c.drawCircle(x + r * 1.85f, y + r * 0.7f, 2.0f, p);

        // Precision Telemetry Reticle Cross
        p.setStyle(Paint.Style.STROKE);
        p.setColor(0xFFE4D8FF);
        p.setStrokeWidth(1.8f);
        float sx = x + r * .8f, sy = y - r * 1.65f;
        c.drawLine(sx - 5, sy, sx + 5, sy, p);
        c.drawLine(sx, sy - 5, sx, sy + 5, p);
        p.setColor(0x5500F5D4);
        p.setStrokeWidth(1.0f);
        c.drawCircle(sx, sy, 4.0f, p);
    }
}
