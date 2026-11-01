package dev.spotsimon.mtrsignalcc.mixin;

import dev.spotsimon.mtrsignalcc.SignalKind;
import dev.spotsimon.mtrsignalcc.api.SignalOverrideAccess;
import dev.spotsimon.mtrsignalcc.client.SignalClientReporter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.mtr.block.BlockSignalBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds a per-side manual override to every MTR signal block entity.
 *
 *  - The override (manual flag + forced aspect) is saved in the block entity NBT. MTR already syncs
 *    block entity NBT to clients, so the client renderer sees it.
 *  - On the client, getActualAspect(...) is MTR's own aspect logic. We let it run untouched (so its
 *    internal timers keep working, which is what makes "switch back to auto" land on MTR's real aspect),
 *    then only replace the returned value if the side is in manual mode.
 *  - Because MTR only computes the native aspect on the client, we report it to the server so the
 *    peripheral can read it in auto mode.
 */
@Mixin(value = BlockSignalBase.BlockEntityBase.class, remap = false)
public abstract class SignalBlockEntityMixin implements SignalOverrideAccess {

    @Unique private static final String KEY_MANUAL_1 = "mtrcc_manual1";
    @Unique private static final String KEY_MANUAL_2 = "mtrcc_manual2";
    @Unique private static final String KEY_ASPECT_1 = "mtrcc_aspect1";
    @Unique private static final String KEY_ASPECT_2 = "mtrcc_aspect2";
    @Unique private static final long HEARTBEAT_MS = 5_000L;

    @Unique private boolean mtrcc$manual1;
    @Unique private boolean mtrcc$manual2;
    @Unique private int mtrcc$aspect1;
    @Unique private int mtrcc$aspect2;

    // Server side, never saved: last native aspect reported by a client (+1 so that 0 means "unknown")
    @Unique private int mtrcc$nativePlusOne1;
    @Unique private int mtrcc$nativePlusOne2;
    @Unique private long mtrcc$nativeTime1;
    @Unique private long mtrcc$nativeTime2;

    // Client side throttle for reports
    @Unique private int mtrcc$sentPlusOne1;
    @Unique private int mtrcc$sentPlusOne2;
    @Unique private long mtrcc$sentTime1;
    @Unique private long mtrcc$sentTime2;

    // ---------------------------------------------------------------- persistence / sync

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void mtrcc$readNbt(CompoundTag tag, CallbackInfo ci) {
        mtrcc$manual1 = tag.getBoolean(KEY_MANUAL_1);
        mtrcc$manual2 = tag.getBoolean(KEY_MANUAL_2);
        mtrcc$aspect1 = tag.getInt(KEY_ASPECT_1);
        mtrcc$aspect2 = tag.getInt(KEY_ASPECT_2);
    }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void mtrcc$writeNbt(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean(KEY_MANUAL_1, mtrcc$manual1);
        tag.putBoolean(KEY_MANUAL_2, mtrcc$manual2);
        tag.putInt(KEY_ASPECT_1, mtrcc$aspect1);
        tag.putInt(KEY_ASPECT_2, mtrcc$aspect2);
    }

    // ---------------------------------------------------------------- aspect override (client render path)

    @Inject(method = "getActualAspect", at = @At("RETURN"), cancellable = true)
    private void mtrcc$afterGetActualAspect(boolean blocked, boolean side2, CallbackInfoReturnable<Integer> cir) {
        final BlockEntity self = (BlockEntity) (Object) this;
        final Level level = self.getLevel();
        if (level == null || !level.isClientSide()) {
            return;
        }
        final int side = side2 ? 1 : 0;
        final SignalKind kind = SignalKind.of(self);
        if (kind != null) {
            mtrcc$report(self, side, kind.normalize(cir.getReturnValue()));
        }
        if (mtrcc$isManual(side)) {
            cir.setReturnValue(mtrcc$getManualAspect(side));
        }
    }

    @Unique
    private void mtrcc$report(BlockEntity self, int side, int nativeAspect) {
        final long now = System.currentTimeMillis();
        final int lastPlusOne = side == 0 ? mtrcc$sentPlusOne1 : mtrcc$sentPlusOne2;
        final long lastTime = side == 0 ? mtrcc$sentTime1 : mtrcc$sentTime2;
        if (lastPlusOne == nativeAspect + 1 && now - lastTime < HEARTBEAT_MS) {
            return;
        }
        if (side == 0) {
            mtrcc$sentPlusOne1 = nativeAspect + 1;
            mtrcc$sentTime1 = now;
        } else {
            mtrcc$sentPlusOne2 = nativeAspect + 1;
            mtrcc$sentTime2 = now;
        }
        SignalClientReporter.send(self.getBlockPos(), side, nativeAspect);
    }

    // ---------------------------------------------------------------- SignalOverrideAccess

    @Override
    public boolean mtrcc$isManual(int side) {
        return side == 0 ? mtrcc$manual1 : mtrcc$manual2;
    }

    @Override
    public int mtrcc$getManualAspect(int side) {
        return side == 0 ? mtrcc$aspect1 : mtrcc$aspect2;
    }

    @Override
    public void mtrcc$setManual(int side, boolean manual, int aspect) {
        if (side == 0) {
            mtrcc$manual1 = manual;
            mtrcc$aspect1 = aspect;
        } else {
            mtrcc$manual2 = manual;
            mtrcc$aspect2 = aspect;
        }
    }

    @Override
    public int mtrcc$getNativeAspect(int side) {
        return (side == 0 ? mtrcc$nativePlusOne1 : mtrcc$nativePlusOne2) - 1;
    }

    @Override
    public long mtrcc$getNativeTimestamp(int side) {
        return side == 0 ? mtrcc$nativeTime1 : mtrcc$nativeTime2;
    }

    @Override
    public void mtrcc$setNativeAspect(int side, int aspect, long timestampMillis) {
        if (side == 0) {
            mtrcc$nativePlusOne1 = aspect + 1;
            mtrcc$nativeTime1 = timestampMillis;
        } else {
            mtrcc$nativePlusOne2 = aspect + 1;
            mtrcc$nativeTime2 = timestampMillis;
        }
    }
}
