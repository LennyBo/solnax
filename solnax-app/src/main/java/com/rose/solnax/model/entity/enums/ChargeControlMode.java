package com.rose.solnax.model.entity.enums;

/**
 * Global steering mode of the charge optimizer.
 * <p>
 * Modes are stored as cool downs with a {@link CoolDownReason} flagged as
 * {@link CoolDownReason#isMode()} so they expire automatically like any other cool down.
 */
public enum ChargeControlMode {

    /**
     * Full solar optimization: start, adjust and stop charging based on surplus.
     */
    NORMAL(null),

    /**
     * Follow solar production, but never stop an ongoing charge.
     * When the surplus is too low the charge is kept alive at the lowest charge speed.
     * Eco+ still only <em>starts</em> a charge when there is enough surplus.
     */
    ECO_PLUS(CoolDownReason.ECO_PLUS),

    /**
     * The optimizer is asleep regarding the car: it will not start, stop or
     * adjust charging and will not talk to the vehicle at all.
     */
    MANUAL(CoolDownReason.MANUAL);

    private final CoolDownReason reason;

    ChargeControlMode(CoolDownReason reason) {
        this.reason = reason;
    }

    /**
     * @return the cool down reason persisting this mode, or {@code null} for {@link #NORMAL}
     */
    public CoolDownReason getReason() {
        return reason;
    }

    public static ChargeControlMode fromReason(CoolDownReason reason) {
        if (reason == null) {
            return NORMAL;
        }
        for (ChargeControlMode mode : values()) {
            if (reason == mode.reason) {
                return mode;
            }
        }
        return NORMAL;
    }
}
