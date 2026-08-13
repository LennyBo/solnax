package com.rose.solnax.model.entity.enums;

public enum CoolDownReason {

    /**
     * Vehicle scoped reasons — they describe the state of one specific car.
     */
    FULL(false),
    LOW_BATTERY(false),
    NOT_CONNECTED(false),
    NO_RESPONSE(false),

    /**
     * Mode reasons — they steer the whole optimizer instead of blocking a single car.
     */
    MANUAL(true),
    ECO_PLUS(true);

    private final boolean mode;

    CoolDownReason(boolean mode) {
        this.mode = mode;
    }

    /**
     * @return true when this cool down represents a global optimizer mode.
     * Mode cool downs must never be treated as a per-vehicle block, they are
     * evaluated once by the optimizer instead.
     */
    public boolean isMode() {
        return mode;
    }
}
