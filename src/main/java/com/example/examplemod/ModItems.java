package com.example.examplemod; // Убедись, что эта строка совпадает с твоим пакетом

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

public class ModItems {
    // Создаем регистратор для наших предметов с привязкой к твоему MOD_ID
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, "pig_mod");

    // Регистрируем сам деликатес
    // Теперь это просто предмет, игрок не сможет его съесть
    public static final DeferredHolder<Item, Item> PIG_TREAT = ITEMS.register("pig_treat", 
        () -> new Item(new Item.Properties())
    );
    // Метод, который мы вызовем в главном классе, чтобы NeoForge "увидел" этот список
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    public static final DeferredHolder<Item, Item> WARRIOR_UPGRADE = ITEMS.register("warrior_upgrade",
        () -> new UpgradeItem(new Item.Properties().stacksTo(1), "Воин"));

    public static final DeferredHolder<Item, Item> TAMEABLE_PIG_SPAWN_EGG = ITEMS.register("tameable_pig_spawn_egg",
        () -> new DeferredSpawnEggItem(ModEntities.TAMEABLE_PIG, 0xF0A5A5, 0xDB6359, new Item.Properties()));

    // Мешок — свой предмет (не ванильный minecraft:bundle, который в 1.21.1 всё ещё экспериментальный).
    // Пока это просто крафтовый ингредиент без функционала бандла (без своего инвентаря внутри) —
    // нужен только для крафта Поросячьего деликатеса. Текстуру используем готовую ванильную (см. модель).
    public static final DeferredHolder<Item, Item> SACK = ITEMS.register("sack",
        () -> new Item(new Item.Properties()));

    // ==== Предметы для мутаций (см. пакет mutation) ====

    // Пустая пробирка — сырьё для Генетического Экстрактора (появится на следующем этапе).
    // Отдельный простой Item, т.к. никаких данных в ней не хранится, пока она пустая.
    public static final DeferredHolder<Item, Item> EMPTY_DNA_TUBE = ITEMS.register("empty_dna_tube",
        () -> new Item(new Item.Properties()));

    // Заполненная пробирка — DnaTubeItem хранит id мутации в NBT (см. DnaTubeItem.createFilled).
    public static final DeferredHolder<Item, Item> DNA_TUBE = ITEMS.register("dna_tube",
        () -> new DnaTubeItem(new Item.Properties().stacksTo(16)));

    // Генетический Инжектор — многоразовый инструмент, поэтому stacksTo(1), как у обычных тулов.
    // Прочность пока не подключена (не логичной без формулы "сколько уколов на 1 инжектор") —
    // если понадобится, легко добавить .durability(N) + вызов hurtAndBreak() в applyToPig().
    public static final DeferredHolder<Item, Item> GENETIC_INJECTOR = ITEMS.register("genetic_injector",
        () -> new GeneticInjectorItem(new Item.Properties().stacksTo(1)));

    // ==== BlockItem'ы для 4 Tier'ов Генетического Экстрактора (см. ModBlocks.java) ====
    public static final DeferredHolder<Item, Item> GENETIC_EXTRACTOR_TIER1_ITEM = ITEMS.register("genetic_extractor_tier1",
        () -> new net.minecraft.world.item.BlockItem(ModBlocks.GENETIC_EXTRACTOR_TIER1.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> GENETIC_EXTRACTOR_TIER2_ITEM = ITEMS.register("genetic_extractor_tier2",
        () -> new net.minecraft.world.item.BlockItem(ModBlocks.GENETIC_EXTRACTOR_TIER2.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> GENETIC_EXTRACTOR_TIER3_ITEM = ITEMS.register("genetic_extractor_tier3",
        () -> new net.minecraft.world.item.BlockItem(ModBlocks.GENETIC_EXTRACTOR_TIER3.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> GENETIC_EXTRACTOR_TIER4_ITEM = ITEMS.register("genetic_extractor_tier4",
        () -> new net.minecraft.world.item.BlockItem(ModBlocks.GENETIC_EXTRACTOR_TIER4.get(), new Item.Properties()));

    // Посох Курьера — привязывает свинью-курьера к сундуку (см. CourierStaffItem)
    public static final DeferredHolder<Item, Item> COURIER_STAFF = ITEMS.register("courier_staff",
        () -> new CourierStaffItem(new Item.Properties().stacksTo(1)));
}