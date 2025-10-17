package net.minecraft.world.level.saveddata;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.nbt.CompoundTag;

/**
 * In-memory implementation of the SavedData storage API.
 */
public class SavedDataStorage {
    private final Map<String, SavedData> data = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <T extends SavedData> T computeIfAbsent(final Function<CompoundTag, T> load,
            final Supplier<T> factory,
            final String name) {
        Objects.requireNonNull(name, "name");
        return (T) data.computeIfAbsent(name, key -> factory.get());
    }
}
