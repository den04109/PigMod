package com.example.examplemod;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Бомба, которую свинья с мутацией "Уничтожитель" сбрасывает в режиме бомбардировщика.
 *
 * Падает под обычной гравитацией (взято за основу поведение Snowball — просто удобная готовая
 * "бросаемая" физика, сама текстура/рендер сноубола не используется геймплейно). При падении на
 * блок или существо взрывается по-настоящему: ломает блоки и поджигает территорию — как и просил
 * Денис для этой способности. Исключения из урона взрыва — ближайшие игроки И сама свинья-носитель
 * (this.getOwner()): их здоровье и скорость до взрыва запоминаются и восстанавливаются сразу после,
 * так что взрыв их не калечит и не отбрасывает (без этого свинья убивала сама себя первой же бомбой).
 */
public class PigBomb extends Snowball {

    private final float damage;
    private final float explosionRadius;

    public PigBomb(Level level, LivingEntity owner, float damage, float explosionRadius) {
        super(level, owner);
        this.damage = damage;
        this.explosionRadius = explosionRadius;
        // Физика остаётся снежковой, а вот выглядит снаряд теперь настоящей бомбой:
        // клиент рисует 3D-модель pig_mod:item/pig_bomb вместо ванильного снежка.
        this.setItem(new net.minecraft.world.item.ItemStack(ModItems.PIG_BOMB.get()));
    }

    @Override
    protected void onHit(HitResult result) {
        // Намеренно не вызываем super.onHit(...) — ванильный Snowball просто гасится без взрыва.
        if (!this.level().isClientSide) {
            this.detonate();
        }
        this.discard();
    }

    private void detonate() {
        Level level = this.level();
        Vec3 pos = this.position();

        List<LivingEntity> protectedEntities = new ArrayList<>(level.getEntitiesOfClass(Player.class,
                this.getBoundingBox().inflate(this.explosionRadius * 1.5D), Player::isAlive));

        // Свинья-носитель тоже не должна пострадать от собственной бомбы, даже если взрыв
        // случится совсем рядом с ней (например, сразу после взлёта).
        Entity shooter = this.getOwner();
        if (shooter instanceof LivingEntity livingShooter && livingShooter.isAlive() && !protectedEntities.contains(livingShooter)) {
            protectedEntities.add(livingShooter);
        }

        Map<LivingEntity, double[]> before = new HashMap<>();
        for (LivingEntity entity : protectedEntities) {
            Vec3 motion = entity.getDeltaMovement();
            before.put(entity, new double[]{entity.getHealth(), motion.x, motion.y, motion.z});
        }

        // fire = true — бомба поджигает территорию при взрыве, как и было заказано.
        level.explode(this, pos.x, pos.y, pos.z, this.explosionRadius, true, Level.ExplosionInteraction.BLOCK);

        for (LivingEntity entity : protectedEntities) {
            if (!entity.isAlive()) continue;
            double[] snapshot = before.get(entity);
            entity.setHealth((float) Math.min(entity.getMaxHealth(), snapshot[0]));
            entity.setDeltaMovement(snapshot[1], snapshot[2], snapshot[3]);
        }

        // Прямой урон по мобам рядом сверх того, что даёт сама вспышка взрыва (игроков и свинью-носителя
        // не трогаем — они уже защищены выше). Это то же "damage", которое масштабируется с уровнем мутации.
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(this.explosionRadius), e -> e.isAlive() && !(e instanceof Player) && e != shooter)) {
            double dist = entity.position().distanceTo(pos);
            if (dist > this.explosionRadius) continue;
            float scaled = (float) Math.max(2.0D, this.damage * (1.0D - dist / this.explosionRadius));
            entity.hurt(level.damageSources().explosion(this, shooter), scaled);
        }
    }
}
