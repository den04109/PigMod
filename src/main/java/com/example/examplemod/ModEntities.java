package com.example.examplemod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = 
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, ExampleMod.MODID);

    // Регистрируем саму сущность свиньи
    public static final DeferredHolder<EntityType<?>, EntityType<TameablePigEntity>> TAMEABLE_PIG =
            ENTITY_TYPES.register("tameable_pig", () -> EntityType.Builder.of(TameablePigEntity::new, MobCategory.CREATURE)
                    .sized(0.9F, 0.9F) // Размеры хитбокса (как у обычной свиньи)
                    .build("tameable_pig"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}