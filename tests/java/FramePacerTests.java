import dev.ichinomiya.ninebotenhance.core.FramePacer;
import java.util.*;

final class FramePacerTests {
    private static void check(boolean value, String message) { CoreTests.check(value, message); }
    static void run() {
        FramePacer pacer = new FramePacer();
        FramePacer.Ticket first = pacer.schedule(1000);
        check(first.delayMs == 0 && pacer.dispatch(first), "first virtual screen frame is immediately readable");
        pacer.captured(1000);
        FramePacer.Ticket early = pacer.schedule(1049);
        check(early.delayMs == 1, "an early 20 FPS frame waits one millisecond instead of being discarded");
        check(pacer.schedule(1049) == null && pacer.schedule(1050) == null, "notifications coalesce into one pending latest-image read");
        check(pacer.dispatch(early) && !pacer.dispatch(early), "scheduled read is dispatched exactly once");
        pacer.captured(1050);
        FramePacer.Ticket cancelled = pacer.schedule(1075);
        pacer.reset();
        FramePacer.Ticket replacement = pacer.schedule(1080);
        check(replacement.delayMs == 0 && !pacer.dispatch(cancelled) && pacer.dispatch(replacement), "old delayed reads cannot consume the new session's ticket after reset");
        // No image was available. Do not invent a frame or delay the next notification.
        FramePacer.Ticket empty = pacer.schedule(1081);
        check(empty.delayMs == 0 && pacer.dispatch(empty), "an empty read does not advance the pacing deadline");
        pacer.captured(1081);
        FramePacer.Ticket late = pacer.schedule(5000);
        check(late.delayMs == 0 && pacer.dispatch(late), "a long stall resumes with one immediate latest frame");
        pacer.captured(5000);
        check(pacer.schedule(5001).delayMs == 49, "resuming after a stall does not produce a burst of catch-up copies");

        List<Long> jittered = new ArrayList<>();
        for (int i=0; i<200; i++) jittered.add(i*50L - (i%2==1?1:0));
        List<Long> copied = simulate(jittered, 10000);
        check(copied.size() == 200, "alternating 49/51 ms producer intervals retain 20 FPS instead of falling to 10 FPS");
        check(copied.get(0) == 0 && copied.get(199) == 9950, "early frames are read at their intended 50 ms deadline");
        List<Long> fast = new ArrayList<>();
        for (int i=0; i<1200; i++) fast.add(Math.round(i*1000.0/120));
        List<Long> capped = simulate(fast, 10000);
        check(capped.size() == 200, "a 120 Hz producer remains limited to 20 copies per second");
        boolean spaced = true;
        for (int i=1; i<capped.size(); i++) spaced &= capped.get(i)-capped.get(i-1) >= 50;
        check(spaced, "high-rate callbacks cannot bypass the 50 ms copy interval");
        check(simulate(List.of(0L), 10000).equals(List.of(0L)), "a static producer does not synthesize repeated captures");
        check(simulate(List.of(0L, 1000L, 2000L), 2500).equals(List.of(0L, 1000L, 2000L)), "a slow producer is neither accelerated nor delayed unnecessarily");
    }
    /** Model the handler queue with producer callbacks and at most one delayed acquireLatestImage. */
    private static List<Long> simulate(List<Long> arrivals, long until) {
        FramePacer pacer = new FramePacer();
        FramePacer.Ticket ticket = null;
        long due = Long.MAX_VALUE;
        int index = 0;
        boolean imageAvailable = false;
        List<Long> copied = new ArrayList<>();
        while (index < arrivals.size() || ticket != null) {
            long arrival = index < arrivals.size() ? arrivals.get(index) : Long.MAX_VALUE;
            long now = Math.min(arrival, due);
            if (now >= until) break;
            if (arrival <= due) {
                index++; imageAvailable = true;
                FramePacer.Ticket scheduled = pacer.schedule(now);
                if (scheduled != null) { ticket=scheduled; due=now+scheduled.delayMs; }
            } else {
                if (pacer.dispatch(ticket) && imageAvailable) {
                    copied.add(now); pacer.captured(now); imageAvailable=false;
                }
                ticket=null; due=Long.MAX_VALUE;
            }
        }
        return copied;
    }
}
