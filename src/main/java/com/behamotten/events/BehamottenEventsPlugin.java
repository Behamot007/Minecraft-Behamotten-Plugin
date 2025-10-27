package com.behamotten.events;

import java.util.Iterator;
import org.bukkit.advancement.Advancement;
import org.bukkit.plugin.java.JavaPlugin;

import com.behamotten.events.progress.AdvancementProgressListener;
import com.behamotten.events.progress.ProgressDataManager;

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
        getLogger().info(() -> "Loaded " + participationData.getParticipantCount() + " event participants.");
        registerProgressListeners();
        synchronizeAdvancements();
        importQuestDefinitions();
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

    private void synchronizeAdvancements() {
        if (progressDataManager == null) {
            return;
        }
        final Iterator<Advancement> iterator = getServer().advancementIterator();
        final int synchronizedAdvancements = progressDataManager.synchronizeAdvancements(iterator);
        if (synchronizedAdvancements > 0) {
            getLogger().info(() -> "Synchronised " + synchronizedAdvancements + " advancements into progress exports.");
        }
    }

    private void importQuestDefinitions() {
        if (progressDataManager == null) {
            return;
        }
        final int imported = progressDataManager.importQuestDefinitions();
        if (imported > 0) {
            getLogger().info(() -> "Imported " + imported + " quest definitions for progress exports.");
        }
    }
}
