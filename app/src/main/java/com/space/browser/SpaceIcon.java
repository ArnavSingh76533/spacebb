package com.space.browser;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/**
 * Precision-engineered futuristic cyber glyphs:
 * Clean vector strokes, beveled geometry, and sleek sci-fi aesthetics.
 */
final class SpaceIcon extends View {
    private final Paint drawPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path glyphPath = new Path();
    private final String name;
    private final int color;

    SpaceIcon(Context context, String name, int color) {
        super(context);
        this.name = name;
        this.color = color;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        float scale = Math.min(getWidth(), getHeight()) / 24f;
        canvas.translate((getWidth() - 24 * scale) / 2, (getHeight() - 24 * scale) / 2);
        canvas.scale(scale, scale);

        // Subtle ambient neon glow
        Paint gp = glowPaint;
        gp.setColor(color);
        gp.setAlpha(35);
        gp.setStyle(Paint.Style.STROKE);
        gp.setStrokeWidth(3.4f);
        gp.setStrokeCap(Paint.Cap.ROUND);
        gp.setStrokeJoin(Paint.Join.ROUND);

        // Main crisp foreground stroke
        Paint p = drawPaint;
        p.setColor(color);
        p.setAlpha(255);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.75f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);

        drawGlyph(canvas, gp);
        drawGlyph(canvas, p);

        canvas.restore();
    }

    private void drawGlyph(Canvas canvas, Paint p) {
        switch(name) {
            case "back":
                path(canvas, p, 15, 4, 7, 12, 15, 20);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(17, 12, 1.2f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            case "next": case "arrow":
                path(canvas, p, 9, 4, 17, 12, 9, 20);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(7, 12, 1.2f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            case "close":
                path(canvas, p, 6, 6, 18, 18);
                path(canvas, p, 18, 6, 6, 18);
                break;
            case "plus":
                path(canvas, p, 12, 4, 12, 20);
                path(canvas, p, 4, 12, 20, 12);
                break;
            case "search":
                // Futuristic hexagonal lens search scanner
                path(canvas, p, 11, 4, 16, 7, 16, 13, 11, 16, 6, 13, 6, 7, 11, 4);
                path(canvas, p, 15, 14.5f, 21, 20.5f);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(11, 10, 1.4f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            case "home":
                // Angular cyber-citadel silhouette
                path(canvas, p, 3, 11, 12, 3, 21, 11);
                path(canvas, p, 5, 10, 5, 21, 19, 21, 19, 10);
                path(canvas, p, 9, 21, 9, 13, 15, 13, 15, 21);
                break;
            case "tabs":
                // Layered holographic floating viewports
                canvas.drawRoundRect(6, 6, 21, 21, 4, 4, p);
                path(canvas, p, 3, 16, 3, 4, 16, 4);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(13.5f, 13.5f, 1.5f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            case "menu":
                // Cyber telemetry bars with right-aligned status dots
                path(canvas, p, 4, 7, 15, 7);
                path(canvas, p, 4, 12, 20, 12);
                path(canvas, p, 4, 17, 13, 17);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(19, 7, 1.3f, p);
                canvas.drawCircle(17, 17, 1.3f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            case "shield":
                // High-tech quantum energy aegis
                path(canvas, p, 12, 2, 21, 5.5f, 20, 14, 16.5f, 19, 12, 22, 7.5f, 19, 4, 14, 3, 5.5f, 12, 2);
                // Center holographic shield check
                path(canvas, p, 8, 12, 11, 15, 16, 8.5f);
                break;
            case "lock":
                // Cyber security node
                canvas.drawRoundRect(5, 10, 19, 21, 3, 3, p);
                canvas.drawArc(8, 3, 16, 17, 180, 180, false, p);
                path(canvas, p, 12, 13.5f, 12, 17.5f);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(12, 13.5f, 1.3f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            case "globe":
                // Holographic planetary coordinate sphere
                canvas.drawCircle(12, 12, 9, p);
                canvas.drawOval(7.5f, 3, 16.5f, 21, p);
                path(canvas, p, 3, 12, 21, 12);
                break;
            case "star":
                // Futuristic 8-point stellar flare
                path(canvas, p, 12, 2, 14.5f, 8.5f, 21, 9.5f, 16.5f, 14, 18, 21, 12, 17.5f, 6, 21, 7.5f, 14, 3, 9.5f, 9.5f, 8.5f, 12, 2);
                break;
            case "download":
                // Futuristic particle injection arrow & receiving dock
                path(canvas, p, 12, 3, 12, 15);
                path(canvas, p, 7.5f, 10.5f, 12, 15, 16.5f, 10.5f);
                path(canvas, p, 4, 16.5f, 4, 21, 20, 21, 20, 16.5f);
                break;
            case "code":
                // Cyberpunk command terminal brackets
                path(canvas, p, 7, 6, 2, 12, 7, 18);
                path(canvas, p, 17, 6, 22, 12, 17, 18);
                path(canvas, p, 14, 4, 10, 20);
                break;
            case "refresh":
                // High-speed orbital reload turbine
                canvas.drawArc(4, 4, 20, 20, 30, 290, false, p);
                path(canvas, p, 20.5f, 3, 20.5f, 9.5f, 14, 9.5f);
                break;
            case "history":
                // Tachyon chrono-clock dial
                canvas.drawCircle(12, 12, 9, p);
                path(canvas, p, 12, 6.5f, 12, 12, 16.5f, 14.5f);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(12, 12, 1.5f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            case "share":
                // Quantum data distribution node
                canvas.drawCircle(18, 5, 2.8f, p);
                canvas.drawCircle(5, 12, 2.8f, p);
                canvas.drawCircle(18, 19, 2.8f, p);
                path(canvas, p, 7.5f, 10.5f, 15.5f, 6.5f);
                path(canvas, p, 7.5f, 13.5f, 15.5f, 17.5f);
                break;
            case "reader":
                // Cybernetic HUD reader visor
                canvas.drawRoundRect(4, 3, 20, 21, 3, 3, p);
                path(canvas, p, 8, 8, 16, 8);
                path(canvas, p, 8, 12, 16, 12);
                path(canvas, p, 8, 16, 13, 16);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(15.5f, 16, 1.1f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            case "moon":
                // Orbital eclipse crescent with satellite sensor
                path(canvas, p, 18, 3.5f, 13.5f, 5, 10.5f, 9, 10.5f, 15, 14, 18.5f, 19, 19.5f, 21, 18);
                canvas.drawArc(3, 3, 21, 21, 25, 260, false, p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(17, 9, 1.2f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            case "settings":
                // Reactor core gear
                canvas.drawCircle(12, 12, 4.2f, p);
                canvas.drawCircle(12, 12, 9, p);
                for (int i = 0; i < 8; i++) {
                    canvas.save();
                    canvas.rotate(i * 45, 12, 12);
                    path(canvas, p, 12, 1.5f, 12, 4.5f);
                    canvas.restore();
                }
                break;
            default:
                canvas.drawCircle(12, 12, 9, p);
                path(canvas, p, 12, 7, 12, 13);
                canvas.drawPoint(12, 17, p);
        }
    }

    private void path(Canvas c, Paint p, float... pts) {
        Path path = glyphPath;
        path.reset();
        path.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) path.lineTo(pts[i], pts[i + 1]);
        c.drawPath(path, p);
    }
}
