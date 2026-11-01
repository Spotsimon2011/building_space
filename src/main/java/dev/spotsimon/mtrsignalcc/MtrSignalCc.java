package dev.spotsimon.mtrsignalcc;

import dev.spotsimon.mtrsignalcc.command.SignalCommand;
import dev.spotsimon.mtrsignalcc.net.NetworkSetup;
import dev.spotsimon.mtrsignalcc.peripheral.PeripheralSetup;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(MtrSignalCc.MOD_ID)
public final class MtrSignalCc {
    public static final String MOD_ID = "mtr_signal_cc";

    public MtrSignalCc(IEventBus modBus) {
        modBus.addListener(NetworkSetup::register);
        modBus.addListener(PeripheralSetup::register);
        NeoForge.EVENT_BUS.addListener(SignalCommand::register);
    }
}
