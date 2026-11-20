package com.example.examplemod;

/**
 * Режим поведения боевой свиньи, выбираемый владельцем через её GUI.
 */
public enum PigCommand {
    SIT(0, "pig_command.pig_mod.sit"),
    FOLLOW(1, "pig_command.pig_mod.follow"),
    ATTACK(2, "pig_command.pig_mod.attack"),
    WORK(3, "pig_command.pig_mod.work");

    private final int id;
    private final String displayName;

    PigCommand(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public int getId() {
        return id;
    }

    /** Ключ перевода названия режима (assets/pig_mod/lang). */
    public String getTranslationKey() {
        return displayName;
    }

    /** Название режима на языке клиента (для GUI, которое рисует String). */
    public String getDisplayName() {
        return Tr.s(displayName);
    }

    public PigCommand next() {
        return switch (this) {
            case SIT -> FOLLOW;
            case FOLLOW -> ATTACK;
            case ATTACK -> WORK;
            case WORK -> SIT;
        };
    }

    public static PigCommand fromId(int id) {
        for (PigCommand command : values()) {
            if (command.id == id) {
                return command;
            }
        }
        return FOLLOW;
    }
}
