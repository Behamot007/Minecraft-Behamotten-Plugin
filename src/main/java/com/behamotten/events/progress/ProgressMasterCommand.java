package com.behamotten.events.progress;

import java.util.Iterator;
import java.util.Objects;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.advancement.Advancement;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Provides an administrative command to refresh the progress master exports at runtime.
 */
public final class ProgressMasterCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final ProgressDataManager progressDataManager;

    public ProgressMasterCommand(final JavaPlugin plugin, final ProgressDataManager progressDataManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.progressDataManager = Objects.requireNonNull(progressDataManager, "progressDataManager");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label,
            final String[] args) {
        try {
            final Iterator<Advancement> iterator = plugin.getServer().advancementIterator();
            final ProgressDataManager.MasterRefreshResult result = progressDataManager.refreshMasterExports(iterator);
            if (sender != null) {
                sender.sendMessage(ChatColor.GREEN
                        + "Fortschritts-Masterdateien wurden neu erstellt. Advancements: "
                        + result.getAdvancementCount() + ", Quests: " + result.getQuestCount());
            }
        } catch (final RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Aktualisieren der Fortschritts-Masterdateien.", exception);
            if (sender != null) {
                sender.sendMessage(ChatColor.RED
                        + "Die Masterdateien konnten nicht aktualisiert werden. Details siehe Konsole.");
            }
        }
        return true;
    }
}
