package com.example.examplemod.network;

import com.example.examplemod.ExampleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PigRenamePacket(int pigEntityId, String name) implements CustomPacketPayload {
    public static final Type<PigRenamePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pig_rename"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PigRenamePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PigRenamePacket::pigEntityId,
            ByteBufCodecs.stringUtf8(32), PigRenamePacket::name,
            PigRenamePacket::new
    );
    @Override public Type<PigRenamePacket> type() { return TYPE; }
}
