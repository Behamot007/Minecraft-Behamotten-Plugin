package org.bukkit.advancement;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/**
 * Minimal advancement display stub.
 */
public class AdvancementDisplay {
    public Component title() {
        return null;
    }

    public Component description() {
        return null;
    }

    public ItemStack icon() {
        return null;
    }

    public NamespacedKey background() {
        return null;
    }

    public AdvancementFrameType frame() {
        return AdvancementFrameType.TASK;
    }

    public boolean isHidden() {
        return false;
    }

    public boolean doesShowToast() {
        return true;
    }

    public boolean doesAnnounceToChat() {
        return true;
    }
}
