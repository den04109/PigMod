package com.example.examplemod.network;

import com.example.examplemod.ExampleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record TabletSelectPigPacket(UUID pigUuid) implements CustomPacketPayload {
    public static final Type<TabletSelectPigPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "tablet_select_pig"));
    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC = StreamCodec.of((buf, value) -> buf.writeUUID(value), buf -> buf.readUUID());
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletSelectPigPacket> STREAM_CODEC = StreamCodec.composite(
            UUID_CODEC, TabletSelectPigPacket::pigUuid, TabletSelectPigPacket::new
    );
    @Override public Type<TabletSelectPigPacket> type() { return TYPE; }
}
