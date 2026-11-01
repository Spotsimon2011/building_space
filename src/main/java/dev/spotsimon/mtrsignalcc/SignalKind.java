package dev.spotsimon.mtrsignalcc;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The four MTR signal families and the aspects each one can actually show.
 *
 * Aspect numbers match MTR's own numbering (BlockSignalBase.BlockEntityBase#getActualAspect):
 * 0 = proceed, 1 = stop (occupied), 2 = caution, 3 = double caution.
 * MTR internally produces 2 and 3 as timed "release" stages after a block clears; each renderer
 * collapses the values it cannot draw (see {@link #normalize(int)}).
 */
public enum SignalKind {
    LIGHT_2("light", "proceed", "stop"),
    LIGHT_3("light", "proceed", "stop", "caution"),
    LIGHT_4("light", "proceed", "stop", "caution", "double_caution"),
    SEMAPHORE("semaphore", "proceed", "stop");

    public final String family;
    private final String[] names;

    SignalKind(String family, String... names) {
        this.family = family;
        this.names = names;
    }

    public int aspectCount() {
        return names.length;
    }

    public String aspectName(int aspect) {
        return aspect >= 0 && aspect < names.length ? names[aspect] : "unknown";
    }

    /** @return the aspect number for a name, or -1 */
    public int aspectByName(String name) {
        for (int i = 0; i < names.length; i++) {
            if (names[i].equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    /** Collapse MTR's raw aspect value to what this signal type actually displays. */
    public int normalize(int raw) {
        return switch (this) {
            case LIGHT_4 -> raw >= 1 && raw <= 3 ? raw : 0;
            case LIGHT_3 -> raw == 1 || raw == 2 ? raw : 0;
            case LIGHT_2, SEMAPHORE -> raw == 1 ? 1 : 0;
        };
    }

    private static final ClassValue<SignalKind> BY_CLASS = new ClassValue<>() {
        @Override
        protected SignalKind computeValue(Class<?> type) {
            // Nested block entity classes: SignalLight2Aspect1BlockEntity, SignalLight3Aspect2BlockEntity,
            // SignalLight4Aspect1BlockEntity, SignalSemaphore1BlockEntity, ...
            String n = type.getSimpleName();
            if (n.startsWith("SignalLight2Aspect")) return LIGHT_2;
            if (n.startsWith("SignalLight3Aspect")) return LIGHT_3;
            if (n.startsWith("SignalLight4Aspect")) return LIGHT_4;
            if (n.startsWith("SignalSemaphore")) return SEMAPHORE;
            return null;
        }
    };

    @Nullable
    public static SignalKind of(BlockEntity blockEntity) {
        return BY_CLASS.get(blockEntity.getClass());
    }
}
