package dev.spotsimon.mtrsignalcc;

import dev.spotsimon.mtrsignalcc.api.SignalOverrideAccess;
import dev.spotsimon.mtrsignalcc.net.NativeAspectPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.mtr.block.BlockSignalBase;

import java.util.OptionalInt;

/**
 * All signal logic lives here so the CC peripheral and the /mtrsignal command behave identically.
 * Sides are 0-based in this class; the Lua API and the command use 1-based sides.
 * Everything here must be called on the server thread.
 */
public final class SignalControl {
    /** How long a client-reported native aspect is trusted before it counts as "unknown". Clients re-report every 5 s. */
    public static final long NATIVE_FRESH_MS = 15_000L;
    private static final double MAX_REPORT_DISTANCE_SQ = 128.0 * 128.0;

    private SignalControl() {}

    private static SignalOverrideAccess access(BlockSignalBase.BlockEntityBase be) {
        return (SignalOverrideAccess) (Object) be;
    }

    public static int sideCount(BlockSignalBase.BlockEntityBase be) {
        return be.isDoubleSided ? 2 : 1;
    }

    /**
     * @param side1Based null = not given
     * @param requireForDouble if true, omitting the side on a double sided signal is an error; otherwise it means "both sides"
     */
    public static int[] resolveSides(BlockSignalBase.BlockEntityBase be, Integer side1Based, boolean requireForDouble) {
        final int count = sideCount(be);
        if (side1Based == null) {
            if (count == 1) return new int[]{0};
            if (requireForDouble) {
                throw new IllegalArgumentException("This signal is double sided: specify side 1 or 2");
            }
            return new int[]{0, 1};
        }
        if (side1Based < 1 || side1Based > count) {
            throw new IllegalArgumentException("Invalid side " + side1Based + " (this signal has " + count + (count == 1 ? " side)" : " sides)"));
        }
        return new int[]{side1Based - 1};
    }

    public static boolean isManual(BlockSignalBase.BlockEntityBase be, int side) {
        return access(be).mtrcc$isManual(side);
    }

    public static int manualAspect(BlockSignalBase.BlockEntityBase be, int side) {
        return access(be).mtrcc$getManualAspect(side);
    }

    /** MTR's own aspect for this side, if a client reported it recently. */
    public static OptionalInt nativeAspect(BlockSignalBase.BlockEntityBase be, int side) {
        final int value = access(be).mtrcc$getNativeAspect(side);
        if (value < 0 || nativeAgeMs(be, side) > NATIVE_FRESH_MS) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(value);
    }

    /** @return milliseconds since the last client report, or -1 if there has never been one */
    public static long nativeAgeMs(BlockSignalBase.BlockEntityBase be, int side) {
        final long timestamp = access(be).mtrcc$getNativeTimestamp(side);
        return timestamp == 0 ? -1 : Math.max(0, System.currentTimeMillis() - timestamp);
    }

    /** What the signal is showing right now: the forced aspect in manual mode, MTR's aspect in auto mode (if known). */
    public static OptionalInt currentAspect(BlockSignalBase.BlockEntityBase be, int side) {
        return isManual(be, side) ? OptionalInt.of(manualAspect(be, side)) : nativeAspect(be, side);
    }

    public static void setMode(BlockSignalBase.BlockEntityBase be, int side, boolean manual) {
        final SignalOverrideAccess access = access(be);
        if (manual && !access.mtrcc$isManual(side)) {
            // Entering manual mode freezes whatever the signal is currently showing (default: proceed).
            access.mtrcc$setManual(side, true, nativeAspect(be, side).orElse(0));
        } else if (!manual) {
            // Leaving manual mode just drops the override. MTR's own logic never stopped running,
            // so the next rendered frame shows the aspect MTR has chosen.
            access.mtrcc$setManual(side, false, access.mtrcc$getManualAspect(side));
        }
        sync(be);
    }

    public static void setAspect(BlockSignalBase.BlockEntityBase be, int side, int aspect) {
        final SignalKind kind = SignalKind.of(be);
        if (kind == null) {
            throw new IllegalStateException("Unsupported signal block");
        }
        if (!isManual(be, side)) {
            throw new IllegalStateException("Side " + (side + 1) + " is in auto mode; switch it to manual before setting an aspect");
        }
        if (aspect < 0 || aspect >= kind.aspectCount()) {
            throw new IllegalArgumentException("Aspect must be 0.." + (kind.aspectCount() - 1) + " for this signal");
        }
        access(be).mtrcc$setManual(side, true, aspect);
        sync(be);
    }

    private static void sync(BlockSignalBase.BlockEntityBase be) {
        be.setChanged();
        final Level level = be.getLevel();
        if (level != null && !level.isClientSide) {
            final BlockPos pos = be.getBlockPos();
            final BlockState state = be.getBlockState();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** Server-side handler for the client's "native aspect" report. */
    public static void acceptNativeReport(ServerPlayer player, NativeAspectPayload payload) {
        if (payload.side() < 0 || payload.side() > 1) {
            return;
        }
        final ServerLevel level = player.serverLevel();
        final BlockPos pos = payload.pos();
        if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_REPORT_DISTANCE_SQ) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof BlockSignalBase.BlockEntityBase be)) {
            return;
        }
        final SignalKind kind = SignalKind.of(be);
        if (kind == null || (payload.side() == 1 && !be.isDoubleSided)) {
            return;
        }
        access(be).mtrcc$setNativeAspect(payload.side(), kind.normalize(payload.aspect()), System.currentTimeMillis());
    }
}
