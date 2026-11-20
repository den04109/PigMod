package com.example.examplemod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import static com.example.examplemod.GeneticExtractorLayout.*;

public class GeneticExtractorScreen extends AbstractContainerScreen<GeneticExtractorMenu> {

    // ==== Палитра — та же тёмная тема, что и у PigScreen, для визуальной согласованности мода ====
    private static final int COLOR_PANEL_BG     = 0xFF2B2B2B;
    private static final int COLOR_BORDER_LIGHT = 0xFF4A4A4A;
    private static final int COLOR_BORDER_DARK  = 0xFF141414;
    private static final int COLOR_SLOT_BG      = 0xFF373737;
    private static final int COLOR_BAR_BG       = 0xFF1A1A1A;
    private static final int COLOR_TEXT_MUTED   = 0xFFA0A0A0;

    // ==== Акцент по Tier'у — тот же цвет, что светится у самого блока ====
    // Tier 1 — голубой, Tier 2 — зелёный, Tier 3 — фиолетовый, Tier 4 — жёлтый.
    private static final int[] ACCENT       = {0xFF56AAF6, 0xFF60D868, 0xFFB060F6, 0xFFF8BE46};
    private static final int[] ACCENT_DARK  = {0xFF1D4E7C, 0xFF23662B, 0xFF4D2079, 0xFF7A5A12};
    private static final int[] ACCENT_LIGHT = {0xFFA6D6FF, 0xFFAEF0B2, 0xFFDCB0FF, 0xFFFFE49A};

    private final int tier;
    private final int accent;
    private final int accentDark;
    private final int accentLight;

    public GeneticExtractorScreen(GeneticExtractorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.tier = menu.getTier();
        int index = Math.max(0, Math.min(3, this.tier - 1));
        this.accent = ACCENT[index];
        this.accentDark = ACCENT_DARK[index];
        this.accentLight = ACCENT_LIGHT[index];
        this.imageWidth = imageWidth(tier);
        this.imageHeight = imageHeight();
        this.titleLabelX = -1000;
        this.inventoryLabelX = -1000;
    }

    @Override
    protected void renderBg(GuiGraphics gg, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        drawPanel(gg, x, y, x + this.imageWidth, y + this.imageHeight);

        // Цветная шапка под Tier — сразу видно, какой экстрактор открыт.
        gg.fill(x + 1, y + 1, x + this.imageWidth - 1, y + 18, accentDark);
        gg.fill(x + 1, y + 18, x + this.imageWidth - 1, y + 19, accent);
        // Короткий заголовок — на Tier 1 окно узкое, длинный текст в него не влезал и наезжал на "Носители"
        gg.drawString(this.font, Tr.c("gui.pig_mod.extractor.title", tier), x + 8, y + 7, accentLight, false);

        // Слот основы
        drawAccentSlot(gg, x + baseSlotX(), y + baseSlotY());

        // Слоты ДНК-носителей
        for (int i = 0; i < tier; i++) {
            drawSlot(gg, x + carrierSlotX(i), y + carrierSlotY());
        }
        gg.drawString(this.font, Tr.c("gui.pig_mod.extractor.carriers"), x + carrierSlotX(0), y + carrierSlotY() - 10, COLOR_TEXT_MUTED, false);

        // Слот катализатора
        drawSlot(gg, x + catalystSlotX(), y + catalystSlotY());
        gg.drawString(this.font, Tr.c("gui.pig_mod.extractor.catalyst"), x + catalystSlotX() + 20, y + catalystSlotY() + 5, COLOR_TEXT_MUTED, false);

        // Прогресс-бар (стрелки-текстуры нет — рисуем полоску)
        int barX = x + arrowX(tier);
        int barY = y + arrowY();
        int progress = this.menu.getProgress();
        int maxProgress = this.menu.getMaxProgress();
        drawProgressBar(gg, barX, barY, 24, 8, maxProgress == 0 ? 0f : (float) progress / maxProgress);

        // Выходной слот
        drawAccentSlot(gg, x + outputSlotX(tier), y + outputSlotY());

        // Разделитель + инвентарь игрока
        int dividerY = y + invY() - 12;
        gg.fill(x + 8, dividerY, x + this.imageWidth - 8, dividerY + 1, accentDark);
        gg.drawString(this.font, Tr.c("gui.pig_mod.inventory"), x + invX(tier), y + invY() - 10, COLOR_TEXT_MUTED, false);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(gg, x + invX(tier) + col * SLOT, y + invY() + row * SLOT);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(gg, x + invX(tier) + col * SLOT, y + hotbarY());
        }
    }

    private void drawPanel(GuiGraphics gg, int x1, int y1, int x2, int y2) {
        gg.fill(x1, y1, x2, y2, COLOR_PANEL_BG);
        gg.fill(x1, y1, x2, y1 + 1, accent);
        gg.fill(x1, y1, x1 + 1, y2, accent);
        gg.fill(x1, y2 - 1, x2, y2, COLOR_BORDER_DARK);
        gg.fill(x2 - 1, y1, x2, y2, COLOR_BORDER_DARK);
    }

    /** x, y здесь — координаты, где рендерится ИКОНКА ПРЕДМЕТА (совпадает со Slot.x/y в меню). См. PigScreen. */
    private void drawAccentSlot(GuiGraphics gg, int x, int y) {
        gg.fill(x - 2, y - 2, x + 18, y + 18, accentDark);
        drawSlot(gg, x, y);
    }

    private void drawSlot(GuiGraphics gg, int x, int y) {
        int bx = x - 1;
        int by = y - 1;
        gg.fill(bx, by, bx + 18, by + 18, COLOR_BORDER_DARK);
        gg.fill(bx + 1, by + 1, bx + 17, by + 17, COLOR_SLOT_BG);
        gg.fill(bx + 1, by + 1, bx + 17, by + 2, COLOR_BORDER_DARK);
        gg.fill(bx + 1, by + 1, bx + 2, by + 17, COLOR_BORDER_DARK);
    }

    private void drawProgressBar(GuiGraphics gg, int x, int y, int width, int height, float ratio) {
        ratio = Math.max(0f, Math.min(1f, ratio));
        gg.fill(x, y, x + width, y + height, COLOR_BAR_BG);
        int filled = (int) (width * ratio);
        if (filled > 0) {
            gg.fill(x, y, x + filled, y + height, accent);
            gg.fill(x, y + 1, x + filled, y + 2, accentLight);
        }
        gg.fill(x, y, x + width, y + 1, COLOR_BORDER_DARK);
        gg.fill(x, y + height - 1, x + width, y + height, COLOR_BORDER_LIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Всё рисуется в renderBg — здесь намеренно пусто, чтобы не дублировать текст.
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}