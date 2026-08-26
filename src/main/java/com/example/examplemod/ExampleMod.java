package com.example.examplemod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.entity.player.Player;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(ExampleMod.MODID)
public class ExampleMod {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "pig_mod";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "examplemod" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "examplemod" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "examplemod" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a new Block with the id "examplemod:example_block", combining the namespace and path
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    // Creates a new BlockItem with the id "examplemod:example_block", combining the namespace and path
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    // Creates a new food item with the id "examplemod:example_id", nutrition 1 and saturation 2
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    // Единая вкладка мода в креативе: собирает ВСЕ предметы и блоки pig_mod в одном месте,
    // вместо того чтобы они были раскиданы по разным ванильным вкладкам (еда/инструменты/ингредиенты).
    // Иконка — яйцо призыва боевой свиньи: единственный предмет мода, который сразу узнаваем
    // по силуэту (свинья), поэтому он лучше представляет вкладку, чем инструмент или блок.
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.pig_mod"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> ModItems.TAMEABLE_PIG_SPAWN_EGG.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                // Свинья и приручение
                output.accept(ModItems.TAMEABLE_PIG_SPAWN_EGG.get());
                output.accept(ModItems.PIG_TREAT.get());

                // Крафтовые ингредиенты
                output.accept(ModItems.SACK.get());
                output.accept(ModItems.EMPTY_DNA_TUBE.get());
                // DNA_TUBE (заполненная пробирка) в креатив не кладём — без мутации внутри она
                // бессмысленна, а самих мутаций пока нет (появятся, когда придёт список).

                // Инструменты
                output.accept(ModItems.GENETIC_INJECTOR.get());
                output.accept(ModItems.COURIER_STAFF.get());

                // Генетический Экстрактор — все 4 Tier'а, от простого к продвинутому
                output.accept(ModItems.GENETIC_EXTRACTOR_TIER1_ITEM.get());
                output.accept(ModItems.GENETIC_EXTRACTOR_TIER2_ITEM.get());
                output.accept(ModItems.GENETIC_EXTRACTOR_TIER3_ITEM.get());
                output.accept(ModItems.GENETIC_EXTRACTOR_TIER4_ITEM.get());
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public ExampleMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so creative tabs get registered
        ModEntities.register(modEventBus);
        // ПОДКЛЮЧАЕМ НАШ ПОРОСЯЧИЙ ДЕЛИКАТЕС:
        ModItems.register(modEventBus);
        // Генетический Экстрактор: блоки + их блок-сущности
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        ModMenuTypes.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        modEventBus.addListener(this::registerAttributes);

        // Сеть: пакет прокачки/удаления мутации с Вкладки 2 инвентаря свиньи (клиент -> сервер)
        modEventBus.addListener(com.example.examplemod.network.ModNetworking::register);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        // Гарантированно инициализируем реестр мутаций (см. mutation/Mutations.java)
        com.example.examplemod.mutation.Mutations.bootstrap();

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK);
        }
        // Все остальные предметы и блоки мода (свинья, деликатес, мешок, пробирки, инжектор,
        // Генетический Экстрактор) теперь собраны в displayItems() своей вкладки EXAMPLE_TAB выше —
        // так они не разбросаны по чужим вкладкам и их легко найти одним местом в креативе.
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
    event.put(
        ModEntities.TAMEABLE_PIG.get(),
        TameablePigEntity.createAttributes()
    );
}

    // Метод перехватывает правый клик по любому мобу в мире
    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        net.minecraft.world.entity.Entity target = event.getTarget();
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();

        // 1. Проверяем, что кликнули именно по ОБЫЧНОЙ ванильной свинье
        if (target instanceof Pig vanillaPig) {
            
            // 2. Проверяем, держит ли игрок наш Поросячий деликатес
            if (stack.is(ModItems.PIG_TREAT.get())) {
                
                // Останавливаем стандартное поведение игры (чтобы свинья не вошла в режим размножения)
                event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
                event.setCanceled(true);

                // Вся логика выполняется строго на сервере
                if (!event.getLevel().isClientSide()) {
                    ServerLevel level = (ServerLevel) event.getLevel();
                    
                    // Забираем 1 деликатес, если игрок не в креативе
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }

                    // Шанс приручения 1 из 3
                    if (level.random.nextInt(3) == 0) {
                        // Создаем твою боевую свинью
                        TameablePigEntity tameablePig = ModEntities.TAMEABLE_PIG.get().create(level);
                        
                        if (tameablePig != null) {
                            // Копируем координаты и поворот головы ванильной свиньи
                            tameablePig.moveTo(vanillaPig.getX(), vanillaPig.getY(), vanillaPig.getZ(), vanillaPig.getYRot(), vanillaPig.getXRot());
                            
                            // Если ванильная свинья была маленьким поросенком, боевая тоже будет малышом
                            tameablePig.setBaby(vanillaPig.isBaby());
                            
                            // Делаем игрока хозяином
                            tameablePig.tame(player);
                            tameablePig.setOrderedToSit(true); // Сажаем её сразу
                            
                            // Спавним новую боевую свинью в мир
                            level.addFreshEntity(tameablePig);
                            
                            // Пускаем красивые сердечки
                            level.broadcastEntityEvent(tameablePig, (byte) 7);
                            
                            // Удаляем старую ванильную свинью из мира
                            vanillaPig.discard();
                        }
                    } else {
                        // Если не повезло — пускаем дым неудачи вокруг обычной свиньи
                        level.broadcastEntityEvent(vanillaPig, (byte) 6);
                    }
                }
            }
        }
    }
}