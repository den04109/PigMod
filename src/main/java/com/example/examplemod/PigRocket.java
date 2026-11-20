package com.example.examplemod;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ракета мутации "Уничтожитель".
 *
 * В отличие от всех остальных снарядов в моде (огонь Дракона, лазеры Экстрасенса и т.д.), ракета
 * НАМЕРЕННО разрушает блоки при взрыве — так и было заказано для этой способности. Исключения из
 * урона взрыва — ближайшие игроки И сама свинья-носитель (на случай взрыва совсем рядом с ней):
 * их здоровье/скорость до взрыва запоминаются и восстанавливаются сразу после, так что взрыв их
 * не калечит и не отбрасывает, но по-прежнему полноценно калечит блоки, мобов и всё остальное рядом.
 */
public class PigRocket extends SmallFireball {

    private final float damage;
    private final float explosionRadius;

    public PigRocket(Level level, LivingEntity owner, Vec3 direction, float damage, float explosionRadius) {
        super(level, owner, direction);
        this.damage = damage;
        this.explosionRadius = explosionRadius;
        // Ракета — тот же боеприпас, что и бомба, только выпущенный вперёд, поэтому
        // использует ту же 3D-модель вместо ванильного огненного шара.
        this.setItem(new net.minecraft.world.item.ItemStack(ModItems.PIG_BOMB.get()));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // Прямое попадание по цели — дополнительный урон сверх того, что даст сам взрыв.
        if (!this.level().isClientSide && result.getEntity() instanceof LivingEntity target
                && target.isAlive() && !(target instanceof Player) && target != this.getOwner()) {
            target.hurt(this.damageSources().fireball(this, this.getOwner()), this.damage);
        }
        this.detonate();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        // Своя логика ниже — НЕ вызываем super.onHitBlock(...), у ванильного SmallFireball это просто
        // поджигало соседний блок, а нам нужен полноценный взрыв.
        this.detonate();
    }

    private void detonate() {
        if (this.level().isClientSide) {
            this.discard();
            return;
        }

        Level level = this.level();
        Vec3 pos = this.position();

        List<LivingEntity> protectedEntities = new ArrayList<>(level.getEntitiesOfClass(Player.class,
                this.getBoundingBox().inflate(this.explosionRadius * 1.5D), Player::isAlive));

        Entity shooter = this.getOwner();
        if (shooter instanceof LivingEntity livingShooter && livingShooter.isAlive() && !protectedEntities.contains(livingShooter)) {
            protectedEntities.add(livingShooter);
        }

        Map<LivingEntity, double[]> before = new HashMap<>();
        for (LivingEntity entity : protectedEntities) {
            Vec3 motion = entity.getDeltaMovement();
            before.put(entity, new double[]{entity.getHealth(), motion.x, motion.y, motion.z});
        }

        level.explode(this, pos.x, pos.y, pos.z, this.explosionRadius, true, Level.ExplosionInteraction.BLOCK);

        for (LivingEntity entity : protectedEntities) {
            if (!entity.isAlive()) continue;
            double[] snapshot = before.get(entity);
            entity.setHealth((float) Math.min(entity.getMaxHealth(), snapshot[0]));
            entity.setDeltaMovement(snapshot[1], snapshot[2], snapshot[3]);
        }

        this.discard();
    }
}
