package dev.ichinomiya.ninebotenhance.navi;

/**
 * Shared constants for the loopback channel that carries navigation state from a navigation app process to the module process.
 * ColorOS blocks cross-app service binds and content-provider resolution from the navigation apps, but a loopback TCP
 * connection ignores package visibility, so both ends of the module's own code meet on 127.0.0.1. The token is a fixed
 * handshake so an unrelated app cannot feed the dashboard; it is not a secret against the device owner.
 */
public final class NaviLoopback {
    /** Candidate ports tried in order by both ends; the first the module process can bind wins. */
    public static final int[] PORTS={28619,28620,28621,28622,28623};
    public static final String TOKEN="NBE-navi-1";
    /** Line protocol: token line, then one compact JSON object per update, newline-terminated, UTF-8. */
    public static final String CHARSET="UTF-8";
    private NaviLoopback(){}
}
