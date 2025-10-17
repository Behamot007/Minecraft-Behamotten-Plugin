package net.minecraft.nbt;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Simple map-backed implementation of an NBT compound tag.
 */
public class CompoundTag {
    private final Map<String, Object> values = new HashMap<>();

    public ListTag getList(final String key, final int type) {
        final Object value = values.get(key);
        if (value instanceof ListTag list) {
            return list;
        }
        return new ListTag();
    }

    public boolean hasUUID(final String key) {
        return values.get(key) instanceof UUID;
    }

    public UUID getUUID(final String key) {
        final Object value = values.get(key);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        throw new IllegalStateException("No UUID stored under key " + key);
    }

    public String getString(final String key) {
        final Object value = values.get(key);
        if (value instanceof String string) {
            return string;
        }
        return "";
    }

    public void putUUID(final String key, final UUID value) {
        values.put(key, Objects.requireNonNull(value, "value"));
    }

    public void putString(final String key, final String value) {
        values.put(key, Objects.requireNonNull(value, "value"));
    }

    public void put(final String key, final ListTag list) {
        values.put(key, Objects.requireNonNull(list, "list"));
    }
}
