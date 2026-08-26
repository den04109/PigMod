package com.example.examplemod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS = 
            DeferredRegister.create(BuiltInRegistries.MENU, ExampleMod.MODID);

    // Регистрируем контейнер инвентаря свиньи
    public static final DeferredHolder<MenuType<?>, MenuType<PigMenu>> PIG_MENU =
            MENUS.register("pig_menu", () -> IMenuTypeExtension.create((containerId, inv, data) -> {
                int entityId = data.readInt();
                return new PigMenu(containerId, inv, entityId);
            }));

    // Один тип меню на все 4 Tier'а Генетического Экстрактора — сам GeneticExtractorMenu
    // читает нужный tier из блок-сущности по переданной позиции блока.
    public static final DeferredHolder<MenuType<?>, MenuType<GeneticExtractorMenu>> GENETIC_EXTRACTOR_MENU =
            MENUS.register("genetic_extractor_menu", () -> IMenuTypeExtension.create(GeneticExtractorMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}