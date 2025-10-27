package org.bukkit;

import java.util.Objects;

/**
 * Minimal namespaced key stub.
 */
public class NamespacedKey {
    private final String namespace;
    private final String key;

    public NamespacedKey(final String namespace, final String key) {
        this.namespace = namespace;
        this.key = key;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getKey() {
        return key;
    }

    @Override
    public String toString() {
        return (namespace != null ? namespace : "minecraft") + ":" + (key != null ? key : "unknown");
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof NamespacedKey)) {
            return false;
        }
        final NamespacedKey other = (NamespacedKey) obj;
        return Objects.equals(namespace, other.namespace) && Objects.equals(key, other.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, key);
    }
}
