package com.behamotten.events.advancements;

import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Command that exports all known advancements to a consolidated JSON document.
 */
public final class AdvancementExportCommand implements CommandExecutor {
    private final JavaPlugin plugin;

    public AdvancementExportCommand(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label,
            final String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can run this command.");
            return true;
        }
        final Player player = (Player) sender;
        final AdvancementExporter exporter = new AdvancementExporter(plugin, player);
        try {
            final AdvancementExporter.ExportResult result = exporter.export();
            sender.sendMessage(ChatColor.GREEN + "Exported " + result.advancementCount() + " advancements ("
                    + result.groupCount() + " groups). Output: " + result.outputFile());
        } catch (final AdvancementExportException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not export advancements.", exception);
            sender.sendMessage(ChatColor.RED + "Failed to export advancements. Check the server log for details.");
        }
        return true;
    }
}
