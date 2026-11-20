package com.example.examplemod;

import com.example.examplemod.network.TabletActionPacket;
import com.example.examplemod.network.TabletSelectPigPacket;
import com.example.examplemod.network.TabletWhitelistTogglePacket;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Экран Планшета управления.
 *
 * Переписан целиком. Старая версия использовала фиксированный размер 920x560, который почти
 * всегда обрезался по ширине окна — из-за этого панели наезжали друг на друга. Здесь вся
 * раскладка считается от реального размера панели (см. layout()), поэтому UI ровный и при
 * GUI Scale 1, и при 4.
 *
 * Ванильные виджеты (включая EditBox) намеренно не используются: поле поиска нарисовано и
 * обработано вручную — именно поэтому оно теперь гарантированно получает ввод. Раньше фокус
 * EditBox терялся, т.к. Screen#render не вызывался, и поиск просто не работал.
 */
public class PigTabletScreen extends Screen {

    // ==== палитра ====
    private static final int SCREEN_DIM = 0xC0060B12;
    private static final int FRAME_BG   = 0xFF0A1420;
    private static final int PANEL      = 0xFF0E1E2D;
    private static final int PANEL_HI   = 0xFF13293B;
    private static final int PANEL_SEL  = 0xFF124054;
    private static final int ROW_BG     = 0xFF0C1C29;
    private static final int LINE       = 0xFF29465A;
    private static final int LINE_SOFT  = 0xFF1B3244;
    private static final int CYAN       = 0xFF22D3EE;
    private static final int CYAN_DIM   = 0xFF127C92;
    private static final int TEXT       = 0xFFE8F4F8;
    private static final int TEXT_DIM   = 0xFF8AA7B5;
    private static final int TEXT_FAINT = 0xFF54707F;
    private static final int GREEN      = 0xFF4ADE80;
    private static final int ORANGE     = 0xFFFBBF24;
    private static final int RED        = 0xFFF87171;
    private static final int PINK       = 0xFFF9A8D4;

    private static final int HEADER_H = 28;
    private static final int FOOTER_H = 26;
    private static final int PIG_ROW_H = 26;
    private static final int MOB_ROW_H = 20;
    private static final int SCROLL_W = 5;
    private static final int PAD = 7;
    private static final int PROFILE_H = 84;
    private static final int CHIP_H = 14;
    private static final int CHIP_ROW_GAP = 3;
    private static final int CHIP_GAP = 4;
    private static final int WHITELIST_MAX_ROWS = 2;

    // ==== раскладка (пересчитывается в layout()) ====
    private int px, py, pw, ph;
    private int sideX, sideY, sideW, sideH;
    private int rightX, rightY, rightW;
    private int whitelistY, whitelistH;
    private int searchX, searchY, searchW, searchH;
    private int filterX, filterW;
    private int listX, listY, listW, listH;
    private int footerY;

    // ==== состояние ====
    private final List<TameablePigEntity> pigs = new ArrayList<>();
    private List<ResourceLocation> mobs = List.of();

    private UUID selected;
    private String search = "";
    private boolean searchFocused = true;
    private boolean onlyWhitelisted;

    private int pigScroll;
    private int mobScroll;
    private boolean draggingPigs;
    private boolean draggingMobs;
    private int ticks;
    private int refreshCooldown;

    public PigTabletScreen(ItemStack tablet) {
        super(Component.translatable("gui.pig_mod.tablet.title"));
        this.selected = PigControlTabletItem.getSelectedPig(tablet);
    }

    /** Планшет берём из руки каждый раз: стек в инвентаре обновляется сервером. */
    private ItemStack tablet() {
        return PigControlTabletItem.findHeld(minecraft == null ? null : minecraft.player);
    }

    @Override
    protected void init() {
        super.init();
        layout();
        refreshPigs();
        rebuildMobList();
        this.searchFocused = true;
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        if (--refreshCooldown <= 0) {
            refreshCooldown = 10;
            refreshPigs();
        }
    }

    // =============================== данные =========================================

    private void refreshPigs() {
        pigs.clear();
        Player player = minecraft == null ? null : minecraft.player;
        if (player == null) return;

        List<UUID> bound = PigControlTabletItem.getBoundPigs(tablet());
        for (TameablePigEntity pig : player.level().getEntitiesOfClass(
                TameablePigEntity.class, player.getBoundingBox().inflate(160))) {
            if (pig.isTame() && pig.isOwnedBy(player) && bound.contains(pig.getUUID())) {
                pigs.add(pig);
            }
        }
        pigs.sort(Comparator.comparing(p -> p.getName().getString(), String.CASE_INSENSITIVE_ORDER));

        if (selected == null || pigs.stream().noneMatch(p -> p.getUUID().equals(selected))) {
            UUID fromItem = PigControlTabletItem.getSelectedPig(tablet());
            if (fromItem != null && pigs.stream().anyMatch(p -> p.getUUID().equals(fromItem))) {
                selected = fromItem;
            } else if (!pigs.isEmpty()) {
                selected = pigs.get(0).getUUID();
            } else {
                selected = null;
            }
        }
        pigScroll = Mth.clamp(pigScroll, 0, Math.max(0, pigs.size() - pigVisibleRows()));
    }

