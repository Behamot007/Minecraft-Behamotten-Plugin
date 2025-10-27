package org.bukkit.advancement;

import java.util.Collection;
import java.util.Collections;
import org.bukkit.NamespacedKey;

/**
 * Minimal advancement stub.
 */
public class Advancement {
    public NamespacedKey getKey() {
        return new NamespacedKey("minecraft", "dummy");
    }

    public Advancement getParent() {
        return null;
    }

    public AdvancementDisplay getDisplay() {
        return null;
    }

    public Collection<String> getCriteria() {
        return Collections.emptyList();
    }
}
