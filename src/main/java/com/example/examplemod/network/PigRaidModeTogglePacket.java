package com.example.examplemod.network;

import com.example.examplemod.ExampleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент -> Сервер: тумблер "Рейд" рядом с [x] у мутации "Уничтожитель" на Вкладке 2 GUI —
 *  включает/выключает ручной режим бомбардировщика (бомбит по таймеру без оглядки на цель). */
public record PigRaidModeTogglePacket(int pigEntityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PigRaidModeTogglePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pig_raid_mode_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PigRaidModeTogglePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PigRaidModeTogglePacket::pigEntityId,
            PigRaidModeTogglePacket::new
    );

    @Override
    public CustomPacketPayload.Type<PigRaidModeTogglePacket> type() {
        return TYPE;
    }
}