    // ============================ поиск: словарь имён ================================
    // Баг был в том, что поиск сверял запрос только с ПУТЁМ реестра (всегда английский,
    // напр. "zombie") и с названием на ТЕКУЩЕМ языке игры. Если игра запущена на английском
    // (частый случай по умолчанию), запрос "зомби" не совпадал ни с чем. Здесь запрос
    // дополнительно сверяется с русским и английским названием, загруженными напрямую из
    // ванильных lang-файлов (ru_ru.json / en_us.json) — они всегда есть в игре, независимо
    // от того, какой язык выбран в настройках, поэтому поиск работает в обоих написаниях.
    private static Map<String, String> ruNameCache;
    private static Map<String, String> enNameCache;

    private static Map<String, String> loadVanillaLang(String fileName) {
        Map<String, String> map = new HashMap<>();
        try {
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath("minecraft", "lang/" + fileName);
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isEmpty()) return map;
            try (InputStream in = resource.get().open();
                 InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                com.google.gson.JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        map.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
        } catch (Exception ignored) {
            // Файла может не быть в ресурспаках сборки — тогда просто теряем этот источник имён,
            // поиск по пути реестра и по текущему языку игры продолжает работать как раньше.
        }
        return map;
    }

    private static String dictName(Map<String, String> cache, String descriptionId) {
        if (cache == null) return "";
        String value = cache.get(descriptionId);
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private void rebuildMobList() {
        if (ruNameCache == null) ruNameCache = loadVanillaLang("ru_ru.json");
        if (enNameCache == null) enNameCache = loadVanillaLang("en_us.json");

        String query = search.trim().toLowerCase(Locale.ROOT);
        final TameablePigEntity pig = selectedPig();

        List<ResourceLocation> ids = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            if (type == null || type == EntityType.PLAYER) continue;
            if (!LivingEntity.class.isAssignableFrom(type.getBaseClass())) continue;
            if (onlyWhitelisted && (pig == null || !pig.isWhitelisted(type))) continue;

            if (!query.isEmpty()) {
                String descId = type.getDescriptionId();
                String path = id.getPath().toLowerCase(Locale.ROOT).replace('_', ' ');
                String full = id.toString().toLowerCase(Locale.ROOT);
                String name = type.getDescription().getString().toLowerCase(Locale.ROOT);
                String ru = dictName(ruNameCache, descId);
                String en = dictName(enNameCache, descId);
                if (!path.contains(query) && !full.contains(query) && !name.contains(query)
                        && !ru.contains(query) && !en.contains(query)) continue;
            }
            ids.add(id);
        }

        // Мобы из белого списка всегда сверху: иначе только что добавленный моб теряется
        // где-то в середине длинного списка и кажется, что ничего не произошло.
        ids.sort(Comparator
                .comparingInt((ResourceLocation id) -> {
                    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
                    return (pig != null && type != null && pig.isWhitelisted(type)) ? 0 : 1;
                })
                .thenComparing(id -> {
                    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
                    return type == null ? id.toString() : type.getDescription().getString();
                }, String.CASE_INSENSITIVE_ORDER));

        mobs = ids;
        mobScroll = Mth.clamp(mobScroll, 0, Math.max(0, mobs.size() - mobVisibleRows()));
    }

    private TameablePigEntity selectedPig() {
        if (selected == null) return null;
        for (TameablePigEntity pig : pigs) {
            if (pig.getUUID().equals(selected)) return pig;
        }
        return null;
    }

    // ============================ чипы белого списка ================================
    // Раньше белый список был виден только числом ("1 тип(ов)") — чтобы убрать конкретного
    // моба, приходилось искать его в общем списке. Теперь каждый моб из белого списка
    // рисуется отдельной "чипкой" с крестиком прямо под профилем свиньи: клик по чипке
    // сразу снимает этого моба с белого списка, без поиска.

    private record Chip(ResourceLocation id, String label, int x, int y, int w, int h, boolean overflow) {
    }

    /** Сколько дополнительных строк (сверх первой) понадобится чипкам — нужно для layout(). */
    private int maxChipRow(TameablePigEntity pig, int startX, int totalW) {
        if (pig == null || pig.getWhitelistCount() == 0) return 0;
        List<Chip> chips = buildWhitelistChips(pig, startX, 0, totalW);
        int maxRow = 0;
        for (Chip c : chips) {
            maxRow = Math.max(maxRow, c.y() / (CHIP_H + CHIP_ROW_GAP));
        }
        return maxRow;
    }

