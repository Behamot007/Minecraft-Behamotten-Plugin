package net.minecraft.server;

import java.util.Objects;

import net.minecraft.server.level.ServerLevel;

/**
 * Minimal server implementation that exposes the overworld level.
 */
public class MinecraftServer {
    private final ServerLevel overworld = new ServerLevel();

    public ServerLevel overworld() {
        return overworld;
    }
}
