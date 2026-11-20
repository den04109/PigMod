package com.example.examplemod;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Снаряд-лазер. Используется двумя мутациями:
 *   * Уничтожитель — частая спам-стрельба из глаз (раньше была просто линия из частиц редстоуна);
 *   * Экстрасенс — выстрелы из порталов по бокам (раньше выглядели как фаерболы).
 *
 * Физика взята у SmallFireball: полёт по прямой с постоянной скоростью, без гравитации и без
 * торможения — ровно то, что нужно лазеру. Но внешний вид подменяется через setItem(): клиент
 * рисует не огненный шар, а 3D-модель pig_mod:item/pig_laser (красная светящаяся линия
 * с тёмными наконечниками). Никакого огня, взрыва и поджига блоков снаряд не делает.
 *
 * Свинья-стрелок и её хозяин никогда не получают урон от собственного лазера.
 */
public class PigLaser extends SmallFireball {

    private final float damage;
    private final int witherDurationTicks;
    private final int witherAmplifier;

    public PigLaser(Level level, LivingEntity owner, Vec3 direction, float damage) {
        this(level, owner, direction, damage, 0, 0);
    }

    public PigLaser(Level level, LivingEntity owner, Vec3 direction,
                    float damage, int witherDurationTicks, int witherAmplifier) {
        super(level, owner, direction);
        this.damage = damage;
        this.witherDurationTicks = witherDurationTicks;
        this.witherAmplifier = witherAmplifier;
        // Внешний вид снаряда. Данные предмета синхронизируются с клиентом вместе со спавном
        // сущности, поэтому лазер сразу рисуется своей моделью, а не ванильным огненным шаром.
        this.setItem(new ItemStack(ModItems.PIG_LASER.get()));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // Намеренно без super: у ванильного SmallFireball свой урон и поджиг цели.
        Entity shooter = this.getOwner();
        Entity hit = result.getEntity();

        if (!this.level().isClientSide && hit instanceof LivingEntity target
                && target.isAlive() && !isProtected(target, shooter)) {
            target.hurt(this.damageSources().magic(), this.damage);
            if (this.witherDurationTicks > 0) {
                target.addEffect(new MobEffectInstance(MobEffects.WITHER,
                        this.witherDurationTicks, this.witherAmplifier, false, true));
            }
        }
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        // Без super: лазер не поджигает блоки и не взрывается, просто гаснет о препятствие.
        this.discard();
    }

    /** Стрелок и его хозяин не должны страдать от собственного выстрела. */
    private boolean isProtected(LivingEntity entity, Entity shooter) {
        if (entity == shooter) {
            return true;
        }
        return shooter instanceof TamableAnimal tamable && entity == tamable.getOwner();
    }
}
