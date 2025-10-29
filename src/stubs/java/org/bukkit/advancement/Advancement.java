package org.bukkit.advancement;

import java.util.Collection;
import java.util.Collections;
import org.bukkit.NamespacedKey;

/**
 * Minimal advancement stub aligned with the Bukkit interface from 1.21.
 */
public interface Advancement {
    /**
     * Returns the unique key of this advancement.
     */
    default NamespacedKey getKey() {
        return new NamespacedKey("minecraft", "dummy");
    }

    /**
     * Returns the parent advancement if available.
     */
    default Advancement getParent() {
        return null;
    }

    /**
     * Returns the display information, if any.
     */
    default AdvancementDisplay getDisplay() {
        return null;
    }

    /**
     * Returns the criteria defined for this advancement.
     */
    default Collection<String> getCriteria() {
        return Collections.emptyList();
    }
}
