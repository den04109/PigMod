package com.example.examplemod.ai;

import com.example.examplemod.TameablePigEntity;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;

/**
 * Лучник — тонкая обёртка над ванильным RangedAttackGoal (тем же, что используют скелеты),
 * просто добавляет проверку мутации "archer" поверх обычных условий. Сама стрельба — в
 * TameablePigEntity.performRangedAttack (свинья реализует RangedAttackMob).
 */
public class ArcherGoal extends RangedAttackGoal {

    private final TameablePigEntity pig;

    public ArcherGoal(TameablePigEntity pig) {
        super(pig, 1.0D, 40, 15.0F);
        this.pig = pig;
    }

    @Override
    public boolean canUse() {
        return pig.hasMutation("archer") && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return pig.hasMutation("archer") && super.canContinueToUse();
    }
}