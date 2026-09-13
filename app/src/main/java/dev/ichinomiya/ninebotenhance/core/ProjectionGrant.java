package dev.ichinomiya.ninebotenhance.core;

/** One system consent and one MediaProjection creation per live session, including Activity recreation. */
public final class ProjectionGrant {
    private enum State { WAITING, ASKING, STARTING, ACTIVE, STOPPED }
    private State state = State.WAITING;
    public boolean open(boolean recreation) {
        if (recreation) return state == State.ASKING;
        if (state != State.WAITING) return false;
        state = State.ASKING; return true;
    }
    public boolean consume() {
        if (state != State.ASKING) return false;
        state = State.STARTING; return true;
    }
    public boolean ready() {
        if (state != State.STARTING) return false;
        state = State.ACTIVE; return true;
    }
    public void stop() { state = State.STOPPED; }
}
