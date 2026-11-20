package com.example.examplemod.network;

import com.example.examplemod.ExampleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент -> сервер: актуальные клавиши подъёма/снижения для всадника летающей свиньи. */
public record PigFlightInputPacket(int pigEntityId, boolean up, boolean down) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PigFlightInputPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pig_flight_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PigFlightInputPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PigFlightInputPacket::pigEntityId,
            ByteBufCodecs.BOOL, PigFlightInputPacket::up,
            ByteBufCodecs.BOOL, PigFlightInputPacket::down,
            PigFlightInputPacket::new
    );

    @Override
    public CustomPacketPayload.Type<PigFlightInputPacket> type() {
        return TYPE;
    }
}
