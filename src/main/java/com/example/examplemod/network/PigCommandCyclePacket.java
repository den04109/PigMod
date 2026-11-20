package com.example.examplemod.network;

import com.example.examplemod.ExampleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент -> сервер: переключение команды свиньи в её GUI. */
public record PigCommandCyclePacket(int pigEntityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PigCommandCyclePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pig_command_cycle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PigCommandCyclePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PigCommandCyclePacket::pigEntityId,
            PigCommandCyclePacket::new
    );

    @Override
    public CustomPacketPayload.Type<PigCommandCyclePacket> type() {
        return TYPE;
    }
}
