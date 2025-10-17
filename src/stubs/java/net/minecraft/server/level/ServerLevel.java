package net.minecraft.server.level;

import net.minecraft.world.level.saveddata.SavedDataStorage;

/**
 * Minimal world representation exposing a SavedDataStorage instance.
 */
public class ServerLevel {
    private final SavedDataStorage dataStorage = new SavedDataStorage();

    public SavedDataStorage getDataStorage() {
        return dataStorage;
    }
}
