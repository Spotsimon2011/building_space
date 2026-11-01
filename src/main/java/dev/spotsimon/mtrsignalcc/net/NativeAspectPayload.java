package dev.spotsimon.mtrsignalcc.net;

import dev.spotsimon.mtrsignalcc.MtrSignalCc;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: "MTR's native aspect for this signal side is currently X". */
public record NativeAspectPayload(BlockPos pos, int side, int aspect) implements CustomPacketPayload {

    public static final Type<NativeAspectPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MtrSignalCc.MOD_ID, "native_aspect"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NativeAspectPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, NativeAspectPayload::pos,
            ByteBufCodecs.VAR_INT, NativeAspectPayload::side,
            ByteBufCodecs.VAR_INT, NativeAspectPayload::aspect,
            NativeAspectPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
