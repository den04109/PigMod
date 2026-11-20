package com.example.examplemod;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.List;

/**
 * Обработчики игровых событий (game bus), которые не привязаны к конкретной сущности —
 * в основном мутации, требующие вмешательства в чужой урон (Кукла Вуду).
 * Регистрируется явно в ExampleMod (без @EventBusSubscriber — так надёжнее, без сюрпризов
 * с автосканированием аннотаций).
 */
public class PigEventHandlers {

    /**
     * Кукла Вуду: весь входящий урон владельца перенаправляется на свинью с этой мутацией,
     * если она рядом (в 16 блоках) и жива. Урон обнуляется у игрока и применяется к свинье —
     * при этом у самой свиньи по-прежнему действуют её собственные иммунитеты (Нерушимый,
     * Зельеустойчивость и т.п.), так что комбинация мутаций работает ожидаемо.
     */
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        float damage = event.getNewDamage();
        if (damage <= 0) {
            return;
        }

        List<TameablePigEntity> pigs = player.level().getEntitiesOfClass(
                TameablePigEntity.class,
                player.getBoundingBox().inflate(16.0),
                pig -> pig.isAlive() && pig.isOwnedBy(player) && pig.hasMutation("voodoo")
        );
        if (pigs.isEmpty()) {
            return;
        }

        event.setNewDamage(0.0F);
        pigs.get(0).hurt(event.getSource(), damage);
    }

    /**
     * Наездник: защитные мутации свиньи передаются её управляющему хозяину.
     *
     * Нерушимый       -> иммунитет к огню/лаве
     * Сверхдыхание    -> иммунитет к утоплению/удушению
     * Планирование    -> иммунитет к урону от падения
     * Эндерсвинья     -> иммунитет к снарядам
     * Нежить          -> иммунитет к урону от мобов
     */
    public static void onRiderProtectionDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (!(player.getVehicle() instanceof TameablePigEntity pig)
                || !pig.hasMutation("rider")
                || pig.getControllingPassenger() != player) {
            return;
        }

        net.minecraft.world.damagesource.DamageSource source = event.getSource();

        // Нерушимый: огонь, лава и остальные источники из IS_FIRE.
        if (pig.hasMutation("fire_immunity")
                && source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
            event.setNewDamage(0.0F);
            player.clearFire();
            return;
        }

        // Сверхдыхание: утопление и удушение в блоке.
        if (pig.hasMutation("overbreathing")
                && (source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)
                || source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL))) {
            event.setNewDamage(0.0F);
            return;
        }

        // Планирование/Полёт: пассажир также не получает урон от падения.
        if ((pig.hasMutation("gliding") || pig.hasMutation("flight"))
                && source.is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
            event.setNewDamage(0.0F);
            return;
        }

        // Эндерсвинья: защита от снарядов переносится на хозяина.
        if (pig.hasMutation("enderpig")
                && source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
            event.setNewDamage(0.0F);
            return;
        }

    }

    /**
     * Нежить: враждебные мобы не выбирают свинью или её хозяина целью, пока они сами
     * не вступили с этим мобом в бой. После атаки или получения урона ответный агр
     * разрешается снова. Это даёт поведение в духе зомбированного пиглина.
     */
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Enemy hostile)) {
            return;
        }

        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null) {
            return;
        }

        TameablePigEntity protectedPig = null;

        if (target instanceof TameablePigEntity pig && pig.hasMutation("undead_pig")) {
            protectedPig = pig;
        } else if (target instanceof Player player
                && player.getVehicle() instanceof TameablePigEntity pig
                && pig.hasMutation("undead_pig")
                && pig.hasMutation("rider")
                && pig.isSaddled()
                && pig.isOwnedBy(player)) {
            protectedPig = pig;
        }

        if (protectedPig == null) {
            return;
        }

        // Не мешаем мобу отвечать, если защищённая сущность уже атаковала его
        // или сам моб недавно нанёс ей урон.
        boolean alreadyFighting = target.getLastHurtMob() == hostile
                || target.getLastHurtByMob() == hostile;

        if (!alreadyFighting) {
            event.setCanceled(true);
        }
    }

    /**
     * Зельеустойчивость передаётся хозяину: яд и иссушение вообще не могут быть наложены на игрока,
     * пока он управляет свиньёй с этой мутацией.
     */
    public static void onRiderProtectionEffect(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!(player.getVehicle() instanceof TameablePigEntity pig)
                || !pig.hasMutation("rider")
                || pig.getControllingPassenger() != player
                || !pig.hasMutation("potion_resistance")) {
            return;
        }

        net.minecraft.world.effect.MobEffectInstance effect = event.getEffectInstance();
        if (effect.is(net.minecraft.world.effect.MobEffects.POISON)
                || effect.is(net.minecraft.world.effect.MobEffects.WITHER)) {
            event.setResult(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /**
     * Блокирует ванильное авто-спешивание по клавише Sneak, когда игрок верхом на свинье с
     * мутацией "Полёт". У "Полёта" Sneak занят под снижение высоты (см. TameablePigEntity.travel()),
     * а ванильное поведение — Sneak тут же ссаживает игрока с ЛЮБОГО ездового моба, что сделало бы
     * снижение физически недостижимым (игрок слетал бы раньше, чем успевал начать снижаться).
     * Слезть теперь можно только явно — клавишей "Спешиться (Полёт)" (см. PigDismountPacket),
     * которая на секунду выставляет одноразовый флаг через allowNextDismount() и пропускает вызов.
     *
     * ВАЖНО: не покрывает случаи, когда pig умирает/удаляется из мира (там пассажиров сбрасывает
     * отдельная ветка кода движка, а не stopRiding()) — в таких случаях игрок и так корректно
     * оказывается спешен, это событие тут просто не участвует.
     */
    public static void onPigDismountAttempt(EntityMountEvent event) {
        if (event.isMounting()) {
            return;
        }
        if (!(event.getEntityBeingMounted() instanceof TameablePigEntity pig) || !pig.hasMutation("flight")) {
            return;
        }

        // У летающей свиньи Shift используется для снижения, поэтому блокируем только
        // именно ванильное спешивание через Shift. Явное спешивание с клавиши X должно
        // проходить без отдельного флага: один и тот же вызов должен иметь одинаковый
        // результат на клиенте и сервере, иначе после X возникает рассинхронизация
        // пассажира и игрок перестаёт нормально атаковать/взаимодействовать.
        if (event.getEntity() instanceof Player player && player.isShiftKeyDown()) {
            event.setCanceled(true);
        }
    }
}