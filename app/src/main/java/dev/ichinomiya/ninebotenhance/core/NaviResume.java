package dev.ichinomiya.ninebotenhance.core;

/**
 * Decides whether a navigation that was running on the phone should be requested again after the module moved the app onto
 * the virtual display. AMap's map activity does not handle density or touchscreen configuration changes, so the system
 * relaunches it on the display move and the navigation page is destroyed; the destination and travel mode captured from the
 * engine let the same route be planned again through AMap's public URI.
 */
public final class NaviResume {
    /** The last turn-by-turn update must be at most this old: the navigation was live when the display move began. */
    public static final long WINDOW_MS=120000;
    public static String amapUri(NaviDestination destination,NaviUpdate latest,long now){
        if(destination==null||!destination.valid()||!destination.fresh(now)||!"amap".equals(destination.source()))return null;
        if(latest==null||latest.receivedAt()<=0||now-latest.receivedAt()>WINDOW_MS||latest.arrived())return null;
        return destination.amapUri();
    }
    private NaviResume(){}
}
