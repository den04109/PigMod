package com.example.examplemod.network;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.PigControlTabletItem;
import com.example.examplemod.TameablePigEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

public record TabletWhitelistTogglePacket(UUID pigUuid, ResourceLocation entityTypeId) implements CustomPacketPayload {
    public static final Type<TabletWhitelistTogglePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "tablet_whitelist_toggle"));
    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC = StreamCodec.of((buf, value) -> buf.writeUUID(value), buf -> buf.readUUID());
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletWhitelistTogglePacket> STREAM_CODEC = StreamCodec.composite(
            UUID_CODEC, TabletWhitelistTogglePacket::pigUuid,
            ByteBufCodecs.STRING_UTF8.map(ResourceLocation::parse, ResourceLocation::toString), TabletWhitelistTogglePacket::entityTypeId,
            TabletWhitelistTogglePacket::new);
    @Override public Type<TabletWhitelistTogglePacket> type() { return TYPE; }
}
