package dev.ichinomiya.ninebotenhance.core;

/**
 * The TFT board's day/night flag for the navigation and cast screens. Ninebot's map navigation pages send it through
 * {@code DynamicDevice.sendCommand("setDashNaviTheme", ...)} (register 247, bit write: 16-bit mask 1, 16-bit value 1 = night)
 * whenever the cruise page connects and whenever the phone's dark mode flips; the dashboard paints its status bar and
 * instrument card accordingly. It changes nothing but the dashboard's colours.
 */
public final class DashboardTheme {
    public static final String COMMAND="setDashNaviTheme";
    public static byte[] payload(boolean night){return new byte[]{1,0,(byte)(night?1:0),0};}
    private DashboardTheme(){}
}
