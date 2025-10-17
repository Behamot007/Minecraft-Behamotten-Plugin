package net.minecraft.nbt;

import java.util.ArrayList;

/**
 * Minimal list tag implementation backed by an ArrayList.
 */
public class ListTag extends ArrayList<CompoundTag> {
    private static final long serialVersionUID = 1L;

    public CompoundTag getCompound(final int index) {
        return get(index);
    }
}