    private List<Chip> buildWhitelistChips(TameablePigEntity pig, int startX, int startY, int totalW) {
        List<Chip> chips = new ArrayList<>();
        if (pig == null) return chips;
        List<ResourceLocation> ids = pig.getWhitelistIds();
        if (ids.isEmpty()) return chips;

        int x = startX;
        int row = 0;
        for (int i = 0; i < ids.size(); i++) {
            ResourceLocation id = ids.get(i);
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            String name = type != null ? type.getDescription().getString() : id.getPath();
            String label = trim(name, 90) + "  x";
            int w = font.width(label) + 10;
            int remaining = ids.size() - i - 1;

            if (x + w > startX + totalW && x > startX) {
                row++;
                x = startX;
                if (row >= WHITELIST_MAX_ROWS) {
                    if (!chips.isEmpty()) {
                        Chip prev = chips.remove(chips.size() - 1);
                        String overflowLabel = "+" + (remaining + 1);
                        int ow = font.width(overflowLabel) + 10;
                        chips.add(new Chip(null, overflowLabel, prev.x(), prev.y(), ow, CHIP_H, true));
                    }
                    break;
                }
            }
            int y = startY + row * (CHIP_H + CHIP_ROW_GAP);
            chips.add(new Chip(id, label, x, y, w, CHIP_H, false));
            x += w + CHIP_GAP;
        }
        return chips;
    }

    private void renderWhitelistStrip(GuiGraphics gg, TameablePigEntity pig, int mouseX, int mouseY) {
        box(gg, rightX, whitelistY, rightW, whitelistH, PANEL, LINE_SOFT);
        List<Chip> chips = buildWhitelistChips(pig, rightX + 4, whitelistY + 3, rightW - 8);
        if (chips.isEmpty()) {
            label(gg, Tr.s("gui.pig_mod.tablet.whitelist_empty"), rightX + 6,
                    whitelistY + (whitelistH - 8) / 2, TEXT_FAINT);
            return;
        }
        for (Chip c : chips) {
            boolean hover = inside(mouseX, mouseY, c.x(), c.y(), c.w(), c.h());
            int fill = c.overflow() ? PANEL_HI : hover ? 0xFF3A1620 : 0xFF0F3327;
            int border = c.overflow() ? LINE : hover ? RED : GREEN;
            box(gg, c.x(), c.y(), c.w(), c.h(), fill, border);
            label(gg, c.label(), c.x() + 5, c.y() + 3, c.overflow() ? TEXT_DIM : (hover ? RED : TEXT));
        }
    }

    /** @return true, если клик попал по чипке белого списка (и уже обработан). */
    private boolean clickWhitelistChip(TameablePigEntity pig, double mouseX, double mouseY) {
        List<Chip> chips = buildWhitelistChips(pig, rightX + 4, whitelistY + 3, rightW - 8);
        for (Chip c : chips) {
            if (!inside(mouseX, mouseY, c.x(), c.y(), c.w(), c.h())) continue;
            if (c.overflow()) {
                onlyWhitelisted = true;
                search = "";
                searchFocused = false;
                mobScroll = 0;
                rebuildMobList();
            } else {
                // Уже в белом списке -> клик снимает его (тот же пакет, что и добавление).
                PacketDistributor.sendToServer(new TabletWhitelistTogglePacket(pig.getUUID(), c.id()));
            }
            playClick();
            return true;
        }
        return false;
    }

    // =============================== раскладка ======================================

