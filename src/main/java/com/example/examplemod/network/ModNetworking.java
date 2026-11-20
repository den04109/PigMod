package com.example.examplemod.network;

import com.example.examplemod.TameablePigEntity;
import com.example.examplemod.PigControlTabletItem;
import com.example.examplemod.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetworking {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(PigCommandCyclePacket.TYPE, PigCommandCyclePacket.STREAM_CODEC, (payload, context) -> {
            Player player = context.player();
            if (!(player.level().getEntity(payload.pigEntityId()) instanceof TameablePigEntity pig)) {
                return;
            }
            if (!pig.isTame() || !pig.isOwnedBy(player) || pig.distanceTo(player) > 8.0F) {
                return;
            }
            pig.cycleCommand();
        });

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

        registrar.playToServer(PigSizeCyclePacket.TYPE, PigSizeCyclePacket.STREAM_CODEC, (payload, context) -> {
            Player player = context.player();
            if (!(player.level().getEntity(payload.pigEntityId()) instanceof TameablePigEntity pig)) {
                return;
            }
            if (!pig.isTame() || !pig.isOwnedBy(player) || pig.distanceTo(player) > 8.0F) {
                return;
            }
            pig.cycleSizeState();
        });

        registrar.playToServer(PigDismountPacket.TYPE, PigDismountPacket.STREAM_CODEC, (payload, context) -> {
            Player player = context.player();
            if (!(player.level().getEntity(payload.pigEntityId()) instanceof TameablePigEntity pig)) {
                return;
            }
            // Спешиваем игрока только с ЭТОЙ конкретной свиньи и только если он реально на ней верхом —
            // отдельная проверка isOwnedBy тут не нужна: если он не верхом (getVehicle() != pig),
            // спешивать попросту нечего.
            if (player.getVehicle() != pig || !pig.hasMutation("flight")) {
                return;
            }
            // После явного X больше не используем одноразовый флаг в EntityMountEvent:
            // событие теперь блокирует только Shift-дисмаунт и не создаёт client/server desync.
            pig.setFlightInput(false, false);
            player.stopRiding();
        });

        registrar.playToServer(PigFlightInputPacket.TYPE, PigFlightInputPacket.STREAM_CODEC, (payload, context) -> {
            Player player = context.player();
            if (!(player.level().getEntity(payload.pigEntityId()) instanceof TameablePigEntity pig)) {
                return;
            }
            if (player.getVehicle() != pig || !pig.hasMutation("flight")) {
                return;
            }
            pig.setFlightInput(payload.up(), payload.down());
        });

        registrar.playToServer(PigRaidModeTogglePacket.TYPE, PigRaidModeTogglePacket.STREAM_CODEC, (payload, context) -> {
            Player player = context.player();
            if (!(player.level().getEntity(payload.pigEntityId()) instanceof TameablePigEntity pig)) {
                return;
            }
            if (!pig.isTame() || !pig.isOwnedBy(player) || pig.distanceTo(player) > 8.0F) {
                return;
            }
            pig.toggleRaidMode();
        });
        registrar.playToServer(PigRenamePacket.TYPE, PigRenamePacket.STREAM_CODEC, (payload, context) -> {
            Player player = context.player();
            if (!(player.level().getEntity(payload.pigEntityId()) instanceof TameablePigEntity pig)) return;
            if (!pig.isTame() || !pig.isOwnedBy(player) || pig.distanceTo(player) > 8.0F) return;
            String name = payload.name().trim();
            if (!name.isEmpty() && name.length() <= 32) pig.setCustomName(Component.literal(name));
            else if (name.isEmpty()) pig.setCustomName(null);
        });
        registrar.playToServer(TabletSelectPigPacket.TYPE, TabletSelectPigPacket.STREAM_CODEC, (payload, context) -> {
            Player player = context.player();
            ItemStack tablet = PigControlTabletItem.findHeld(player);
            if (tablet.isEmpty()) return;
            TameablePigEntity pig = findOwnedPig(player, payload.pigUuid());
            if (pig != null) PigControlTabletItem.selectPig(tablet, pig);
        });

        registrar.playToServer(TabletActionPacket.TYPE, TabletActionPacket.STREAM_CODEC, (payload, context) -> {
            Player player = context.player();
            ItemStack tablet = PigControlTabletItem.findHeld(player);
            if (tablet.isEmpty()) return;

            // Удаление из памяти планшета работает даже для свиньи, которой нет рядом —
            // иначе из списка нельзя было бы убрать потерянную/умершую свинью.
            if (payload.action() == TabletActionPacket.ACTION_UNBIND_PIG) {
                PigControlTabletItem.unbindPig(tablet, payload.pigUuid());
                return;
            }

            TameablePigEntity pig = findOwnedPig(player, payload.pigUuid());
            if (pig == null) return;
            switch (payload.action()) {
                case TabletActionPacket.ACTION_CLEAR_AREA -> {
                    pig.clearWorkArea();
                    player.displayClientMessage(Component.translatable("message.pig_mod.tablet.area_cleared"), true);
                }
                case TabletActionPacket.ACTION_CLEAR_CHEST -> {
                    pig.clearCourierChest();
                    player.displayClientMessage(Component.translatable("message.pig_mod.tablet.chest_cleared"), true);
                }
                case TabletActionPacket.ACTION_CLEAR_WHITELIST -> {
                    pig.clearWhitelist();
                    player.displayClientMessage(Component.translatable("message.pig_mod.tablet.whitelist_cleared"), true);
                }
                default -> { }
            }
        });

        registrar.playToServer(TabletWhitelistTogglePacket.TYPE, TabletWhitelistTogglePacket.STREAM_CODEC, (payload, context) -> {
            Player player = context.player();
            ItemStack tablet = PigControlTabletItem.findHeld(player);
            if (tablet.isEmpty()) return;
            TameablePigEntity pig = findOwnedPig(player, payload.pigUuid());
            if (pig == null) return;
            var type = BuiltInRegistries.ENTITY_TYPE.get(payload.entityTypeId());
            if (type != null) pig.toggleWhitelistEntityType(type);
        });

    }

    /** Ищет свинью игрока по UUID в радиусе действия планшета. */
    private static TameablePigEntity findOwnedPig(Player player, java.util.UUID uuid) {
        if (player == null || uuid == null) return null;
        for (TameablePigEntity candidate : player.level().getEntitiesOfClass(
                TameablePigEntity.class, player.getBoundingBox().inflate(256))) {
            if (candidate.getUUID().equals(uuid) && candidate.isTame() && candidate.isOwnedBy(player)) {
                return candidate;
            }
        }
        return null;
    }
}
