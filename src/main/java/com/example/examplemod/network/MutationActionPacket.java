package com.example.examplemod.network;

import com.example.examplemod.ExampleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Клиент -> Сервер: игрок нажал [+] (прокачать) или [x] (удалить) напротив мутации
 * на Вкладке 2 инвентаря свиньи. Обработка — в ModNetworking (проверка владения и дистанции,
 * сама логика прокачки/удаления уже есть в TameablePigEntity).
 */
public record MutationActionPacket(int pigEntityId, String mutationId, boolean remove) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MutationActionPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "mutation_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MutationActionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MutationActionPacket::pigEntityId,
            ByteBufCodecs.STRING_UTF8, MutationActionPacket::mutationId,
            ByteBufCodecs.BOOL, MutationActionPacket::remove,
            MutationActionPacket::new
    );

    @Override
    public CustomPacketPayload.Type<MutationActionPacket> type() {
        return TYPE;
    }
}