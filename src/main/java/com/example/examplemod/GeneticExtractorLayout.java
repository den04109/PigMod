package com.example.examplemod;

/**
 * Единый источник координат слотов Генетического Экстрактора. И GeneticExtractorMenu (сервер/клиент —
 * реальные Slot'ы), и GeneticExtractorScreen (отрисовка рамок) берут числа ТОЛЬКО отсюда — раньше
 * рассинхронизация похожих констант в PigMenu/PigScreen уже приводила к "съезжанию" на пиксель,
 * поэтому здесь этот класс общий для обоих.
 */
public final class GeneticExtractorLayout {

    public static final int SLOT = 18;

    private GeneticExtractorLayout() {
    }

    public static int baseSlotX() {
        return 16;
    }

    public static int baseSlotY() {
        return 32;
    }

    public static int carrierSlotX(int carrierIndex) {
        return 44 + carrierIndex * (SLOT + 2);
    }

    public static int carrierSlotY() {
        return 32;
    }

    public static int catalystSlotX() {
        return 16;
    }

    public static int catalystSlotY() {
        return 62;
    }

    public static int arrowX(int tier) {
        return carrierSlotX(tier - 1) + SLOT + 8;
    }

    public static int arrowY() {
        return 45;
    }

    public static int outputSlotX(int tier) {
        return arrowX(tier) + 24 + 5;
    }

    public static int outputSlotY() {
        return 47;
    }

    public static int imageWidth(int tier) {
        // Math.max — у Tier 1 узкое окно (мало слотов-носителей), но заголовок "Экстрактор (Tier N)"
        // всё равно должен в него помещаться, иначе текст вылезает за рамки панели.
        return Math.max(outputSlotX(tier) + SLOT + 16, 150);
    }

    public static int imageHeight() {
        return 166;
    }

    public static int invX(int tier) {
        return (imageWidth(tier) - 9 * SLOT) / 2;
    }

    public static int invY() {
        return 92;
    }

    public static int hotbarY() {
        return invY() + 3 * SLOT + 4;
    }
}