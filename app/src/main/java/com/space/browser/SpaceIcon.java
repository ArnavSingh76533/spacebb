package com.space.browser;

import android.content.Context;
import android.graphics.*;
import android.view.View;

final class SpaceIcon extends View {
    private final Paint drawPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path glyphPath = new Path();
    private final String name;
    private final int color;
    SpaceIcon(Context context, String name, int color) { super(context); this.name=name; this.color=color; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        float scale = Math.min(getWidth(), getHeight()) / 24f;
        canvas.translate((getWidth()-24*scale)/2, (getHeight()-24*scale)/2); canvas.scale(scale,scale);
        Paint p = drawPaint; p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.7f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        switch(name) {
            case "back": path(canvas,p,15,5,8,12,15,19); break;
            case "next": case "arrow": path(canvas,p,9,5,16,12,9,19); break;
            case "close": path(canvas,p,6,6,18,18); path(canvas,p,18,6,6,18); break;
            case "plus": path(canvas,p,12,5,12,19); path(canvas,p,5,12,19,12); break;
            case "search": canvas.drawCircle(10.5f,10.5f,6.5f,p); path(canvas,p,16,16,21,21); break;
            case "home": path(canvas,p,3,10,12,3,21,10); path(canvas,p,6,9,6,21,10,21,10,15,14,15,14,21,18,21,18,9); break;
            case "tabs": canvas.drawRoundRect(4,4,20,20,4,4,p); path(canvas,p,9,9,15,9,15,15,9,15,9,9); break;
            case "menu": for (int y=6;y<=18;y+=6) { path(canvas,p,5,y,19,y); } break;
            case "shield": path(canvas,p,12,2,21,6,20,14,17,19,12,22,7,19,4,14,3,6,12,2); path(canvas,p,8,12,11,15,16,9); break;
            case "lock": canvas.drawRoundRect(5,10,19,21,3,3,p); canvas.drawArc(8,2,16,16,180,180,false,p); path(canvas,p,12,14,12,17); break;
            case "globe": canvas.drawCircle(12,12,9,p); canvas.drawOval(8,3,16,21,p); path(canvas,p,3,12,21,12); break;
            case "star": path(canvas,p,12,2,15,8,22,9,17,14,18,21,12,18,6,21,7,14,2,9,9,8,12,2); break;
            case "download": path(canvas,p,12,3,12,15,7,10); path(canvas,p,12,15,17,10); path(canvas,p,4,16,4,21,20,21,20,16); break;
            case "code": path(canvas,p,7,6,1,12,7,18); path(canvas,p,17,6,23,12,17,18); path(canvas,p,14,3,10,21); break;
            case "refresh": canvas.drawArc(4,4,20,20,35,295,false,p); path(canvas,p,20,3,20,9,14,9); break;
            case "history": canvas.drawCircle(12,12,9,p); path(canvas,p,12,6,12,12,16,14); break;
            case "share": canvas.drawCircle(18,4,3,p); canvas.drawCircle(5,12,3,p); canvas.drawCircle(18,20,3,p); path(canvas,p,8,10,15,5); path(canvas,p,8,14,15,19); break;
            case "reader": canvas.drawRoundRect(4,3,20,21,2,2,p); path(canvas,p,8,8,16,8); path(canvas,p,8,12,16,12); path(canvas,p,8,16,13,16); break;
            case "moon": path(canvas,p,18,3,14,4,11,8,11,13,14,17,19,18,22,16); canvas.drawArc(3,3,21,21,15,275,false,p); break;
            case "settings": canvas.drawCircle(12,12,4,p); canvas.drawCircle(12,12,9,p); for(int i=0;i<8;i++){canvas.save();canvas.rotate(i*45,12,12);path(canvas,p,12,1,12,4);canvas.restore();} break;
            default: canvas.drawCircle(12,12,9,p); path(canvas,p,12,7,12,13); canvas.drawPoint(12,17,p);
        }
        canvas.restore();
    }
    private void path(Canvas c, Paint p, float... pts) { Path path=glyphPath; path.reset(); path.moveTo(pts[0],pts[1]); for(int i=2;i<pts.length;i+=2)path.lineTo(pts[i],pts[i+1]); c.drawPath(path,p); }
}
