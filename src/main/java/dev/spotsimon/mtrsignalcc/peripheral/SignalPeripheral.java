package dev.spotsimon.mtrsignalcc.peripheral;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.spotsimon.mtrsignalcc.SignalControl;
import dev.spotsimon.mtrsignalcc.SignalKind;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.mtr.block.BlockSignalBase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Peripheral type: "mtr_signal"
 *
 * Sides are numbered 1 and 2 (a single sided signal only has side 1).
 * Aspects are numbered from 0, see getAspects().
 */
public final class SignalPeripheral implements IPeripheral {
    private final BlockSignalBase.BlockEntityBase be;
    private final SignalKind kind;

    public SignalPeripheral(BlockSignalBase.BlockEntityBase be, SignalKind kind) {
        this.be = be;
        this.kind = kind;
    }

    @Override
    public String getType() {
        return "mtr_signal";
    }

    @Override
    public Object getTarget() {
        return be;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof SignalPeripheral p && p.be == be;
    }

    // ------------------------------------------------------------------ static info

    /** "light" or "semaphore" */
    @LuaFunction(mainThread = true)
    public String getSignalType() {
        return kind.family;
    }

    @LuaFunction(mainThread = true)
    public boolean isDoubleSided() {
        return be.isDoubleSided;
    }

    /** 1 or 2 */
    @LuaFunction(mainThread = true)
    public int getSideCount() {
        return SignalControl.sideCount(be);
    }

    @LuaFunction(mainThread = true)
    public int getAspectCount() {
        return kind.aspectCount();
    }

    /** Table keyed by aspect number: {[0]="proceed", [1]="stop", ...}. These are the aspects you may set. */
    @LuaFunction(mainThread = true)
    public Map<Integer, String> getAspects() {
        final Map<Integer, String> aspects = new LinkedHashMap<>();
        for (int i = 0; i < kind.aspectCount(); i++) {
            aspects.put(i, kind.aspectName(i));
        }
        return aspects;
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getInfo() {
        final Map<String, Object> info = new LinkedHashMap<>();
        final ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        info.put("block", id == null ? "unknown" : id.toString());
        info.put("signalType", kind.family);
        info.put("doubleSided", be.isDoubleSided);
        info.put("sides", SignalControl.sideCount(be));
        info.put("aspectCount", kind.aspectCount());
        info.put("aspects", getAspects());
        info.put("x", be.getBlockPos().getX());
        info.put("y", be.getBlockPos().getY());
        info.put("z", be.getBlockPos().getZ());
        return info;
    }

    // ------------------------------------------------------------------ status

    /** Full status: info fields plus a "sideStatus" list (index 1 = side 1, index 2 = side 2). */
    @LuaFunction(mainThread = true)
    public Map<String, Object> getStatus() {
        final Map<String, Object> status = getInfo();
        final List<Object> sides = new ArrayList<>();
        for (int side = 0; side < SignalControl.sideCount(be); side++) {
            sides.add(sideStatus(side));
        }
        status.put("sideStatus", sides);
        return status;
    }

    /** Status of one side. Side is required on a double sided signal. */
    @LuaFunction(mainThread = true)
    public Map<String, Object> getSideStatus(IArguments args) throws LuaException {
        return sideStatus(singleSide(args.optInt(0)));
    }

    /** Aspect number the side is showing right now, or nil if unknown (auto mode and no player nearby to report it). */
    @LuaFunction(mainThread = true)
    @Nullable
    public Integer getAspect(IArguments args) throws LuaException {
        final OptionalInt aspect = SignalControl.currentAspect(be, singleSide(args.optInt(0)));
        return aspect.isPresent() ? aspect.getAsInt() : null;
    }

    @LuaFunction(mainThread = true)
    @Nullable
    public String getAspectName(IArguments args) throws LuaException {
        final OptionalInt aspect = SignalControl.currentAspect(be, singleSide(args.optInt(0)));
        return aspect.isPresent() ? kind.aspectName(aspect.getAsInt()) : null;
    }

    /** "auto" or "manual" */
    @LuaFunction(mainThread = true)
    public String getMode(IArguments args) throws LuaException {
        return SignalControl.isManual(be, singleSide(args.optInt(0))) ? "manual" : "auto";
    }

    // ------------------------------------------------------------------ control

    /**
     * setMode("auto" | "manual" [, side])
     * Without a side, applies to every side of the signal.
     * manual: the side keeps what it shows now, then obeys setAspect().
     * auto: setAspect() is refused and the side immediately shows the aspect MTR has chosen.
     */
    @LuaFunction(mainThread = true)
    public void setMode(IArguments args) throws LuaException {
        final String mode = args.getString(0).toLowerCase();
        final boolean manual;
        switch (mode) {
            case "manual" -> manual = true;
            case "auto", "automatic" -> manual = false;
            default -> throw new LuaException("Mode must be \"auto\" or \"manual\"");
        }
        final int[] sides = resolve(args.optInt(1), false);
        for (final int side : sides) {
            SignalControl.setMode(be, side, manual);
        }
    }

    /**
     * setAspect(aspect [, side])  -- aspect is a number or a name from getAspects()
     * Only works while the side is in manual mode. Side is required on a double sided signal.
     */
    @LuaFunction(mainThread = true)
    public void setAspect(IArguments args) throws LuaException {
        final Object raw = args.get(0);
        final int aspect;
        if (raw instanceof String name) {
            aspect = kind.aspectByName(name);
            if (aspect < 0) {
                throw new LuaException("Unknown aspect \"" + name + "\"; valid: " + getAspects());
            }
        } else {
            aspect = args.getInt(0);
        }
        final int side = singleSide(args.optInt(1));
        try {
            SignalControl.setAspect(be, side, aspect);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new LuaException(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ helpers

    private int singleSide(Optional<Integer> side1Based) throws LuaException {
        return resolve(side1Based, true)[0];
    }

    private int[] resolve(Optional<Integer> side1Based, boolean requireForDouble) throws LuaException {
        try {
            return SignalControl.resolveSides(be, side1Based.orElse(null), requireForDouble);
        } catch (IllegalArgumentException e) {
            throw new LuaException(e.getMessage());
        }
    }

    private Map<String, Object> sideStatus(int side) {
        final Map<String, Object> m = new LinkedHashMap<>();
        final boolean manual = SignalControl.isManual(be, side);
        m.put("side", side + 1);
        m.put("mode", manual ? "manual" : "auto");

        final OptionalInt current = SignalControl.currentAspect(be, side);
        m.put("aspectKnown", current.isPresent());
        if (current.isPresent()) {
            m.put("aspect", current.getAsInt());
            m.put("aspectName", kind.aspectName(current.getAsInt()));
        }
        if (manual) {
            m.put("manualAspect", SignalControl.manualAspect(be, side));
        }

        // What MTR itself is choosing, regardless of the override
        final OptionalInt nativeAspect = SignalControl.nativeAspect(be, side);
        m.put("nativeKnown", nativeAspect.isPresent());
        if (nativeAspect.isPresent()) {
            m.put("nativeAspect", nativeAspect.getAsInt());
            m.put("nativeAspectName", kind.aspectName(nativeAspect.getAsInt()));
        }
        final long age = SignalControl.nativeAgeMs(be, side);
        if (age >= 0) {
            m.put("nativeAgeMs", age);
        }
        return m;
    }
}
