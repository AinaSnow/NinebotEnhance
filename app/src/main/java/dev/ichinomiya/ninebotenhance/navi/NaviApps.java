package dev.ichinomiya.ninebotenhance.navi;

/** Navigation apps the module observes for turn-by-turn data. */
public final class NaviApps {
    public static final String AMAP="com.autonavi.minimap",TENCENT="com.tencent.map",BAIDU="com.baidu.BaiduMap";
    public static final String[] ALL={AMAP,TENCENT,BAIDU};
    public static boolean supported(String pkg){return AMAP.equals(pkg)||TENCENT.equals(pkg)||BAIDU.equals(pkg);}
    public static String label(String pkg){
        switch(pkg==null?"":pkg){case AMAP:return "amap";case TENCENT:return "tencent";case BAIDU:return "baidu";default:return pkg;}
    }
    private NaviApps(){}
}
