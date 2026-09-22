package dev.ichinomiya.ninebotenhance.notification;

import android.graphics.*;
import android.text.TextPaint;
import dev.ichinomiya.ninebotenhance.core.BmsCard;
import dev.ichinomiya.ninebotenhance.core.BmsData;
import dev.ichinomiya.ninebotenhance.core.HudPalette;
import java.util.List;

/**
 * Draws the BMS card in reference units at y = 0: one line per non-empty layout row, the fields of a row sharing its width in
 * equal slots (label, value, unit), an overlong row squeezed rather than clipped. Without a fresh reading the card shows "BMS"
 * and "未连接" on one line. Shared by the dashboard HUD and the layout preview.
 */
public final class BmsCardPainter {
    private static final float INSET=8,LABEL=11,VALUE=14,UNIT=9,RADIUS=11;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics metrics=new Paint.FontMetrics();
    public float height(BmsCard.Layout layout,boolean connected){return connected?layout.height():BmsCard.ROW_HEIGHT;}
    public void draw(Canvas c,HudPalette p,float left,float width,BmsCard.Layout layout,BmsData data,boolean connected){
        float height=height(layout,connected);
        paint.setStyle(Paint.Style.FILL);paint.setColor(p.surface());c.drawRoundRect(left,0,left+width,height,RADIUS,RADIUS,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(p.border());c.drawRoundRect(left+.5f,.5f,left+width-.5f,height-.5f,RADIUS,RADIUS,paint);
        paint.setStyle(Paint.Style.FILL);
        if(!connected){
            float centerY=BmsCard.ROW_HEIGHT/2;
            centerLine(c,"BMS",left+INSET+2,centerY,13,p.label(),false);
            centerLine(c,"未连接",left+INSET+2+36,centerY,13,p.unit(),false);
            return;
        }
        List<List<Integer>> rows=layout.visibleRows();
        if(rows.isEmpty()){centerLine(c,"BMS",left+INSET+2,BmsCard.ROW_HEIGHT/2,13,p.label(),false);return;}
        float available=width-2*INSET;
        for(int r=0;r<rows.size();r++){
            List<Integer> row=rows.get(r);float centerY=r*BmsCard.ROW_HEIGHT+BmsCard.ROW_HEIGHT/2,slot=available/row.size();
            for(int i=0;i<row.size();i++){
                int field=row.get(i);String label=BmsCard.label(field),value=BmsCard.value(field,data),unit=BmsCard.unit(field);
                float needed=measure(label,LABEL,false)+3+measure(value,VALUE,true)+2+measure(unit,UNIT,false);
                float x=left+INSET+i*slot;int saved=c.save();
                if(needed>slot-2){c.translate(x,0);c.scale((slot-2)/needed,1);c.translate(-x,0);}
                centerLine(c,label,x,centerY,LABEL,p.label(),false);x+=measure(label,LABEL,false)+3;
                centerLine(c,value,x,centerY,VALUE,p.text(),true);x+=measure(value,VALUE,true)+2;
                centerLine(c,unit,x,centerY,UNIT,p.unit(),false);
                c.restoreToCount(saved);
            }
        }
    }
    private void font(float size,boolean bold){text.setTextSize(size);text.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));}
    private float measure(String s,float size,boolean bold){font(size,bold);return text.measureText(s);}
    private void centerLine(Canvas c,String s,float x,float centerY,float size,int color,boolean bold){font(size,bold);text.getFontMetrics(metrics);text.setColor(color);c.drawText(s,x,centerY-(metrics.ascent+metrics.descent)/2,text);}
}
