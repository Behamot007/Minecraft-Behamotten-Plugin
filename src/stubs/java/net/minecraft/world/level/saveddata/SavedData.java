package net.minecraft.world.level.saveddata;

import net.minecraft.nbt.CompoundTag;

/**
 * Base class for persistent data containers.
 */
public abstract class SavedData {
    private boolean dirty;

    protected void setDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public abstract CompoundTag save(CompoundTag tag);
}
