package org.bukkit.event.player;

import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

/**
 * Minimal advancement completion event stub.
 */
public class PlayerAdvancementDoneEvent extends Event {
    private final Player player;
    private final Advancement advancement;

    public PlayerAdvancementDoneEvent(final Player player, final Advancement advancement) {
        this.player = player;
        this.advancement = advancement;
    }

    public Player getPlayer() {
        return player;
    }

    public Advancement getAdvancement() {
        return advancement;
    }
}
