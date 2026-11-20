package com.example.examplemod.network;

import com.example.examplemod.ExampleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Клиент -> Сервер: игрок нажал клавишу "Спешиться (Полёт)" (по умолчанию X, настраивается в
 * Настройках управления — см. ClientKeyBindings), сидя верхом на свинье с мутацией "Полёт".
 *
 * Нужен отдельным пакетом, а не обычным ПКМ/ванильным Sneak, потому что Sneak у "Полёта" занят
 * под снижение высоты (см. TameablePigEntity.travel()) — обычное ванильное спешивание по Sneak
 * для таких свиней заблокировано (см. PigEventHandlers.onPigDismountAttempt), поэтому нужен
 * отдельный явный способ слезть.
 */
public record PigDismountPacket(int pigEntityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PigDismountPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pig_dismount"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PigDismountPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PigDismountPacket::pigEntityId,
            PigDismountPacket::new
    );

    @Override
    public CustomPacketPayload.Type<PigDismountPacket> type() {
        return TYPE;
    }
}