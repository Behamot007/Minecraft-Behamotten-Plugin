package net.neoforged.neoforge.common;

import net.neoforged.bus.api.IEventBus;

/**
 * Provides a simple global event bus placeholder.
 */
public final class MinecraftForge {
    public static final IEventBus EVENT_BUS = new IEventBus() { };

    private MinecraftForge() {
    }
}
