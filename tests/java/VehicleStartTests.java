import dev.ichinomiya.ninebotenhance.core.DirectSession;
import dev.ichinomiya.ninebotenhance.hook.BooleanResultObserver;
import dev.ichinomiya.ninebotenhance.hook.HookPolicy;
import java.util.ArrayList;
import java.util.List;
import static java.lang.Boolean.*;

public final class VehicleStartTests {
    private static void check(boolean value, String description) { CoreTests.check(value, description); }
    public interface Continuation { Object getContext(); void resumeWith(Object result); }
    public static final class RecordingContinuation implements Continuation {
        final Object context = new Object(); Object result; int calls;
        public Object getContext() { return context; }
        public void resumeWith(Object value) { result = value; calls++; }
    }
    public static final class StateOwner { public static final class isPowerOn$1 {} }
    static void run() {
        String a = "0123456789abcdef0123456789abcdef", b = "fedcba9876543210fedcba9876543210";
        DirectSession session = new DirectSession();
        check(session.begin(a) && session.phase() == DirectSession.Phase.CHECKING_VEHICLE && a.equals(session.vehicleCheckRequest()), "vehicle starts without display allocation permission");
        check(!session.prepareDisplay(a) && !session.granted(a), "unknown vehicle state blocks allocation even if module is authorized");
        check(session.cruiseReady(a) && !session.prepareDisplay(a), "an activity alone is not proof of vehicle power");
        check(session.powerChecked(a, false) && !session.prepareDisplay(a), "vehicle off cannot create a virtual display");
        check(session.powerChecked(a, true) && !session.prepareDisplay(a), "concurrent positive query cannot override rejection");
        check(session.end(a) && session.begin(b), "failed check can be retried with fresh request");
        check(!session.powerChecked(a, true) && !session.cruiseReady(a) && !session.prepareDisplay(a), "old callbacks cannot authorize another vehicle attempt");
        check(session.powerChecked(b, true) && !session.prepareDisplay(b), "power alone cannot bypass original cruise checks");
        check(session.cruiseReady(b) && session.prepareDisplay(b), "both prerequisites grant exactly one display creation");
        check(!session.prepareDisplay(b) && !session.powerChecked(b, true) && session.vehicleCheckRequest() == null, "repeated callbacks do not allocate twice");
        check(session.granted(b) && session.launch(b) && session.running(b), "accepted vehicle proceeds through frames and capture");
        check(session.end(b) && session.begin(a), "stop resets previous power and activity readiness");
        check(!session.prepareDisplay(a), "no reuse of prior vehicle on state");
        check(session.end(a) && !session.powerChecked(a, true) && !session.cruiseReady(a), "cancel or timeout rejects late asynchronous results");
        check(session.begin(b, DirectSession.Mode.LOCAL) && session.phase() == DirectSession.Phase.CONSENT && session.vehicleCheckRequest() == null, "local simulation skips all vehicle checks");
        check(!session.powerChecked(b, false) && !session.cruiseReady(b), "vehicle callbacks cannot affect local preview");
        check(session.granted(b) && session.localReady(b), "local preview works with vehicle off or absent");
        List<Boolean> observed = new ArrayList<>();
        BooleanResultObserver observer = new BooleanResultObserver(observed::add);
        observer.returned(null); observer.returned(new Object()); observer.returned(new IllegalStateException());
        check(observed.isEmpty(), "suspended, failure and unknown results never become power on");
        observer.returned(FALSE); observer.returned(TRUE);
        check(observed.equals(List.of(FALSE)), "synchronous rejection observed once unchanged");
        observed.clear(); observer = new BooleanResultObserver(observed::add);
        RecordingContinuation original = new RecordingContinuation();
        Continuation proxy = (Continuation) observer.continuation(Continuation.class, original);
        check(proxy.getContext() == original.context, "wrapped continuation retains original coroutine context");
        Object failure = new Object(); proxy.resumeWith(failure);
        check(original.calls == 1 && original.result == failure && observed.isEmpty(), "failed coroutine result forwarded unchanged and cannot authorize");
        // A separate successful coroutine, not a second resume of the failed one.
        original = new RecordingContinuation();
        observer = new BooleanResultObserver(observed::add);
        proxy = (Continuation) observer.continuation(Continuation.class, original);
        observer.returned(new Object()); proxy.resumeWith(TRUE); observer.returned(TRUE);
        check(original.calls == 1 && original.result == TRUE && observed.equals(List.of(TRUE)), "asynchronous Boolean forwarded exactly once and observed once");
        observed.clear();
        IllegalStateException exception = new IllegalStateException("original failure");
        Continuation throwing = new Continuation() {
            public Object getContext() { throw exception; }
            public void resumeWith(Object value) { throw exception; }
        };
        proxy = (Continuation) new BooleanResultObserver(observed::add).continuation(Continuation.class, throwing);
        try { proxy.resumeWith(TRUE); check(false, "original exception expected"); }
        catch (IllegalStateException e) { check(e == exception && observed.isEmpty(), "reflection unwraps the exact original exception without granting"); }
        RecordingContinuation unchanged = new RecordingContinuation();
        proxy = (Continuation) new BooleanResultObserver(value -> { throw new IllegalStateException(); }).continuation(Continuation.class, unchanged);
        proxy.resumeWith(TRUE);
        check(unchanged.calls == 1 && unchanged.result == TRUE, "observer errors do not alter original continuation completion");
        check(BooleanResultObserver.ownStateMachine(StateOwner.class.getName(), "isPowerOn", new StateOwner.isPowerOn$1()), "suspend state machine reentry is excluded from wrapping");
        check(!BooleanResultObserver.ownStateMachine(StateOwner.class.getName(), "isPowerOn", unchanged), "caller continuation may be observed on initial invocation");
        String messenger = "cn.ninebot.device.motor.navi.DashNaviDataMessenger$Companion";
        check(HookPolicy.interestingClass(messenger) && HookPolicy.vehiclePowerMethod(messenger, "isPowerOn"), "known original power predicate is observable");
        check(!HookPolicy.vehiclePowerMethod(messenger, "setPowerOn") && !HookPolicy.vehiclePowerMethod("other.Vehicle", "isPowerOn"), "observer excludes commands and unrelated app methods");
    }
}
