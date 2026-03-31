package com.mafia.game;

import net.kyori.adventure.text.format.NamedTextColor;

public enum Role {
    MAFIA("마피아", NamedTextColor.RED, true),
    POLICE("경찰", NamedTextColor.BLUE, false),
    DOCTOR("의사", NamedTextColor.GREEN, false),
    JOKER("조커", NamedTextColor.YELLOW, false),
    CIVILIAN("시민", NamedTextColor.WHITE, false);

    private final String displayName;
    private final NamedTextColor color;
    private final boolean evil;

    Role(String displayName, NamedTextColor color, boolean evil) {
        this.displayName = displayName;
        this.color = color;
        this.evil = evil;
    }

    public String getDisplayName() { return displayName; }
    public NamedTextColor getColor() { return color; }
    public boolean isEvil() { return evil; }
}
