package com.example.examplemod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;

import com.example.examplemod.mutation.MutationDefinition;
import com.example.examplemod.mutation.Mutations;
import com.example.examplemod.network.MutationActionPacket;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class PigScreen extends AbstractContainerScreen<PigMenu> {

    // ==== Палитра (тёмная тема) ====
    private static final int COLOR_PANEL_BG      = 0xFF2B2B2B;
    private static final int COLOR_BORDER_LIGHT  = 0xFF4A4A4A;
    private static final int COLOR_BORDER_DARK   = 0xFF141414;
    private static final int COLOR_SLOT_BG       = 0xFF373737;
    private static final int COLOR_BAR_BG        = 0xFF1A1A1A;
    private static final int COLOR_EXP_FILL      = 0xFF55C6E0;
    private static final int COLOR_HP_FILL_OK    = 0xFF6DBF5C;
    private static final int COLOR_HP_FILL_LOW   = 0xFFDB5A5A;
    private static final int COLOR_TEXT_MAIN     = 0xFFE0E0E0;
    private static final int COLOR_TEXT_GOLD     = 0xFFD8B34D;
    private static final int COLOR_TEXT_MUTED    = 0xFFA0A0A0;
    private static final int COLOR_TAB_ACTIVE    = 0xFF3E3E3E;
    private static final int COLOR_TAB_INACTIVE  = 0xFF232323;
    private static final int COLOR_BUTTON_OK     = 0xFF4E8F4A;
    private static final int COLOR_BUTTON_OFF    = 0xFF3A3A3A;
    private static final int COLOR_BUTTON_REMOVE = 0xFF9A3E3E;
    private static final int COLOR_MODAL_OVERLAY = 0xA0000000;
    private static final int COLOR_BUTTON_YES    = 0xFF4E8F4A;
    private static final int COLOR_BUTTON_YES_HOVER = 0xFF5FA95A;
    private static final int COLOR_BUTTON_NO     = 0xFF9A3E3E;
    private static final int COLOR_BUTTON_NO_HOVER   = 0xFFC24E4E;
    private static final int COLOR_SCROLL_TRACK  = 0xFF1A1A1A;
    private static final int COLOR_SCROLL_THUMB  = 0xFF5A5A5A;
    private static final int COLOR_SCROLL_THUMB_HOVER = 0xFF6E6E6E;

    // ==== Геометрия окна ====
    // Должна совпадать с координатами слотов в PigMenu! Слоты улучшений убраны — свинье они не нужны.
    // Окно расширено (было 216x202) — тексты и кнопки в списке мутаций больше не "слипаются",
    // а блоки статуса стали просторнее.
    private static final int TAB_Y = 5, TAB_H = 16;
    private static final int TAB1_X = 108, TAB1_W = 70;
    private static final int TAB2_X = 182, TAB2_W = 70;

    private static final int CONTENT_Y = 26;
    private static final int MODEL_BOX_X = 8, MODEL_BOX_W = 100, MODEL_BOX_H = 78;
    private static final int STATS_BOX_X = 116, STATS_BOX_W = 136, STATS_BOX_H = 78;

    private static final int MUT_LIST_X = 8, MUT_LIST_W = 232, MUT_LIST_H = 78;
    private static final int MUT_ROW_H = 26;
    private static final int MUT_VISIBLE_ROWS = MUT_LIST_H / MUT_ROW_H;

    // Скроллбар списка мутаций — стоит справа от списка вплотную, видно даже без наведения мыши,
    // чтобы было понятно, что список листается (актуально для тех, у кого не работает колёсико).
    private static final int SCROLL_GAP = 3;
    private static final int SCROLL_W = 6;
    private static final int SCROLL_THUMB_MIN_H = 12;

    private static final int INV_X = 49, INV_Y = 126, SLOT_SIZE = 18;
    private static final int HOTBAR_X = 49, HOTBAR_Y = 188;

    private int activeTab = 0; // 0 = Статус, 1 = Мутации
    private int mutationScroll = 0; // индекс первой видимой строки списка мутаций
    private boolean draggingScrollbar = false;
    private int scrollbarDragOffsetY = 0; // смещение клика внутри ползунка, чтобы он не "прыгал" под курсор

    // Прямоугольники кнопок [+]/[x] последнего кадра — используются в mouseClicked (без рассинхронизации с render)
    private final List<MutationRowButtons> lastRowButtons = new ArrayList<>();

    // ==== Модалка подтверждения удаления мутации ====
    // Пока не null — открыт диалог "Да/Нет", всё остальное окно заблокировано для кликов.
    private String pendingRemoveId = null;
    private String pendingRemoveName = null;
    private static final int MODAL_W = 190;
    private static final int MODAL_H = 80;
    private static final int MODAL_BTN_W = 70;
    private static final int MODAL_BTN_H = 16;

    public PigScreen(PigMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 260;
        this.imageHeight = 214;
        // Заголовок и подпись инвентаря рисуем сами в кастомных местах
        this.titleLabelX = -1000;
        this.inventoryLabelX = -1000;
    }

    @Override
    protected void renderBg(GuiGraphics gg, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        drawPanel(gg, x, y, x + this.imageWidth, y + this.imageHeight);
        gg.drawString(this.font, "Боевая Свинья", x + 8, y + 8, COLOR_TEXT_GOLD, false);

        drawTabs(gg, x, y, mouseX, mouseY);

        TameablePigEntity pig = this.menu.getPig();

        if (activeTab == 0) {
            renderStatusTab(gg, x, y, mouseX, mouseY, pig);
        } else {
            renderMutationsTab(gg, x, y, pig);
        }

        // --- Разделительная линия перед инвентарём игрока ---
        int dividerY = y + INV_Y - 12;
        gg.fill(x + 8, dividerY, x + this.imageWidth - 8, dividerY + 1, COLOR_BORDER_DARK);

        gg.drawString(this.font, "Инвентарь", x + INV_X, y + INV_Y - 10, COLOR_TEXT_MUTED, false);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(gg, x + INV_X + col * SLOT_SIZE, y + INV_Y + row * SLOT_SIZE);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(gg, x + HOTBAR_X + col * SLOT_SIZE, y + HOTBAR_Y);
        }
    }

    // ==== ВКЛАДКА 1: Статус ====

    private void renderStatusTab(GuiGraphics gg, int x, int y, int mouseX, int mouseY, TameablePigEntity pig) {
        drawSunkenBox(gg, x + MODEL_BOX_X, y + CONTENT_Y, x + MODEL_BOX_X + MODEL_BOX_W, y + CONTENT_Y + MODEL_BOX_H);
        if (pig != null) {
            int boxX = x + MODEL_BOX_X;
            int boxY = y + CONTENT_Y;
            // renderEntityInInventoryFollowsMouse сам делает enableScissor/disableScissor внутри себя.
            // yOffset — смещение МОДЕЛИ В БЛОКАХ (не пикселях) — держим маленьким, иначе свинья пропадёт
            // из вида. mouseX/mouseY передаём как есть (абсолютные координаты), метод сам считает разницу.
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    gg,
                    boxX + 2, boxY + 2, boxX + MODEL_BOX_W - 2, boxY + MODEL_BOX_H - 2,
                    32,
                    0.0f,
                    (float) mouseX,
                    (float) mouseY,
                    pig
            );
        } else {
            gg.drawCenteredString(this.font, "?", x + MODEL_BOX_X + MODEL_BOX_W / 2, y + CONTENT_Y + MODEL_BOX_H / 2 - 4, COLOR_TEXT_MUTED);
        }

        drawSunkenBox(gg, x + STATS_BOX_X, y + CONTENT_Y, x + STATS_BOX_X + STATS_BOX_W, y + CONTENT_Y + STATS_BOX_H);
        if (pig != null) {
            int sx = x + STATS_BOX_X + 8;
            int sy = y + CONTENT_Y + 8;
            int barWidth = STATS_BOX_W - 16;

            gg.drawString(this.font, "Уровень: " + pig.getPigLevel(), sx, sy, COLOR_TEXT_MAIN, false);

            int exp = pig.getPigExp();
            int expNeeded = pig.getPigLevel() * 15;
            gg.drawString(this.font, "Опыт: " + exp + " / " + expNeeded, sx, sy + 14, COLOR_TEXT_MUTED, false);
            drawBar(gg, sx, sy + 24, barWidth, 6, expNeeded == 0 ? 0f : (float) exp / expNeeded, COLOR_EXP_FILL);

            float health = pig.getHealth();
            float maxHealth = pig.getMaxHealth();
            gg.drawString(this.font, "Здоровье: " + (int) health + " / " + (int) maxHealth, sx, sy + 38, COLOR_TEXT_MUTED, false);
            float healthRatio = maxHealth <= 0 ? 0f : health / maxHealth;
            int hpColor = healthRatio > 0.3f ? COLOR_HP_FILL_OK : COLOR_HP_FILL_LOW;
            drawBar(gg, sx, sy + 48, barWidth, 6, healthRatio, hpColor);

            double damage = pig.getAttributeValue(Attributes.ATTACK_DAMAGE);
            gg.drawString(this.font, "Урон: " + String.format("%.1f", damage), sx, sy + 62, COLOR_TEXT_MUTED, false);
        }
    }

    // ==== ВКЛАДКА 2: Мутации ====

    private void renderMutationsTab(GuiGraphics gg, int x, int y, TameablePigEntity pig) {
        lastRowButtons.clear();

        int listX = x + MUT_LIST_X;
        int listY = y + CONTENT_Y;

        // Оставшиеся очки развития — прямо над списком, чтобы всегда было видно, сколько ещё доступно
        if (pig != null) {
            gg.drawString(this.font, "Очков развития: " + pig.getSkillPoints(), listX, listY - 10, COLOR_TEXT_GOLD, false);
        }

        drawSunkenBox(gg, listX, listY, listX + MUT_LIST_W, listY + MUT_LIST_H);

        if (pig == null) {
            return;
        }

        List<String> unlocked = getUnlockedMutationIds(pig);

        if (unlocked.isEmpty()) {
            gg.drawString(this.font, "Мутаций пока нет — введите Пробирку через Инжектор",
                    listX + 6, listY + 8, COLOR_TEXT_MUTED, false);
            return;
        }

        int maxScroll = Math.max(0, unlocked.size() - MUT_VISIBLE_ROWS);
        mutationScroll = Math.max(0, Math.min(mutationScroll, maxScroll));

        gg.enableScissor(listX + 1, listY + 1, listX + MUT_LIST_W - 1, listY + MUT_LIST_H - 1);
        for (int row = 0; row < MUT_VISIBLE_ROWS; row++) {
            int index = mutationScroll + row;
            if (index >= unlocked.size()) {
                break;
            }
            drawMutationRow(gg, listX, listY + row * MUT_ROW_H, MUT_LIST_W, unlocked.get(index), pig);
        }
        gg.disableScissor();

        if (maxScroll > 0) {
            gg.drawString(this.font, (mutationScroll + 1) + "-" + Math.min(unlocked.size(), mutationScroll + MUT_VISIBLE_ROWS) + " из " + unlocked.size(),
                    listX + MUT_LIST_W - 70, listY + MUT_LIST_H + 3, COLOR_TEXT_MUTED, false);
            drawMutationScrollbar(gg, listX, listY, unlocked.size(), maxScroll);
        }
    }

    /** Список ID разблокированных мутаций в стабильном порядке (как зарегистрированы в моде). */
    private List<String> getUnlockedMutationIds(TameablePigEntity pig) {
        List<String> unlocked = new ArrayList<>();
        if (pig == null) {
            return unlocked;
        }
        for (String id : Mutations.all().keySet()) {
            if (pig.hasMutation(id)) {
                unlocked.add(id);
            }
        }
        return unlocked;
    }

    /** Координаты дорожки скроллбара для текущего положения окна (пересчитываются и в render, и в кликах). */
    private int[] scrollbarTrackRect(int listX, int listY) {
        int trackX = listX + MUT_LIST_W + SCROLL_GAP;
        return new int[] { trackX, listY, SCROLL_W, MUT_LIST_H };
    }

    private void drawMutationScrollbar(GuiGraphics gg, int listX, int listY, int totalRows, int maxScroll) {
        int[] track = scrollbarTrackRect(listX, listY);
        int trackX = track[0], trackY = track[1], trackW = track[2], trackH = track[3];

        gg.fill(trackX, trackY, trackX + trackW, trackY + trackH, COLOR_SCROLL_TRACK);
        gg.fill(trackX, trackY, trackX + trackW, trackY + 1, COLOR_BORDER_DARK);
        gg.fill(trackX, trackY + trackH - 1, trackX + trackW, trackY + trackH, COLOR_BORDER_LIGHT);

        int[] thumb = scrollbarThumbRect(listX, listY, totalRows, maxScroll);
        boolean hover = draggingScrollbar || isHovering(thumb[0], thumb[1], thumb[2], thumb[3], lastMouseX, lastMouseY);
        gg.fill(thumb[0], thumb[1], thumb[0] + thumb[2], thumb[1] + thumb[3], hover ? COLOR_SCROLL_THUMB_HOVER : COLOR_SCROLL_THUMB);
    }

    /** Прямоугольник ползунка скроллбара исходя из текущей прокрутки. */
    private int[] scrollbarThumbRect(int listX, int listY, int totalRows, int maxScroll) {
        int[] track = scrollbarTrackRect(listX, listY);
        int trackX = track[0], trackY = track[1], trackW = track[2], trackH = track[3];

        int thumbH = Math.max(SCROLL_THUMB_MIN_H, trackH * MUT_VISIBLE_ROWS / totalRows);
        thumbH = Math.min(thumbH, trackH);
        int freeSpace = trackH - thumbH;
        int thumbY = maxScroll <= 0 ? trackY : trackY + freeSpace * mutationScroll / maxScroll;
        return new int[] { trackX, thumbY, trackW, thumbH };
    }

    private void drawMutationRow(GuiGraphics gg, int listX, int rowY, int listW, String mutationId, TameablePigEntity pig) {
        MutationDefinition def = Mutations.get(mutationId);
        int level = pig.getMutationLevel(mutationId);

        String fullName = def != null ? Component.translatable(def.getDisplayNameKey()).getString() : mutationId;
        int maxLevel = def != null ? def.getMaxLevel() : level;

        // Кнопки удаления/прокачки — правая часть строки. Считаем их координаты сначала,
        // чтобы название и подпись уровня гарантированно не наезжали на кнопки (раньше сливалось).
        int removeW = 16, removeH = 15;
        int removeX = listX + listW - 8 - removeW;
        int removeY = rowY + (MUT_ROW_H - removeH) / 2;

        int plusW = 38, plusH = 15;
        int plusX = removeX - 6 - plusW;
        int plusY = rowY + (MUT_ROW_H - plusH) / 2;

        // Подрезаем название под доступную ширину строки (слева до кнопок) — иначе длинное имя
        // мутации может "наехать" на кнопки [+]/[x], как раньше случалось с плейсхолдером-текстом.
        String name = fullName;
        int nameMaxWidth = plusX - listX - 12;
        if (this.font.width(name) > nameMaxWidth) {
            name = this.font.plainSubstrByWidth(name, nameMaxWidth - this.font.width("…")) + "…";
        }

        gg.drawString(this.font, name, listX + 6, rowY + 4, COLOR_TEXT_MAIN, false);
        String levelText = "Lvl " + level + "/" + maxLevel;
        String bonus = def != null ? def.describeLevel(level) : null;
        if (bonus != null) {
            levelText += "  " + bonus;
        }
        // Вторая строка тоже подрезается под ширину, чтобы бонус не заезжал под кнопки
        if (this.font.width(levelText) > nameMaxWidth) {
            levelText = this.font.plainSubstrByWidth(levelText, nameMaxWidth - this.font.width("…")) + "…";
        }
        gg.drawString(this.font, levelText, listX + 6, rowY + 15, COLOR_TEXT_MUTED, false);

        // Разделитель между строками списка, чтобы визуально не сливались друг с другом
        gg.fill(listX + 4, rowY + MUT_ROW_H - 1, listX + listW - 4, rowY + MUT_ROW_H, COLOR_BORDER_DARK);

        // Кнопка [x] удалить
        boolean removeHover = isHovering(removeX, removeY, removeW, removeH, lastMouseX, lastMouseY);
        gg.fill(removeX, removeY, removeX + removeW, removeY + removeH, removeHover ? 0xFFC24E4E : COLOR_BUTTON_REMOVE);
        gg.drawCenteredString(this.font, "x", removeX + removeW / 2, removeY + 3, COLOR_TEXT_MAIN);

        // Кнопка [+] прокачать
        boolean canUpgrade = def != null && level < maxLevel;
        int cost = def != null ? def.getUpgradeCost(level) : -1;
        boolean affordable = canUpgrade && pig.getSkillPoints() >= cost;
        boolean plusHover = canUpgrade && isHovering(plusX, plusY, plusW, plusH, lastMouseX, lastMouseY);
        int plusColor = !canUpgrade ? COLOR_BUTTON_OFF : (affordable ? (plusHover ? 0xFF5FA95A : COLOR_BUTTON_OK) : COLOR_BUTTON_OFF);
        gg.fill(plusX, plusY, plusX + plusW, plusY + plusH, plusColor);
        String plusLabel = canUpgrade ? ("+1 (" + cost + ")") : "MAX";
        gg.drawCenteredString(this.font, plusLabel, plusX + plusW / 2, plusY + 3, COLOR_TEXT_MAIN);

        lastRowButtons.add(new MutationRowButtons(mutationId, fullName, plusX, plusY, plusW, plusH,
                canUpgrade && affordable, removeX, removeY, removeW, removeH));
    }

    private boolean isHovering(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // Запоминаем последние координаты мыши для ховера кнопок (renderBg получает mouseX/mouseY, но
    // drawMutationRow вызывается без них напрямую — проще один раз сохранить, чем прокидывать всюду)
    private int lastMouseX;
    private int lastMouseY;

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        // super.render() (AbstractContainerScreen) сам вызывает renderBackground()/renderBg() первым
        // делом — вызывать его ещё раз здесь не нужно, это рисовало весь фон (и свинью) дважды за кадр.
        // Он же рисует предметы в слотах ПОСЛЕ renderBg — поэтому модалку нельзя рисовать внутри
        // renderBg, иначе иконки предметов и текст инвентаря лягут поверх диалога (как было раньше).
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (pendingRemoveId != null) {
            // Модалка рисуется здесь, самой последней — поверх абсолютно всего, включая предметы.
            renderRemoveConfirmModal(guiGraphics, mouseX, mouseY);
        } else {
            this.renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    private void drawTabs(GuiGraphics gg, int x, int y, int mouseX, int mouseY) {
        drawTab(gg, x + TAB1_X, y + TAB_Y, TAB1_W, "Статус", activeTab == 0, mouseX, mouseY);
        drawTab(gg, x + TAB2_X, y + TAB_Y, TAB2_W, "Мутации", activeTab == 1, mouseX, mouseY);
    }

    private void drawTab(GuiGraphics gg, int tx, int ty, int tw, String label, boolean active, int mouseX, int mouseY) {
        boolean hover = isHovering(tx, ty, tw, TAB_H, mouseX, mouseY);
        int bg = active ? COLOR_TAB_ACTIVE : (hover ? 0xFF333333 : COLOR_TAB_INACTIVE);
        gg.fill(tx, ty, tx + tw, ty + TAB_H, bg);
        gg.fill(tx, ty, tx + tw, ty + 1, COLOR_BORDER_LIGHT);
        gg.fill(tx, ty, tx + 1, ty + TAB_H, COLOR_BORDER_LIGHT);
        gg.fill(tx + tw - 1, ty, tx + tw, ty + TAB_H, COLOR_BORDER_DARK);
        gg.fill(tx, ty + TAB_H - 1, tx + tw, ty + TAB_H, COLOR_BORDER_DARK);
        int textColor = active ? COLOR_TEXT_GOLD : COLOR_TEXT_MUTED;
        gg.drawCenteredString(this.font, label, tx + tw / 2, ty + 4, textColor);
    }

    // ==== Модалка подтверждения удаления ====

    private void renderRemoveConfirmModal(GuiGraphics gg, int mouseX, int mouseY) {
        // Затемняем весь экран (а не только окно свиньи), чтобы явно читалось как блокирующий диалог
        gg.fill(0, 0, this.width, this.height, COLOR_MODAL_OVERLAY);

        int mx = (this.width - MODAL_W) / 2;
        int my = (this.height - MODAL_H) / 2;
        drawPanel(gg, mx, my, mx + MODAL_W, my + MODAL_H);

        String question = "Вы действительно хотите удалить \"" + pendingRemoveName + "\" из своей свиньи?";
        List<FormattedCharSequence> lines = this.font.split(Component.literal(question), MODAL_W - 16);
        int textY = my + 10;
        for (FormattedCharSequence line : lines) {
            gg.drawString(this.font, line, mx + 8, textY, COLOR_TEXT_MAIN, false);
            textY += 10;
        }

        int btnY = my + MODAL_H - MODAL_BTN_H - 8;
        int yesX = mx + 12;
        int noX = mx + MODAL_W - MODAL_BTN_W - 12;

        boolean yesHover = isHovering(yesX, btnY, MODAL_BTN_W, MODAL_BTN_H, mouseX, mouseY);
        gg.fill(yesX, btnY, yesX + MODAL_BTN_W, btnY + MODAL_BTN_H, yesHover ? COLOR_BUTTON_YES_HOVER : COLOR_BUTTON_YES);
        gg.drawCenteredString(this.font, "Да", yesX + MODAL_BTN_W / 2, btnY + 4, COLOR_TEXT_MAIN);

        boolean noHover = isHovering(noX, btnY, MODAL_BTN_W, MODAL_BTN_H, mouseX, mouseY);
        gg.fill(noX, btnY, noX + MODAL_BTN_W, btnY + MODAL_BTN_H, noHover ? COLOR_BUTTON_NO_HOVER : COLOR_BUTTON_NO);
        gg.drawCenteredString(this.font, "Нет", noX + MODAL_BTN_W / 2, btnY + 4, COLOR_TEXT_MAIN);
    }

    private boolean handleModalClick(int mouseX, int mouseY) {
        int mx = (this.width - MODAL_W) / 2;
        int my = (this.height - MODAL_H) / 2;
        int btnY = my + MODAL_H - MODAL_BTN_H - 8;
        int yesX = mx + 12;
        int noX = mx + MODAL_W - MODAL_BTN_W - 12;

        if (isHovering(yesX, btnY, MODAL_BTN_W, MODAL_BTN_H, mouseX, mouseY)) {
            sendMutationAction(pendingRemoveId, true);
            pendingRemoveId = null;
            pendingRemoveName = null;
            return true;
        }
        if (isHovering(noX, btnY, MODAL_BTN_W, MODAL_BTN_H, mouseX, mouseY)) {
            pendingRemoveId = null;
            pendingRemoveName = null;
            return true;
        }
        // Клик мимо кнопок (в том числе мимо самой панели) — просто закрывает диалог без удаления
        pendingRemoveId = null;
        pendingRemoveName = null;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pendingRemoveId != null) {
            // Пока открыт диалог подтверждения, весь остальной интерфейс заблокирован для кликов
            if (button == 0) {
                return handleModalClick((int) mouseX, (int) mouseY);
            }
            return true;
        }

        if (button == 0) {
            int x = (this.width - this.imageWidth) / 2;
            int y = (this.height - this.imageHeight) / 2;

            if (isHovering(x + TAB1_X, y + TAB_Y, TAB1_W, TAB_H, (int) mouseX, (int) mouseY)) {
                activeTab = 0;
                return true;
            }
            if (isHovering(x + TAB2_X, y + TAB_Y, TAB2_W, TAB_H, (int) mouseX, (int) mouseY)) {
                activeTab = 1;
                return true;
            }

            if (activeTab == 1) {
                if (handleScrollbarClick(x, y, (int) mouseX, (int) mouseY)) {
                    return true;
                }
                for (MutationRowButtons row : lastRowButtons) {
                    if (row.canUpgrade && isHovering(row.plusX, row.plusY, row.plusW, row.plusH, (int) mouseX, (int) mouseY)) {
                        sendMutationAction(row.mutationId, false);
                        return true;
                    }
                    if (isHovering(row.removeX, row.removeY, row.removeW, row.removeH, (int) mouseX, (int) mouseY)) {
                        // Удаление больше не мгновенное — сначала спрашиваем подтверждение
                        pendingRemoveId = row.mutationId;
                        pendingRemoveName = row.mutationName;
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** @return true, если клик обработан скроллбаром (попал на ползунок или на дорожку). */
    private boolean handleScrollbarClick(int x, int y, int mouseX, int mouseY) {
        TameablePigEntity pig = this.menu.getPig();
        if (pig == null) {
            return false;
        }
        int listX = x + MUT_LIST_X;
        int listY = y + CONTENT_Y;
        List<String> unlocked = getUnlockedMutationIds(pig);
        int maxScroll = Math.max(0, unlocked.size() - MUT_VISIBLE_ROWS);
        if (maxScroll <= 0) {
            return false;
        }

        int[] thumb = scrollbarThumbRect(listX, listY, unlocked.size(), maxScroll);
        if (isHovering(thumb[0], thumb[1], thumb[2], thumb[3], mouseX, mouseY)) {
            draggingScrollbar = true;
            scrollbarDragOffsetY = mouseY - thumb[1];
            return true;
        }

        int[] track = scrollbarTrackRect(listX, listY);
        if (isHovering(track[0], track[1], track[2], track[3], mouseX, mouseY)) {
            // Клик по дорожке выше/ниже ползунка — пролистать на страницу, как в стандартных скроллбарах
            if (mouseY < thumb[1]) {
                mutationScroll = Math.max(0, mutationScroll - MUT_VISIBLE_ROWS);
            } else {
                mutationScroll = Math.min(maxScroll, mutationScroll + MUT_VISIBLE_ROWS);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            updateScrollFromDrag((int) mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar && button == 0) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Пересчитывает mutationScroll исходя из текущей позиции мыши при перетаскивании ползунка. */
    private void updateScrollFromDrag(int mouseY) {
        TameablePigEntity pig = this.menu.getPig();
        if (pig == null) {
            return;
        }
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        int listX = x + MUT_LIST_X;
        int listY = y + CONTENT_Y;

        List<String> unlocked = getUnlockedMutationIds(pig);
        int maxScroll = Math.max(0, unlocked.size() - MUT_VISIBLE_ROWS);
        if (maxScroll <= 0) {
            return;
        }

        int[] track = scrollbarTrackRect(listX, listY);
        int[] thumb = scrollbarThumbRect(listX, listY, unlocked.size(), maxScroll);
        int freeSpace = track[3] - thumb[3];
        if (freeSpace <= 0) {
            return;
        }

        int desiredThumbY = mouseY - scrollbarDragOffsetY;
        int relativeY = desiredThumbY - track[1];
        relativeY = Math.max(0, Math.min(freeSpace, relativeY));
        mutationScroll = Math.round(relativeY * (float) maxScroll / freeSpace);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (pendingRemoveId != null) {
            return true;
        }
        if (activeTab == 1) {
            mutationScroll -= (int) Math.signum(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void sendMutationAction(String mutationId, boolean remove) {
        TameablePigEntity pig = this.menu.getPig();
        if (pig == null) {
            return;
        }
        PacketDistributor.sendToServer(new MutationActionPacket(pig.getId(), mutationId, remove));
    }

    /** Прямоугольники кнопок одной строки мутации — заполняются в render, читаются в mouseClicked. */
    private record MutationRowButtons(String mutationId, String mutationName, int plusX, int plusY, int plusW, int plusH,
                                       boolean canUpgrade, int removeX, int removeY, int removeW, int removeH) {
    }

    /** Обычная приподнятая панель (фон окна) */
    private void drawPanel(GuiGraphics gg, int x1, int y1, int x2, int y2) {
        gg.fill(x1, y1, x2, y2, COLOR_PANEL_BG);
        gg.fill(x1, y1, x2, y1 + 1, COLOR_BORDER_LIGHT);       // верх
        gg.fill(x1, y1, x1 + 1, y2, COLOR_BORDER_LIGHT);       // лево
        gg.fill(x1, y2 - 1, x2, y2, COLOR_BORDER_DARK);        // низ
        gg.fill(x2 - 1, y1, x2, y2, COLOR_BORDER_DARK);        // право
    }

    /** "Вдавленная" панель (для блока модели/статов/списка мутаций) */
    private void drawSunkenBox(GuiGraphics gg, int x1, int y1, int x2, int y2) {
        gg.fill(x1, y1, x2, y2, COLOR_SLOT_BG);
        gg.fill(x1, y1, x2, y1 + 1, COLOR_BORDER_DARK);        // верх — тень (вдавленность)
        gg.fill(x1, y1, x1 + 1, y2, COLOR_BORDER_DARK);        // лево
        gg.fill(x1, y2 - 1, x2, y2, COLOR_BORDER_LIGHT);       // низ — блик
        gg.fill(x2 - 1, y1, x2, y2, COLOR_BORDER_LIGHT);       // право
    }

    /**
     * Один слот 18x18 с вдавленным бевелом, как в ванильном инвентаре.
     * x, y здесь — координаты, где реально рендерится ИКОНКА ПРЕДМЕТА (совпадают с Slot.x/Slot.y в PigMenu).
     */
    private void drawSlot(GuiGraphics gg, int x, int y) {
        int bx = x - 1;
        int by = y - 1;
        gg.fill(bx, by, bx + 18, by + 18, COLOR_BORDER_DARK);
        gg.fill(bx + 1, by + 1, bx + 17, by + 17, COLOR_SLOT_BG);
        gg.fill(bx + 1, by + 1, bx + 17, by + 2, COLOR_BORDER_DARK); // лёгкая тень сверху
        gg.fill(bx + 1, by + 1, bx + 2, by + 17, COLOR_BORDER_DARK); // лёгкая тень слева
    }

    /** Полоска прогресса (опыт / здоровье) */
    private void drawBar(GuiGraphics gg, int x, int y, int width, int height, float ratio, int fillColor) {
        ratio = Math.max(0f, Math.min(1f, ratio));
        gg.fill(x, y, x + width, y + height, COLOR_BAR_BG);
        int filled = (int) (width * ratio);
        if (filled > 0) {
            gg.fill(x, y, x + filled, y + height, fillColor);
        }
        gg.fill(x, y, x + width, y + 1, COLOR_BORDER_DARK);
        gg.fill(x, y + height - 1, x + width, y + height, COLOR_BORDER_LIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Все подписи рисуются в renderBg вместе с остальным интерфейсом,
        // здесь намеренно ничего не делаем, чтобы не дублировать текст.
    }
}