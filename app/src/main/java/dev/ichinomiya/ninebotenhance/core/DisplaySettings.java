package dev.ichinomiya.ninebotenhance.core;

/**
 * Output canvas and independent app buffer, placed at bottom-left without scaling. With {@code keepPhoneDpi} the buffer
 * still is virtualWidth x virtualHeight and {@code dpi} still defines the dp layout, but the display renders at the phone's
 * density with a proportionally larger logical size that the system scales back into the buffer: an app moving between
 * the phone and the virtual display then never sees a density change.
 */
public final class DisplaySettings {
    public final int width,height,virtualWidth,virtualHeight,dpi,backgroundColor,lightBackgroundColor;
    public final boolean keepPhoneDpi;
    /** Largest logical side WindowManager is asked for; beyond it the dp layout shrinks rather than the density drifting. */
    public static final int MAX_RENDER_SIDE=4096;
    /** Logical size and density the display renders at while the RGBA buffer stays virtualWidth x virtualHeight. */
    public record RenderPlan(int width,int height,int dpi){}
    public static final int DEFAULT_WIDTH=848,DEFAULT_HEIGHT=480,DEFAULT_VIRTUAL_WIDTH=640,DEFAULT_VIRTUAL_HEIGHT=440,DEFAULT_DPI=160;
    public static final int DEFAULT_BACKGROUND_COLOR=0xff242424,DEFAULT_LIGHT_BACKGROUND_COLOR=0xffe6eaee,LAYOUT_VERSION=2;
    public static final boolean DEFAULT_KEEP_PHONE_DPI=true;
    public DisplaySettings(int width,int height,int dpi){this(width,height,width,height,dpi,DEFAULT_BACKGROUND_COLOR);}
    public DisplaySettings(int width,int height,int virtualWidth,int virtualHeight,int dpi,int backgroundColor){this(width,height,virtualWidth,virtualHeight,dpi,backgroundColor,DEFAULT_KEEP_PHONE_DPI);}
    public DisplaySettings(int width,int height,int virtualWidth,int virtualHeight,int dpi,int backgroundColor,boolean keepPhoneDpi){this(width,height,virtualWidth,virtualHeight,dpi,backgroundColor,keepPhoneDpi,DEFAULT_LIGHT_BACKGROUND_COLOR);}
    /** {@code backgroundColor} fills the frame around the app in the dark dashboard theme, {@code lightBackgroundColor} in the light one. */
    public DisplaySettings(int width,int height,int virtualWidth,int virtualHeight,int dpi,int backgroundColor,boolean keepPhoneDpi,int lightBackgroundColor){
        if(width<320||height<320||width>1920||height>1920||(width&1)!=0||(height&1)!=0||(long)width*height>2073600)
            throw new IllegalArgumentException("整帧宽高需为 320–1920 的偶数，总像素不超过 1920×1080。");
        if(virtualWidth<240||virtualHeight<240||virtualWidth>width||virtualHeight>height||(virtualWidth&1)!=0||(virtualHeight&1)!=0)
            throw new IllegalArgumentException("虚拟屏宽高需为不小于 240 的偶数，且不能超过整帧宽高。");
        if(dpi<100||dpi>480||Math.min(virtualWidth,virtualHeight)*160L/dpi<160)
            throw new IllegalArgumentException("DPI 为 100–480，虚拟屏最短边至少 160 dp。");
        BandColor.requireOpaque(backgroundColor);BandColor.requireOpaque(lightBackgroundColor);
        this.width=width;this.height=height;this.virtualWidth=virtualWidth;this.virtualHeight=virtualHeight;this.dpi=dpi;this.backgroundColor=backgroundColor;this.keepPhoneDpi=keepPhoneDpi;this.lightBackgroundColor=lightBackgroundColor;
    }
    /** Same dp layout at the phone's density; null when the option is off, the density is unknown or already equal. */
    public RenderPlan renderPlan(int phoneDpi){
        if(!keepPhoneDpi||phoneDpi<=0||phoneDpi==dpi)return null;
        double scale=phoneDpi/(double)dpi,w=virtualWidth*scale,h=virtualHeight*scale;
        double fit=Math.min(1,Math.min(MAX_RENDER_SIDE/w,MAX_RENDER_SIDE/h));
        return new RenderPlan(even(w*fit),even(h*fit),phoneDpi);
    }
    private static int even(double value){return (int)Math.round(value/2)*2;}
    public static DisplaySettings defaults(){return new DisplaySettings(DEFAULT_WIDTH,DEFAULT_HEIGHT,DEFAULT_VIRTUAL_WIDTH,DEFAULT_VIRTUAL_HEIGHT,DEFAULT_DPI,DEFAULT_BACKGROUND_COLOR,DEFAULT_KEEP_PHONE_DPI);}
    @FunctionalInterface public interface IntSetting{int get(String key,int fallback);}
    public static DisplaySettings read(IntSetting values){
        if(values.get("layout_version",0)<LAYOUT_VERSION){
            // Old sizes described app content plus top_inset. Preserve that output extent.
            int width=values.get("width",DEFAULT_WIDTH);
            long oldHeight=(long)values.get("height",440)+values.get("top_inset",40);
            if(oldHeight<320||oldHeight>1920)throw new IllegalArgumentException("旧版画面尺寸无效，请重新设置");
            int height=((int)oldHeight+1)&~1;
            return new DisplaySettings(width,height,Math.max(240,(int)(width*640L/848)&~1),Math.max(240,(int)(height*440L/480)&~1),
                    values.get("dpi",DEFAULT_DPI),values.get("top_color",DEFAULT_BACKGROUND_COLOR));
        }
        return new DisplaySettings(values.get("width",DEFAULT_WIDTH),values.get("height",DEFAULT_HEIGHT),
                values.get("virtual_width",DEFAULT_VIRTUAL_WIDTH),values.get("virtual_height",DEFAULT_VIRTUAL_HEIGHT),
                values.get("dpi",DEFAULT_DPI),values.get("background_color",DEFAULT_BACKGROUND_COLOR),values.get("keep_phone_dpi",DEFAULT_KEEP_PHONE_DPI?1:0)!=0,values.get("light_background_color",DEFAULT_LIGHT_BACKGROUND_COLOR));
    }
    /** Same settings inside the frame the cast configuration prescribes; the virtual display shrinks to fit, an unusable frame is ignored. */
    public DisplaySettings withFrame(int frameWidth,int frameHeight){
        int w=frameWidth&~1,h=frameHeight&~1;
        if(w==width&&h==height)return this;
        try { return new DisplaySettings(w,h,Math.min(virtualWidth,w)&~1,Math.min(virtualHeight,h)&~1,dpi,backgroundColor,keepPhoneDpi,lightBackgroundColor); }
        catch(IllegalArgumentException e) { return this; }
    }
    public int contentTop(){return height-virtualHeight;}
    public int background(boolean dark){return dark?backgroundColor:lightBackgroundColor;}
    public String label(){return "整帧 "+width+" × "+height+"，虚拟屏 "+virtualWidth+" × "+virtualHeight+"，"+dpi+" DPI"+(keepPhoneDpi?"（保持手机 DPI）":"")+"，背景 "+BandColor.hex(backgroundColor)+" / "+BandColor.hex(lightBackgroundColor);}
    public static String shellQuote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
}
