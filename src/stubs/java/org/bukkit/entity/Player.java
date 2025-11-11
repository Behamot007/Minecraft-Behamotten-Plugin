package org.bukkit.entity;

import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;

public interface Player extends CommandSender {
    UUID getUniqueId();

    String getName();

    default AdvancementProgress getAdvancementProgress(final Advancement advancement) {
        return new AdvancementProgress() {
        };
    }
}
