package com.example.examplemod;

import com.example.examplemod.client.PigAddonLayer;
import com.example.examplemod.client.PigAddonModel;
import com.example.examplemod.client.EnderPigModel;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ExampleModClient {

    public ExampleModClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        ExampleMod.LOGGER.info("HELLO FROM CLIENT SETUP");
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TAMEABLE_PIG.get(), TameablePigRenderer::new);
    }

    /** Геометрия деталей мутаций (крылья, нимб, пушки и т.д.) — см. PigAddonModel. */
    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(PigAddonModel.LAYER, PigAddonModel::createLayer);
        event.registerLayerDefinition(EnderPigModel.LAYER, EnderPigModel::createLayer);
    }

    // Клавиша "Спешиться (Полёт)" — настраивается в Настройках управления (см. ClientKeyBindings).
    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        ClientKeyBindings.register(event);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.PIG_MENU.get(), PigScreen::new);
        event.register(ModMenuTypes.GENETIC_EXTRACTOR_MENU.get(), GeneticExtractorScreen::new);
    }

    /**
     * Цвета предметов.
     *
     * ВАЖНО про пробирки: раньше все 31 пробирка рисовались ОДНОЙ парой текстур
     * (dna_tube_glass + dna_tube_fluid), а цвет жидкости задавался тинтом по id мутации.
     * Именно из-за этого они и выглядели пустыми ячейками в креативе — тинт перекрывал
     * слои. Теперь у каждой мутации своя готовая текстура (item/dna_tube_&lt;id&gt;.png),
     * тинт им не нужен вообще, и обработчик здесь для них НЕ регистрируется.
     *
     * Остаётся только старая универсальная пробирка pig_mod:dna_tube (для старых миров):
     * у неё цвет по-прежнему считается по NBT. Альфа-канал задаётся явно (0xFF...), иначе
     * прозрачный цвет может полностью убрать слой.
     */
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex != 1) {
                return 0xFFFFFFFF; // layer0 (стекло) — без перекраски
            }
            String mutationId = DnaTubeItem.getMutationId(stack);
            int color = mutationId != null ? DnaTubeItem.colorForMutation(mutationId) : 0xFFFFFF;
            return 0xFF000000 | color;
        }, ModItems.DNA_TUBE.get());

        // Яйцо призыва: у него своя готовая текстура, поэтому любой слой остаётся как есть.
        // Явная регистрация нужна, чтобы ванильный обработчик яиц призыва (он отдаёт цвет
        // без альфы) не сделал иконку невидимой.
        event.register((stack, tintIndex) -> 0xFFFFFFFF, ModItems.TAMEABLE_PIG_SPAWN_EGG.get());
    }

    /**
     * Отрисовщик свиньи.
     *
     * Сама свинья — ванильная PigModel с ванильной анимацией (ходьба, поворот головы)
     * и ванильной текстурой. Мутации, которые меняют саму свинью (эндерсвинья, нежить,
     * взрывная, дракон, экстрасенс, уничтожитель), просто подменяют текстуру. Всё
     * навесное — отдельным слоем PigAddonLayer поверх.
     */
    public static class TameablePigRenderer extends MobRenderer<TameablePigEntity, PigModel<TameablePigEntity>> {

        private static final ResourceLocation PIG_LOCATION = ResourceLocation.parse("textures/entity/pig/pig.png");

        /**
         * Полноразмерные текстуры тела не накладываются друг на друга.
         * Вместо этого выбирается ОДИН внешний вид по фиксированному приоритету.
         * Это одновременно решает две проблемы: у свиньи никогда не бывает
         * текстуры одной мутации + модели другой, а добавление новой mutation-скин
         * не ломает модель Эндер-свиньи.
         *
         * Для новой мутации, которая меняет весь внешний вид свиньи, достаточно
         * добавить одну строку сюда. customModel=true нужно только для мутаций,
         * которым нужна отдельная геометрия.
         */
        private enum PigAppearance {
            DESTROYER("destroyer", false),
            DRAGON("dragon", false),
            EXPLOSIVE_PIG("explosive_pig", false),
            ENDERPIG("enderpig", true),
            UNDEAD_PIG("undead_pig", false),
            PSYCHIC("psychic", false);

            private final String mutationId;
            private final boolean customModel;
            private final ResourceLocation texture;

            PigAppearance(String mutationId, boolean customModel) {
                this.mutationId = mutationId;
                this.customModel = customModel;
                this.texture = ResourceLocation.fromNamespaceAndPath(
                        ExampleMod.MODID, "textures/entity/pig_" + mutationId + ".png");
            }
        }

        private final PigModel<TameablePigEntity> normalModel;
        private final EnderPigModel enderModel;

        public TameablePigRenderer(EntityRendererProvider.Context context) {
            super(context, new PigModel<>(context.bakeLayer(ModelLayers.PIG)), 0.7F);
            this.normalModel = this.model;
            this.enderModel = new EnderPigModel(context.bakeLayer(EnderPigModel.LAYER));
            this.addLayer(new PigAddonLayer(this, context));
        }

        /**
         * Возвращает внешний вид с наивысшим приоритетом среди mutation,
         * которые полностью меняют текстуру свиньи.
         */
        private static PigAppearance resolveAppearance(TameablePigEntity entity) {
            for (PigAppearance appearance : PigAppearance.values()) {
                if (entity.hasMutation(appearance.mutationId)) {
                    return appearance;
                }
            }
            return null;
        }

        @Override
        public void render(TameablePigEntity entity, float entityYaw, float partialTicks,
                           com.mojang.blaze3d.vertex.PoseStack poseStack,
                           net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {
            PigModel<TameablePigEntity> previousModel = this.model;
            PigAppearance appearance = resolveAppearance(entity);

            // Геометрия Эндер-свиньи используется только тогда, когда именно
            // ENDERPIG выиграл выбор полнотекстурной мутации. Если, например,
            // у свиньи одновременно есть dragon + enderpig, остаётся обычная
            // модель и dragon-текстура — без смешивания несовместимых моделей.
            this.model = appearance != null && appearance.customModel
                    ? this.enderModel
                    : this.normalModel;
            try {
                super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            } finally {
                this.model = previousModel;
            }
        }

        @Override
        public ResourceLocation getTextureLocation(TameablePigEntity entity) {
            PigAppearance appearance = resolveAppearance(entity);
            return appearance != null ? appearance.texture : PIG_LOCATION;
        }
    }

}
