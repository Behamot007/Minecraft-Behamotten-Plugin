package org.bukkit.advancement;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/**
 * Minimal advancement display stub mirroring the Bukkit API.
 *
 * <p>The upstream API models {@code AdvancementDisplay} as an interface, so we
 * provide the same shape here to ensure our code links against the correct
 * type during compilation. Default implementations are supplied to keep the
 * stub convenient for unit tests.</p>
 */
public interface AdvancementDisplay {
    default Component title() {
        return null;
    }

    default Component description() {
        return null;
    }

    default ItemStack icon() {
        return null;
    }

    default NamespacedKey background() {
        return null;
    }

    default AdvancementFrameType frame() {
        return AdvancementFrameType.TASK;
    }

    default boolean isHidden() {
        return false;
    }

    default boolean doesShowToast() {
        return true;
    }

    default boolean doesAnnounceToChat() {
        return true;
    }
}
