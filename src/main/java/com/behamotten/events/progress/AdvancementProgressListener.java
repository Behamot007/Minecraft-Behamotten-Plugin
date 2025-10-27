package com.behamotten.events.progress;

import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

/**
 * Listens for advancement completions and updates the export data accordingly.
 */
public final class AdvancementProgressListener implements Listener {
    private final ProgressDataManager dataManager;

    public AdvancementProgressListener(final ProgressDataManager dataManager) {
        this.dataManager = Objects.requireNonNull(dataManager, "dataManager");
    }

    @EventHandler
    public void onAdvancementCompleted(final PlayerAdvancementDoneEvent event) {
        final Advancement advancement = event.getAdvancement();
        if (shouldIgnore(advancement)) {
            return;
        }
        dataManager.recordAdvancementCompletion(event.getPlayer(), advancement);
    }

    private boolean shouldIgnore(final Advancement advancement) {
        if (advancement == null) {
            return true;
        }
        final NamespacedKey key = advancement.getKey();
        if (key == null) {
            return true;
        }
        final String keyValue = key.getKey();
        return keyValue != null && keyValue.startsWith("recipes/");
    }
}
