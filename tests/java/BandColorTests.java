import dev.ichinomiya.ninebotenhance.core.*;
import java.nio.ByteBuffer;
import java.util.Map;

public final class BandColorTests {
    static void run(){
        CoreTests.check(BandColor.parse("#242424")==DisplaySettings.DEFAULT_BACKGROUND_COLOR,"default background is opaque near-black grey");
        CoreTests.check(BandColor.parse(" 12a0E3 ")==0xff12a0e3&&BandColor.hex(0xff12a0e3).equals("#12A0E3"),"custom RGB accepts mixed case and round-trips");
        for(String invalid:new String[]{null,"#123","#ff242424","#GG2424"})CoreTests.rejects(()->BandColor.parse(invalid),"invalid colour rejected");
        CoreTests.rejects(()->new DisplaySettings(848,480,640,440,160,0x80242424),"transparency rejected before allocation");
        DisplaySettings d=DisplaySettings.read((key,fallback)->fallback);
        CoreTests.check(d.width==848&&d.height==480&&d.virtualWidth==640&&d.virtualHeight==440&&d.dpi==160,"empty settings use calibrated frame and virtual display defaults");
        DisplaySettings legacy=DisplaySettings.read(Map.of("width",848,"height",440,"dpi",160,"top_inset",40,"top_color",0xff12a0e3)::getOrDefault);
        CoreTests.check(legacy.width==848&&legacy.height==480&&legacy.virtualWidth==640&&legacy.virtualHeight==440&&legacy.backgroundColor==0xff12a0e3,"legacy default migrates to split layout, preserving output size and custom colour");
        DisplaySettings odd=DisplaySettings.read(Map.of("width",900,"height",500,"dpi",160,"top_inset",25)::getOrDefault);
        CoreTests.check(odd.width==900&&odd.height==526&&odd.virtualWidth==678&&odd.virtualHeight==482,"odd legacy total height rounds up without losing extent");
        DisplaySettings zero=DisplaySettings.read(Map.of("width",860,"height",480,"dpi",200,"top_inset",0)::getOrDefault);
        CoreTests.check(zero.height==480&&zero.dpi==200,"explicit zero legacy inset does not grow the output");
        DisplaySettings saved=DisplaySettings.read(Map.of("layout_version",2,"width",900,"height",500,"virtual_width",700,"virtual_height",400,"dpi",160,"background_color",0xff12a0e3,"top_inset",40)::getOrDefault);
        CoreTests.check(saved.width==900&&saved.height==500&&saved.virtualWidth==700&&saved.virtualHeight==400&&saved.backgroundColor==0xff12a0e3,"new settings preserve explicit independent dimensions and ignore legacy inset");
        CoreTests.check(saved.label().contains("700 × 400")&&saved.label().contains("#12A0E3"),"diagnostics report both dimensions and background");
        ByteBuffer app=ByteBuffer.allocate(d.virtualWidth*d.virtualHeight*4);app.putInt(app.capacity()-4,0x102030ff);
        ByteBuffer output=ByteBuffer.allocate(d.width*d.height*4);
        PixelPacking.compose(app,d.virtualWidth*4,4,d.virtualWidth,d.virtualHeight,output,d.width,d.height,d.backgroundColor);
        CoreTests.check(output.getInt(0)==0x242424ff&&output.getInt((d.width*40-1)*4)==0x242424ff,"top 40 rows are full-width background");
        CoreTests.check(output.getInt(((d.height-1)*d.width+d.virtualWidth-1)*4)==0x102030ff,"last virtual-display pixel survives at bottom-left content corner");
        CoreTests.check(output.limit()==848*480*4&&output.getInt(output.limit()-4)==0x242424ff,"right background fills all the way to the bottom of the fixed canvas");
    }
}
