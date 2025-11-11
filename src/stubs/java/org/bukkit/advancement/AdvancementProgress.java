package org.bukkit.advancement;

import java.util.Collection;
import java.util.Collections;

/**
 * Minimal advancement progress stub mirroring the Bukkit interface.
 */
public interface AdvancementProgress {
    default boolean awardCriteria(final String criterion) {
        return true;
    }

    default Collection<String> getRemainingCriteria() {
        return Collections.emptyList();
    }

    default Collection<String> getAwardedCriteria() {
        return Collections.emptyList();
    }
}
