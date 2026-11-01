package dev.spotsimon.mtrsignalcc.peripheral;

import dan200.computercraft.api.peripheral.PeripheralCapability;
import dev.spotsimon.mtrsignalcc.SignalKind;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.mtr.block.BlockSignalBase;
import org.mtr.registry.BlockEntityTypes;

public final class PeripheralSetup {
    private PeripheralSetup() {}

    public static void register(RegisterCapabilitiesEvent event) {
        // Field names in MTR: SIGNAL_LIGHT_2_ASPECT_x are the 2-aspect lights, ..._3_..., ..._4_..., SEMAPHORE.
        add(event, BlockEntityTypes.SIGNAL_LIGHT_2_ASPECT_1.get());
        add(event, BlockEntityTypes.SIGNAL_LIGHT_2_ASPECT_2.get());
        add(event, BlockEntityTypes.SIGNAL_LIGHT_2_ASPECT_3.get());
        add(event, BlockEntityTypes.SIGNAL_LIGHT_2_ASPECT_4.get());
        add(event, BlockEntityTypes.SIGNAL_LIGHT_3_ASPECT_1.get());
        add(event, BlockEntityTypes.SIGNAL_LIGHT_3_ASPECT_2.get());
        add(event, BlockEntityTypes.SIGNAL_LIGHT_4_ASPECT_1.get());
        add(event, BlockEntityTypes.SIGNAL_LIGHT_4_ASPECT_2.get());
        add(event, BlockEntityTypes.SIGNAL_SEMAPHORE_1.get());
        add(event, BlockEntityTypes.SIGNAL_SEMAPHORE_2.get());
    }

    private static <T extends BlockSignalBase.BlockEntityBase> void add(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(PeripheralCapability.get(), type, (be, side) -> {
            final SignalKind kind = SignalKind.of(be);
            return kind == null ? null : new SignalPeripheral(be, kind);
        });
    }
}
