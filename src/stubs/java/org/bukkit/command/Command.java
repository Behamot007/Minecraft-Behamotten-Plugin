package org.bukkit.command;

/**
 * Minimal representation of a Bukkit command.
 */
public class Command {
    private final String name;

    public Command(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
