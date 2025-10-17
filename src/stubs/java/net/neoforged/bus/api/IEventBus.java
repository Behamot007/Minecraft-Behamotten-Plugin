package net.neoforged.bus.api;

import java.util.Objects;
import java.util.function.Consumer;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Minimal stub of NeoForge's event bus used for compilation in environments
 * where the real NeoForge classes are unavailable.
 */
public interface IEventBus {
    default void addListener(final Consumer<FMLCommonSetupEvent> listener) {
        Objects.requireNonNull(listener, "listener");
    }

    default void register(final Object listener) {
        Objects.requireNonNull(listener, "listener");
    }
}
