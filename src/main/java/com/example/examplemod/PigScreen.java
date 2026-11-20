package com.example.examplemod;

import com.example.examplemod.mutation.MutationDefinition;
import com.example.examplemod.mutation.Mutations;
import com.example.examplemod.network.MutationActionPacket;
import com.example.examplemod.network.PigRaidModeTogglePacket;
import com.example.examplemod.network.PigCommandCyclePacket;
import com.example.examplemod.network.PigSizeCyclePacket;
import com.example.examplemod.network.PigRenamePacket;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class PigScreen extends AbstractContainerScreen<PigMenu> {

    // =========================
    // Палитра
    // =========================
    private static final int COLOR_PANEL_BG       = 0xFF25282C;
    private static final int COLOR_PANEL_ALT     = 0xFF2D3035;
    private static final int COLOR_BORDER_LIGHT  = 0xFF596069;
    private static final int COLOR_BORDER_DARK   = 0xFF15171A;
    private static final int COLOR_SLOT_BG       = 0xFF30343A;
    private static final int COLOR_ROW_A         = 0xFF30343A;
    private static final int COLOR_ROW_B         = 0xFF2B2F34;
    private static final int COLOR_TEXT_MAIN     = 0xFFE7E9EC;
    private static final int COLOR_TEXT_MUTED    = 0xFFAAB0B8;
    private static final int COLOR_TEXT_GOLD     = 0xFFE0B850;
    private static final int COLOR_TEXT_GREEN    = 0xFF71D36B;
    private static final int COLOR_TEXT_RED      = 0xFFE06A6A;
    private static final int COLOR_BAR_BG        = 0xFF17191C;
    private static final int COLOR_EXP           = 0xFF56C6DE;
    private static final int COLOR_HP            = 0xFF72C96A;
    private static final int COLOR_HUNGER        = 0xFFE1A63A;
    private static final int COLOR_TAB           = 0xFF353A41;
    private static final int COLOR_TAB_HOVER     = 0xFF41474F;
    private static final int COLOR_BUTTON        = 0xFF41474C;
    private static final int COLOR_BUTTON_HOVER  = 0xFF50565E;
    private static final int COLOR_BUTTON_OK     = 0xFF467E45;
    private static final int COLOR_BUTTON_OK_H   = 0xFF5A9C55;
    private static final int COLOR_BUTTON_BAD    = 0xFF8E4040;
    private static final int COLOR_BUTTON_BAD_H  = 0xFFB14E4E;
    private static final int COLOR_SCROLL_TRACK  = 0xFF1D2024;
    private static final int COLOR_SCROLL_THUMB  = 0xFF747C86;
    private static final int COLOR_SCROLL_HOVER  = 0xFF929AA4;
    private static final int COLOR_OVERLAY       = 0xC8000000;

    // =========================
    // Геометрия
    // =========================
    private static final int GUI_W = 320;
    private static final int GUI_H = 290;

    private static final int HEADER_X = 10;
    private static final int HEADER_Y = 7;
    private static final int HEADER_W = GUI_W - 20;
    private static final int HEADER_H = 44;

    private static final int TABS_Y = 55;
    private static final int TAB_H = 22;
    private static final int TAB_GAP = 4;
    private static final int TAB_W = (GUI_W - 20 - TAB_GAP * 2) / 3;

    private static final int CONTENT_X = 10;
    private static final int CONTENT_Y = 82;
    private static final int CONTENT_W = GUI_W - 20;
    private static final int CONTENT_H = GUI_H - CONTENT_Y - 10;

    // Главная страница
    private static final int STATUS_MODEL_W = 112;
    private static final int STATUS_STATS_X = CONTENT_X + STATUS_MODEL_W + 8;
    private static final int STATUS_STATS_W = CONTENT_W - STATUS_MODEL_W - 8;

    // Мутации
    private static final int MUT_HEADER_H = 25;
    private static final int MUT_ROW_H = 57;
    private static final int MUT_SCROLLBAR_W = 8;
    private static final int MUT_LIST_W = CONTENT_W;
    private static final int MUT_LIST_X = CONTENT_X;
    private static final int MUT_LIST_Y = CONTENT_Y;
    private static final int MUT_LIST_H = CONTENT_H;
    private static final int MUT_VIEW_Y = MUT_LIST_Y + MUT_HEADER_H;
    private static final int MUT_VIEW_H = MUT_LIST_H - MUT_HEADER_H;

    // Инвентарь
    private static final int INV_BOX_X = CONTENT_X;
    private static final int INV_BOX_Y = CONTENT_Y;
    private static final int INV_BOX_W = CONTENT_W;
    private static final int INV_BOX_H = CONTENT_H;
    private static final int INV_GRID_W = 9 * 18;
    private static final int INV_GRID_H = 3 * 18;

    private int activeTab = 0; // 0 = Основная, 1 = Мутации, 2 = Инвентарь
    private int mutationScroll = 0;
    private boolean scrollDragging = false;
    private int scrollDragOffset = 0;

    private int lastMouseX;
    private int lastMouseY;

    private final List<MutationRowButtons> lastRowButtons = new ArrayList<>();
    private final List<SpecialToggleButton> lastSpecialButtons = new ArrayList<>();

    private String pendingRemoveMutationId = null;
    private int dialogYesX, dialogYesY, dialogNoX, dialogNoY;
    private int commandButtonX, commandButtonY, commandButtonW, commandButtonH;
    private int renameButtonX, renameButtonY, renameButtonW, renameButtonH;
    private EditBox nameBox;

    private static final int DIALOG_W = 220;
    private static final int DIALOG_H = 82;
    private static final int DIALOG_BTN_W = 82;
    private static final int DIALOG_BTN_H = 18;

    public PigScreen(PigMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_W;
        this.imageHeight = GUI_H;
        this.titleLabelX = -1000;
        this.inventoryLabelX = -1000;
    }

    /**
     * Поле имени создаётся здесь, а не в конструкторе: в конструкторе this.font ещё null
     * (его задаёт Screen.init), и поле падало с NullPointerException при первой отрисовке.
     * init() вызывается и при изменении размера окна, поэтому сохраняем набранный текст.
     */
    @Override
    protected void init() {
        super.init();
        String oldValue = this.nameBox != null ? this.nameBox.getValue() : "";
        boolean wasEditing = this.nameBox != null && this.nameBox.visible;
        this.nameBox = new EditBox(this.font, 0, 0, 120, 16, Tr.c("gui.pig_mod.pig.name_field"));
        this.nameBox.setMaxLength(32);
        this.nameBox.setValue(oldValue);
        this.nameBox.visible = wasEditing;
        this.addRenderableWidget(this.nameBox);
        if (wasEditing) {
            this.setFocused(this.nameBox);
        }
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        super.render(gg, mouseX, mouseY, partialTick);
        this.renderTooltip(gg, mouseX, mouseY);
    }

    /**
     * AbstractContainerScreen renders each slot through renderSlot().
     * We only draw the pig inventory slots on page 3.
     */
    @Override
    protected void renderSlot(GuiGraphics gg, Slot slot) {
        if (activeTab == 2) {
            super.renderSlot(gg, slot);
        }
    }

    /**
     * Slot hit-testing is disabled outside the inventory page, so moving the
     * mouse over mutation cards can never select/highlight an invisible slot.
     */
    @Override
    public Slot getSlotUnderMouse() {
        if (activeTab != 2) {
            return null;
        }
        return super.getSlotUnderMouse();
    }

    @Override
    protected void renderBg(GuiGraphics gg, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - GUI_W) / 2;
        int y = (this.height - GUI_H) / 2;

        drawPanel(gg, x, y, GUI_W, GUI_H);
        drawHeader(gg, x, y);
        drawTabs(gg, x, y, mouseX, mouseY);

        TameablePigEntity pig = this.menu.getPig();
        switch (activeTab) {
            case 0 -> renderMainTab(gg, x, y, mouseX, mouseY, pig);
            case 1 -> renderMutationsTab(gg, x, y, pig);
            case 2 -> renderInventoryTab(gg, x, y, pig);
            default -> activeTab = 0;
        }

        if (pendingRemoveMutationId != null) {
            renderRemoveConfirmDialog(gg, mouseX, mouseY);
        }
    }

    private void drawHeader(GuiGraphics gg, int x, int y) {
        drawSunkenBox(gg, x + HEADER_X, y + HEADER_Y, x + HEADER_X + HEADER_W, y + HEADER_Y + HEADER_H);

        TameablePigEntity pig = this.menu.getPig();
        if (pig == null) {
            gg.drawString(this.font, Tr.s("gui.pig_mod.pig.fallback"), x + 18, y + 14, COLOR_TEXT_GOLD, false);
            return;
        }

        int iconCenterX = x + HEADER_X + 28;
        int iconTop = y + HEADER_Y + 3;
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                gg, iconCenterX - 22, iconTop,
                iconCenterX + 22, iconTop + 38,
                31, 0.0f, this.lastMouseX, this.lastMouseY, pig);

        int textX = x + 58;
        String pigName = pig.hasCustomName() ? pig.getName().getString() : Tr.s("entity.pig_mod.tameable_pig");
        gg.drawString(this.font, pigName, textX, y + 10, COLOR_TEXT_GOLD, false);
        renameButtonX = x + 150; renameButtonY = y + 8; renameButtonW = 18; renameButtonH = 18;
        drawButton(gg, renameButtonX, renameButtonY, renameButtonW, renameButtonH, "✎", COLOR_BUTTON, COLOR_BUTTON_HOVER, isHovering(renameButtonX,renameButtonY,renameButtonW,renameButtonH,lastMouseX,lastMouseY));
        // Поле ввода имени не прячем каждый кадр (раньше из-за этого оно исчезало сразу после клика
        // по карандашу): позицию задаём только пока не редактируем, прячем только вне вкладки "Основная".
        if (!this.nameBox.visible) {
            this.nameBox.setX(textX);
            this.nameBox.setY(y + 9);
        }
        if (activeTab != 0) {
            this.nameBox.visible = false;
        }
        gg.drawString(this.font, Tr.s("gui.pig_mod.level", pig.getPigLevel()), textX, y + 24, COLOR_TEXT_MAIN, false);
        gg.drawString(this.font, Tr.s("gui.pig_mod.points", pig.getSkillPoints()), textX + 72, y + 24, COLOR_TEXT_GOLD, false);

        int barX = x + 184;
        int barW = 128;
        int expNeeded = Math.max(1, pig.getPigLevel() * 15);
        gg.drawString(this.font, Tr.s("gui.pig_mod.exp", pig.getPigExp(), expNeeded), barX, y + 8, COLOR_TEXT_MUTED, false);
        drawBar(gg, barX, y + 19, barW, 5, (float) pig.getPigExp() / expNeeded, COLOR_EXP);

        float maxHealth = (float) Math.max(1.0, pig.getDisplayedMaxHealth());
        float healthRatio = pig.getHealth() / maxHealth;
        gg.drawString(this.font, Tr.s("gui.pig_mod.health_value", (int) pig.getHealth(), (int) maxHealth),
                barX, y + 29, COLOR_TEXT_MUTED, false);
        drawBar(gg, barX, y + 40, barW, 5, healthRatio, COLOR_HP);
    }

    private void drawTabs(GuiGraphics gg, int x, int y, int mouseX, int mouseY) {
        String[] labels = {Tr.s("gui.pig_mod.tab.main"), Tr.s("gui.pig_mod.tab.mutations"), Tr.s("gui.pig_mod.inventory")};
        for (int i = 0; i < 3; i++) {
            int tx = x + 10 + i * (TAB_W + TAB_GAP);
            drawTab(gg, tx, y + TABS_Y, labels[i], activeTab == i, mouseX, mouseY);
        }
    }

    private void drawTab(GuiGraphics gg, int tx, int ty, String label, boolean active, int mouseX, int mouseY) {
        boolean hover = isHovering(tx, ty, TAB_W, TAB_H, mouseX, mouseY);
        int bg = active ? COLOR_TAB_HOVER : (hover ? 0xFF2F343A : COLOR_TAB);
        gg.fill(tx, ty, tx + TAB_W, ty + TAB_H, bg);
        drawBorder(gg, tx, ty, TAB_W, TAB_H);
        gg.drawCenteredString(this.font, label, tx + TAB_W / 2, ty + 7,
                active ? COLOR_TEXT_GOLD : COLOR_TEXT_MAIN);
    }

    // =========================
    // Страница 1 — Основная
    // =========================

    private void renderMainTab(GuiGraphics gg, int x, int y, int mouseX, int mouseY, TameablePigEntity pig) {
        drawSunkenBox(gg, x + CONTENT_X, y + CONTENT_Y,
                x + CONTENT_X + STATUS_MODEL_W, y + CONTENT_Y + CONTENT_H);
        drawSunkenBox(gg, x + STATUS_STATS_X, y + CONTENT_Y,
                x + STATUS_STATS_X + STATUS_STATS_W, y + CONTENT_Y + CONTENT_H);

        commandButtonX = commandButtonY = commandButtonW = commandButtonH = 0;

        if (pig == null) {
            gg.drawCenteredString(this.font, Tr.s("gui.pig_mod.pig_not_found"),
                    x + GUI_W / 2, y + CONTENT_Y + 84, COLOR_TEXT_MUTED);
            return;
        }

        // Крупная модель
        int modelLeft = x + CONTENT_X + 5;
        int modelTop = y + CONTENT_Y + 5;
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                gg, modelLeft, modelTop,
                modelLeft + STATUS_MODEL_W - 10, modelTop + 94,
                58, 0.0f, mouseX, mouseY, pig);

        int mx = x + CONTENT_X + 8;
        int my = y + CONTENT_Y + 105;
        gg.drawCenteredString(this.font, Tr.s("gui.pig_mod.info"), x + CONTENT_X + STATUS_MODEL_W / 2, my, COLOR_TEXT_GOLD);

        String gender = Tr.s(pig.isFemale() ? "gui.pig_mod.gender.female" : "gui.pig_mod.gender.male");
        gg.drawString(this.font, Tr.s("gui.pig_mod.gender", gender), mx, my + 15, COLOR_TEXT_MAIN, false);

        int hunger = pig.getHunger();
        int maxHunger = pig.getMaxHunger();
        gg.drawString(this.font, Tr.s("gui.pig_mod.hunger"), mx, my + 30, COLOR_TEXT_MUTED, false);
        drawRightString(gg, hunger + " / " + maxHunger, x + CONTENT_X + STATUS_MODEL_W - 8, my + 30, COLOR_TEXT_MAIN);
        drawBar(gg, mx, my + 41, STATUS_MODEL_W - 16, 7,
                (float) hunger / Math.max(1, maxHunger), COLOR_HUNGER);

        int sx = x + STATUS_STATS_X + 8;
        int sy = y + CONTENT_Y + 9;
        int sw = STATUS_STATS_W - 16;

        gg.drawString(this.font, Tr.s("gui.pig_mod.stats"), sx, sy, COLOR_TEXT_GOLD, false);
        int health = (int) pig.getHealth();
        int maxHealth = (int) Math.max(1.0, pig.getDisplayedMaxHealth());
        gg.drawString(this.font, Tr.s("gui.pig_mod.health"), sx, sy + 19, COLOR_TEXT_MUTED, false);
        drawRightString(gg, health + " / " + maxHealth, sx + sw, sy + 19, COLOR_TEXT_MAIN);
        drawBar(gg, sx, sy + 31, sw, 7, (float) health / Math.max(1, maxHealth), COLOR_HP);

        double damage = pig.getDisplayedAttackDamage();
        double armor = pig.getDisplayedArmor();
        double speed = pig.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);

        drawStatLine(gg, sx, sy + 50, sw, Tr.s("gui.pig_mod.damage"), String.format(java.util.Locale.ROOT, "%.1f", damage));
        drawStatLine(gg, sx, sy + 64, sw, Tr.s("gui.pig_mod.armor"), String.format(java.util.Locale.ROOT, "%.1f", armor));
        drawStatLine(gg, sx, sy + 78, sw, Tr.s("gui.pig_mod.speed"), String.format(java.util.Locale.ROOT, "%.2f", speed));

        gg.fill(sx, sy + 96, sx + sw, sy + 97, COLOR_BORDER_DARK);
        gg.drawString(this.font, Tr.s("gui.pig_mod.progress"), sx, sy + 105, COLOR_TEXT_GOLD, false);
        drawStatLine(gg, sx, sy + 122, sw, Tr.s("gui.pig_mod.skill_points"), String.valueOf(pig.getSkillPoints()));
        drawStatLine(gg, sx, sy + 136, sw, Tr.s("gui.pig_mod.mutation_count"), String.valueOf(countUnlockedMutations(pig)));

        gg.drawString(this.font, Tr.s("gui.pig_mod.command"), sx, sy + 157, COLOR_TEXT_MUTED, false);
        commandButtonX = sx;
        commandButtonY = y + CONTENT_Y + 176;
        commandButtonW = sw;
        commandButtonH = 20;
        boolean commandHover = isHovering(commandButtonX, commandButtonY, commandButtonW, commandButtonH, mouseX, mouseY);
        int commandColor = pig.getCommand() == PigCommand.ATTACK ? COLOR_BUTTON_BAD
                : pig.getCommand() == PigCommand.WORK ? COLOR_BUTTON_OK : COLOR_BUTTON;
        int commandHoverColor = pig.getCommand() == PigCommand.ATTACK ? COLOR_BUTTON_BAD_H
                : pig.getCommand() == PigCommand.WORK ? COLOR_BUTTON_OK_H : COLOR_BUTTON_HOVER;
        drawButton(gg, commandButtonX, commandButtonY, commandButtonW, commandButtonH,
                pig.getCommand().getDisplayName(), commandColor, commandHoverColor, commandHover);

    }

    private void drawStatLine(GuiGraphics gg, int x, int y, int w, String label, String value) {
        gg.drawString(this.font, label, x, y, COLOR_TEXT_MUTED, false);
        drawRightString(gg, value, x + w, y, COLOR_TEXT_MAIN);
    }

    private int countUnlockedMutations(TameablePigEntity pig) {
        int count = 0;
        for (String id : Mutations.all().keySet()) {
            if (pig.hasMutation(id)) count++;
        }
        return count;
    }

    // =========================
    // Страница 2 — Мутации
    // =========================

    private void renderMutationsTab(GuiGraphics gg, int x, int y, TameablePigEntity pig) {
        lastRowButtons.clear();
        lastSpecialButtons.clear();

        int listX = x + MUT_LIST_X;
        int listY = y + MUT_LIST_Y;

        drawSunkenBox(gg, listX, listY, listX + MUT_LIST_W, listY + MUT_LIST_H);
        gg.drawString(this.font, Tr.s("gui.pig_mod.mutations.active"), listX + 8, listY + 7, COLOR_TEXT_MAIN, false);
        if (pig != null) {
            drawRightString(gg, Tr.s("gui.pig_mod.mutations.points_available", pig.getSkillPoints()),
                    listX + MUT_LIST_W - 16, listY + 7, COLOR_TEXT_GOLD);
        }
        gg.fill(listX + 1, listY + MUT_HEADER_H - 1,
                listX + MUT_LIST_W - 1, listY + MUT_HEADER_H, COLOR_BORDER_DARK);

        if (pig == null) return;

        List<String> unlocked = new ArrayList<>();
        for (String id : Mutations.all().keySet()) {
            if (pig.hasMutation(id)) unlocked.add(id);
        }

        int visibleRows = Math.max(1, MUT_VIEW_H / MUT_ROW_H);
        int maxScroll = Math.max(0, unlocked.size() - visibleRows);
        mutationScroll = Math.max(0, Math.min(mutationScroll, maxScroll));

        if (unlocked.isEmpty()) {
            gg.drawCenteredString(this.font, Tr.s("gui.pig_mod.mutations.none"),
                    listX + MUT_LIST_W / 2, listY + 72, COLOR_TEXT_MUTED);
            gg.drawCenteredString(this.font, Tr.s("gui.pig_mod.mutations.none_hint"),
                    listX + MUT_LIST_W / 2, listY + 88, COLOR_TEXT_MUTED);
            return;
        }

        int viewRight = listX + MUT_LIST_W - MUT_SCROLLBAR_W - 2;
        gg.enableScissor(listX + 2, listY + MUT_HEADER_H, viewRight, listY + MUT_LIST_H - 2);
        for (int row = 0; row < visibleRows; row++) {
            int index = mutationScroll + row;
            if (index >= unlocked.size()) break;
            drawMutationRow(gg, listX + 2, listY + MUT_HEADER_H + row * MUT_ROW_H,
                    viewRight - (listX + 2), unlocked.get(index), pig, row);
        }
        gg.disableScissor();

        drawMutationScrollbar(gg, viewRight + 1, listY + MUT_HEADER_H, MUT_SCROLLBAR_W - 2,
                MUT_VIEW_H - 3, unlocked.size(), visibleRows, maxScroll);

        if (maxScroll > 0) {
            String range = Tr.s("gui.pig_mod.mutations.range", mutationScroll + 1, Math.min(unlocked.size(), mutationScroll + visibleRows), unlocked.size());
            drawRightString(gg, range, viewRight - 3, listY + MUT_LIST_H - 9, COLOR_TEXT_MUTED);
        }
    }

    private void drawMutationRow(GuiGraphics gg, int rowX, int rowY, int rowW,
                                 String mutationId, TameablePigEntity pig, int rowIndex) {
        int bg = rowIndex % 2 == 0 ? COLOR_ROW_A : COLOR_ROW_B;
        gg.fill(rowX, rowY, rowX + rowW, rowY + MUT_ROW_H - 1, bg);
        gg.fill(rowX, rowY + MUT_ROW_H - 1, rowX + rowW, rowY + MUT_ROW_H, COLOR_BORDER_DARK);

        MutationDefinition def = Mutations.get(mutationId);
        int level = pig.getMutationLevel(mutationId);
        int maxLevel = def != null ? def.getMaxLevel() : level;
        String name = def != null ? Component.translatable(def.getDisplayNameKey()).getString() : mutationId;
        String[] description = mutationDescription(mutationId);

        int rightX = rowX + rowW - 90;
        int textX = rowX + 7;
        int textMaxW = Math.max(80, rightX - textX - 8);

        if (this.font.width(name) > textMaxW) {
            name = this.font.plainSubstrByWidth(name, Math.max(20, textMaxW - this.font.width("…"))) + "…";
        }
        gg.drawString(this.font, name, textX, rowY + 5, COLOR_TEXT_MAIN, false);

        drawWrappedTwoLines(gg, description, textX, rowY + 17, textMaxW, COLOR_TEXT_MUTED);

        String effect = def != null ? def.describeLevel(level) : null;
        String levelText = Tr.s("gui.pig_mod.mutations.level", level, maxLevel);
        if (effect != null && !effect.isBlank()) {
            levelText += "  •  " + effect;
        }
        gg.drawString(this.font, levelText, textX, rowY + 43, COLOR_TEXT_GREEN, false);

        boolean canUpgrade = def != null && level < maxLevel;
        int cost = def != null ? def.getUpgradeCost(level) : 0;
        boolean affordable = canUpgrade && pig.getSkillPoints() >= cost;

        int removeX = rowX + rowW - 84;
        int removeY = rowY + 35;
        int removeW = 38;
        int removeH = 16;
        boolean removeHover = isHovering(removeX, removeY, removeW, removeH, lastMouseX, lastMouseY);
        drawButton(gg, removeX, removeY, removeW, removeH, Tr.s("gui.pig_mod.mutations.remove"), COLOR_BUTTON_BAD, COLOR_BUTTON_BAD_H, removeHover);

        int upgradeX = rowX + rowW - 84;
        int upgradeY = rowY + 5;
        int upgradeW = 80;
        int upgradeH = 17;
        boolean upgradeHover = isHovering(upgradeX, upgradeY, upgradeW, upgradeH, lastMouseX, lastMouseY);
        int upgradeNormal = canUpgrade && affordable ? COLOR_BUTTON_OK : COLOR_BUTTON;
        int upgradeHoverColor = canUpgrade && affordable ? COLOR_BUTTON_OK_H : COLOR_BUTTON_HOVER;
        String upgradeText = !canUpgrade ? Tr.s("gui.pig_mod.mutations.max") : Tr.s("gui.pig_mod.mutations.upgrade", cost);
        drawButton(gg, upgradeX, upgradeY, upgradeW, upgradeH, upgradeText,
                upgradeNormal, upgradeHoverColor, upgradeHover && canUpgrade);

        if (mutationId.equals("resize") || mutationId.equals("destroyer")) {
            int specialX = rowX + rowW - 84;
            int specialY = rowY + 21;
            String label;
            if (mutationId.equals("resize")) {
                label = switch (pig.getSizeState()) {
                    case 1 -> Tr.s("gui.pig_mod.size.small");
                    case 2 -> Tr.s("gui.pig_mod.size.large");
                    default -> Tr.s("gui.pig_mod.size.normal");
                };
            } else {
                label = Tr.s(pig.isRaidMode() ? "gui.pig_mod.raid.on" : "gui.pig_mod.raid.off");
            }
            boolean hover = isHovering(specialX, specialY, 80, 13, lastMouseX, lastMouseY);
            drawButton(gg, specialX, specialY, 80, 13, label, COLOR_BUTTON, COLOR_BUTTON_HOVER, hover);
            lastSpecialButtons.add(new SpecialToggleButton(mutationId, specialX, specialY, 80, 13));
        }

        lastRowButtons.add(new MutationRowButtons(mutationId, upgradeX, upgradeY, upgradeW, upgradeH,
                canUpgrade && affordable, removeX, removeY, removeW, removeH));
    }

    private String[] mutationDescription(String id) {
        // Описание из lang-файлов: mutation.pig_mod.<id>.desc1 / desc2 (для неизвестных id — default)
        String base = Mutations.get(id) != null ? "mutation.pig_mod." + id : "mutation.pig_mod.default";
        return new String[]{Tr.s(base + ".desc1"), Tr.s(base + ".desc2")};
    }

    private void drawWrappedTwoLines(GuiGraphics gg, String[] lines, int x, int y, int maxWidth, int color) {
        int lineY = y;
        int used = 0;
        for (String source : lines) {
            String line = source;
            if (this.font.width(line) > maxWidth) {
                line = this.font.plainSubstrByWidth(line, Math.max(10, maxWidth - this.font.width("…"))) + "…";
            }
            gg.drawString(this.font, line, x, lineY, color, false);
            lineY += 11;
            if (++used >= 2) break;
        }
    }

    private void drawMutationScrollbar(GuiGraphics gg, int x, int y, int w, int h,
                                       int total, int visible, int maxScroll) {
        gg.fill(x, y, x + w, y + h, COLOR_SCROLL_TRACK);
        if (maxScroll <= 0) return;

        int thumbH = Math.max(18, (int) ((long) h * visible / Math.max(visible, total)));
        int travel = h - thumbH;
        int thumbY = y + (int) ((long) travel * mutationScroll / maxScroll);
        boolean hover = isHovering(x, y, w, h, lastMouseX, lastMouseY);
        gg.fill(x, thumbY, x + w, thumbY + thumbH, hover || scrollDragging ? COLOR_SCROLL_HOVER : COLOR_SCROLL_THUMB);
    }

    private int getMutationMaxScroll() {
        TameablePigEntity pig = this.menu.getPig();
        if (pig == null) return 0;
        int total = countUnlockedMutations(pig);
        int visible = Math.max(1, MUT_VIEW_H / MUT_ROW_H);
        return Math.max(0, total - visible);
    }

    private void updateScrollFromMouse(int mouseY, int trackY, int trackH, int total, int visible) {
        int maxScroll = Math.max(0, total - visible);
        if (maxScroll <= 0) {
            mutationScroll = 0;
            return;
        }
        int thumbH = Math.max(18, (int) ((long) trackH * visible / Math.max(visible, total)));
        int travel = trackH - thumbH;
        int targetTop = mouseY - scrollDragOffset;
        int clamped = Math.max(trackY, Math.min(trackY + travel, targetTop));
        mutationScroll = Math.round((float) (clamped - trackY) / Math.max(1, travel) * maxScroll);
    }

    // =========================
    // Страница 3 — Инвентарь
    // =========================

    private void renderInventoryTab(GuiGraphics gg, int x, int y, TameablePigEntity pig) {
        int bx = x + INV_BOX_X;
        int by = y + INV_BOX_Y;
        drawSunkenBox(gg, bx, by, bx + INV_BOX_W, by + INV_BOX_H);

        gg.drawString(this.font, Tr.s("gui.pig_mod.inventory.title"), bx + 9, by + 7, COLOR_TEXT_GOLD, false);
        drawRightString(gg, Tr.s("gui.pig_mod.inventory.slots"),
                bx + INV_BOX_W - 9, by + 7, COLOR_TEXT_MUTED);

        int gx = x + PigMenu.INV_X - 1;
        int gy = y + PigMenu.INV_Y - 1;
        int gw = PigMenu.INV_COLS * PigMenu.SLOT_SIZE + 1;
        int gh = PigMenu.INV_ROWS * PigMenu.SLOT_SIZE + 1;

        drawGridFrame(gg, gx - 6, gy - 6, gw + 12, gh + 12);

        if (pig == null || pig.getInventory().isEmpty()) {
            gg.drawCenteredString(this.font, Tr.s("gui.pig_mod.inventory.empty"),
                    bx + INV_BOX_W / 2, by + 145, COLOR_TEXT_MUTED);
            return;
        }

        gg.drawString(this.font, Tr.s("gui.pig_mod.inventory.loot_hint"),
                bx + 9, by + 174, COLOR_TEXT_MUTED, false);
        gg.drawString(this.font, Tr.s("gui.pig_mod.inventory.loot_jobs"),
                bx + 9, by + 187, COLOR_TEXT_MAIN, false);
    }

    // =========================
    // Удаление мутации
    // =========================

    private void renderRemoveConfirmDialog(GuiGraphics gg, int mouseX, int mouseY) {
        gg.fill(0, 0, this.width, this.height, COLOR_OVERLAY);

        int dx = (this.width - DIALOG_W) / 2;
        int dy = (this.height - DIALOG_H) / 2;
        drawPanel(gg, dx, dy, DIALOG_W, DIALOG_H);

        String mutationName = pendingRemoveMutationId;
        MutationDefinition def = pendingRemoveMutationId == null ? null : Mutations.get(pendingRemoveMutationId);
        if (def != null) mutationName = Component.translatable(def.getDisplayNameKey()).getString();

        gg.drawCenteredString(this.font, Tr.s("gui.pig_mod.remove.title"), dx + DIALOG_W / 2, dy + 10, COLOR_TEXT_MAIN);
        gg.drawCenteredString(this.font, mutationName == null ? "" : mutationName,
                dx + DIALOG_W / 2, dy + 23, COLOR_TEXT_GOLD);
        gg.drawCenteredString(this.font, Tr.s("gui.pig_mod.remove.warning"),
                dx + DIALOG_W / 2, dy + 36, COLOR_TEXT_MUTED);

        dialogYesX = dx + 18;
        dialogNoX = dx + DIALOG_W - 18 - DIALOG_BTN_W;
        dialogYesY = dy + DIALOG_H - DIALOG_BTN_H - 10;
        dialogNoY = dialogYesY;

        boolean yesHover = isHovering(dialogYesX, dialogYesY, DIALOG_BTN_W, DIALOG_BTN_H, mouseX, mouseY);
        boolean noHover = isHovering(dialogNoX, dialogNoY, DIALOG_BTN_W, DIALOG_BTN_H, mouseX, mouseY);
        drawButton(gg, dialogYesX, dialogYesY, DIALOG_BTN_W, DIALOG_BTN_H,
                Tr.s("gui.pig_mod.remove.yes"), COLOR_BUTTON_BAD, COLOR_BUTTON_BAD_H, yesHover);
        drawButton(gg, dialogNoX, dialogNoY, DIALOG_BTN_W, DIALOG_BTN_H,
                Tr.s("gui.pig_mod.remove.cancel"), COLOR_BUTTON, COLOR_BUTTON_HOVER, noHover);
    }

    // =========================
    // Mouse
    // =========================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameBox != null && nameBox.visible) {
            if (keyCode == 257 || keyCode == 335) { // Enter / Numpad Enter — сохранить имя
                TameablePigEntity pig = menu.getPig();
                if (pig != null) PacketDistributor.sendToServer(new PigRenamePacket(pig.getId(), nameBox.getValue()));
                nameBox.visible = false;
                setFocused(null);
                return true;
            }
            if (keyCode == 256) { // Esc — отменить ввод, не закрывая окно свиньи
                nameBox.visible = false;
                setFocused(null);
                return true;
            }
            // Пока печатаем имя, клавиши (в т.ч. "E" инвентаря) не должны закрывать окно
            if (nameBox.keyPressed(keyCode, scanCode, modifiers) || nameBox.canConsumeInput()) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pendingRemoveMutationId != null) {
            if (button == 0) {
                if (isHovering(dialogYesX, dialogYesY, DIALOG_BTN_W, DIALOG_BTN_H, (int) mouseX, (int) mouseY)) {
                    sendMutationAction(pendingRemoveMutationId, true);
                    pendingRemoveMutationId = null;
                    return true;
                }
                if (isHovering(dialogNoX, dialogNoY, DIALOG_BTN_W, DIALOG_BTN_H, (int) mouseX, (int) mouseY)) {
                    pendingRemoveMutationId = null;
                    return true;
                }
            }
            return true;
        }

        int x = (this.width - GUI_W) / 2;
        int y = (this.height - GUI_H) / 2;

        if (button == 0) {
            for (int i = 0; i < 3; i++) {
                int tx = x + 10 + i * (TAB_W + TAB_GAP);
                if (isHovering(tx, y + TABS_Y, TAB_W, TAB_H, (int) mouseX, (int) mouseY)) {
                    activeTab = i;
                    this.nameBox.visible = false;
                    return true;
                }
            }

            if (activeTab == 0 && isHovering(renameButtonX, renameButtonY, renameButtonW, renameButtonH, (int) mouseX, (int) mouseY)) {
                TameablePigEntity pig = this.menu.getPig();
                if(pig!=null){
                    String current=pig.hasCustomName()?pig.getName().getString():"";
                    this.nameBox.setValue(current);
                    this.nameBox.setX((this.width-GUI_W)/2 + 58); this.nameBox.setY((this.height-GUI_H)/2 + 10);
                    this.nameBox.visible=true; this.setFocused(this.nameBox);
                }
                return true;
            }

            if (activeTab == 0 && nameBox.visible && isHovering(nameBox.getX(), nameBox.getY(), nameBox.getWidth(), nameBox.getHeight(), (int) mouseX, (int) mouseY)) return super.mouseClicked(mouseX, mouseY, button);
            if (nameBox.visible) { // клик мимо поля — отмена редактирования имени
                nameBox.visible = false;
                setFocused(null);
            }

            if (activeTab == 0 && commandButtonW > 0
                    && isHovering(commandButtonX, commandButtonY, commandButtonW, commandButtonH, (int) mouseX, (int) mouseY)) {
                TameablePigEntity pig = this.menu.getPig();
                if (pig != null) {
                    PacketDistributor.sendToServer(new PigCommandCyclePacket(pig.getId()));
                }
                return true;
            }

            if (activeTab == 1) {
                int trackX = x + MUT_LIST_X + MUT_LIST_W - MUT_SCROLLBAR_W - 1;
                int trackY = y + MUT_LIST_Y + MUT_HEADER_H;
                int trackH = MUT_VIEW_H - 3;
                TameablePigEntity pig = this.menu.getPig();
                if (pig != null && isHovering(trackX, trackY, MUT_SCROLLBAR_W, trackH,
                        (int) mouseX, (int) mouseY)) {
                    int total = countUnlockedMutations(pig);
                    int visible = Math.max(1, MUT_VIEW_H / MUT_ROW_H);
                    int maxScroll = Math.max(0, total - visible);
                    if (maxScroll > 0) {
                        int thumbH = Math.max(18, (int) ((long) trackH * visible / Math.max(visible, total)));
                        int thumbY = trackY + (int) ((long) (trackH - thumbH) * mutationScroll / maxScroll);
                        scrollDragOffset = Math.max(0, (int) mouseY - thumbY);
                        if ((int) mouseY > thumbY + thumbH) {
                            scrollDragOffset = thumbH / 2;
                            updateScrollFromMouse((int) mouseY, trackY, trackH, total, visible);
                        }
                        scrollDragging = true;
                        return true;
                    }
                }

                for (SpecialToggleButton special : lastSpecialButtons) {
                    if (isHovering(special.x(), special.y(), special.w(), special.h(), (int) mouseX, (int) mouseY)) {
                        TameablePigEntity pig2 = this.menu.getPig();
                        if (pig2 != null) {
                            if (special.mutationId().equals("resize")) {
                                PacketDistributor.sendToServer(new PigSizeCyclePacket(pig2.getId()));
                            } else if (special.mutationId().equals("destroyer")) {
                                PacketDistributor.sendToServer(new PigRaidModeTogglePacket(pig2.getId()));
                            }
                        }
                        return true;
                    }
                }

                for (MutationRowButtons row : lastRowButtons) {
                    if (row.canUpgrade && isHovering(row.plusX, row.plusY, row.plusW, row.plusH,
                            (int) mouseX, (int) mouseY)) {
                        sendMutationAction(row.mutationId, false);
                        return true;
                    }
                    if (isHovering(row.removeX, row.removeY, row.removeW, row.removeH,
                            (int) mouseX, (int) mouseY)) {
                        pendingRemoveMutationId = row.mutationId;
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollDragging && button == 0 && activeTab == 1) {
            TameablePigEntity pig = this.menu.getPig();
            if (pig != null) {
                int x = (this.width - GUI_W) / 2;
                int y = (this.height - GUI_H) / 2;
                int total = countUnlockedMutations(pig);
                int visible = Math.max(1, MUT_VIEW_H / MUT_ROW_H);
                updateScrollFromMouse((int) mouseY,
                        y + MUT_LIST_Y + MUT_HEADER_H,
                        MUT_VIEW_H - 3, total, visible);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && scrollDragging) {
            scrollDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (pendingRemoveMutationId != null) return true;

        int x = (this.width - GUI_W) / 2;
        int y = (this.height - GUI_H) / 2;
        if (activeTab == 1 && isHovering(x + MUT_LIST_X, y + MUT_LIST_Y + MUT_HEADER_H,
                MUT_LIST_W, MUT_VIEW_H, (int) mouseX, (int) mouseY)) {
            mutationScroll -= (int) Math.signum(scrollY);
            mutationScroll = Math.max(0, Math.min(mutationScroll, getMutationMaxScroll()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void sendMutationAction(String mutationId, boolean remove) {
        TameablePigEntity pig = this.menu.getPig();
        if (pig == null) return;
        PacketDistributor.sendToServer(new MutationActionPacket(pig.getId(), mutationId, remove));
    }

    private boolean isHovering(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void drawRightString(GuiGraphics gg, String text, int rightX, int y, int color) {
        gg.drawString(this.font, text, rightX - this.font.width(text), y, color, false);
    }

    // =========================
    // Drawing helpers
    // =========================

    private void drawButton(GuiGraphics gg, int x, int y, int w, int h, String label,
                            int normal, int hover, boolean hovered) {
        gg.fill(x, y, x + w, y + h, hovered ? hover : normal);
        drawBorder(gg, x, y, w, h);
        gg.drawCenteredString(this.font, label, x + w / 2, y + Math.max(3, (h - 8) / 2), COLOR_TEXT_MAIN);
    }

    private void drawPanel(GuiGraphics gg, int x, int y, int w, int h) {
        gg.fill(x, y, x + w, y + h, COLOR_PANEL_BG);
        drawBorder(gg, x, y, w, h);
    }

    private void drawSunkenBox(GuiGraphics gg, int x1, int y1, int x2, int y2) {
        gg.fill(x1, y1, x2, y2, COLOR_PANEL_ALT);
        gg.fill(x1, y1, x2, y1 + 1, COLOR_BORDER_DARK);
        gg.fill(x1, y1, x1 + 1, y2, COLOR_BORDER_DARK);
        gg.fill(x1, y2 - 1, x2, y2, COLOR_BORDER_LIGHT);
        gg.fill(x2 - 1, y1, x2, y2, COLOR_BORDER_LIGHT);
    }

    private void drawGridFrame(GuiGraphics gg, int x, int y, int w, int h) {
        gg.fill(x, y, x + w, y + h, COLOR_BORDER_DARK);
        gg.fill(x + 2, y + 2, x + w - 2, y + h - 2, COLOR_SLOT_BG);
    }

    private void drawBorder(GuiGraphics gg, int x, int y, int w, int h) {
        gg.fill(x, y, x + w, y + 1, COLOR_BORDER_LIGHT);
        gg.fill(x, y, x + 1, y + h, COLOR_BORDER_LIGHT);
        gg.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER_DARK);
        gg.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER_DARK);
    }

    private void drawBar(GuiGraphics gg, int x, int y, int w, int h, float ratio, int color) {
        ratio = Math.max(0.0f, Math.min(1.0f, ratio));
        gg.fill(x, y, x + w, y + h, COLOR_BAR_BG);
        int fill = (int) (w * ratio);
        if (fill > 0) gg.fill(x, y, x + fill, y + h, color);
        gg.fill(x, y, x + w, y + 1, COLOR_BORDER_DARK);
        gg.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER_LIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Весь текст рисуется вручную в renderBg.
    }

    private record MutationRowButtons(String mutationId,
                                      int plusX, int plusY, int plusW, int plusH,
                                      boolean canUpgrade,
                                      int removeX, int removeY, int removeW, int removeH) {
    }

    private record SpecialToggleButton(String mutationId, int x, int y, int w, int h) {
    }
}
