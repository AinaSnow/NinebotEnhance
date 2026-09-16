package dev.ichinomiya.ninebotenhance.core;

import java.util.Objects;

/**
 * Eased motion of one sidebar card between layouts. Position and size glide over MOVE_MS; a card that appears
 * fades in while rising into place and a card that vanishes fades out while sinking. The very first target settles
 * at once so a fresh session shows its cards immediately.
 */
public final class CardMotion {
    public static final long MOVE_MS=300,FADE_MS=220;
    public static final float SLIDE=10;
    private SidebarLayout.Box from,to,resting;private float alphaFrom,alphaTo;private long started=-1;private boolean visible;
    /** Set the layout target for this card; null hides it. Repeating the current target changes nothing. */
    public void target(SidebarLayout.Box box,long now){
        boolean show=box!=null;
        if(started<0){from=to=resting=box;visible=show;alphaFrom=alphaTo=show?1:0;started=now;return;}
        if(show==visible&&Objects.equals(box,resting))return;
        float alphaNow=alpha(now);SidebarLayout.Box boxNow=box(now);
        if(show&&!visible){from=boxNow!=null&&alphaNow>0?boxNow:shift(box,SLIDE);to=box;alphaFrom=alphaNow;alphaTo=1;}
        else if(!show){from=boxNow;to=shift(boxNow,SLIDE);alphaFrom=alphaNow;alphaTo=0;}
        else{from=boxNow;to=box;alphaFrom=alphaNow;alphaTo=1;}
        resting=box;visible=show;started=now;
    }
    /** Where the card is drawn right now; null only before the first target. */
    public SidebarLayout.Box box(long now){
        if(from==null||to==null)return to==null?from:to;
        float t=ease(progress(now,MOVE_MS));
        return new SidebarLayout.Box(from.left()+(to.left()-from.left())*t,from.top()+(to.top()-from.top())*t,from.right()+(to.right()-from.right())*t,from.bottom()+(to.bottom()-from.bottom())*t);
    }
    public float alpha(long now){return alphaFrom+(alphaTo-alphaFrom)*ease(progress(now,FADE_MS));}
    public boolean animating(long now){return started>=0&&now-started<Math.max(MOVE_MS,FADE_MS)&&(alphaFrom!=alphaTo||!Objects.equals(from,to));}
    /** Whether anything should be painted: the card is wanted, or it is still fading out. */
    public boolean drawn(long now){return visible||alpha(now)>0.01f;}
    public SidebarLayout.Box resting(){return resting;}
    private float progress(long now,long duration){return started<0?1:Math.max(0,Math.min(1,(now-started)/(float)duration));}
    /** Cubic ease-out shared by every HUD motion. */
    public static float ease(float t){float u=1-t;return 1-u*u*u;}
    private static SidebarLayout.Box shift(SidebarLayout.Box box,float dy){return box==null?null:box.shifted(dy);}
}