    private void layout() {
        pw = Mth.clamp(width - 30, 300, 520);
        ph = Mth.clamp(height - 30, 180, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        footerY = py + ph - FOOTER_H;

        sideX = px + PAD;
        sideY = py + HEADER_H;
        sideW = Mth.clamp(pw * 30 / 100, 118, 152);
        sideH = footerY - sideY - 4;

        rightX = sideX + sideW + 6;
        rightY = sideY;
        rightW = px + pw - PAD - rightX;

        // Полоска "белого списка": рисуется в 1-2 строки в зависимости от того, сколько
        // мобов реально помещается — с одним-двумя мобами (частый случай) занимает минимум
        // места, разрастаясь только когда мобов действительно много.
        whitelistY = rightY + PROFILE_H + 4;
        int chipRows = 1 + maxChipRow(selectedPig(), rightX + 4, rightW - 8);
        whitelistH = chipRows * CHIP_H + (chipRows - 1) * CHIP_ROW_GAP + 6;

        searchH = 16;
        filterW = 66;
        searchX = rightX;
        searchY = whitelistY + whitelistH + 4;
        searchW = rightW - filterW - 4;
        filterX = searchX + searchW + 4;

        listX = rightX;
        listY = searchY + searchH + 5;
        listW = rightW;
        listH = Math.max(MOB_ROW_H + 4, footerY - listY - 4);
    }

    private int pigVisibleRows() {
        return Math.max(1, (sideH - 27) / PIG_ROW_H);
    }

    private int mobVisibleRows() {
        return Math.max(1, (listH - 4) / MOB_ROW_H);
    }

    private int footerButtonW() {
        int available = pw - PAD * 2 - 62 - 6;
        return Math.max(40, Math.min(90, (available - 8) / 3));
    }

    // ============================ примитивы отрисовки ===============================

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void box(GuiGraphics gg, int x, int y, int w, int h, int fill, int border) {
        gg.fill(x, y, x + w, y + h, fill);
        gg.fill(x, y, x + w, y + 1, border);
        gg.fill(x, y + h - 1, x + w, y + h, border);
        gg.fill(x, y, x + 1, y + h, border);
        gg.fill(x + w - 1, y, x + w, y + h, border);
    }

    private void label(GuiGraphics gg, String text, int x, int y, int color) {
        gg.drawString(font, text, x, y, color, false);
    }

    private String trim(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    private void button(GuiGraphics gg, int x, int y, int w, int h, String text,
                        double mouseX, double mouseY, boolean active, int accent) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        int fill = active ? PANEL_SEL : hovered ? PANEL_HI : PANEL;
        int border = active ? accent : hovered ? LINE : LINE_SOFT;
        box(gg, x, y, w, h, fill, border);
        gg.drawCenteredString(font, trim(text, w - 6), x + w / 2, y + (h - 8) / 2,
                active || hovered ? TEXT : TEXT_DIM);
    }

    private void scrollbar(GuiGraphics gg, int x, int y, int h, int scroll, int maxScroll, int visible, int total) {
        gg.fill(x, y, x + SCROLL_W, y + h, 0xFF0B1A25);
        if (maxScroll <= 0) return;
        int thumbH = Mth.clamp((int) ((long) h * visible / Math.max(1, total)), 14, h);
        int travel = Math.max(1, h - thumbH);
        int thumbY = y + (int) ((long) travel * scroll / maxScroll);
        gg.fill(x, thumbY, x + SCROLL_W, thumbY + thumbH, CYAN_DIM);
        gg.fill(x, thumbY, x + 1, thumbY + thumbH, CYAN);
    }

    // ================================= render =======================================

    @Override
    public void renderBackground(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        // Никакого ванильного размытия: планшет должен оставаться чётким.
        gg.fill(0, 0, width, height, SCREEN_DIM);
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        layout();
        renderBackground(gg, mouseX, mouseY, partialTick);

        box(gg, px, py, pw, ph, FRAME_BG, LINE);
        gg.fill(px + 1, py + 1, px + pw - 1, py + 2, 0xFF16344A);

        renderHeader(gg);
        renderSidebar(gg, mouseX, mouseY);

        TameablePigEntity pig = selectedPig();
        if (pig == null) {
            int h = footerY - rightY - 4;
            box(gg, rightX, rightY, rightW, h, PANEL, LINE_SOFT);
            gg.drawCenteredString(font, Tr.s("gui.pig_mod.tablet.no_pig"), rightX + rightW / 2, rightY + h / 2 - 18, CYAN);
            gg.drawCenteredString(font, Tr.s("gui.pig_mod.tablet.no_pig_hint1"), rightX + rightW / 2, rightY + h / 2, TEXT_DIM);
            gg.drawCenteredString(font, Tr.s("gui.pig_mod.tablet.no_pig_hint2"), rightX + rightW / 2, rightY + h / 2 + 12, TEXT_DIM);
        } else {
            renderProfile(gg, pig);
            renderWhitelistStrip(gg, pig, mouseX, mouseY);
            renderSearch(gg, mouseX, mouseY);
            renderMobList(gg, pig, mouseX, mouseY);
        }

        renderFooter(gg, pig, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics gg) {
        label(gg, "PIG CONTROL", px + PAD, py + 8, CYAN);
        int titleX = px + PAD + font.width("PIG CONTROL") + 5;
        label(gg, trim(Tr.s("gui.pig_mod.tablet.subtitle"), pw - PAD * 2 - font.width("PIG CONTROL") - 90), titleX, py + 8, TEXT_DIM);

        String status = Tr.s(pigs.isEmpty() ? "gui.pig_mod.tablet.status.offline" : "gui.pig_mod.tablet.status.ok");
        int color = pigs.isEmpty() ? ORANGE : GREEN;
        int w = font.width(status) + 10;
        box(gg, px + pw - PAD - w, py + 5, w, 14, 0xFF0C2130, color);
        gg.drawCenteredString(font, status, px + pw - PAD - w / 2, py + 8, color);

        gg.fill(px + PAD, py + HEADER_H - 5, px + pw - PAD, py + HEADER_H - 4, LINE_SOFT);
    }

    private void renderSidebar(GuiGraphics gg, int mouseX, int mouseY) {
        box(gg, sideX, sideY, sideW, sideH, PANEL, LINE_SOFT);
        label(gg, Tr.s("gui.pig_mod.tablet.pigs"), sideX + 6, sideY + 6, TEXT);
        String counter = pigs.size() + " / " + PigControlTabletItem.getBoundPigs(tablet()).size();
        label(gg, counter, sideX + sideW - 6 - font.width(counter), sideY + 6, TEXT_FAINT);
        gg.fill(sideX + 6, sideY + 18, sideX + sideW - 6, sideY + 19, CYAN_DIM);

        int rows = pigVisibleRows();
        int listTop = sideY + 23;
        int innerW = sideW - 12 - SCROLL_W - 2;

        gg.enableScissor(sideX + 1, listTop, sideX + sideW - 1, Math.min(listTop + rows * PIG_ROW_H, sideY + sideH - 1));
        for (int row = 0; row < rows; row++) {
            int index = pigScroll + row;
            if (index >= pigs.size()) break;
            TameablePigEntity pig = pigs.get(index);
            int ry = listTop + row * PIG_ROW_H;
            boolean active = pig.getUUID().equals(selected);
            boolean hover = inside(mouseX, mouseY, sideX + 6, ry, innerW, PIG_ROW_H - 2);

            box(gg, sideX + 6, ry, innerW, PIG_ROW_H - 2,
                    active ? PANEL_SEL : hover ? PANEL_HI : ROW_BG,
                    active ? CYAN : LINE_SOFT);

            gg.renderItem(new ItemStack(ModItems.TAMEABLE_PIG_SPAWN_EGG.get()), sideX + 9, ry + 4);

            label(gg, trim(pig.getName().getString(), innerW - 32), sideX + 29, ry + 4, active ? TEXT : TEXT_DIM);
            String sub = Tr.s("gui.pig_mod.tablet.pig_sub", pig.getPigLevel(), pig.getCommand().getDisplayName());
            label(gg, trim(sub, innerW - 32), sideX + 29, ry + 14, commandColor(pig));
        }
        gg.disableScissor();

        int maxScroll = Math.max(0, pigs.size() - rows);
        scrollbar(gg, sideX + sideW - 6 - SCROLL_W, listTop, rows * PIG_ROW_H, pigScroll, maxScroll, rows, Math.max(1, pigs.size()));

        if (pigs.isEmpty()) {
            gg.drawCenteredString(font, Tr.s("gui.pig_mod.tablet.empty"), sideX + sideW / 2, listTop + 12, TEXT_FAINT);
        } else {
            label(gg, Tr.s("gui.pig_mod.tablet.remove_hint"), sideX + 6, sideY + sideH - 11, TEXT_FAINT);
        }
    }

    private int commandColor(TameablePigEntity pig) {
        return switch (pig.getCommand()) {
            case WORK -> ORANGE;
            case ATTACK -> RED;
            case SIT -> TEXT_FAINT;
            default -> GREEN;
        };
    }

    private void renderProfile(GuiGraphics gg, TameablePigEntity pig) {
        box(gg, rightX, rightY, rightW, PROFILE_H, PANEL, LINE_SOFT);
        int x = rightX + 8;
        int y = rightY + 6;

        String gender = Tr.s(pig.isFemale() ? "gui.pig_mod.tablet.female" : "gui.pig_mod.tablet.male");
        int genderColor = pig.isFemale() ? PINK : 0xFF7DD3FC;
        int gw = font.width(gender) + 10;
        box(gg, rightX + rightW - 8 - gw, y - 1, gw, 12, 0xFF0C2130, genderColor);
        gg.drawCenteredString(font, gender, rightX + rightW - 8 - gw / 2, y + 1, genderColor);

        label(gg, trim(pig.getName().getString(), rightW - gw - 26), x, y, CYAN);

        int barW = (rightW - 24) / 2;
        statBar(gg, x, y + 14, barW, Tr.s("gui.pig_mod.tablet.health"), pig.getHealth(), pig.getDisplayedMaxHealth(), GREEN);
        statBar(gg, x + barW + 8, y + 14, barW, Tr.s("gui.pig_mod.tablet.hunger"), pig.getHunger(), pig.getMaxHunger(), ORANGE);

        int col = (rightW - 16) / 3;
        int row1 = y + 36;
        kv(gg, x, row1, Tr.s("gui.pig_mod.tablet.level"), String.valueOf(pig.getPigLevel()), TEXT, col - 4);
        kv(gg, x + col, row1, Tr.s("gui.pig_mod.tablet.points"), String.valueOf(pig.getSkillPoints()), TEXT, col - 4);
        kv(gg, x + col * 2, row1, Tr.s("gui.pig_mod.tablet.command"), pig.getCommand().getDisplayName(), commandColor(pig), col - 4);

        // Реальные данные о сундуке/области/белом списке.
        // Раньше здесь всегда было "не привязан"/"не задана": поля жили только на сервере.
        int row2 = row1 + 20;
        BlockPos chest = pig.getCourierChestPos();
        kv(gg, x, row2, Tr.s("gui.pig_mod.tablet.chest"), chest == null ? Tr.s("gui.pig_mod.tablet.not_bound") : pos(chest),
                chest == null ? TEXT_FAINT : GREEN, col - 4);

        BlockPos a = pig.getWorkAreaPoint1();
        BlockPos b = pig.getWorkAreaPoint2();
        String area;
        int areaColor;
        if (a != null && b != null) {
            area = (Math.abs(a.getX() - b.getX()) + 1) + "x" + (Math.abs(a.getZ() - b.getZ()) + 1);
            areaColor = GREEN;
        } else if (a != null) {
            area = Tr.s("gui.pig_mod.tablet.area_partial");
            areaColor = ORANGE;
        } else {
            area = Tr.s("gui.pig_mod.tablet.not_set");
            areaColor = TEXT_FAINT;
        }
        kv(gg, x + col, row2, Tr.s("gui.pig_mod.tablet.area"), area, areaColor, col - 4);

        int wl = pig.getWhitelistCount();
        kv(gg, x + col * 2, row2, Tr.s("gui.pig_mod.tablet.whitelist"), Tr.s("gui.pig_mod.tablet.whitelist_count", wl), wl > 0 ? GREEN : TEXT_FAINT, col - 4);
    }

    private void kv(GuiGraphics gg, int x, int y, String key, String value, int valueColor, int maxW) {
        label(gg, trim(key, maxW), x, y, TEXT_FAINT);
        label(gg, trim(value, maxW), x, y + 10, valueColor);
    }

    private static String pos(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private void statBar(GuiGraphics gg, int x, int y, int w, String name, double value, double max, int color) {
        label(gg, name, x, y, TEXT_FAINT);
        String text = (int) value + "/" + (int) max;
        label(gg, text, x + w - font.width(text), y, TEXT_DIM);
        int barY = y + 10;
        box(gg, x, barY, w, 6, 0xFF08131C, LINE_SOFT);
        float ratio = max <= 0 ? 0f : (float) Mth.clamp(value / max, 0.0, 1.0);
        int filled = Math.round((w - 2) * ratio);
        if (filled > 0) gg.fill(x + 1, barY + 1, x + 1 + filled, barY + 5, color);
    }

    private void renderSearch(GuiGraphics gg, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, searchX, searchY, searchW, searchH);
        box(gg, searchX, searchY, searchW, searchH, 0xFF08141E,
                searchFocused ? CYAN : hovered ? LINE : LINE_SOFT);

        int textX = searchX + 5;
        int textY = searchY + (searchH - 8) / 2;
        if (search.isEmpty() && !searchFocused) {
            label(gg, trim(Tr.s("gui.pig_mod.tablet.search"), searchW - 10), textX, textY, TEXT_FAINT);
        } else {
            String shown = search;
            while (font.width(shown) > searchW - 16 && !shown.isEmpty()) shown = shown.substring(1);
            label(gg, shown, textX, textY, TEXT);
            if (searchFocused && (ticks / 6) % 2 == 0) {
                int cx = textX + font.width(shown) + 1;
                gg.fill(cx, textY - 1, cx + 1, textY + 9, CYAN);
            }
        }
        if (!search.isEmpty()) {
            label(gg, "×", searchX + searchW - 9, textY, TEXT_DIM);
        }

        button(gg, filterX, searchY, filterW, searchH, Tr.s(onlyWhitelisted ? "gui.pig_mod.tablet.only_whitelisted" : "gui.pig_mod.tablet.all_mobs"),
                mouseX, mouseY, onlyWhitelisted, GREEN);
    }

    private void renderMobList(GuiGraphics gg, TameablePigEntity pig, int mouseX, int mouseY) {
        box(gg, listX, listY, listW, listH, PANEL, LINE_SOFT);

        if (mobs.isEmpty()) {
            gg.drawCenteredString(font, Tr.s("gui.pig_mod.tablet.nothing_found"), listX + listW / 2, listY + listH / 2 - 4, TEXT_FAINT);
            return;
        }

        int rows = mobVisibleRows();
        int innerW = listW - 4 - SCROLL_W - 3;

        gg.enableScissor(listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);
        for (int row = 0; row < rows; row++) {
            int index = mobScroll + row;
            if (index >= mobs.size()) break;
            ResourceLocation id = mobs.get(index);
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            if (type == null) continue;

            int ry = listY + 2 + row * MOB_ROW_H;
            boolean white = pig.isWhitelisted(type);
            boolean hover = inside(mouseX, mouseY, listX + 2, ry, innerW, MOB_ROW_H - 2);

            box(gg, listX + 2, ry, innerW, MOB_ROW_H - 2,
                    white ? 0xFF0F3327 : hover ? PANEL_HI : ROW_BG,
                    white ? GREEN : hover ? LINE : LINE_SOFT);

            SpawnEggItem egg = SpawnEggItem.byId(type);
            if (egg != null) {
                gg.renderItem(new ItemStack(egg), listX + 5, ry + 1);
            } else {
                gg.fill(listX + 9, ry + 5, listX + 17, ry + 13, white ? GREEN : TEXT_FAINT);
            }

            String action = white ? Tr.s("gui.pig_mod.tablet.in_list") : hover ? Tr.s("gui.pig_mod.tablet.add") : "";
            int actionW = action.isEmpty() ? 0 : font.width(action) + 6;
            int nameW = innerW - 28 - actionW;
            label(gg, trim(type.getDescription().getString(), nameW), listX + 26, ry + 1, white ? TEXT : TEXT_DIM);
            label(gg, trim(id.toString(), nameW), listX + 26, ry + 10, TEXT_FAINT);
            if (!action.isEmpty()) {
                label(gg, action, listX + 2 + innerW - font.width(action) - 4, ry + 5, white ? GREEN : CYAN);
            }
        }
        gg.disableScissor();

        int maxScroll = Math.max(0, mobs.size() - rows);
        scrollbar(gg, listX + listW - 3 - SCROLL_W, listY + 2, rows * MOB_ROW_H, mobScroll, maxScroll, rows, mobs.size());
    }

    private void renderFooter(GuiGraphics gg, TameablePigEntity pig, int mouseX, int mouseY) {
        gg.fill(px + PAD, footerY - 1, px + pw - PAD, footerY, LINE_SOFT);

        int by = footerY + 5;
        int bh = 16;
        int closeW = 62;
        int bw = footerButtonW();

        drawActionButton(gg, px + PAD, by, bw, bh, Tr.s("gui.pig_mod.tablet.clear_area"), mouseX, mouseY, pig != null && pig.hasWorkArea());
        drawActionButton(gg, px + PAD + bw + 4, by, bw, bh, Tr.s("gui.pig_mod.tablet.clear_chest"), mouseX, mouseY, pig != null && pig.getCourierChestPos() != null);
        drawActionButton(gg, px + PAD + (bw + 4) * 2, by, bw, bh, Tr.s("gui.pig_mod.tablet.clear_list"), mouseX, mouseY, pig != null && pig.getWhitelistCount() > 0);

        button(gg, px + pw - PAD - closeW, by, closeW, bh, Tr.s("gui.pig_mod.tablet.close"), mouseX, mouseY, false, CYAN);
    }

    private void drawActionButton(GuiGraphics gg, int x, int y, int w, int h, String text,
                                  int mouseX, int mouseY, boolean usable) {
        boolean hovered = usable && inside(mouseX, mouseY, x, y, w, h);
        int fill = hovered ? 0xFF3A1620 : usable ? PANEL : 0xFF0A141D;
        int border = hovered ? RED : usable ? LINE_SOFT : 0xFF14222D;
        box(gg, x, y, w, h, fill, border);
        gg.drawCenteredString(font, trim(text, w - 6), x + w / 2, y + (h - 8) / 2,
                usable ? (hovered ? RED : TEXT_DIM) : TEXT_FAINT);
    }

    // ================================== ввод ========================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        layout();

        TameablePigEntity chipPig = selectedPig();
        if (chipPig != null && clickWhitelistChip(chipPig, mouseX, mouseY)) {
            return true;
        }

        if (inside(mouseX, mouseY, searchX, searchY, searchW, searchH)) {
            searchFocused = true;
            if (!search.isEmpty() && mouseX >= searchX + searchW - 12) {
                search = "";
                mobScroll = 0;
                rebuildMobList();
            }
            return true;
        }
        searchFocused = false;

        if (inside(mouseX, mouseY, filterX, searchY, filterW, searchH)) {
            onlyWhitelisted = !onlyWhitelisted;
            mobScroll = 0;
            rebuildMobList();
            playClick();
            return true;
        }

        int by = footerY + 5;
        int bh = 16;
        int closeW = 62;
        int bw = footerButtonW();
        if (inside(mouseX, mouseY, px + pw - PAD - closeW, by, closeW, bh)) {
            onClose();
            return true;
        }

        TameablePigEntity pig = selectedPig();
        if (pig != null) {
            if (pig.hasWorkArea() && inside(mouseX, mouseY, px + PAD, by, bw, bh)) {
                sendAction(pig, TabletActionPacket.ACTION_CLEAR_AREA);
                return true;
            }
            if (pig.getCourierChestPos() != null && inside(mouseX, mouseY, px + PAD + bw + 4, by, bw, bh)) {
                sendAction(pig, TabletActionPacket.ACTION_CLEAR_CHEST);
                return true;
            }
            if (pig.getWhitelistCount() > 0 && inside(mouseX, mouseY, px + PAD + (bw + 4) * 2, by, bw, bh)) {
                sendAction(pig, TabletActionPacket.ACTION_CLEAR_WHITELIST);
                return true;
            }
        }

        // Список свиней
        int rows = pigVisibleRows();
        int listTop = sideY + 23;
        int scrollX = sideX + sideW - 6 - SCROLL_W;
        if (inside(mouseX, mouseY, scrollX, listTop, SCROLL_W, rows * PIG_ROW_H)) {
            draggingPigs = true;
            dragPigScroll(mouseY, listTop, rows * PIG_ROW_H);
            return true;
        }
        if (inside(mouseX, mouseY, sideX + 6, listTop, sideW - 12, rows * PIG_ROW_H)) {
            int row = (int) ((mouseY - listTop) / PIG_ROW_H);
            int index = pigScroll + row;
            if (index >= 0 && index < pigs.size()) {
                TameablePigEntity clicked = pigs.get(index);
                if (button == 1) {
                    // ПКМ по карточке — убрать свинью из памяти планшета
                    PacketDistributor.sendToServer(new TabletActionPacket(clicked.getUUID(), TabletActionPacket.ACTION_UNBIND_PIG));
                    pigs.remove(index);
                    if (clicked.getUUID().equals(selected)) selected = null;
                } else {
                    selected = clicked.getUUID();
                    PacketDistributor.sendToServer(new TabletSelectPigPacket(selected));
                    mobScroll = 0;
                    rebuildMobList();
                }
                playClick();
                return true;
            }
        }

        // Список мобов
        if (pig != null) {
            int mobRows = mobVisibleRows();
            int mobScrollX = listX + listW - 3 - SCROLL_W;
            if (inside(mouseX, mouseY, mobScrollX, listY + 2, SCROLL_W, mobRows * MOB_ROW_H)) {
                draggingMobs = true;
                dragMobScroll(mouseY, listY + 2, mobRows * MOB_ROW_H);
                return true;
            }
            if (inside(mouseX, mouseY, listX + 2, listY + 2, listW - 8 - SCROLL_W, mobRows * MOB_ROW_H)) {
                int row = (int) ((mouseY - (listY + 2)) / MOB_ROW_H);
                int index = mobScroll + row;
                if (index >= 0 && index < mobs.size()) {
                    PacketDistributor.sendToServer(new TabletWhitelistTogglePacket(pig.getUUID(), mobs.get(index)));
                    playClick();
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendAction(TameablePigEntity pig, int action) {
        PacketDistributor.sendToServer(new TabletActionPacket(pig.getUUID(), action));
        playClick();
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingPigs) {
            dragPigScroll(mouseY, sideY + 23, pigVisibleRows() * PIG_ROW_H);
            return true;
        }
        if (draggingMobs) {
            dragMobScroll(mouseY, listY + 2, mobVisibleRows() * MOB_ROW_H);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingPigs = false;
        draggingMobs = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void dragPigScroll(double mouseY, int trackTop, int trackH) {
        int max = Math.max(0, pigs.size() - pigVisibleRows());
        if (max <= 0) { pigScroll = 0; return; }
        double ratio = Mth.clamp((mouseY - trackTop) / Math.max(1, trackH), 0.0, 1.0);
        pigScroll = (int) Math.round(ratio * max);
    }

    private void dragMobScroll(double mouseY, int trackTop, int trackH) {
        int max = Math.max(0, mobs.size() - mobVisibleRows());
        if (max <= 0) { mobScroll = 0; return; }
        double ratio = Mth.clamp((mouseY - trackTop) / Math.max(1, trackH), 0.0, 1.0);
        mobScroll = (int) Math.round(ratio * max);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        layout();
        int step = scrollY > 0 ? -1 : 1;
        if (inside(mouseX, mouseY, sideX, sideY, sideW, sideH)) {
            pigScroll = Mth.clamp(pigScroll + step, 0, Math.max(0, pigs.size() - pigVisibleRows()));
            return true;
        }
        if (inside(mouseX, mouseY, listX, listY, listW, listH)) {
            mobScroll = Mth.clamp(mobScroll + step * 2, 0, Math.max(0, mobs.size() - mobVisibleRows()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchFocused && codePoint >= ' ' && codePoint != 127 && search.length() < 48) {
            search = search + codePoint;
            mobScroll = 0;
            rebuildMobList();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!search.isEmpty()) {
                    search = search.substring(0, search.length() - 1);
                    mobScroll = 0;
                    rebuildMobList();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V && Screen.hasControlDown() && minecraft != null) {
                String clip = minecraft.keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    search = (search + clip.replaceAll("[\\r\\n]", "")).trim();
                    if (search.length() > 48) search = search.substring(0, 48);
                    mobScroll = 0;
                    rebuildMobList();
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
