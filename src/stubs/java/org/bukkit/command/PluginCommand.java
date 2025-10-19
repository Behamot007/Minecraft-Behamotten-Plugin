package org.bukkit.command;

/**
 * Minimal plugin command implementation for compilation.
 */
public class PluginCommand extends Command {
    public PluginCommand(final String name) {
        super(name);
    }

    public void setExecutor(final CommandExecutor executor) {
        // no-op for stubs
    }

    public void setTabCompleter(final TabCompleter completer) {
        // no-op for stubs
    }
}
