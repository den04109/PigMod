package com.example.examplemod;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Лазерный снаряд мутации "Экстрасенс".
 *
 * Раньше это был ванильный WitherSkull, у которого своя медленная "инерционная" физика
 * (как у визера) — снаряд плёлся, заметно тормозил в полёте и по пути мог случайно задеть
 * саму свинью или хозяина иссушением/взрывом. Теперь снаряд сделан на базе Fireball
 * (как у Дракона) — летит по прямой с постоянной высокой скоростью, без торможения и без
 * гравитации, то есть ведёт себя как выстрел лазером/пулей.
 *
 * При попадании по цели наносит урон и накладывает иссушение — БЕЗ какого-либо взрыва
 * (ни визуального, ни по урону/блокам). При попадании в блок снаряд просто гаснет.
 * Свинья-стрелок и её хозяин никогда не получают урон/иссушение от этого снаряда.
 */
public class PigPsychicSkull extends SmallFireball {

    private final float damage;
    private final int witherDurationTicks;
    private final int witherAmplifier;

    public PigPsychicSkull(Level level, LivingEntity owner, Vec3 direction,
                            float damage, int witherDurationTicks, int witherAmplifier) {
        super(level, owner, direction);
        this.damage = damage;
        this.witherDurationTicks = witherDurationTicks;
        this.witherAmplifier = witherAmplifier;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // Намеренно не вызываем super.onHitEntity(...) — у ванильного SmallFireball свой фиксированный
        // урон и поджиг цели, здесь вместо огня — урон + иссушение, без всякого взрыва.
        Entity shooter = this.getOwner(); // это сама свинья (она передаётся как owner при выстреле)
        Entity hitEntity = result.getEntity();

        if (!this.level().isClientSide && hitEntity instanceof LivingEntity target
                && target.isAlive() && !isProtected(target, shooter)) {
            applyHit(target, this.damage);
        }
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        // Своя логика — НЕ вызываем super.onHitBlock(...), чтобы не было ванильного урона/поджига блоков.
        // Взрыва здесь нет вообще: снаряд просто гаснет при попадании в блок.
        this.discard();
    }

    private void applyHit(LivingEntity entity, float amount) {
        entity.hurt(this.damageSources().magic(), amount);
        if (this.witherDurationTicks > 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.WITHER, this.witherDurationTicks, this.witherAmplifier, false, true));
        }
    }

    /** Свинья-стрелок и её хозяин никогда не должны получать урон/иссушение от собственного снаряда. */
    private boolean isProtected(LivingEntity entity, Entity shooter) {
        if (entity == shooter) {
            return true;
        }
        if (shooter instanceof TamableAnimal tamable && entity == tamable.getOwner()) {
            return true;
        }
        return false;
    }
}
