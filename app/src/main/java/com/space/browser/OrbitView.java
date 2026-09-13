package com.space.browser;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Static vector art: no image payloads, animation loops or network requests. */
final class OrbitView extends View {
    OrbitView(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    @Override protected void onDraw(Canvas c) {
        float w=getWidth(), h=getHeight(), r=Math.min(w,h)*.23f, x=w*.52f,y=h*.49f;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(x,y,r*2.4f,new int[]{0x443A2D78,0x00131124},null,Shader.TileMode.CLAMP)); c.drawCircle(x,y,r*2.4f,p);p.setShader(null);
        c.save();c.rotate(-25,x,y);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0x406F6590); c.drawOval(x-r*1.8f,y-r*.75f,x+r*1.8f,y+r*.75f,p);c.restore();
        p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(x-r,y-r,x+r,y+r,new int[]{0xFFE5DCFF,0xFF9E81F1,0xFF433471},null,Shader.TileMode.CLAMP));c.drawCircle(x,y,r,p);p.setShader(null);
        c.save();c.rotate(-25,x,y);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(r*.095f);p.setColor(0xFFC8B8FF);c.drawArc(x-r*1.8f,y-r*.50f,x+r*1.8f,y+r*.50f,0,180,false,p);c.restore();
        p.setStyle(Paint.Style.FILL);p.setColor(0xFFE4D8FF); c.drawCircle(x+r*1.5f,y-r*1.25f,3,p);c.drawCircle(x-r*1.65f,y-r*.6f,2,p);c.drawCircle(x-r*1.1f,y+r*1.4f,2,p);
        p.setStrokeWidth(1.5f);float sx=x+r*.8f,sy=y-r*1.65f;c.drawLine(sx-4,sy,sx+4,sy,p);c.drawLine(sx,sy-4,sx,sy+4,p);
    }
}
