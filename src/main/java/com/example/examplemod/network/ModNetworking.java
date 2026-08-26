package com.example.examplemod.network;

import com.example.examplemod.TameablePigEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetworking {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(MutationActionPacket.TYPE, MutationActionPacket.STREAM_CODEC, (payload, context) -> {
            // handle() по умолчанию выполняется на основном серверном потоке (PayloadRegistrar так
            // и делает по умолчанию), поэтому здесь безопасно сразу трогать мир/сущности.
            Player player = context.player();
            if (!(player.level().getEntity(payload.pigEntityId()) instanceof TameablePigEntity pig)) {
                return;
            }
            // Проверяем владение и дистанцию — тот же лимит, что и у самого меню (PigMenu.stillValid)
            if (!pig.isTame() || !pig.isOwnedBy(player) || pig.distanceTo(player) > 8.0F) {
                return;
            }

            if (payload.remove()) {
                pig.removeMutation(payload.mutationId());
            } else {
                pig.upgradeMutation(payload.mutationId());
            }
        });
    }
}