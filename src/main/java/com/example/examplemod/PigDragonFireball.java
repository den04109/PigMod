package com.example.examplemod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Огненный снаряд мутации "Дракон".
 *
 * В отличие от ванильного SmallFireball:
 *  - урон и время горения цели зависят от уровня мутации (см. TameablePigEntity.dragonFireballDamage/
 *    dragonFireballFireSeconds) — чем выше lvl, тем сильнее огонь;
 *  - НИКОГДА не поджигает блоки при попадании (ни по цели, ни по стене) — именно ванильное поведение
 *    SmallFireball.onHitBlock() поджигало соседний блок и устраивало случайные пожары в листве;
 *  - на последних уровнях (см. dragonHasBreathCloud) при попадании выпускает облако дыхания в духе
 *    Эндер-дракона — нестойкое AreaEffectCloud, которое наносит урон существам внутри, но, как и у
 *    ванильного дракона, не трогает блоки и ничего не поджигает.
 */
public class PigDragonFireball extends SmallFireball {

    private final float damage;
    private final int fireSeconds;
    private final boolean breathCloud;

    public PigDragonFireball(Level level, LivingEntity owner, Vec3 direction,
                              float damage, int fireSeconds, boolean breathCloud) {
        super(level, owner, direction);
        this.damage = damage;
        this.fireSeconds = fireSeconds;
        this.breathCloud = breathCloud;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // Намеренно не вызываем super.onHitEntity(...) — у ванильного SmallFireball урон (5.0) и
        // время горения (5 сек) зашиты константами, здесь их заменяет масштабирование по уровню.
        if (!this.level().isClientSide) {
            Entity entity = result.getEntity();
            Entity owner = this.getOwner();
            int previousFire = entity.getRemainingFireTicks();
            entity.igniteForSeconds(this.fireSeconds);
            boolean hurt = entity.hurt(this.damageSources().fireball(this, owner), this.damage);
            if (!hurt) {
                entity.setRemainingFireTicks(previousFire);
            }
        }
        this.spawnBreathCloudIfNeeded(result.getLocation());
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        // Специально НЕ вызываем super.onHitBlock(...) — именно там ванильный SmallFireball ставит
        // блок огня рядом с точкой попадания. Пропуская это, снаряд просто гаснет без пожара.
        this.spawnBreathCloudIfNeeded(result.getLocation());
        this.discard();
    }

    private void spawnBreathCloudIfNeeded(Vec3 pos) {
        if (!this.breathCloud || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        AreaEffectCloud cloud = new AreaEffectCloud(serverLevel, pos.x, pos.y, pos.z);
        if (this.getOwner() instanceof LivingEntity livingOwner) {
            cloud.setOwner(livingOwner);
        }
        cloud.setParticle(ParticleTypes.DRAGON_BREATH);
        cloud.setRadius(2.5F);
        cloud.setDuration(80);
        cloud.setRadiusPerTick((0.5F - cloud.getRadius()) / cloud.getDuration());
        cloud.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 0, false, false));
        serverLevel.addFreshEntity(cloud);

        serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, pos.x, pos.y, pos.z, 25, 0.4D, 0.3D, 0.4D, 0.03D);
    }
}
