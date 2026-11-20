package com.example.examplemod.network;

import com.example.examplemod.ExampleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Служебные действия планшета над выбранной свиньёй: сброс области, сброс сундука,
 * очистка белого списка и удаление свиньи из памяти планшета.
 *
 * Все они делают ровно одно: меняют состояние на сервере. Отдельные пакеты под каждое
 * действие были бы четырьмя почти одинаковыми классами, поэтому здесь один пакет с кодом действия.
 */
public record TabletActionPacket(UUID pigUuid, int action) implements CustomPacketPayload {

    public static final int ACTION_CLEAR_AREA = 0;
    public static final int ACTION_CLEAR_CHEST = 1;
    public static final int ACTION_CLEAR_WHITELIST = 2;
    public static final int ACTION_UNBIND_PIG = 3;

    public static final Type<TabletActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "tablet_action"));

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of((buf, value) -> buf.writeUUID(value), buf -> buf.readUUID());

    public static final StreamCodec<RegistryFriendlyByteBuf, TabletActionPacket> STREAM_CODEC = StreamCodec.composite(
            UUID_CODEC, TabletActionPacket::pigUuid,
            ByteBufCodecs.VAR_INT, TabletActionPacket::action,
            TabletActionPacket::new);

    @Override
    public Type<TabletActionPacket> type() { return TYPE; }
}
