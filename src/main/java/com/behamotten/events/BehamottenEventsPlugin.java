package com.behamotten.events;

import java.util.Iterator;
import org.bukkit.advancement.Advancement;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import com.behamotten.events.progress.AdvancementProgressListener;
import com.behamotten.events.progress.ProgressDataManager;
import com.behamotten.events.progress.ProgressDataManager.MasterRefreshResult;
import com.behamotten.events.progress.ProgressMasterCommand;

/**
 * Main plugin entry point for managing event participation commands and persistence.
 */
public final class BehamottenEventsPlugin extends JavaPlugin {
    private EventParticipationData participationData;
    private ProgressDataManager progressDataManager;

    @Override
    public void onEnable() {
        participationData = EventParticipationData.load(this);
        progressDataManager = ProgressDataManager.load(this);
        new EventCommandRegistrar(this, participationData).registerCommands();
        registerProgressCommands();
        getLogger().info(() -> "Loaded " + participationData.getParticipantCount() + " event participants.");
        registerProgressListeners();
        initializeProgressMasters();
    }

    @Override
    public void onDisable() {
        if (participationData != null) {
            participationData.save();
        }
        if (progressDataManager != null) {
            progressDataManager.saveAll();
        }
    }

    private void registerProgressListeners() {
        if (progressDataManager == null) {
            return;
        }
        getServer().getPluginManager().registerEvents(new AdvancementProgressListener(progressDataManager), this);
    }

    private void registerProgressCommands() {
        if (progressDataManager == null) {
            return;
        }
        final PluginCommand refreshCommand = getCommand("refreshprogressmasters");
        if (refreshCommand == null) {
            getLogger().severe("Command 'refreshprogressmasters' is not defined in plugin.yml.");
            return;
        }
        refreshCommand.setExecutor(new ProgressMasterCommand(this, progressDataManager));
    }

    private void initializeProgressMasters() {
        if (progressDataManager == null) {
            return;
        }
        final Iterator<Advancement> iterator = getServer().advancementIterator();
        final MasterRefreshResult result = progressDataManager.refreshMasterExports(iterator);
        getLogger().info(() -> "Fortschritts-Masterdateien initialisiert (Advancements: "
                + result.getAdvancementCount() + ", Quests: " + result.getQuestCount() + ").");
    }
}
