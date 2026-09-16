package dev.ichinomiya.ninebotenhance.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** Two thumbs on one track with the span between them highlighted. Integer values; the thumbs cannot cross. */
public final class RangeBar extends View {
    public interface Listener{void changed(int low,int high);}
    private final int min,max;private int low,high,active=-1;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final int accent,track;private final float radius,pad;private Listener listener;
    public RangeBar(Context context,MirrorUi theme,int min,int max,int low,int high){
        super(context);this.min=min;this.max=Math.max(min+1,max);this.low=clamp(low);this.high=Math.max(this.low,clamp(high));
        accent=theme.accent;track=theme.input;radius=MirrorUi.dp(context,9);pad=radius+MirrorUi.dp(context,4);setMinimumHeight(MirrorUi.dp(context,44));
    }
    public void setListener(Listener value){listener=value;}
    public int low(){return low;}
    public int high(){return high;}
    @Override protected void onMeasure(int widthSpec,int heightSpec){setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(),widthSpec),resolveSize(getSuggestedMinimumHeight(),heightSpec));}
    @Override protected void onDraw(Canvas canvas){
        float y=getHeight()/2f,lx=x(low),hx=x(high);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(MirrorUi.dp(getContext(),3));
        paint.setColor(track);canvas.drawLine(pad,y,getWidth()-pad,y,paint);
        paint.setColor(accent);canvas.drawLine(lx,y,hx,y,paint);
        paint.setStyle(Paint.Style.FILL);canvas.drawCircle(lx,y,radius,paint);canvas.drawCircle(hx,y,radius,paint);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:{
                if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
                float lx=x(low),hx=x(high),px=e.getX();
                // The nearer thumb follows the finger; on top of each other, the side of the touch decides.
                active=lx==hx?(px<lx?0:1):Math.abs(px-lx)<=Math.abs(px-hx)?0:1;
                move(px);return true;}
            case MotionEvent.ACTION_MOVE:move(e.getX());return true;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:active=-1;if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;
            default:return super.onTouchEvent(e);
        }
    }
    @Override public boolean performClick(){return super.performClick();}
    private void move(float px){
        float span=Math.max(1,getWidth()-2*pad);int value=min+Math.round(Math.max(0,Math.min(1,(px-pad)/span))*(max-min));
        if(active==0)low=Math.min(value,high);else if(active==1)high=Math.max(value,low);else return;
        invalidate();if(listener!=null)listener.changed(low,high);
    }
    private float x(int value){return pad+(value-min)/(float)(max-min)*(getWidth()-2*pad);}
    private int clamp(int value){return Math.max(min,Math.min(max,value));}
}
