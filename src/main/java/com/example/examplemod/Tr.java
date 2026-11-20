package com.example.examplemod;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Короткие помощники для переводов (assets/pig_mod/lang/*.json).
 * c(...) — готовый Component (для чата и подсказок: переводится на стороне клиента);
 * s(...) — строка для GUI, которое рисует текст через String (берёт язык клиента).
 */
public final class Tr {

    private Tr() {
    }

    public static MutableComponent c(String key, Object... args) {
        return Component.translatable(key, args);
    }

    public static String s(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
