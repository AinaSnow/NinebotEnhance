import dev.ichinomiya.ninebotenhance.core.*;
import dev.ichinomiya.ninebotenhance.core.SidebarLayout.Box;
import java.util.*;

final class DashboardLayoutTests {
    private static void check(boolean result,String message){CoreTests.check(result,message);}
    /** Shape of the M5P TFT configuration: 848 x 480, the left navigation column and the dashboard's own instrument card. */
    static final String M5P="{\n \"version\": \"1.0.0\", \"naviType\": 1, \"channel\": 1, \"dimensionWidth\": 848.0, \"dimensionHeight\": 480.0,\n"
            +" \"codecWidth\": 848.0, \"codecHeight\": 480.0, \"codecType\": 1, \"frameRate\": 20, \"bitRate\": 1500000,\n"
            +" \"layout\": {\n  \"baseMap\": {\"style\": \"d2x_1\", \"uiStyle\": {\"mapType\": 0, \"userImageType\": 2, \"screenAnchor\": {\"x\": 0.5, \"y\": 0.8},\n"
            +"    \"boundRects\": [{\"width\": 272.0, \"height\": 480.0, \"x\": 0, \"y\": 0}, {\"width\": 200.0, \"height\": 214.0, \"x\": 641.0, \"y\": 44.0}]},\n"
            +"    \"frame\": {\"width\": 848.0, \"height\": 480.0, \"x\": 0, \"y\": 0}},\n"
            +"  \"mapInfo\": {\"progressLine\": {\"frame\": {\"width\": 272, \"height\": 12, \"x\": 0, \"y\": 103}}, \"style\": \"d2x_1\",\n"
            +"    \"frame\": {\"width\": 272.0, \"height\": 354.0, \"x\": 8, \"y\": 44}}\n },\n \"temp_key\": [\"1\", \"2\"]\n}";
    static void run() {
        Object root=Json.parse("{\"a\":[1,2.5,-3e2,true,false,null,\"s\\\"\\n\\u4e2d\"],\"b\":{\"c\":{}},\"d\":[]}");
        Map<String,Object> map=Json.object(root);
        List<Object> a=Json.array(map.get("a"));
        check(a.size()==7&&Json.number(a.get(0),0)==1&&Json.number(a.get(1),0)==2.5&&Json.number(a.get(2),0)==-300&&a.get(3)==Boolean.TRUE&&a.get(4)==Boolean.FALSE&&a.get(5)==null&&"s\"\n中".equals(a.get(6)),"json scalars, escapes and unicode parse");
        check(Json.object(Json.get(root,"b","c")).isEmpty()&&Json.array(map.get("d")).isEmpty()&&Json.get(root,"b","x","y")==null,"nested lookup tolerates missing keys");
        for(String bad:new String[]{"{","[1,]","{\"a\" 1}","{\"a\":1} x","\"open","nul","{\"a\":+}"}) {
            boolean rejected=false;try{Json.parse(bad);}catch(IllegalArgumentException e){rejected=true;}
            check(rejected,"malformed json rejected: "+bad);
        }

        DashboardLayout layout=DashboardLayout.parse(M5P);
        check(layout.frameWidth()==848&&layout.frameHeight()==480&&layout.style().equals("d2x_1"),"frame comes from the codec size of the configuration");
        check(layout.boundRects().size()==2&&layout.phoneDrawn().size()==1,"both bound rectangles and the phone-drawn map info frame are read");
        List<Box> occlusions=layout.occlusions();
        check(occlusions.size()==1&&occlusions.get(0).equals(new Box(641,44,841,258)),"the navigation column overlaps a phone-drawn element, only the instrument card remains a dashboard occlusion");
        check(layout.referenceOcclusions().equals(occlusions),"an 848 x 480 frame maps to the reference frame unchanged");
        check(layout.describe().contains("occlusions=(641,44,841,258)"),"description lists the occlusion");

        DashboardLayout half=DashboardLayout.parse(M5P.replace("\"codecWidth\": 848.0, \"codecHeight\": 480.0","\"codecWidth\": 636.0, \"codecHeight\": 360.0"));
        check(half.frameWidth()==636&&half.frameHeight()==360&&half.occlusions().get(0).equals(new Box(480.75f,33,630.75f,193.5f)),"bound rectangles scale from dimension units to codec pixels");
        Box reference=half.referenceOcclusions().get(0);
        check(Math.abs(reference.left()-641)<.01&&Math.abs(reference.top()-44)<.01&&Math.abs(reference.right()-841)<.01&&Math.abs(reference.bottom()-258)<.01,"reference occlusions undo the HUD fit of a smaller frame");

        DashboardLayout noCodec=DashboardLayout.parse(M5P.replace("\"codecWidth\": 848.0, \"codecHeight\": 480.0, ",""));
        check(noCodec.frameWidth()==848&&noCodec.occlusions().size()==1,"missing codec size falls back to the dimension size");
        DashboardLayout onlyMap=DashboardLayout.parse(M5P.replace("\"mapInfo\": {\"progressLine\": {\"frame\": {\"width\": 272, \"height\": 12, \"x\": 0, \"y\": 103}}, \"style\": \"d2x_1\",\n    \"frame\": {\"width\": 272.0, \"height\": 354.0, \"x\": 8, \"y\": 44}}","\"mapInfo\": {\"style\": \"d2x_1\"}"));
        check(onlyMap.occlusions().size()==2&&onlyMap.occlusions().get(0).equals(new Box(0,0,272,480)),"without phone-drawn elements every bound rectangle counts as a dashboard occlusion");
        for(String bad:new String[]{"{}","{\"layout\":{}}","{\"layout\":{\"baseMap\":{}}}","[]","{\"layout\":{\"baseMap\":{\"frame\":{\"width\":100,\"height\":100}}}}"}) {
            boolean rejected=false;try{DashboardLayout.parse(bad);}catch(IllegalArgumentException e){rejected=true;}
            check(rejected,"unusable configuration rejected: "+bad);
        }
        check(DashboardLayout.DEFAULT.occlusions().equals(List.of(SidebarLayout.INSTRUMENT))&&DashboardLayout.DEFAULT.referenceOcclusions().equals(List.of(SidebarLayout.INSTRUMENT)),"default layout keeps the calibrated instrument card");

        check(SidebarLayout.intersects(new Box(0,0,10,10),new Box(9,9,20,20))&&!SidebarLayout.intersects(new Box(0,0,10,10),new Box(10,0,20,10))&&!SidebarLayout.intersects(new Box(0,0,10,10),new Box(0,10,10,20)),"box intersection is strict on shared edges");
        WidgetSettings all=WidgetSettings.DEFAULT.withMask(WidgetSettings.ALL|WidgetSettings.VOLTAGE_CHART|WidgetSettings.SPEED_CHART|WidgetSettings.POWER_CHART);
        SidebarLayout.Sizes sizes=new SidebarLayout.Sizes(190,190,190,190);
        SidebarLayout.Stack calibrated=SidebarLayout.arrange(all,WidgetSettings.ALL,0,sizes,false);
        SidebarLayout.Stack configured=SidebarLayout.arrange(all,WidgetSettings.ALL,0,sizes,false,occlusions);
        SidebarLayout.Stack none=SidebarLayout.arrange(all,WidgetSettings.ALL,0,sizes,false,List.of());
        check(calibrated.power().right()==SidebarLayout.LEFT_COLUMN_RIGHT&&configured.power().right()==SidebarLayout.LEFT_COLUMN_RIGHT&&none.power().right()==SidebarLayout.RIGHT,"a tall right column overflows past a calibrated or configured occlusion, and never without one");
        SidebarLayout.Stack low=SidebarLayout.arrange(all,WidgetSettings.ALL,0,sizes,false,List.of(new Box(640,0,842,20)));
        check(low.power().right()==SidebarLayout.RIGHT&&low.power().top()>20,"cards that stay below the occlusion keep the right column");

        DisplaySettings framed=DisplaySettings.defaults().withFrame(636,360);
        check(framed.width==636&&framed.height==360&&framed.virtualWidth==636&&framed.virtualHeight==360,"a smaller frame clamps the virtual display into it");
        DisplaySettings same=DisplaySettings.defaults().withFrame(848,480);
        check(same.width==848&&same.virtualWidth==640&&same.virtualHeight==440,"an unchanged frame keeps the virtual display");
        check(DisplaySettings.defaults().withFrame(100,100).width==848,"an unusable frame is ignored");
        DashboardLayout portrait=DashboardLayout.parse(M5P.replace("\"dimensionWidth\": 848.0","\"dimensionWidth\": 240.0").replace("\"dimensionHeight\": 480.0","\"dimensionHeight\": 320.0")
                .replace("\"codecWidth\": 848.0","\"codecWidth\": 240.0").replace("\"codecHeight\": 480.0","\"codecHeight\": 320.0"));
        check(portrait.frameWidth()==240&&portrait.frameHeight()==320,"a 240 x 320 half-screen configuration is read instead of rejected");
        DisplaySettings small=new DisplaySettings(848,480,600,480,180,0xff242424,true).withFrame(240,320);
        check(small.width==240&&small.height==320&&small.virtualWidth==240&&small.virtualHeight==300&&small.dpi==180,"the virtual display shrinks into a portrait frame keeping the density and the top strip");
        check(SidebarLayout.fits(848,480)&&SidebarLayout.fits(636,360)&&SidebarLayout.fits(340,192)&&!SidebarLayout.fits(200,100)&&!SidebarLayout.fits(0,320),"landscape cards are drawn down to 40% of the reference fit and hidden below");
        SidebarLayout.Fit portraitFit=SidebarLayout.fit(240,320),wide=SidebarLayout.fit(848,480);
        check(portraitFit.halfScreen()&&!wide.halfScreen()&&Math.abs(portraitFit.scale()-240/210f)<1e-4&&Math.abs(portraitFit.dx()-(240-848*portraitFit.scale()))<.01&&Math.abs(portraitFit.dy()-(320-480*portraitFit.scale()))<.01&&SidebarLayout.fits(240,320),"a portrait frame maps the card column onto the full frame width");
        check(wide.scale()==1&&wide.dx()==0&&wide.dy()==0,"the reference frame maps onto itself");
        check(SidebarLayout.notificationWidth(312,true)==190&&SidebarLayout.notificationWidth(312,false)==312&&SidebarLayout.notificationWidth(180,true)==180,"half-screen notifications are capped at the column width");
        check(small.virtualHeight==300&&DisplaySettings.defaults().withFrame(636,360).virtualHeight==360,"a portrait frame keeps a 20 px strip above the app");
        int stackMask=WidgetSettings.PHONE|WidgetSettings.MUSIC|WidgetSettings.TYRES|WidgetSettings.VOLTAGE|WidgetSettings.SPEED|WidgetSettings.POWER|WidgetSettings.LAMP;
        SidebarLayout.Stack single=SidebarLayout.arrange(WidgetSettings.DEFAULT,stackMask,0,SidebarLayout.fullWidth(),true,List.of(SidebarLayout.INSTRUMENT),true);
        SidebarLayout.Stack twoColumn=SidebarLayout.arrange(WidgetSettings.DEFAULT,stackMask,0,SidebarLayout.fullWidth(),false,List.of(SidebarLayout.INSTRUMENT),false);
        boolean allRight=true,anyLeft=false;
        for(int w:new int[]{WidgetSettings.PHONE,WidgetSettings.MUSIC,WidgetSettings.TYRES,WidgetSettings.VOLTAGE,WidgetSettings.SPEED,WidgetSettings.POWER,WidgetSettings.LAMP}){
            SidebarLayout.Box one=single.of(w),two=twoColumn.of(w);
            allRight&=one!=null&&one.right()==SidebarLayout.RIGHT&&one.width()==SidebarLayout.WIDTH;anyLeft|=two!=null&&two.right()!=SidebarLayout.RIGHT;
        }
        check(allRight&&anyLeft&&!single.notificationDodged(),"the half-screen column keeps every full-width card on the right where the two-column layout would overflow to the left");
    }
}
