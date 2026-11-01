package dev.spotsimon.mtrsignalcc.api;

/**
 * Implemented onto MTR's BlockSignalBase.BlockEntityBase by SignalBlockEntityMixin.
 * Cast with {@code (SignalOverrideAccess) (Object) blockEntity}.
 * {@code side} is 0-based here (0 = first side, 1 = second side of a double sided signal).
 */
public interface SignalOverrideAccess {
    boolean mtrcc$isManual(int side);

    int mtrcc$getManualAspect(int side);

    void mtrcc$setManual(int side, boolean manual, int aspect);

    /** @return the last aspect MTR computed for this side as reported by a client, or -1 if never reported */
    int mtrcc$getNativeAspect(int side);

    /** @return System.currentTimeMillis() of the last report, or 0 */
    long mtrcc$getNativeTimestamp(int side);

    void mtrcc$setNativeAspect(int side, int aspect, long timestampMillis);
}
