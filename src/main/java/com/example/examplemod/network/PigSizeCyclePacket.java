package com.example.examplemod.network;

import com.example.examplemod.ExampleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент -> Сервер: кнопка "Размер" на Вкладке 1 GUI свиньи — переключает Обычный/Маленький/Большой. */
public record PigSizeCyclePacket(int pigEntityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PigSizeCyclePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pig_size_cycle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PigSizeCyclePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PigSizeCyclePacket::pigEntityId,
            PigSizeCyclePacket::new
    );

    @Override
    public CustomPacketPayload.Type<PigSizeCyclePacket> type() {
        return TYPE;
    }
}