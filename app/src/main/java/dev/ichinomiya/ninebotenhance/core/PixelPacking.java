package dev.ichinomiya.ninebotenhance.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class PixelPacking {
    /** Reads no last-row padding; some ImageReader buffers do not expose that padding. */
    public static void rgba(ByteBuffer input, int rowStride, int pixelStride, int width, int height, ByteBuffer output) {
        compose(input,rowStride,pixelStride,width,height,output,width,height,0xff000000);
    }
    /** Opaque top/right background, with every source pixel preserved at bottom-left. */
    public static void compose(ByteBuffer input,int rowStride,int pixelStride,int width,int height,ByteBuffer output,int frameWidth,int frameHeight,int color) {
        if(width<1||height<1||frameWidth<width||frameHeight<height||pixelStride<4||rowStride<(long)width*pixelStride
                ||(long)frameWidth*frameHeight*4>output.capacity())throw new IllegalArgumentException("Invalid canvas layout");
        BandColor.requireOpaque(color);
        int base=input.position();long end=(long)base+(long)(height-1)*rowStride+(long)(width-1)*pixelStride+4;
        if(end>input.limit())throw new IllegalArgumentException("Incomplete RGBA buffer");
        output.clear();int rgba=((color&0xffffff)<<8)|255,packed=output.order()==ByteOrder.BIG_ENDIAN?rgba:Integer.reverseBytes(rgba);
        int top=frameHeight-height;
        for(int i=0;i<frameWidth*top;i++)output.putInt(packed);
        for(int y=0;y<height;y++){
            int start=base+y*rowStride;
            if(pixelStride==4){ByteBuffer row=input.duplicate();row.position(start);row.limit(start+width*4);output.put(row);}
            else for(int x=0;x<width;x++)for(int c=0;c<4;c++)output.put(input.get(start+x*pixelStride+c));
            for(int x=width;x<frameWidth;x++)output.putInt(packed);
        }
        output.flip();
    }
    private PixelPacking() {}
}
