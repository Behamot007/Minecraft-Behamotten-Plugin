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
 * Administrative command that (re-)generates the advancement master export on demand.
 */
public final class AdvancementMasterCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final ProgressDataManager progressDataManager;

    public AdvancementMasterCommand(final JavaPlugin plugin, final ProgressDataManager progressDataManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.progressDataManager = Objects.requireNonNull(progressDataManager, "progressDataManager");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label,
            final String[] args) {
        try {
            final Iterator<Advancement> iterator = plugin.getServer().advancementIterator();
            final ProgressDataManager.AdvancementMasterResult result = progressDataManager
                    .regenerateAdvancementMaster(iterator);
            if (sender != null) {
                if (result.getErrors().isEmpty()) {
                    sender.sendMessage(ChatColor.GREEN + "Advancement-Masterdatei wurde aktualisiert (" + result
                            .getAdvancementCount() + " Einträge, " + result.getEnglishResourceCount()
                            + " englische Texte).");
                } else {
                    sender.sendMessage(ChatColor.RED + "Die Advancement-Masterdatei konnte nicht vollständig aktualisiert"
                            + " werden: " + String.join(", ", result.getErrors()));
                }
            }
        } catch (final RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Aktualisieren der Advancement-Masterdatei.",
                    exception);
            if (sender != null) {
                sender.sendMessage(ChatColor.RED
                        + "Die Advancement-Masterdatei konnte nicht aktualisiert werden. Details siehe Konsole.");
            }
        }
        return true;
    }
}

