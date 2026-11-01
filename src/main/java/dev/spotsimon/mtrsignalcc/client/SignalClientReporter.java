package dev.spotsimon.mtrsignalcc.client;

import dev.spotsimon.mtrsignalcc.net.NativeAspectPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Kept in its own class so the (client-only) Minecraft class is never touched on a dedicated server:
 * the mixin only calls this after checking level.isClientSide().
 */
public final class SignalClientReporter {
    private SignalClientReporter() {}

    public static void send(BlockPos pos, int side, int aspect) {
        final ClientPacketListener connection = Minecraft.getInstance().getConnection();
        // The server may not have this addon; don't send a payload it can't decode.
        if (connection != null && connection.hasChannel(NativeAspectPayload.TYPE.id())) {
            PacketDistributor.sendToServer(new NativeAspectPayload(pos.immutable(), side, aspect));
        }
    }
}
