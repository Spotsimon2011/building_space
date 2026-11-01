package dev.spotsimon.mtrsignalcc.net;

import dev.spotsimon.mtrsignalcc.SignalControl;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NetworkSetup {
    private NetworkSetup() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        // optional(): a client without this addon can still join a server that has it.
        final PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(NativeAspectPayload.TYPE, NativeAspectPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                SignalControl.acceptNativeReport(player, payload);
            }
        });
    }
}
