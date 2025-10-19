package org.bukkit;

/**
 * Minimal ChatColor enum for compilation purposes.
 */
public enum ChatColor {
    RED("§c"),
    GREEN("§a"),
    YELLOW("§e"),
    GOLD("§6");

    private final String code;

    ChatColor(final String code) {
        this.code = code;
    }

    @Override
    public String toString() {
        return code;
    }
}
