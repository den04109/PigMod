package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ExampleModClient {
    
    public ExampleModClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        ExampleMod.LOGGER.info("HELLO FROM CLIENT SETUP");
    }

    // ИСПРАВЛЕНО: Теперь мы регистрируем наш собственный рендерер вместо стандартного PigRenderer
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TAMEABLE_PIG.get(), TameablePigRenderer::new);
    }

    @SubscribeEvent
public static void registerScreens(RegisterMenuScreensEvent event) {
    event.register(ModMenuTypes.PIG_MENU.get(), PigScreen::new);
    event.register(ModMenuTypes.GENETIC_EXTRACTOR_MENU.get(), GeneticExtractorScreen::new);
}

    // Тинт для Пробирки с ДНК: layer1 модели (жидкость) красится в цвет, зависящий от id
    // мутации внутри — пока нет отдельной текстуры под каждую мутацию, это самый дешёвый
    // способ визуально их различать. Пустая пробирка (без мутации) остаётся белой/нейтральной.
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex != 1) {
                return 0xFFFFFF; // layer0 (стекло) тинта не требует
            }
            String mutationId = DnaTubeItem.getMutationId(stack);
            return mutationId != null ? DnaTubeItem.colorForMutation(mutationId) : 0xFFFFFF;
        }, ModItems.DNA_TUBE.get());
    }

    // НАШ СОБСТВЕННЫЙ ОТРИСОВЩИК СВИНЬИ:
    public static class TameablePigRenderer extends MobRenderer<TameablePigEntity, PigModel<TameablePigEntity>> {
        // Путь к стандартной текстуре свиньи в файлах игры
        private static final ResourceLocation PIG_LOCATION = ResourceLocation.parse("textures/entity/pig/pig.png");

        public TameablePigRenderer(EntityRendererProvider.Context context) {
            // Передаем модель свиньи и размер тени (0.7)
            super(context, new PigModel<>(context.bakeLayer(ModelLayers.PIG)), 0.7F);
        }

        @Override
        public ResourceLocation getTextureLocation(TameablePigEntity entity) {
            return PIG_LOCATION;
        }
    }
}