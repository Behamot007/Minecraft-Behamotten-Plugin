package net.neoforged.fml.javafmlmod;

import net.neoforged.bus.api.IEventBus;

/**
 * Minimal implementation returning a shared stub event bus.
 */
public final class FMLJavaModLoadingContext {
    private static final FMLJavaModLoadingContext INSTANCE = new FMLJavaModLoadingContext();
    private final IEventBus eventBus = new IEventBus() { };

    private FMLJavaModLoadingContext() {
    }

    public static FMLJavaModLoadingContext get() {
        return INSTANCE;
    }

    public IEventBus getModEventBus() {
        return eventBus;
    }
}
