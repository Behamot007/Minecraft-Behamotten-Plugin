package com.behamotten.events.progress;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Administrative command that (re-)generates the quest master export based on FTB quest data.
 */
public final class QuestMasterCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final ProgressDataManager progressDataManager;

    public QuestMasterCommand(final JavaPlugin plugin, final ProgressDataManager progressDataManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.progressDataManager = Objects.requireNonNull(progressDataManager, "progressDataManager");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label,
            final String[] args) {
        try {
            final ProgressDataManager.QuestMasterResult result = progressDataManager.regenerateQuestMaster();
            if (sender != null) {
                final List<String> errors = result.getErrors();
                if (errors.isEmpty()) {
                    final StringBuilder message = new StringBuilder();
                    message.append(ChatColor.GREEN).append("Quest-Masterdatei wurde aktualisiert (")
                            .append(result.getQuestCount()).append(" Quests in ").append(result.getChapterCount())
                            .append(" Kapiteln, ").append(result.getTranslationCount()).append(" Übersetzungen)");
                    final List<String> warnings = result.getWarnings();
                    if (!warnings.isEmpty()) {
                        message.append(ChatColor.YELLOW).append(" – Warnungen: ").append(String.join(", ", warnings));
                    }
                    sender.sendMessage(message.toString());
                } else {
                    sender.sendMessage(ChatColor.RED
                            + "Die Quest-Masterdatei konnte nicht vollständig aktualisiert werden: "
                            + String.join(", ", errors));
                    final List<String> warnings = result.getWarnings();
                    if (!warnings.isEmpty()) {
                        sender.sendMessage(ChatColor.YELLOW + "Zusätzliche Warnungen: " + String.join(", ", warnings));
                    }
                }
            }
        } catch (final RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Aktualisieren der Quest-Masterdatei.", exception);
            if (sender != null) {
                sender.sendMessage(ChatColor.RED
                        + "Die Quest-Masterdatei konnte nicht aktualisiert werden. Details siehe Konsole.");
            }
        }
        return true;
    }
}

